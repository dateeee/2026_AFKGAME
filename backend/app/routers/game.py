"""ゲーム状態ルーター"""

import logging

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.dependencies import get_current_player
from app.models.player import Player
from app.schemas.player import (
    GameStateResponse,
    SettingsResponse,
    SettingsUpdate,
)
from app.services.game_state_builder import build_game_state

logger = logging.getLogger("afkgame.game")

router = APIRouter(prefix="/api/game", tags=["game"])


@router.get("/state", response_model=GameStateResponse)
def get_game_state(
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> GameStateResponse:
    """現在のゲーム状態を返す"""
    return build_game_state(player, db)


@router.put("/settings", response_model=SettingsResponse)
def update_settings(
    update: SettingsUpdate,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> SettingsResponse:
    """プレイヤー設定を更新"""
    settings = player.settings
    if not settings:
        from app.models.player import PlayerSettings
        settings = PlayerSettings(player_id=player.id)
        db.add(settings)

    if update.potion_threshold is not None:
        settings.potion_threshold = update.potion_threshold
    if update.battle_log_count is not None:
        settings.battle_log_count = update.battle_log_count
    if update.toast_enabled is not None:
        settings.toast_enabled = update.toast_enabled
    # auto_sell_rarity は null/空文字 でリセット可能にするため model_fields_set で判定
    if "auto_sell_rarity" in update.model_fields_set:
        value = update.auto_sell_rarity
        settings.auto_sell_rarity = value if value else None

    db.commit()
    db.refresh(settings)

    logger.info("設定更新", extra={"player_id": str(player.id)})

    return SettingsResponse.model_validate(settings)
