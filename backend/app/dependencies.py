"""共通依存関数"""

import logging

import jwt
from fastapi import Depends, Header, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.db.database import get_db
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
    """Bearerトークン（JWT）からUserを取得"""
    if not authorization:
        logger.warning("認証失敗", extra={"reason": "header_missing"})
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authorization header missing")

    if not authorization.startswith("Bearer "):
        logger.warning("認証失敗", extra={"reason": "invalid_format"})
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid authorization header")

    token = authorization[7:]

    try:
        payload = verify_access_token(token)
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired")
    except jwt.InvalidTokenError:
        logger.warning("認証失敗", extra={"reason": "invalid_token", "token": mask_token(token)})
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")

    user_id = payload.get("sub")
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token payload")

    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        logger.warning("認証失敗", extra={"reason": "user_not_found", "user_id": user_id})
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found")

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
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Player not found")

    return player
