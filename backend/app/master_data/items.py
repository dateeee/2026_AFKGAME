"""アイテムマスターデータ"""

from dataclasses import dataclass


@dataclass(frozen=True)
class ItemData:
    id: str
    name: str
    category: str
    price: int
    heal_ratio: float  # maxHP に対する回復割合
    stack_limit: int


ITEMS: dict[str, ItemData] = {
    "hp_potion": ItemData("hp_potion", "HPポーション", "potion", 25, 0.20, 99),
}


def get_item(item_id: str) -> ItemData:
    return ITEMS[item_id]
