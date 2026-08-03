"""共通依存関数"""

import logging

import jwt
from fastapi import Depends, Header, Request
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.exceptions import AppError
from app.logging_config import mask_token
from app.models.player import Player
from app.models.user import User
from app.services.auth_service import verify_access_token

logger = logging.getLogger("afkgame.auth")


def get_current_user(
    request: Request,
    authorization: str = Header(default=""),
    db: Session = Depends(get_db),
) -> User:
    """Bearerトークン（JWT）からUserを取得。

    401 の理由は `error.code` で判別できるようにする（tech_logging.md エラーコード体系）。
    クライアントは `AUTH_TOKEN_EXPIRED`（refresh を試す）と `AUTH_INVALID_TOKEN`
    （ログアウトして再ログイン）をコードで区別する。
    """
    if not authorization:
        logger.warning("認証失敗", extra={"reason": "header_missing"})
        raise AppError("AUTH_HEADER_MISSING", "Authorization header missing", 401)

    if not authorization.startswith("Bearer "):
        logger.warning("認証失敗", extra={"reason": "invalid_format"})
        raise AppError("AUTH_INVALID_FORMAT", "Invalid authorization header", 401)

    token = authorization[7:]

    try:
        payload = verify_access_token(token)
    except jwt.ExpiredSignatureError:
        raise AppError("AUTH_TOKEN_EXPIRED", "Token expired", 401)
    except jwt.InvalidTokenError:
        logger.warning("認証失敗", extra={"reason": "invalid_token", "token": mask_token(token)})
        raise AppError("AUTH_INVALID_TOKEN", "Invalid token", 401)

    user_id = payload.get("sub")
    if not user_id:
        raise AppError("AUTH_INVALID_TOKEN", "Invalid token payload", 401)

    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        logger.warning("認証失敗", extra={"reason": "user_not_found", "user_id": user_id})
        raise AppError("AUTH_USER_NOT_FOUND", "User not found", 401)

    request.state.player_id = user_id
    return user


def get_current_player(
    request: Request,
    authorization: str = Header(default=""),
    db: Session = Depends(get_db),
) -> Player:
    """BearerトークンからPlayerを取得（既存APIとの互換性維持）"""
    user = get_current_user(request, authorization, db)

    player = db.query(Player).filter(Player.user_id == user.id).first()
    if not player:
        logger.warning("Player不在", extra={"user_id": user.id})
        raise AppError("AUTH_PLAYER_NOT_FOUND", "Player not found", 404)

    return player
