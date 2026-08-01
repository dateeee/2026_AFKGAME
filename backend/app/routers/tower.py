"""塔ルーター"""

import logging

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

logger = logging.getLogger("afkgame.tower")

from app.db.database import get_db
from app.dependencies import get_current_player
from app.models.player import Player, TowerClearRecord
from app.master_data.towers import TOWERS, get_tower
from app.schemas.tower import (
    TowerSelectRequest,
    TowerModeRequest,
    RetreatConditionsRequest,
    TowerInfo,
)

router = APIRouter(prefix="/api/tower", tags=["tower"])


def _is_tower_unlocked(tower_id: str, cleared_ids: set[str]) -> bool:
    tower = get_tower(tower_id)
    return tower.unlock_tower_id is None or tower.unlock_tower_id in cleared_ids


def _get_cleared_tower_ids(player: Player, db: Session) -> set[str]:
    records = db.query(TowerClearRecord).filter_by(player_id=player.id, cleared=True).all()
    return {r.tower_id for r in records}


@router.get("/list", response_model=list[TowerInfo])
def list_towers(
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
):
    records = {r.tower_id: r for r in db.query(TowerClearRecord).filter_by(player_id=player.id).all()}
    cleared_ids = {tid for tid, r in records.items() if r.cleared}
    return [
        TowerInfo(
            id=t.id,
            name=t.name,
            dungeon_name=t.dungeon_name,
            total_floors=t.total_floors,
            unlock_tower_id=t.unlock_tower_id,
            unlocked=_is_tower_unlocked(t.id, cleared_ids),
            cleared=records[t.id].cleared if t.id in records else False,
            highest_floor=records[t.id].highest_floor if t.id in records else 0,
        )
        for t in TOWERS.values()
    ]


@router.post("/select")
def select_tower(
    req: TowerSelectRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
):
    if player.current_tower_id:
        raise HTTPException(status_code=400, detail="Already in a tower")
    if req.tower_id not in TOWERS:
        raise HTTPException(status_code=404, detail="Tower not found")

    tower = get_tower(req.tower_id)
    if not _is_tower_unlocked(req.tower_id, _get_cleared_tower_ids(player, db)):
        raise HTTPException(status_code=403, detail="Tower is locked")
    if req.target_floor < 1 or req.target_floor > tower.total_floors:
        raise HTTPException(status_code=400, detail="Invalid target floor")

    if req.mode not in ("auto_repeat", "stop_on_clear"):
        raise HTTPException(status_code=400, detail="Invalid mode")

    player.current_tower_id = req.tower_id
    player.current_floor = 1
    player.target_floor = req.target_floor
    player.tower_mode = req.mode
    player.current_enemy_id = None
    player.current_enemy_hp = None
    player.run_gold = 0
    db.commit()

    logger.info("塔選択", extra={"player_id": str(player.id), "tower_id": req.tower_id})

    return {"status": "ok", "tower_id": req.tower_id, "target_floor": req.target_floor}


@router.post("/retire")
def retire_tower(
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
):
    if not player.current_tower_id:
        raise HTTPException(status_code=400, detail="Not in a tower")

    # リタイア: 獲得済み報酬は保持（game_spec §2.2、ペナルティなし）
    player.current_tower_id = None
    player.current_floor = None
    player.target_floor = None
    player.current_enemy_id = None
    player.current_enemy_hp = None
    player.run_gold = 0
    db.commit()

    logger.info("塔リタイア", extra={"player_id": str(player.id)})

    return {"status": "ok"}


@router.put("/mode")
def set_tower_mode(
    req: TowerModeRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
):
    if req.mode not in ("auto_repeat", "stop_on_clear"):
        raise HTTPException(status_code=400, detail="Invalid mode")
    player.tower_mode = req.mode
    db.commit()

    logger.info("塔モード変更", extra={"player_id": str(player.id), "mode": req.mode})

    return {"status": "ok", "mode": req.mode}


@router.put("/retreat-conditions")
def set_retreat_conditions(
    req: RetreatConditionsRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
):
    if not (0.0 <= req.hp_threshold <= 1.0):
        raise HTTPException(status_code=400, detail="Invalid threshold")
    player.hp_threshold = req.hp_threshold
    db.commit()

    logger.info("撤退条件変更", extra={"player_id": str(player.id), "hp_threshold": req.hp_threshold})

    return {"status": "ok", "hp_threshold": req.hp_threshold}
