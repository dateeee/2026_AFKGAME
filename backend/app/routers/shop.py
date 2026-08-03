"""ショップルーター"""

import logging

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.dependencies import get_current_player
from app.exceptions import AppError
from app.models.player import Player
from app.models.item import InventoryItem
from app.master_data.equipment import get_equipment_base
from app.master_data.items import ITEMS, get_item
from app.schemas.equipment import EquipmentResponse
from app.schemas.shop import (
    BuyRequest,
    ShopBuyResponse,
    ShopDailyItemResponse,
    ShopItemResponse,
    ShopLineupResponse,
)
from app.services import shop_daily_service

logger = logging.getLogger("afkgame.shop")

router = APIRouter(prefix="/api/shop", tags=["shop"])


@router.get("/lineup", response_model=ShopLineupResponse)
def get_lineup(
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> ShopLineupResponse:
    potion_ids = [i for i, d in ITEMS.items() if d.category == "potion"]
    # 種別ごとに引かず1回でまとめる（ISSUE-110）
    owned_by_item = {
        inv.item_id: inv.quantity
        for inv in db.query(InventoryItem).filter(
            InventoryItem.player_id == player.id,
            InventoryItem.item_id.in_(potion_ids),
        )
    }

    lineup: list[ShopItemResponse] = []
    for item_id in potion_ids:
        item_data = ITEMS[item_id]
        owned = owned_by_item.get(item_id, 0)
        lineup.append(ShopItemResponse(
            item_id=item_data.id,
            name=item_data.name,
            price=item_data.price,
            heal_ratio=item_data.heal_ratio,
            quantity_owned=owned,
            stack_limit=item_data.stack_limit,
        ))

    state = shop_daily_service.ensure_fresh_lineup(db, player)
    daily = []
    for slot in shop_daily_service.get_slots(db, state):
        base = get_equipment_base(slot.base_id)
        daily.append(ShopDailyItemResponse(
            slot_index=slot.slot_index,
            category=slot.category,
            base_id=slot.base_id,
            name=base.name,
            slot=base.slot,
            rarity=slot.rarity,
            level=slot.level,
            stat_atk=slot.stat_atk,
            stat_def=slot.stat_def,
            stat_hp=slot.stat_hp,
            stat_spd=slot.stat_spd,
            price=slot.price,
            sold_out=slot.sold,
        ))

    return ShopLineupResponse(
        lineup=lineup,
        daily=daily,
        daily_reset_at=shop_daily_service.format_reset_at(state.reset_at),
    )


@router.post("/buy", response_model=ShopBuyResponse)
def buy_item(
    req: BuyRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> ShopBuyResponse:
    if req.daily_slot_index is not None:
        equipment = shop_daily_service.buy_daily(db, player, req.daily_slot_index)
        return ShopBuyResponse(
            status="ok",
            gold=player.gold,
            equipment=EquipmentResponse.model_validate(equipment),
        )

    # 業務エラーは AppError に統一する（日替わり枠と同じ error.code 体系 / tech_logging.md）。
    # quantity の下限はスキーマが検証済み（違反は 422）
    if req.item_id not in ITEMS:
        raise AppError("SHOP_ITEM_NOT_FOUND", "商品が見つかりません", 404)

    item_data = get_item(req.item_id)
    total_cost = item_data.price * req.quantity

    if player.gold < total_cost:
        logger.warning(
            "ゴールド不足",
            extra={"player_id": str(player.id), "item_id": req.item_id, "gold": player.gold},
        )
        raise AppError("SHOP_INSUFFICIENT_GOLD", "ゴールドが足りません", 400)

    inv = db.query(InventoryItem).filter_by(
        player_id=player.id, item_id=req.item_id
    ).first()
    current_qty = inv.quantity if inv else 0

    if current_qty + req.quantity > item_data.stack_limit:
        raise AppError(
            "SHOP_STACK_LIMIT",
            f"所持上限（{item_data.stack_limit}個）を超えます",
            400,
        )

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

    return ShopBuyResponse(
        status="ok",
        gold=player.gold,
        item_id=req.item_id,
        quantity=inv.quantity,
    )
