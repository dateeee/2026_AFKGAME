"""戦闘ルーター"""

import logging
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.dependencies import get_current_player
from app.models.player import Player
from app.rng import new_rng
from app.schemas.battle import OfflineSummary, TickResponse
from app.schemas.equipment import EquipmentResponse
from app.services.battle_service import process_pending_ticks
from app.services.game_state_builder import build_game_state
from app.config import (
    MAX_LOG_PER_RESPONSE,
    MAX_OFFLINE_HOURS,
    TICK_INTERVAL_SECONDS,
)

logger = logging.getLogger("afkgame.battle")

router = APIRouter(prefix="/api/battle", tags=["battle"])


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
    max_ticks = MAX_OFFLINE_HOURS * 3600 // TICK_INTERVAL_SECONDS
    elapsed_ticks = int(elapsed // TICK_INTERVAL_SECONDS)
    capped = elapsed_ticks > max_ticks
    pending_ticks = min(elapsed_ticks, max_ticks)

    if pending_ticks <= 0:
        return TickResponse(
            battle_logs=[],
            updated_state=build_game_state(player, db),
        )

    character = player.characters[0] if player.characters else None
    if not character:
        return TickResponse(
            battle_logs=[],
            updated_state=build_game_state(player, db),
        )

    # 乱数源は1リクエストにつき1インスタンス（tech_rng.md §2）。
    # 戦闘・エンカウント・ドロップ・装備生成がこのインスタンスを共有する
    accumulated, calc_method = process_pending_ticks(
        player, character, pending_ticks, db, new_rng()
    )

    logger.info(
        "tick処理完了",
        extra={
            "ticks": pending_ticks,
            "calc_method": calc_method,
            "player_id": str(player.id),
        },
    )

    # 端数は次回へ繰り越す（tech_tick.md §1）。now を代入すると毎回0〜59秒が捨てられ、
    # プレイし続けるほど進行が遅れる。上限クランプ時だけは超過分を破棄する（§2）。
    if capped:
        player.last_tick_at = now
    else:
        player.last_tick_at = last_tick + timedelta(seconds=pending_ticks * TICK_INTERVAL_SECONDS)
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
        updated_state=build_game_state(player, db),
        offline_summary=offline_summary,
        equipment_drops=[EquipmentResponse.model_validate(e) for e in accumulated.equipment_drops],
        equipment_auto_sold=accumulated.equipment_auto_sold,
    )
