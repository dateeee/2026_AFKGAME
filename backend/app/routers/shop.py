"""ショップルーター"""

import logging

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

logger = logging.getLogger("afkgame.shop")

from app.db.database import get_db
from app.dependencies import get_current_player
from app.models.player import Player
from app.models.item import InventoryItem
from app.master_data.items import ITEMS, get_item

router = APIRouter(prefix="/api/shop", tags=["shop"])


class BuyRequest(BaseModel):
    item_id: str
    quantity: int


class ShopItem(BaseModel):
    item_id: str
    name: str
    price: int
    quantity_owned: int


@router.get("/lineup")
def get_lineup(
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
):
    lineup: list[dict] = []
    for item_id, item_data in ITEMS.items():
        if item_data.category != "potion":
            continue
        inv = db.query(InventoryItem).filter_by(
            player_id=player.id, item_id=item_id
        ).first()
        owned = inv.quantity if inv else 0
        lineup.append({
            "item_id": item_data.id,
            "name": item_data.name,
            "price": item_data.price,
            "heal_ratio": item_data.heal_ratio,
            "quantity_owned": owned,
            "stack_limit": item_data.stack_limit,
        })
    return {"lineup": lineup}


@router.post("/buy")
def buy_item(
    req: BuyRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
):
    if req.item_id not in ITEMS:
        raise HTTPException(status_code=404, detail="Item not found")

    item_data = get_item(req.item_id)
    total_cost = item_data.price * req.quantity

    if req.quantity <= 0:
        raise HTTPException(status_code=400, detail="Invalid quantity")
    if player.gold < total_cost:
        logger.warning(
            "ゴールド不足",
            extra={"player_id": str(player.id), "item_id": req.item_id, "gold": player.gold},
        )
        raise HTTPException(status_code=400, detail="Not enough gold")

    inv = db.query(InventoryItem).filter_by(
        player_id=player.id, item_id=req.item_id
    ).first()
    current_qty = inv.quantity if inv else 0

    if current_qty + req.quantity > item_data.stack_limit:
        raise HTTPException(status_code=400, detail="Exceeds stack limit")

    player.gold -= total_cost

    if inv:
        inv.quantity += req.quantity
    else:
        inv = InventoryItem(player_id=player.id, item_id=req.item_id, quantity=req.quantity)
        db.add(inv)

    db.commit()

    logger.info(
        "アイテム購入",
        extra={"player_id": str(player.id), "item_id": req.item_id, "quantity": req.quantity, "gold": player.gold},
    )

    return {
        "status": "ok",
        "gold": player.gold,
        "item_id": req.item_id,
        "quantity": inv.quantity,
    }
