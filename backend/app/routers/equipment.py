"""装備ルーター"""

import logging

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.dependencies import get_current_player
from app.exceptions import AppError
from app.models.player import Player
from app.models.equipment import Equipment
from app.schemas.common import StatusResponse
from app.schemas.equipment import (
    EquipmentResponse,
    EquipRequest,
    LockRequest,
    LockResponse,
    SellRequest,
    SellResponse,
)
from app.services.equipment_service import equip_item, sell_items, toggle_lock

logger = logging.getLogger("afkgame.equipment")

router = APIRouter(prefix="/api/equipment", tags=["equipment"])


@router.get("/list", response_model=list[EquipmentResponse])
def list_equipment(
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> list[EquipmentResponse]:
    """プレイヤーの全装備を返す"""
    items = db.query(Equipment).filter_by(player_id=player.id).all()
    return [EquipmentResponse.model_validate(e) for e in items]


@router.post("/equip", response_model=StatusResponse)
def equip(
    req: EquipRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> StatusResponse:
    """装備を装着/解除"""
    try:
        equip_item(player, req.character_id, req.slot, req.equipment_id, db)
    except ValueError as e:
        raise AppError("EQUIP_INVALID_OPERATION", str(e), 400)
    db.commit()
    logger.info("装備変更", extra={"player_id": str(player.id), "slot": req.slot})
    return StatusResponse()


@router.post("/sell", response_model=SellResponse)
def sell(
    req: SellRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> SellResponse:
    """装備を売却"""
    gold_earned, items_sold = sell_items(player, req.equipment_ids, db)
    db.commit()
    logger.info("装備売却", extra={
        "player_id": str(player.id),
        "items_sold": items_sold,
        "gold_earned": gold_earned,
    })
    return SellResponse(gold_earned=gold_earned, items_sold=items_sold)


@router.post("/lock", response_model=LockResponse)
def lock(
    req: LockRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> LockResponse:
    """装備のロック切替"""
    try:
        new_state = toggle_lock(player, req.equipment_id, db)
    except ValueError as e:
        raise AppError("EQUIP_NOT_FOUND", str(e), 404)
    db.commit()
    return LockResponse(locked=new_state)
