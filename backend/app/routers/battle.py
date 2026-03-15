"""戦闘ルーター"""

import logging
from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

logger = logging.getLogger("afkgame.battle")

from app.db.database import get_db
from app.dependencies import get_current_player
from app.models.player import Player
from app.schemas.battle import OfflineSummary, TickResponse
from app.schemas.player import (
    CharacterResponse,
    EnemyInfo,
    GameStateResponse,
    PlayerResponse,
    SettingsResponse,
    TowerClearInfo,
)
from app.schemas.equipment import EquipmentResponse
from app.services.battle_service import TickResult, process_tick
from app.services.equipment_service import get_equipped_map
from app.master_data.enemies import get_enemy
from app.config import (
    FAST_CALC_THRESHOLD,
    MAX_LOG_PER_RESPONSE,
    MAX_OFFLINE_HOURS,
    TICK_INTERVAL_SECONDS,
)

router = APIRouter(prefix="/api/battle", tags=["battle"])


def _build_game_state(player: Player, db: Session | None = None) -> GameStateResponse:
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


@router.post("/tick", response_model=TickResponse)
def tick_endpoint(
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> TickResponse:
    """tick処理: 経過時間分のtickを一括処理"""
    now = datetime.now(timezone.utc)
    # SQLiteはtimezone情報を保持しないため、naiveなdatetimeが返される場合がある
    last_tick = player.last_tick_at
    if last_tick.tzinfo is None:
        last_tick = last_tick.replace(tzinfo=timezone.utc)
    elapsed = (now - last_tick).total_seconds()
    pending_ticks = min(
        int(elapsed // TICK_INTERVAL_SECONDS),
        MAX_OFFLINE_HOURS * 3600 // TICK_INTERVAL_SECONDS,
    )

    if pending_ticks <= 0:
        return TickResponse(
            battle_logs=[],
            updated_state=_build_game_state(player, db),
        )

    character = player.characters[0] if player.characters else None
    if not character:
        return TickResponse(
            battle_logs=[],
            updated_state=_build_game_state(player, db),
        )

    accumulated = TickResult()

    if pending_ticks <= FAST_CALC_THRESHOLD:
        # フルシミュレーション
        for _ in range(pending_ticks):
            tick_result = process_tick(player, character, db)
            accumulated.accumulate(tick_result)
        calc_method = "normal"
    else:
        # 簡易計算: 10サンプルtickの平均 × 残り
        sample_count = min(10, pending_ticks)
        sample_result = TickResult()
        for _ in range(sample_count):
            tick_result = process_tick(player, character, db)
            sample_result.accumulate(tick_result)

        remaining = pending_ticks - sample_count
        if sample_count > 0 and remaining > 0:
            multiplier = remaining / sample_count
            player.gold += int(sample_result.total_gold * multiplier)
            character.exp += int(sample_result.total_exp * multiplier)

            from app.services.battle_service import _check_level_up
            extra_levels = _check_level_up(character)

            sample_result.total_gold += int(sample_result.total_gold * multiplier)
            sample_result.total_exp += int(sample_result.total_exp * multiplier)
            sample_result.enemies_defeated += int(sample_result.enemies_defeated * multiplier)
            sample_result.potions_used += int(sample_result.potions_used * multiplier)
            sample_result.levels_gained += extra_levels
            sample_result.floors_cleared += int(sample_result.floors_cleared * multiplier)

        accumulated = sample_result
        calc_method = "simplified"

    logger.info(
        "tick処理完了",
        extra={
            "ticks": pending_ticks,
            "calc_method": calc_method,
            "player_id": str(player.id),
        },
    )

    player.last_tick_at = now
    db.commit()

    # ログを最新N件に制限
    all_logs = accumulated.battle_logs
    if len(all_logs) > MAX_LOG_PER_RESPONSE:
        all_logs = all_logs[-MAX_LOG_PER_RESPONSE:]

    offline_summary = None
    if pending_ticks > 1:
        offline_summary = OfflineSummary(
            elapsed_seconds=int(elapsed),
            processed_ticks=pending_ticks,
            calc_method=calc_method,
            total_gold=accumulated.total_gold,
            total_exp=accumulated.total_exp,
            enemies_defeated=accumulated.enemies_defeated,
            potions_used=accumulated.potions_used,
            levels_gained=accumulated.levels_gained,
            floors_cleared=accumulated.floors_cleared,
        )

    return TickResponse(
        battle_logs=all_logs,
        updated_state=_build_game_state(player, db),
        offline_summary=offline_summary,
        equipment_drops=[EquipmentResponse.model_validate(e) for e in accumulated.equipment_drops],
        equipment_auto_sold=accumulated.equipment_auto_sold,
    )
