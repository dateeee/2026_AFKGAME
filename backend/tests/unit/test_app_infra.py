"""単体テスト: アプリ基盤（db/database.py・dependencies.py・exceptions.py・logging_config.py・main.py）

仕様: tech/tech_api.md「API共通仕様」（統一エラーレスポンス）、tech/tech_logging.md、tech/tech_auth.md
分岐観点:
  - get_db: セッションの生成と終了時クローズ
  - 認証: ヘッダなし / Bearer形式でない / 期限切れ / 署名不正 / sub欠落 / ユーザー不在 / プレイヤー不在 / 正常
  - 例外ハンドラ: AppError / HTTPException（detail 文字列・非文字列） / バリデーション / 未捕捉例外、
    request_id の有無でレスポンスに含む・含まない
  - ログ: mask_token の長短、mask_email の形式不正、text/json フォーマッターの extra・例外情報の有無、
    setup_logging の LOG_FORMAT 分岐と不正 LOG_LEVEL のフォールバック
  - main: ヘルスチェック
"""

import json
import logging
import sys

import jwt
import pytest
from fastapi import FastAPI, HTTPException, Request
from fastapi.testclient import TestClient
from pydantic import BaseModel
from sqlalchemy.exc import IntegrityError, OperationalError
from sqlalchemy.orm import Session

from app import config, main
from app.config import APP_VERSION
from app.db.database import get_db
from app.exceptions import AppError, register_exception_handlers
from app import logging_config
from app.logging_config import (
    JsonFormatter,
    TextFormatter,
    dev_only_token,
    mask_email,
    mask_token,
    setup_logging,
)
from app.models.item import InventoryItem
from app.models.player import Player, PlayerSettings, TowerClearRecord
from app.models.user import User
from app.services.auth_service import create_access_token
from tests.helpers import error_code, error_message

pytestmark = pytest.mark.unit


# ── db/database.py ──


class TestGetDb:
    def test_セッションを生成し終了時にクローズする(self):
        gen = get_db()
        session = next(gen)
        assert isinstance(session, Session)
        with pytest.raises(StopIteration):
            next(gen)  # ジェネレータ終了 → finally で close される


# ── dependencies.py ──


class TestGetCurrentUser:
    """401 の理由は error.code で判別できること（tech_logging.md エラーコード体系）"""

    def test_認証ヘッダなしは401(self, client):
        res = client.get("/api/game/state", headers={"Authorization": ""})
        assert res.status_code == 401
        assert error_code(res) == "AUTH_HEADER_MISSING"
        assert error_message(res) == "Authorization header missing"

    def test_Bearer形式でないヘッダは401(self, client):
        res = client.get("/api/game/state", headers={"Authorization": "Basic abc"})
        assert res.status_code == 401
        assert error_code(res) == "AUTH_INVALID_FORMAT"
        assert error_message(res) == "Invalid authorization header"

    def test_期限切れトークンは401(self, client, monkeypatch):
        def _expired(token):
            raise jwt.ExpiredSignatureError("expired")

        monkeypatch.setattr("app.dependencies.verify_access_token", _expired)
        res = client.get("/api/game/state")
        assert res.status_code == 401
        # 期限切れは refresh を試すべき状態。AUTH_INVALID_TOKEN（再ログイン）と区別する
        assert error_code(res) == "AUTH_TOKEN_EXPIRED"
        assert error_message(res) == "Token expired"

    def test_署名不正トークンは401(self, client, monkeypatch):
        def _invalid(token):
            raise jwt.InvalidTokenError("invalid")

        monkeypatch.setattr("app.dependencies.verify_access_token", _invalid)
        res = client.get("/api/game/state")
        assert res.status_code == 401
        assert error_code(res) == "AUTH_INVALID_TOKEN"
        assert error_message(res) == "Invalid token"

    def test_subのないペイロードは401(self, client, monkeypatch):
        monkeypatch.setattr("app.dependencies.verify_access_token", lambda token: {})
        res = client.get("/api/game/state")
        assert res.status_code == 401
        assert error_code(res) == "AUTH_INVALID_TOKEN"
        assert error_message(res) == "Invalid token payload"

    def test_存在しないユーザーのトークンは401(self, client):
        token = create_access_token("ghost-user", is_guest=False)
        res = client.get("/api/game/state", headers={"Authorization": f"Bearer {token}"})
        assert res.status_code == 401
        assert error_code(res) == "AUTH_USER_NOT_FOUND"
        assert error_message(res) == "User not found"


class TestGetCurrentPlayer:
    def test_プレイヤー未作成のユーザーは404(self, client, db):
        u = User(id="no-player-user", email="np@example.com", is_guest=False)
        db.add(u)
        db.commit()
        token = create_access_token(u.id, is_guest=False)
        res = client.get("/api/game/state", headers={"Authorization": f"Bearer {token}"})
        assert res.status_code == 404
        assert error_code(res) == "AUTH_PLAYER_NOT_FOUND"
        assert error_message(res) == "Player not found"

    def test_正常な認証でプレイヤーを取得できる(self, client):
        assert client.get("/api/game/state").status_code == 200


# ── exceptions.py ──


class _ValidatedBody(BaseModel):
    value: int


def _build_error_app() -> FastAPI:
    """例外ハンドラ検証用の最小アプリ（本体 app には手を入れない）"""
    test_app = FastAPI()
    register_exception_handlers(test_app)

    @test_app.get("/app-error")
    def _app_error(request: Request):
        request.state.request_id = "req-test-1"
        raise AppError("TEST_ERROR", "テストエラー", status_code=402)

    @test_app.get("/app-error-default")
    def _app_error_default():
        raise AppError("TEST_DEFAULT", "既定ステータスのエラー")

    @test_app.get("/http-error")
    def _http_error():
        raise HTTPException(status_code=404, detail="not found")

    @test_app.get("/http-error-dict")
    def _http_error_dict():
        raise HTTPException(status_code=418, detail={"reason": "teapot"})

    @test_app.post("/validated")
    def _validated(body: _ValidatedBody):
        return {"ok": True}

    @test_app.get("/unexpected")
    def _unexpected():
        raise RuntimeError("想定外")

    return test_app


class TestAppError:
    def test_コードとステータスを属性に持つ(self):
        exc = AppError("CODE_X", "メッセージ", status_code=418)
        assert exc.code == "CODE_X"
        assert exc.message == "メッセージ"
        assert exc.status_code == 418
        assert str(exc) == "メッセージ"


class TestExceptionHandlers:
    @pytest.fixture
    def error_client(self):
        return TestClient(_build_error_app(), raise_server_exceptions=False)

    def test_AppErrorは統一形式でrequest_id付きで返る(self, error_client):
        res = error_client.get("/app-error")
        assert res.status_code == 402
        body = res.json()["error"]
        assert body["code"] == "TEST_ERROR"
        assert body["message"] == "テストエラー"
        assert body["request_id"] == "req-test-1"

    def test_request_idが無ければレスポンスから省略される(self, error_client):
        res = error_client.get("/app-error-default")
        assert res.status_code == 400  # status_code 省略時の既定値
        assert "request_id" not in res.json()["error"]

    def test_HTTPExceptionはコードHTTPステータスに変換される(self, error_client):
        res = error_client.get("/http-error")
        assert res.status_code == 404
        assert error_code(res) == "HTTP_404"
        assert error_message(res) == "not found"

    def test_文字列でないdetailは文字列化される(self, error_client):
        res = error_client.get("/http-error-dict")
        assert res.status_code == 418
        assert "teapot" in error_message(res)

    def test_バリデーションエラーは422の統一形式(self, error_client):
        res = error_client.post("/validated", json={"value": "数値でない"})
        assert res.status_code == 422
        assert error_code(res) == "VALIDATION_ERROR"

    def test_未捕捉例外は500の統一形式(self, error_client):
        res = error_client.get("/unexpected")
        assert res.status_code == 500
        assert error_code(res) == "INTERNAL_UNEXPECTED_ERROR"


# ── logging_config.py ──


def _make_record(msg="テストメッセージ", exc_info=None, **extras) -> logging.LogRecord:
    record = logging.LogRecord(
        name="afkgame.test",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg=msg,
        args=(),
        exc_info=exc_info,
    )
    for key, value in extras.items():
        setattr(record, key, value)
    return record


class TestMaskToken:
    @pytest.mark.parametrize(
        "token",
        [
            "abc",       # 8文字未満
            "12345678",  # 境界そのもの（8文字）
        ],
    )
    def test_8文字以下は全体をマスクする(self, token):
        assert mask_token(token) == "****"

    def test_9文字以上は先頭と末尾のみ表示する(self):
        assert mask_token("abcdefghijkl") == "abcd****ijkl"


class TestDevOnlyToken:
    """メール送信の実装までの暫定措置（backend-review ISSUE-104）"""

    def test_開発環境では生のトークンを返す(self, monkeypatch):
        monkeypatch.setattr(logging_config, "IS_PRODUCTION", False)
        assert dev_only_token("abcdefghijkl") == "abcdefghijkl"

    def test_本番環境ではマスクする(self, monkeypatch):
        monkeypatch.setattr(logging_config, "IS_PRODUCTION", True)
        assert dev_only_token("abcdefghijkl") == "abcd****ijkl"


class TestMaskEmail:
    def test_先頭2文字とドメインのみ表示する(self):
        assert mask_email("taro@example.com") == "ta***@example.com"

    def test_アットマークがなければ全体をマスクする(self):
        assert mask_email("not-an-email") == "***"


class TestTextFormatter:
    def test_extra属性がkey_value形式で付加される(self):
        out = TextFormatter().format(_make_record(player_id="p1", gold=100))
        assert "afkgame.test" in out
        assert "player_id=p1" in out
        assert "gold=100" in out

    def test_extraがなければ付加情報なしで出力される(self):
        out = TextFormatter().format(_make_record())
        assert out.endswith("テストメッセージ")


class TestJsonFormatter:
    def test_extra属性がJSONのキーとして含まれる(self):
        out = json.loads(JsonFormatter().format(_make_record(player_id="p1")))
        assert out["message"] == "テストメッセージ"
        assert out["level"] == "INFO"
        assert out["player_id"] == "p1"
        assert "exception" not in out  # 例外情報なし

    def test_例外情報がexceptionキーに含まれる(self):
        try:
            raise ValueError("boom")
        except ValueError:
            exc_info = sys.exc_info()
        out = json.loads(JsonFormatter().format(_make_record(exc_info=exc_info)))
        assert "ValueError: boom" in out["exception"]


class TestSetupLogging:
    @pytest.fixture(autouse=True)
    def _restore_logger(self):
        """afkgame ロガーのグローバル状態をテスト後に復元する"""
        logger = logging.getLogger("afkgame")
        handlers = logger.handlers[:]
        level = logger.level
        propagate = logger.propagate
        yield
        logger.handlers = handlers
        logger.setLevel(level)
        logger.propagate = propagate

    # 環境変数の参照は config.py に集約したため、上書きは引数で行う（config.LOG_* が既定）

    def test_既定はテキストフォーマッター(self):
        setup_logging()
        logger = logging.getLogger("afkgame")
        assert isinstance(logger.handlers[0].formatter, TextFormatter)
        assert logger.level == logging.INFO
        assert logger.propagate is False

    def test_json指定でJSONフォーマッターになる(self):
        setup_logging(fmt="json")
        logger = logging.getLogger("afkgame")
        assert isinstance(logger.handlers[0].formatter, JsonFormatter)

    def test_不正なLOG_LEVELはINFOにフォールバックする(self):
        setup_logging(level="verbose")
        assert logging.getLogger("afkgame").level == logging.INFO


# ── main.py ──


class TestConfig:
    """tech_operations.md §12.2 環境変数一覧

    分岐観点: 環境変数あり（上書き）/ なし（既定値）
    """

    @staticmethod
    def _reload_config():
        import importlib

        from app import config

        return importlib.reload(config)

    def test_DATABASE_URLは環境変数で上書きできる(self, monkeypatch):
        monkeypatch.setenv("DATABASE_URL", "sqlite:///./e2e.db")
        try:
            assert self._reload_config().DATABASE_URL == "sqlite:///./e2e.db"
        finally:
            monkeypatch.delenv("DATABASE_URL")
            self._reload_config()

    def test_DATABASE_URLの既定値はローカルのSQLite(self, monkeypatch):
        monkeypatch.delenv("DATABASE_URL", raising=False)
        assert self._reload_config().DATABASE_URL == "sqlite:///./afkgame.db"

    def test_JWT_SECRETの既定値はHS256の推奨鍵長を満たす(self):
        # RFC 7518 §3.2: HS256 の鍵は 32 バイト以上
        assert len(config.DEFAULT_JWT_SECRET.encode()) >= 32


class TestValidateProductionSettings:
    """tech_operations.md §12.2 起動時バリデーション

    分岐観点: 本番 × 既定値（起動中止）/ 本番 × 設定済み / 非本番（既定値でも起動可）
    """

    def test_本番でJWT_SECRETが既定値のままなら起動を中止する(self, monkeypatch):
        monkeypatch.setattr(config, "IS_PRODUCTION", True)
        monkeypatch.setattr(config, "JWT_SECRET", config.DEFAULT_JWT_SECRET)
        with pytest.raises(RuntimeError, match="JWT_SECRET"):
            config.validate_production_settings()

    def test_本番でもJWT_SECRETが設定済みなら起動できる(self, monkeypatch):
        monkeypatch.setattr(config, "IS_PRODUCTION", True)
        monkeypatch.setattr(config, "JWT_SECRET", "x" * 32)
        config.validate_production_settings()

    def test_非本番は既定値のままでも起動できる(self, monkeypatch):
        monkeypatch.setattr(config, "IS_PRODUCTION", False)
        monkeypatch.setattr(config, "JWT_SECRET", config.DEFAULT_JWT_SECRET)
        config.validate_production_settings()


class TestUniqueConstraints:
    """query-then-create するテーブルの重複行をDBで防ぐ（backend-review ISSUE-108）

    二重リクエスト（ポーリングと復帰処理の同時実行等）で重複行ができると、
    `.first()` がどちらを返すか不定になり進捗・所持数が欠落する。
    """

    def _expect_conflict(self, db, row) -> None:
        db.add(row)
        with pytest.raises(IntegrityError):
            db.commit()
        db.rollback()

    def test_塔クリア記録は塔ごとに1件(self, db, player, tower_record):
        tower_record("goblin_tower")
        self._expect_conflict(db, TowerClearRecord(player_id=player.id, tower_id="goblin_tower"))

    def test_所持アイテムはアイテムごとに1件(self, db, player):
        self._expect_conflict(db, InventoryItem(player_id=player.id, item_id="hp_potion"))

    def test_プレイヤー設定はプレイヤーごとに1件(self, db, player):
        self._expect_conflict(db, PlayerSettings(player_id=player.id))

    def test_ユーザーに紐づくプレイヤーは1件(self, db, player, user):
        self._expect_conflict(db, Player(user_id=user.id))


class TestInitSchema:
    """スキーマの正は alembic/versions。create_all は開発・テスト用のフォールバック

    分岐観点: 非本番（create_all する）/ 本番（しない）
    """

    def test_非本番ではcreate_allでテーブルを生成する(self, monkeypatch):
        called = []
        monkeypatch.setattr(main, "IS_PRODUCTION", False)
        monkeypatch.setattr(main.Base.metadata, "create_all", lambda **kw: called.append(kw))
        main.init_schema()
        assert len(called) == 1

    def test_本番ではcreate_allを実行しない(self, monkeypatch):
        called = []
        monkeypatch.setattr(main, "IS_PRODUCTION", True)
        monkeypatch.setattr(main.Base.metadata, "create_all", lambda **kw: called.append(kw))
        main.init_schema()
        assert called == []


class TestHealthCheck:
    """tech_operations.md §12.3 / tech_api.md §運用

    分岐観点: DB疎通OK（200）/ DB疎通NG（503）
    """

    def test_DB疎通できれば200でバージョンとDB状態を返す(self, client):
        res = client.get("/health")
        assert res.status_code == 200
        assert res.json() == {"status": "ok", "version": APP_VERSION, "db": "ok"}

    def test_DB疎通に失敗すれば503でdegradedを返す(self, client, monkeypatch):
        def _fail(*args, **kwargs):
            raise OperationalError("SELECT 1", {}, Exception("db is down"))

        monkeypatch.setattr(Session, "execute", _fail)

        res = client.get("/health")
        assert res.status_code == 503
        assert res.json() == {"status": "degraded", "db": "error"}

    def test_ヘルスチェックは認証を要求しない(self, client):
        client.headers.pop("Authorization")
        assert client.get("/health").status_code == 200
