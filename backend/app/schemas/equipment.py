"""装備関連スキーマ"""

from datetime import datetime

from app.schemas import CamelModel


class EquipmentResponse(CamelModel):
    id: str
    base_id: str
    slot: str
    rarity: str
    level: int
    enhance_level: int
    stat_atk: int | None
    stat_def: int | None
    stat_hp: int | None
    stat_spd: int | None
    lifesteal: float | None
    is_two_handed: bool
    locked: bool
    acquired_at: datetime


class EquipRequest(CamelModel):
    character_id: str
    slot: str
    equipment_id: str | None = None  # None = 外す


class SellRequest(CamelModel):
    equipment_ids: list[str]


class SellResponse(CamelModel):
    gold_earned: int
    items_sold: int


class LockRequest(CamelModel):
    equipment_id: str
