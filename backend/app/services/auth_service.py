"""認証サービス（Phase 2）"""

import hashlib
import logging
import secrets
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt
from sqlalchemy.orm import Session

from app.config import (
    ACCESS_TOKEN_EXPIRE_MINUTES,
    BCRYPT_COST_FACTOR,
    EMAIL_VERIFICATION_EXPIRE_HOURS,
    JWT_ALGORITHM,
    JWT_SECRET,
    REFRESH_TOKEN_EXPIRE_DAYS,
)
from app.models.user import EmailVerificationToken, RefreshToken, User

logger = logging.getLogger("afkgame.auth")


# ── パスワード ──


def hash_password(password: str) -> str:
    salt = bcrypt.gensalt(rounds=BCRYPT_COST_FACTOR)
    return bcrypt.hashpw(password.encode(), salt).decode()


def verify_password(password: str, password_hash: str) -> bool:
    return bcrypt.checkpw(password.encode(), password_hash.encode())


# ── JWT ──


def create_access_token(user_id: str, is_guest: bool) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": user_id,
        "type": "access",
        "role": "user",
        "isGuest": is_guest,
        "iat": now,
        "exp": now + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)


def verify_access_token(token: str) -> dict:
    """アクセストークンを検証してペイロードを返す。無効な場合は例外。"""
    return jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])


# ── リフレッシュトークン ──


def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def create_refresh_token(user_id: str, db: Session) -> str:
    """リフレッシュトークンを生成し、ハッシュをDBに保存。生トークンを返す。"""
    raw_token = secrets.token_urlsafe(48)
    token_hash = _hash_token(raw_token)

    record = RefreshToken(
        user_id=user_id,
        token_hash=token_hash,
        expires_at=datetime.now(timezone.utc) + timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS),
    )
    db.add(record)
    db.flush()
    return raw_token


def refresh_tokens(raw_refresh_token: str, db: Session) -> tuple[str, str, User]:
    """リフレッシュトークンを検証し、ローテーション。新しいアクセス+リフレッシュトークンを返す。"""
    token_hash = _hash_token(raw_refresh_token)
    record = db.query(RefreshToken).filter(RefreshToken.token_hash == token_hash).first()

    if not record:
        raise ValueError("Invalid refresh token")

    # 既にrevokedなトークンが使われた → 不正検知: 全トークン無効化
    if record.revoked:
        logger.warning("不正リフレッシュトークン検知", extra={"user_id": record.user_id})
        revoke_all_tokens(record.user_id, db)
        raise ValueError("Refresh token reuse detected")

    expires_at = record.expires_at if record.expires_at.tzinfo else record.expires_at.replace(tzinfo=timezone.utc)
    if expires_at < datetime.now(timezone.utc):
        raise ValueError("Refresh token expired")

    # 旧トークンを無効化
    record.revoked = True

    user = db.query(User).filter(User.id == record.user_id).first()
    if not user:
        raise ValueError("User not found")

    # 新トークン発行
    new_access = create_access_token(user.id, user.is_guest)
    new_refresh = create_refresh_token(user.id, db)

    return new_access, new_refresh, user


def revoke_all_tokens(user_id: str, db: Session) -> None:
    """指定ユーザーの全リフレッシュトークンを無効化。"""
    db.query(RefreshToken).filter(
        RefreshToken.user_id == user_id,
        RefreshToken.revoked == False,  # noqa: E712
    ).update({"revoked": True})


# ── メール確認トークン ──


def create_email_verification_token(user_id: str, db: Session) -> str:
    """メール確認トークンを生成。生トークンを返す。"""
    raw_token = secrets.token_urlsafe(32)
    token_hash = _hash_token(raw_token)

    record = EmailVerificationToken(
        user_id=user_id,
        token_hash=token_hash,
        expires_at=datetime.now(timezone.utc) + timedelta(hours=EMAIL_VERIFICATION_EXPIRE_HOURS),
    )
    db.add(record)
    db.flush()
    return raw_token


def verify_email_token(raw_token: str, db: Session) -> User:
    """メール確認トークンを検証し、ユーザーのemail_verifiedをtrueにする。"""
    token_hash = _hash_token(raw_token)
    record = (
        db.query(EmailVerificationToken)
        .filter(EmailVerificationToken.token_hash == token_hash)
        .first()
    )

    if not record:
        raise ValueError("Invalid verification token")
    if record.used:
        raise ValueError("Token already used")
    expires_at = record.expires_at if record.expires_at.tzinfo else record.expires_at.replace(tzinfo=timezone.utc)
    if expires_at < datetime.now(timezone.utc):
        raise ValueError("Verification token expired")

    record.used = True

    user = db.query(User).filter(User.id == record.user_id).first()
    if not user:
        raise ValueError("User not found")

    user.email_verified = True
    return user
