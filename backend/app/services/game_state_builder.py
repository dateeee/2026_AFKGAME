"""ゲーム状態構築 — game.py / battle.py 共通"""

from sqlalchemy.orm import Session

from app.master_data.enemies import get_enemy
from app.models.player import Player
from app.schemas.equipment import EquipmentResponse
from app.schemas.player import (
    CharacterResponse,
    EnemyInfo,
    GameStateResponse,
    PlayerResponse,
    SettingsResponse,
    TowerClearInfo,
)
from app.services.equipment_service import get_effective_stats, get_equipped_map


def build_game_state(player: Player, db: Session) -> GameStateResponse:
    """プレイヤーの現在のゲーム状態を構築する"""
    potions: dict[str, int] = {}
    for item in player.inventory_items:
        if item.item_id.endswith("_potion"):
            potions[item.item_id] = item.quantity

    towers_cleared: dict[str, TowerClearInfo] = {}
    for record in player.tower_clear_records:
        towers_cleared[record.tower_id] = TowerClearInfo(
            cleared=record.cleared, highest_floor=record.highest_floor
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

    equipment_list = [EquipmentResponse.model_validate(e) for e in player.equipment]
    equipped = {}
    characters = []
    if player.characters:
        equipped = get_equipped_map(player.characters[0].id, db)
        for c in player.characters:
            char_resp = CharacterResponse.model_validate(c)
            eff = get_effective_stats(c, db)
            char_resp.effective_max_hp = c.max_hp + eff["hp_bonus"]
            characters.append(char_resp)
    else:
        characters = []

    return GameStateResponse(
        player=PlayerResponse.model_validate(player),
        characters=characters,
        settings=SettingsResponse.model_validate(player.settings) if player.settings else SettingsResponse(
            potion_threshold=0.3, battle_log_count=50, toast_enabled=True, auto_sell_rarity=None
        ),
        potions=potions,
        towers_cleared=towers_cleared,
        current_enemy=current_enemy,
        equipment=equipment_list,
        equipped=equipped,
    )
