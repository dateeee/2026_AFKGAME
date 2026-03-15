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
