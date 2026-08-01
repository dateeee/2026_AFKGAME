"""単体テスト: 認証サービス（services/auth_service.py）

仕様: tech/tech_auth.md §1 認証方式・§4 JWT構造・§6 認証関連DBモデル
分岐観点:
  - パスワード検証の一致 / 不一致
  - リフレッシュトークン: 未知 / 再利用（不正検知で全無効化） / 期限切れ / ユーザー不在 / 正常ローテーション
  - 期限比較は naive datetime（DB由来）を UTC 扱いする三項の両側
  - revoke_all_tokens は対象ユーザーのみ（他ユーザーのトークンは無効化しない）
  - メール確認トークン: 未知 / 使用済み / 期限切れ / ユーザー不在 / 正常（used と email_verified 更新）
"""

from datetime import datetime

import pytest

from app.models.user import EmailVerificationToken, RefreshToken, User
from app.services import auth_service
from app.services.auth_service import (
    _hash_token,
    create_access_token,
    create_email_verification_token,
    create_refresh_token,
    hash_password,
    refresh_tokens,
    revoke_all_tokens,
    verify_access_token,
    verify_email_token,
    verify_password,
)

pytestmark = pytest.mark.unit


@pytest.fixture(autouse=True)
def fast_bcrypt(monkeypatch):
    """テスト高速化: bcryptコストファクタを許容最小値(4)に下げる"""
    monkeypatch.setattr(auth_service, "BCRYPT_COST_FACTOR", 4)


class TestPassword:
    def test_ハッシュ化したパスワードを検証できる(self):
        hashed = hash_password("secret-password")
        assert hashed != "secret-password"
        assert verify_password("secret-password", hashed) is True

    def test_異なるパスワードは検証に失敗する(self):
        hashed = hash_password("secret-password")
        assert verify_password("wrong-password", hashed) is False


class TestAccessToken:
    def test_ペイロードが仕様どおりの構造を持つ(self):
        token = create_access_token("user-1", is_guest=True)
        payload = verify_access_token(token)
        assert payload["sub"] == "user-1"
        assert payload["type"] == "access"
        assert payload["role"] == "user"
        assert payload["isGuest"] is True
        assert payload["exp"] > payload["iat"]


class TestCreateRefreshToken:
    def test_生トークンを返しDBにはハッシュのみ保存する(self, db, user):
        raw = create_refresh_token(user.id, db)
        record = db.query(RefreshToken).filter_by(user_id=user.id).one()
        assert record.token_hash == _hash_token(raw)
        assert record.token_hash != raw
        assert record.revoked is False


class TestRefreshTokens:
    def test_未知のトークンはエラー(self, db):
        with pytest.raises(ValueError, match="Invalid refresh token"):
            refresh_tokens("no-such-token", db)

    def test_無効化済みトークンの再利用で全トークンを無効化する(self, db, user):
        reused = create_refresh_token(user.id, db)
        create_refresh_token(user.id, db)  # 有効なトークンも巻き添えで無効化される
        db.query(RefreshToken).filter_by(token_hash=_hash_token(reused)).update({"revoked": True})

        with pytest.raises(ValueError, match="Refresh token reuse detected"):
            refresh_tokens(reused, db)

        assert all(r.revoked for r in db.query(RefreshToken).filter_by(user_id=user.id))

    def test_期限切れトークンはエラー(self, db, user):
        raw = create_refresh_token(user.id, db)
        record = db.query(RefreshToken).filter_by(token_hash=_hash_token(raw)).one()
        record.expires_at = datetime(2020, 1, 1)  # naive datetime → UTC扱いで比較する分岐
        db.flush()

        with pytest.raises(ValueError, match="Refresh token expired"):
            refresh_tokens(raw, db)

    def test_ユーザー不在はエラー(self, db):
        raw = create_refresh_token("ghost-user", db)
        with pytest.raises(ValueError, match="User not found"):
            refresh_tokens(raw, db)

    def test_成功時はローテーションして新トークンペアを返す(self, db, user):
        old_raw = create_refresh_token(user.id, db)

        new_access, new_refresh, returned = refresh_tokens(old_raw, db)

        assert returned.id == user.id
        assert new_refresh != old_raw
        assert verify_access_token(new_access)["sub"] == user.id
        old = db.query(RefreshToken).filter_by(token_hash=_hash_token(old_raw)).one()
        assert old.revoked is True
        new = db.query(RefreshToken).filter_by(token_hash=_hash_token(new_refresh)).one()
        assert new.revoked is False


class TestRevokeAllTokens:
    def test_対象ユーザーの有効トークンのみ全て無効化する(self, db, user):
        create_refresh_token(user.id, db)
        create_refresh_token(user.id, db)
        other = User(id="other-user", is_guest=True)
        db.add(other)
        db.flush()
        other_raw = create_refresh_token(other.id, db)

        revoke_all_tokens(user.id, db)

        assert all(r.revoked for r in db.query(RefreshToken).filter_by(user_id=user.id))
        other_record = db.query(RefreshToken).filter_by(token_hash=_hash_token(other_raw)).one()
        assert other_record.revoked is False


class TestEmailVerificationToken:
    def test_生トークンを返しDBにはハッシュのみ保存する(self, db, user):
        raw = create_email_verification_token(user.id, db)
        record = db.query(EmailVerificationToken).filter_by(user_id=user.id).one()
        assert record.token_hash == _hash_token(raw)
        assert record.token_hash != raw
        assert record.used is False

    def test_検証成功でemail_verifiedとusedが更新される(self, db, user):
        raw = create_email_verification_token(user.id, db)

        verified = verify_email_token(raw, db)

        assert verified.id == user.id
        assert verified.email_verified is True
        record = db.query(EmailVerificationToken).filter_by(user_id=user.id).one()
        assert record.used is True

    def test_未知のトークンはエラー(self, db):
        with pytest.raises(ValueError, match="Invalid verification token"):
            verify_email_token("no-such-token", db)

    def test_使用済みトークンはエラー(self, db, user):
        raw = create_email_verification_token(user.id, db)
        verify_email_token(raw, db)

        with pytest.raises(ValueError, match="Token already used"):
            verify_email_token(raw, db)

    def test_期限切れトークンはエラー(self, db, user):
        raw = create_email_verification_token(user.id, db)
        record = db.query(EmailVerificationToken).filter_by(user_id=user.id).one()
        record.expires_at = datetime(2020, 1, 1)  # naive datetime → UTC扱いで比較する分岐
        db.flush()

        with pytest.raises(ValueError, match="Verification token expired"):
            verify_email_token(raw, db)

    def test_ユーザー不在はエラー(self, db):
        raw = create_email_verification_token("ghost-user", db)
        with pytest.raises(ValueError, match="User not found"):
            verify_email_token(raw, db)
