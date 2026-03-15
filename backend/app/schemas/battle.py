"""戦闘関連スキーマ"""

from typing import Any

from app.schemas import CamelModel
from app.schemas.equipment import EquipmentResponse
from app.schemas.player import GameStateResponse


class OfflineSummary(CamelModel):
    elapsed_seconds: int
    processed_ticks: int
    calc_method: str  # "normal" | "simplified"
    total_gold: int
    total_exp: int
    enemies_defeated: int
    potions_used: int
    levels_gained: int
    floors_cleared: int = 0


class TickResponse(CamelModel):
    battle_logs: list[list[dict[str, Any]]]
    updated_state: GameStateResponse
    offline_summary: OfflineSummary | None = None
    equipment_drops: list[EquipmentResponse] = []
    equipment_auto_sold: list[dict[str, Any]] = []
