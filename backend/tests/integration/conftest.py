"""結合テスト共通フィクスチャ（L1: API統合テスト）

単体テストと違い、プレイヤーの作成も認証も**すべてAPI経由**で行う。
DBは `tests/conftest.py` の `db`（インメモリSQLite）を共有し、`get_db` を差し替える。

外部要因（乱数・時刻）はシナリオの成否に影響する範囲だけ固定する
（[.claude/skills/integration-test/SKILL.md](../../../.claude/skills/integration-test/SKILL.md) §3）。
"""

import logging
import random
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.db.database import Base, get_db
from app.main import app
from app.models.player import Player

# 乱数の固定シード。戦闘・ドロップの結果を実行のたびに同じにする
INTEGRATION_SEED = 20260802


@pytest.fixture
def db() -> Session:
    """結合テスト用のDBセッション

    `tests/conftest.py` の `db` を上書きする。単体テスト用は `expire_on_commit=False`
    だが、それではコミット後もリレーションが古いまま残り、本番（既定は True）と
    挙動が変わる。API をまたいだ状態の引き継ぎを検証するため、既定のまま使う。
    """
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=engine)
    session = sessionmaker(bind=engine, class_=Session)()
    try:
        yield session
    finally:
        session.close()
        engine.dispose()


@pytest.fixture
def api(db) -> TestClient:
    """未認証の TestClient。認証はシナリオ内でAPIを叩いて行う"""
    app.dependency_overrides[get_db] = lambda: db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture
def guest(api: TestClient) -> dict:
    """POST /api/auth/guest で新規プレイヤーを作り、以降のリクエストを認証済みにする"""
    res = api.post("/api/auth/guest")
    assert res.status_code == 200, res.text
    body = res.json()
    api.headers.update({"Authorization": f"Bearer {body['accessToken']}"})
    return body


@pytest.fixture
def guest_player(db, guest: dict) -> Player:
    """ゲストに紐づく Player。時刻の巻き戻しなどテスト都合の操作に使う"""
    return db.query(Player).filter_by(user_id=guest["user"]["id"]).one()


@pytest.fixture
def rewind(db):
    """last_tick_at を `seconds` 秒前へ戻し、その時間だけ放置した状態を作る"""

    def _rewind(player: Player, seconds: float) -> None:
        past = datetime.now(timezone.utc) - timedelta(seconds=seconds)
        # SQLite はタイムゾーンを保持しないため naive で保存する
        player.last_tick_at = past.replace(tzinfo=None)
        db.commit()

    return _rewind


@pytest.fixture
def fixed_rng():
    """乱数を固定する。ダメージ分散・エンカウント・ドロップ内容を再現可能にする"""
    random.seed(INTEGRATION_SEED)
    yield
    random.seed()


@pytest.fixture
def app_logs(caplog):
    """アプリのログを caplog で拾えるようにする

    `logging_config` は `afkgame` ロガーの `propagate` を False にするため、
    caplog（rootにハンドラを付ける）だけでは記録を受け取れない。
    メール確認トークンのように**ログ経由でしか外へ出ない値**の取得に使う。
    """
    logger = logging.getLogger("afkgame")
    logger.addHandler(caplog.handler)
    try:
        with caplog.at_level(logging.INFO, logger="afkgame"):
            yield caplog
    finally:
        logger.removeHandler(caplog.handler)


@pytest.fixture
def always_drop(monkeypatch):
    """装備ドロップ抽選を必ず成立させる（ドロップ率そのものは結合の検証対象ではない）"""
    from app.master_data import equipment as equipment_master

    monkeypatch.setattr(equipment_master, "_roll_drop", lambda is_boss: True)
