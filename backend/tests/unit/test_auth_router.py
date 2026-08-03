"""単体テスト: 認証ルーター（/api/auth/*）

仕様: tech/tech_auth.md §2 登録方法・§3 認証フロー・§5 認証API
分岐観点:
  - ゲスト作成: guest_ プレフィックスID発行と初期データ（Player/キャラ/設定/ポーション）生成
  - 登録: パスワード長の境界（8文字以上） / メール重複 / 表示名の指定・省略
  - ログイン: ユーザー不在 / パスワード未設定（Google連携等） / 不一致 / 成功時の最終ログイン更新
  - リフレッシュ: 無効トークン（401） / 正常ローテーション / 再利用検知
  - ログアウト: トークン該当あり（無効化） / なし（素通り）
  - Google認証: GOOGLE_CLIENT_ID 未設定 / 設定済みでも未実装（両方501）
  - アカウント連携: 本登録済み拒否 / メール+パスワード / Google / どちらも無し、の各枝
  - パスワードリセット: ユーザー存在有無で同一レスポンス（トークン発行有無のみ差分）
"""

import hashlib
from datetime import datetime

import pytest
from fastapi.testclient import TestClient

from app.config import INITIAL_CHARACTER, INITIAL_POTIONS
from app.db.database import get_db
from app.main import app
from app.models.character import Character
from app.models.item import InventoryItem
from app.models.player import Player, PlayerSettings
from app.models.user import (
    TOKEN_PURPOSE_PASSWORD_RESET,
    EmailVerificationToken,
    RefreshToken,
    User,
)
from app.services import auth_service
from app.services.auth_service import create_email_verification_token
from tests.helpers import error_code, error_message

pytestmark = pytest.mark.unit


@pytest.fixture(autouse=True)
def fast_bcrypt(monkeypatch):
    """テスト高速化: bcryptコストファクタを許容最小値(4)に下げる"""
    monkeypatch.setattr(auth_service, "BCRYPT_COST_FACTOR", 4)


@pytest.fixture
def client(db):
    """認証ヘッダなしのTestClient（conftestのclientと異なりPlayerを事前作成しない）"""
    app.dependency_overrides[get_db] = lambda: db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


def _register(client, email="user@example.com", password="password123", display_name=None):
    body = {"email": email, "password": password}
    if display_name is not None:
        body["displayName"] = display_name
    return client.post("/api/auth/register", json=body)


def _login(client, email="user@example.com", password="password123"):
    return client.post("/api/auth/login", json={"email": email, "password": password})


def _guest_headers(client) -> tuple[dict, str]:
    """ゲストを作成し (認証ヘッダ, user_id) を返す"""
    body = client.post("/api/auth/guest").json()
    return {"Authorization": f"Bearer {body['accessToken']}"}, body["user"]["id"]


class TestCreateGuest:
    def test_ゲスト作成でguestプレフィックスのIDとトークンペアが返る(self, client):
        res = client.post("/api/auth/guest")
        assert res.status_code == 200
        body = res.json()
        assert body["accessToken"]
        assert body["refreshToken"]
        assert body["user"]["id"].startswith("guest_")
        assert body["user"]["isGuest"] is True
        assert body["user"]["displayName"] == "冒険者"

    def test_ゲスト作成で初期データが一式作られる(self, client, db):
        user_id = client.post("/api/auth/guest").json()["user"]["id"]
        player = db.query(Player).filter_by(user_id=user_id).one()
        assert db.query(PlayerSettings).filter_by(player_id=player.id).count() == 1
        character = db.query(Character).filter_by(player_id=player.id).one()
        assert character.name == INITIAL_CHARACTER["name"]
        items = db.query(InventoryItem).filter_by(player_id=player.id).all()
        assert {(i.item_id, i.quantity) for i in items} == set(INITIAL_POTIONS.items())


class TestRegister:
    def test_登録成功でuserプレフィックスのIDと未確認メール状態が返る(self, client):
        res = _register(client)
        assert res.status_code == 200
        user = res.json()["user"]
        assert user["id"].startswith("user_")
        assert user["email"] == "user@example.com"
        assert user["isGuest"] is False
        assert user["emailVerified"] is False

    @pytest.mark.parametrize(
        ("password", "expected_status"),
        [
            # 最低長はスキーマ検証（tech_api.md §エラー: 型・範囲違反は 422）
            ("1234567", 422),   # 7文字 → 最低長未満
            ("12345678", 200),  # 8文字ちょうど → 境界で許可
        ],
    )
    def test_パスワード最低長の境界(self, client, password, expected_status):
        res = _register(client, password=password)
        assert res.status_code == expected_status

    def test_登録済みメールは409(self, client):
        _register(client)
        res = _register(client)
        assert res.status_code == 409
        assert error_code(res) == "AUTH_EMAIL_TAKEN"
        assert error_message(res) == "Email already registered"

    def test_表示名を省略するとメールのローカル部になる(self, client):
        assert _register(client).json()["user"]["displayName"] == "user"

    def test_表示名を指定すればそのまま使われる(self, client):
        res = _register(client, display_name="テスト勇者")
        assert res.json()["user"]["displayName"] == "テスト勇者"

    def test_登録時にメール確認トークンが発行される(self, client, db):
        user_id = _register(client).json()["user"]["id"]
        assert db.query(EmailVerificationToken).filter_by(user_id=user_id).count() == 1


class TestLogin:
    def test_ログイン成功でトークンペアが返る(self, client):
        _register(client)
        res = _login(client)
        assert res.status_code == 200
        assert res.json()["user"]["email"] == "user@example.com"
        assert res.json()["refreshToken"]

    def test_ログイン成功で最終ログイン日時が更新される(self, client, db):
        user_id = _register(client).json()["user"]["id"]
        u = db.query(User).filter_by(id=user_id).one()
        u.last_login_at = datetime(2020, 1, 1)
        db.commit()
        assert _login(client).status_code == 200
        db.refresh(u)
        assert u.last_login_at.replace(tzinfo=None) > datetime(2020, 1, 1)

    def test_未登録メールは401(self, client):
        res = _login(client, email="nobody@example.com")
        assert res.status_code == 401
        assert error_code(res) == "AUTH_INVALID_CREDENTIALS"
        assert error_message(res) == "Invalid credentials"

    def test_パスワード未設定ユーザーは401(self, client, db):
        # Google連携のみ等で password_hash が null のユーザー
        db.add(User(id="google-user", email="g@example.com", is_guest=False, password_hash=None))
        db.commit()
        assert _login(client, email="g@example.com").status_code == 401

    def test_パスワード不一致は401(self, client):
        _register(client)
        res = _login(client, password="wrongpass123")
        assert res.status_code == 401
        assert error_code(res) == "AUTH_INVALID_CREDENTIALS"
        assert error_message(res) == "Invalid credentials"


class TestRefresh:
    def test_リフレッシュ成功で新しいトークンペアに交換される(self, client):
        old = client.post("/api/auth/guest").json()["refreshToken"]
        res = client.post("/api/auth/refresh", json={"refreshToken": old})
        assert res.status_code == 200
        assert res.json()["refreshToken"] != old

    def test_ローテーション済みトークンの再利用は401(self, client):
        old = client.post("/api/auth/guest").json()["refreshToken"]
        client.post("/api/auth/refresh", json={"refreshToken": old})
        res = client.post("/api/auth/refresh", json={"refreshToken": old})
        assert res.status_code == 401
        assert error_code(res) == "AUTH_REFRESH_INVALID"
        assert error_message(res) == "Refresh token reuse detected"

    def test_未知のリフレッシュトークンは401(self, client):
        res = client.post("/api/auth/refresh", json={"refreshToken": "bogus"})
        assert res.status_code == 401
        assert error_code(res) == "AUTH_REFRESH_INVALID"
        assert error_message(res) == "Invalid refresh token"


class TestLogout:
    def test_ログアウトでリフレッシュトークンが無効化される(self, client, db):
        raw = client.post("/api/auth/guest").json()["refreshToken"]
        res = client.post("/api/auth/logout", json={"refreshToken": raw})
        assert res.status_code == 200
        assert res.json() == {"status": "ok"}
        token_hash = hashlib.sha256(raw.encode()).hexdigest()
        assert db.query(RefreshToken).filter_by(token_hash=token_hash).one().revoked is True

    def test_未知のトークンでも200でokを返す(self, client):
        res = client.post("/api/auth/logout", json={"refreshToken": "bogus"})
        assert res.status_code == 200
        assert res.json() == {"status": "ok"}


class TestVerifyEmail:
    def test_有効なトークンでメール確認が完了する(self, client, db, user):
        raw = create_email_verification_token(user.id, db)
        db.commit()
        res = client.get("/api/auth/verify-email", params={"token": raw})
        assert res.status_code == 200
        assert res.json()["status"] == "ok"
        db.refresh(user)
        assert user.email_verified is True

    def test_無効なトークンは400(self, client):
        res = client.get("/api/auth/verify-email", params={"token": "bogus"})
        assert res.status_code == 400
        assert error_code(res) == "AUTH_VERIFICATION_INVALID"
        assert error_message(res) == "Invalid verification token"


class TestGoogleAuth:
    def test_クライアントID未設定なら設定なしの501(self, client, monkeypatch):
        monkeypatch.setattr("app.routers.auth.GOOGLE_CLIENT_ID", "")
        res = client.post("/api/auth/google", json={"authCode": "code"})
        assert res.status_code == 501
        assert error_code(res) == "AUTH_GOOGLE_NOT_CONFIGURED"
        assert error_message(res) == "Google OAuth is not configured"

    def test_クライアントID設定済みでも未実装の501(self, client, monkeypatch):
        monkeypatch.setattr("app.routers.auth.GOOGLE_CLIENT_ID", "dummy-client-id")
        res = client.post("/api/auth/google", json={"authCode": "code"})
        assert res.status_code == 501
        assert error_code(res) == "AUTH_GOOGLE_NOT_IMPLEMENTED"
        assert error_message(res) == "Google OAuth not yet implemented"


class TestLinkAccount:
    def _link(self, client, headers, **body):
        return client.post("/api/auth/link-account", json=body, headers=headers)

    def test_メール連携でゲストが同一IDのまま本登録に移行する(self, client, db):
        headers, user_id = _guest_headers(client)
        res = self._link(client, headers, email="linked@example.com", password="password123")
        assert res.status_code == 200
        user = res.json()["user"]
        assert user["id"] == user_id  # アカウントの作り直しは行わない（仕様 §3）
        assert user["isGuest"] is False
        assert user["email"] == "linked@example.com"
        assert user["displayName"] == "linked"

    def test_メール連携で旧トークンは全無効化され新トークンのみ有効(self, client, db):
        headers, user_id = _guest_headers(client)
        new_refresh = self._link(
            client, headers, email="linked@example.com", password="password123"
        ).json()["refreshToken"]
        records = db.query(RefreshToken).filter_by(user_id=user_id).all()
        active = [r for r in records if not r.revoked]
        assert len(active) == 1
        assert active[0].token_hash == hashlib.sha256(new_refresh.encode()).hexdigest()

    def test_本登録済みアカウントの連携は400(self, client):
        token = _register(client).json()["accessToken"]
        headers = {"Authorization": f"Bearer {token}"}
        res = self._link(client, headers, email="x@example.com", password="password123")
        assert res.status_code == 400
        assert error_code(res) == "AUTH_ALREADY_REGISTERED"
        assert error_message(res) == "Account is already registered"

    def test_8文字未満のパスワードは422(self, client):
        headers, _ = _guest_headers(client)
        res = self._link(client, headers, email="a@example.com", password="1234567")
        assert res.status_code == 422

    def test_登録済みメールへの連携は409(self, client):
        _register(client, email="taken@example.com")
        headers, _ = _guest_headers(client)
        res = self._link(client, headers, email="taken@example.com", password="password123")
        assert res.status_code == 409
        assert error_code(res) == "AUTH_EMAIL_TAKEN"
        assert error_message(res) == "Email already registered"

    def test_Google連携はクライアントID未設定なら設定なしの501(self, client, monkeypatch):
        monkeypatch.setattr("app.routers.auth.GOOGLE_CLIENT_ID", "")
        headers, _ = _guest_headers(client)
        res = self._link(client, headers, googleAuthCode="code")
        assert res.status_code == 501
        assert error_code(res) == "AUTH_GOOGLE_NOT_CONFIGURED"
        assert error_message(res) == "Google OAuth is not configured"

    def test_Google連携はクライアントID設定済みでも未実装の501(self, client, monkeypatch):
        monkeypatch.setattr("app.routers.auth.GOOGLE_CLIENT_ID", "dummy-client-id")
        headers, _ = _guest_headers(client)
        res = self._link(client, headers, googleAuthCode="code")
        assert res.status_code == 501
        assert error_code(res) == "AUTH_GOOGLE_NOT_IMPLEMENTED"
        assert error_message(res) == "Google OAuth not yet implemented"

    def test_メールのみでパスワードなしは400(self, client):
        # email and password の短絡評価: 右側（password）が偽
        headers, _ = _guest_headers(client)
        res = self._link(client, headers, email="a@example.com")
        assert res.status_code == 400
        assert error_code(res) == "AUTH_LINK_PAYLOAD_INVALID"
        assert error_message(res) == "Provide email+password or googleAuthCode"

    def test_連携情報なしは400(self, client):
        headers, _ = _guest_headers(client)
        res = self._link(client, headers)
        assert res.status_code == 400
        assert error_code(res) == "AUTH_LINK_PAYLOAD_INVALID"
        assert error_message(res) == "Provide email+password or googleAuthCode"


class TestPasswordResetRequest:
    OK_BODY = {"status": "ok", "message": "If the email exists, a reset link has been sent"}

    def test_登録ユーザーにはリセットトークンが発行される(self, client, db):
        user_id = _register(client).json()["user"]["id"]
        res = client.post("/api/auth/password-reset/request", json={"email": "user@example.com"})
        assert res.status_code == 200
        assert res.json() == self.OK_BODY
        # 登録時のメール確認トークン1件 + リセットトークン1件
        assert db.query(EmailVerificationToken).filter_by(user_id=user_id).count() == 2

    def test_未登録メールでも同じレスポンスを返しトークンは発行しない(self, client, db):
        res = client.post("/api/auth/password-reset/request", json={"email": "nobody@example.com"})
        assert res.status_code == 200
        assert res.json() == self.OK_BODY
        assert db.query(EmailVerificationToken).count() == 0

    def test_パスワード未設定ユーザーにはトークンを発行しない(self, client, db, user):
        # conftest の user は password_hash なし（Google連携等を想定）
        res = client.post("/api/auth/password-reset/request", json={"email": user.email})
        assert res.status_code == 200
        assert res.json() == self.OK_BODY
        assert db.query(EmailVerificationToken).count() == 0


class TestPasswordResetConfirm:
    def _confirm(self, client, token, new_password="newpassword456"):
        return client.post(
            "/api/auth/password-reset/confirm",
            json={"token": token, "newPassword": new_password},
        )

    def test_リセット成功で全トークン無効化後に新パスワードでログインできる(self, client, db):
        user_id = _register(client).json()["user"]["id"]
        raw = create_email_verification_token(
            user_id, db, purpose=TOKEN_PURPOSE_PASSWORD_RESET
        )
        db.commit()

        res = self._confirm(client, raw)
        assert res.status_code == 200
        assert res.json() == {"status": "ok", "message": "Password has been reset"}
        assert all(r.revoked for r in db.query(RefreshToken).filter_by(user_id=user_id))
        assert _login(client, password="newpassword456").status_code == 200

    def test_メール確認トークンではリセットできない(self, client, db):
        """用途の異なるトークンは流用できない（tech_auth.md §6 の用途分離）"""
        user_id = _register(client).json()["user"]["id"]
        raw = create_email_verification_token(user_id, db)  # 既定 = verify_email
        db.commit()

        res = self._confirm(client, raw)
        assert res.status_code == 400
        assert error_message(res) == "Invalid verification token"
        # 副作用でパスワードが変わっていないこと
        assert _login(client).status_code == 200

    def test_リセットトークンではメール確認できない(self, client, db):
        user_id = _register(client).json()["user"]["id"]
        raw = create_email_verification_token(
            user_id, db, purpose=TOKEN_PURPOSE_PASSWORD_RESET
        )
        db.commit()

        res = client.get("/api/auth/verify-email", params={"token": raw})
        assert res.status_code == 400
        assert error_message(res) == "Invalid verification token"

    def test_リセット完了でメール確認済みにはならない(self, client, db):
        user_id = _register(client).json()["user"]["id"]
        raw = create_email_verification_token(
            user_id, db, purpose=TOKEN_PURPOSE_PASSWORD_RESET
        )
        db.commit()

        assert self._confirm(client, raw).status_code == 200
        assert db.query(User).filter_by(id=user_id).first().email_verified is False

    def test_8文字未満の新パスワードは422(self, client):
        res = self._confirm(client, "any-token", new_password="1234567")
        assert res.status_code == 422

    def test_無効なトークンは400(self, client):
        res = self._confirm(client, "bogus")
        assert res.status_code == 400
        assert error_code(res) == "AUTH_RESET_TOKEN_INVALID"
        assert error_message(res) == "Invalid verification token"
