"""塔関連スキーマ"""

from app.schemas import CamelModel


class TowerSelectRequest(CamelModel):
    tower_id: str
    target_floor: int
    mode: str = "auto_repeat"  # "auto_repeat" | "stop_on_clear"


class TowerModeRequest(CamelModel):
    mode: str  # "auto_repeat" | "stop_on_clear"


class RetreatConditionsRequest(CamelModel):
    hp_threshold: float


class TowerInfo(CamelModel):
    id: str
    name: str
    dungeon_name: str
    total_floors: int
    unlock_tower_id: str | None = None
    unlocked: bool
    cleared: bool
    highest_floor: int
    target_floor_cap: int  # 選択可能な目標階の上限 = min(highest_floor + 1, total_floors)
