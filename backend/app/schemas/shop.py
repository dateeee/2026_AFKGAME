"""ショップスキーマ"""

from pydantic import Field, model_validator

from app.schemas import CamelModel
from app.schemas.equipment import EquipmentResponse


class BuyRequest(CamelModel):
    """常設購入は itemId、日替わり購入は dailySlotIndex を指定する（tech_shop.md §4）"""

    item_id: str | None = None
    quantity: int = Field(default=1, ge=1)
    daily_slot_index: int | None = Field(default=None, ge=0, le=4)

    @model_validator(mode="after")
    def _exclusive_target(self) -> "BuyRequest":
        if (self.item_id is None) == (self.daily_slot_index is None):
            raise ValueError("itemId と dailySlotIndex はどちらか一方のみ指定してください")
        return self


class ShopItemResponse(CamelModel):
    item_id: str
    name: str
    price: int
    heal_ratio: float
    quantity_owned: int
    stack_limit: int


class ShopDailyItemResponse(CamelModel):
    slot_index: int
    category: str
    base_id: str
    name: str
    slot: str
    rarity: str
    level: int
    stat_atk: int | None
    stat_def: int | None
    stat_hp: int | None
    stat_spd: int | None
    price: int
    sold_out: bool


class ShopLineupResponse(CamelModel):
    lineup: list[ShopItemResponse]
    daily: list[ShopDailyItemResponse]
    daily_reset_at: str


class ShopBuyResponse(CamelModel):
    status: str
    gold: int
    item_id: str | None = None
    quantity: int | None = None
    equipment: EquipmentResponse | None = None
