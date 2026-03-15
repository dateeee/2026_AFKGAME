"""ショップスキーマ"""

from app.schemas import CamelModel


class BuyRequest(CamelModel):
    item_id: str
    quantity: int


class ShopItemResponse(CamelModel):
    item_id: str
    name: str
    price: int
    heal_ratio: float
    quantity_owned: int
    stack_limit: int


class ShopLineupResponse(CamelModel):
    lineup: list[ShopItemResponse]


class ShopBuyResponse(CamelModel):
    status: str
    gold: int
    item_id: str
    quantity: int
