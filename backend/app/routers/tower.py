"""塔ルーター"""

import logging
import math

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

logger = logging.getLogger("afkgame.tower")

from app.db.database import get_db
from app.dependencies import get_current_player
from app.models.player import Player
from app.master_data.towers import TOWERS, get_tower
from app.master_data.characters import required_exp

router = APIRouter(prefix="/api/tower", tags=["tower"])


class TowerSelectRequest(BaseModel):
    tower_id: str
    target_floor: int


class TowerModeRequest(BaseModel):
    mode: str  # "auto_repeat" | "stop_on_clear"


class RetreatConditionsRequest(BaseModel):
    hp_threshold: float


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
    if req.target_floor < 1 or req.target_floor > tower.total_floors:
        raise HTTPException(status_code=400, detail="Invalid target floor")

    player.current_tower_id = req.tower_id
    player.current_floor = 1
    player.target_floor = req.target_floor
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

    # 退却ペナルティ
    character = player.characters[0] if player.characters else None
    if character:
        exp_penalty = math.floor(required_exp(character.level) * 0.5)
        character.exp = max(0, character.exp - exp_penalty)
    gold_penalty = player.run_gold
    player.gold = max(0, player.gold - gold_penalty)

    player.current_tower_id = None
    player.current_floor = None
    player.target_floor = None
    player.current_enemy_id = None
    player.current_enemy_hp = None
    player.run_gold = 0
    db.commit()

    logger.info("塔リタイア", extra={"player_id": str(player.id), "gold": gold_penalty})

    return {"status": "ok", "gold_lost": gold_penalty}


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
