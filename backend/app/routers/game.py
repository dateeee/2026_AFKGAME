"""ゲーム状態ルーター"""

import logging

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

logger = logging.getLogger("afkgame.game")

from app.db.database import get_db
from app.dependencies import get_current_player
from app.models.player import Player
from app.schemas.player import (
    CharacterResponse,
    EnemyInfo,
    GameStateResponse,
    PlayerResponse,
    SettingsResponse,
    SettingsUpdate,
    TowerClearInfo,
)
from app.schemas.equipment import EquipmentResponse
from app.master_data.enemies import get_enemy
from app.services.equipment_service import get_equipped_map

router = APIRouter(prefix="/api/game", tags=["game"])


@router.get("/state", response_model=GameStateResponse)
def get_game_state(
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> GameStateResponse:
    """現在のゲーム状態を返す"""
    potions: dict[str, int] = {}
    for item in player.inventory_items:
        if item.item_id.endswith("_potion"):
            potions[item.item_id] = item.quantity

    towers_cleared: dict[str, TowerClearInfo] = {}
    for record in player.tower_clear_records:
        towers_cleared[record.tower_id] = TowerClearInfo(
            cleared=record.cleared,
            highest_floor=record.highest_floor,
        )

    current_enemy = None
    if player.current_enemy_id and player.current_enemy_hp is not None and player.current_enemy_hp > 0:
        try:
            ed = get_enemy(player.current_enemy_id)
            current_enemy = EnemyInfo(
                id=ed.id, name=ed.name, hp=player.current_enemy_hp,
                max_hp=ed.hp, level=ed.level,
            )
        except KeyError:
            pass

    # 装備データ
    equipment_list = [EquipmentResponse.model_validate(e) for e in player.equipment]
    # 最初のキャラの装備マップ
    equipped = {}
    if player.characters:
        equipped = get_equipped_map(player.characters[0].id, db)

    return GameStateResponse(
        player=PlayerResponse.model_validate(player),
        characters=[CharacterResponse.model_validate(c) for c in player.characters],
        settings=SettingsResponse.model_validate(player.settings) if player.settings else SettingsResponse(
            potion_threshold=0.5, battle_log_count=50, toast_enabled=True, auto_sell_rarity=None
        ),
        potions=potions,
        towers_cleared=towers_cleared,
        current_enemy=current_enemy,
        equipment=equipment_list,
        equipped=equipped,
    )


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
    if update.auto_sell_rarity is not None:
        settings.auto_sell_rarity = update.auto_sell_rarity if update.auto_sell_rarity != "" else None

    db.commit()
    db.refresh(settings)

    logger.info("設定更新", extra={"player_id": str(player.id)})

    return SettingsResponse.model_validate(settings)
