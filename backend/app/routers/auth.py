"""認証ルーター（Phase 2: JWT認証）"""

import logging
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.config import (
    GOOGLE_CLIENT_ID,
    INITIAL_CHARACTER,
    INITIAL_POTIONS,
)
from app.db.database import get_db
from app.dependencies import get_current_user
from app.models.character import Character
from app.models.item import InventoryItem
from app.models.player import Player, PlayerSettings
from app.models.user import TOKEN_PURPOSE_PASSWORD_RESET, User, new_guest_id
from app.schemas.common import MessageResponse, StatusResponse
from app.schemas.auth import (
    AuthResponse,
    GoogleAuthRequest,
    LinkAccountRequest,
    LoginRequest,
    PasswordResetConfirm,
    PasswordResetRequest,
    RefreshRequest,
    RegisterRequest,
    UserInfo,
)
from app.services.auth_service import (
    consume_password_reset_token,
    create_access_token,
    create_email_verification_token,
    create_refresh_token,
    hash_password,
    refresh_tokens,
    revoke_all_tokens,
    revoke_refresh_token,
    verify_email_token,
    verify_password,
)
from app.services.equipment_service import create_equip_slots

logger = logging.getLogger("afkgame.auth")

router = APIRouter(prefix="/api/auth", tags=["auth"])


def _create_player_for_user(user: User, db: Session) -> Player:
    """新規ユーザー用のPlayerとキャラクターを初期化する。"""
    player = Player(user_id=user.id)
    db.add(player)
    db.flush()

    settings = PlayerSettings(player_id=player.id)
    db.add(settings)

    character = Character(
        player_id=player.id,
        name=INITIAL_CHARACTER["name"],
        type=INITIAL_CHARACTER["type"],
        level=INITIAL_CHARACTER["level"],
        exp=INITIAL_CHARACTER["exp"],
        hp=INITIAL_CHARACTER["hp"],
        max_hp=INITIAL_CHARACTER["max_hp"],
        base_atk=INITIAL_CHARACTER["base_atk"],
        base_def=INITIAL_CHARACTER["base_def"],
        base_spd=INITIAL_CHARACTER["base_spd"],
    )
    db.add(character)
    db.flush()

    create_equip_slots(character.id, db)

    for item_id, quantity in INITIAL_POTIONS.items():
        item = InventoryItem(player_id=player.id, item_id=item_id, quantity=quantity)
        db.add(item)

    return player


def _build_auth_response(user: User, access_token: str, raw_refresh_token: str) -> AuthResponse:
    return AuthResponse(
        access_token=access_token,
        refresh_token=raw_refresh_token,
        user=UserInfo(
            id=user.id,
            email=user.email,
            display_name=user.display_name,
            is_guest=user.is_guest,
            email_verified=user.email_verified,
        ),
    )


@router.post("/guest", response_model=AuthResponse)
def create_guest(db: Session = Depends(get_db)) -> AuthResponse:
    """ゲストアカウントを作成し、JWTを返す。"""
    user = User(id=new_guest_id(), is_guest=True, display_name="冒険者")
    db.add(user)
    db.flush()

    _create_player_for_user(user, db)

    access_token = create_access_token(user.id, is_guest=True)
    raw_refresh = create_refresh_token(user.id, db)

    db.commit()
    logger.info("ゲストアカウント作成", extra={"user_id": user.id})

    return _build_auth_response(user, access_token, raw_refresh)


@router.post("/register", response_model=AuthResponse)
def register(body: RegisterRequest, db: Session = Depends(get_db)) -> AuthResponse:
    """メール+パスワードでアカウント登録。

    メール形式・パスワード最短長は RegisterRequest が検証する（違反は 422）。
    """
    existing = db.query(User).filter(User.email == body.email).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Email already registered",
        )

    user = User(
        is_guest=False,
        email=body.email,
        password_hash=hash_password(body.password),
        display_name=body.display_name or body.email.split("@")[0],
        email_verified=False,
    )
    db.add(user)
    db.flush()

    _create_player_for_user(user, db)

    # メール確認トークン生成（MVP: ログ出力のみ）
    verification_token = create_email_verification_token(user.id, db)
    logger.info(
        "メール確認トークン発行（開発用ログ出力）",
        extra={"user_id": user.id, "verification_token": verification_token},
    )

    access_token = create_access_token(user.id, is_guest=False)
    raw_refresh = create_refresh_token(user.id, db)

    db.commit()
    logger.info("メール登録完了", extra={"user_id": user.id, "email": body.email})

    return _build_auth_response(user, access_token, raw_refresh)


@router.post("/login", response_model=AuthResponse)
def login(body: LoginRequest, db: Session = Depends(get_db)) -> AuthResponse:
    """メール+パスワードでログイン。"""
    user = db.query(User).filter(User.email == body.email).first()
    if not user or not user.password_hash:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")

    if not verify_password(body.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")

    user.last_login_at = datetime.now(timezone.utc)

    access_token = create_access_token(user.id, user.is_guest)
    raw_refresh = create_refresh_token(user.id, db)

    db.commit()
    logger.info("ログイン成功", extra={"user_id": user.id})

    return _build_auth_response(user, access_token, raw_refresh)


@router.post("/refresh", response_model=AuthResponse)
def refresh(body: RefreshRequest, db: Session = Depends(get_db)) -> AuthResponse:
    """リフレッシュトークンで新しいトークンペアを取得。"""
    try:
        new_access, new_refresh, user = refresh_tokens(body.refresh_token, db)
    except ValueError as e:
        db.commit()  # revoke_all_tokensの変更をコミット
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(e))

    db.commit()
    return _build_auth_response(user, new_access, new_refresh)


@router.post("/logout", response_model=StatusResponse)
def logout(
    body: RefreshRequest,
    db: Session = Depends(get_db),
) -> StatusResponse:
    """リフレッシュトークンを無効化してログアウト。"""
    revoke_refresh_token(body.refresh_token, db)
    db.commit()
    return StatusResponse()


@router.get("/verify-email", response_model=MessageResponse)
def verify_email(
    token: str = Query(...),
    db: Session = Depends(get_db),
) -> MessageResponse:
    """メール確認トークンを検証。"""
    try:
        user = verify_email_token(token, db)
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))

    db.commit()
    logger.info("メール確認完了", extra={"user_id": user.id})
    return MessageResponse(message="Email verified")


@router.post("/google", response_model=AuthResponse)
def google_auth(body: GoogleAuthRequest, db: Session = Depends(get_db)) -> AuthResponse:
    """Google認可コードでログイン/登録。"""
    if not GOOGLE_CLIENT_ID:
        raise HTTPException(
            status_code=status.HTTP_501_NOT_IMPLEMENTED,
            detail="Google OAuth is not configured",
        )

    # TODO: Google認可コード → アクセストークン → ユーザー情報取得
    # MVP段階では未実装（GOOGLE_CLIENT_ID設定時のみ有効化）
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail="Google OAuth not yet implemented",
    )


@router.post("/link-account", response_model=AuthResponse)
def link_account(
    body: LinkAccountRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> AuthResponse:
    """ゲストアカウントを本登録（メールまたはGoogle）に移行。"""
    if not current_user.is_guest:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Account is already registered",
        )

    if body.email and body.password:
        existing = db.query(User).filter(User.email == body.email).first()
        if existing:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Email already registered",
            )

        current_user.email = body.email
        current_user.password_hash = hash_password(body.password)
        current_user.is_guest = False
        current_user.display_name = body.email.split("@")[0]

        verification_token = create_email_verification_token(current_user.id, db)
        logger.info(
            "アカウント移行: メール確認トークン発行",
            extra={"user_id": current_user.id, "verification_token": verification_token},
        )

    elif body.google_auth_code:
        if not GOOGLE_CLIENT_ID:
            raise HTTPException(
                status_code=status.HTTP_501_NOT_IMPLEMENTED,
                detail="Google OAuth is not configured",
            )
        raise HTTPException(
            status_code=status.HTTP_501_NOT_IMPLEMENTED,
            detail="Google OAuth not yet implemented",
        )
    else:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Provide email+password or googleAuthCode",
        )

    # 旧トークン全無効化 + 新トークン発行
    revoke_all_tokens(current_user.id, db)
    access_token = create_access_token(current_user.id, is_guest=False)
    raw_refresh = create_refresh_token(current_user.id, db)

    db.commit()
    logger.info("ゲスト→本登録移行完了", extra={"user_id": current_user.id})

    return _build_auth_response(current_user, access_token, raw_refresh)


@router.post("/password-reset/request", response_model=MessageResponse)
def password_reset_request(
    body: PasswordResetRequest,
    db: Session = Depends(get_db),
) -> MessageResponse:
    """パスワードリセットメール送信。"""
    user = db.query(User).filter(User.email == body.email).first()
    if user and user.password_hash:
        # メール確認とは用途を分けたトークンを発行する（流用防止 / tech_auth.md §6）
        token = create_email_verification_token(
            user.id, db, purpose=TOKEN_PURPOSE_PASSWORD_RESET
        )
        logger.info(
            "パスワードリセットトークン発行（開発用ログ出力）",
            extra={"user_id": user.id, "reset_token": token},
        )
        db.commit()

    # セキュリティ: ユーザー存在有無に関わらず同じレスポンス
    return MessageResponse(message="If the email exists, a reset link has been sent")


@router.post("/password-reset/confirm", response_model=MessageResponse)
def password_reset_confirm(
    body: PasswordResetConfirm,
    db: Session = Depends(get_db),
) -> MessageResponse:
    """パスワードリセット実行。パスワード最短長は PasswordResetConfirm が検証する（違反は 422）。"""
    try:
        user = consume_password_reset_token(body.token, db)
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))

    user.password_hash = hash_password(body.new_password)
    revoke_all_tokens(user.id, db)
    db.commit()

    logger.info("パスワードリセット完了", extra={"user_id": user.id})
    return MessageResponse(message="Password has been reset")
