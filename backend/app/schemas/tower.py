"""塔関連スキーマ

型・範囲の検証はスキーマ側に寄せる（違反は 422 / tech_api.md §エラー）。
目標階の上限は塔ごとの到達状況に依存するため、ルーター側で検証する（400）。
"""

from typing import Literal

from pydantic import Field

from app.schemas import CamelModel

TowerMode = Literal["auto_repeat", "stop_on_clear"]


class TowerSelectRequest(CamelModel):
    tower_id: str
    target_floor: int = Field(ge=1)
    mode: TowerMode = "auto_repeat"


class TowerModeRequest(CamelModel):
    mode: TowerMode


class RetreatConditionsRequest(CamelModel):
    hp_threshold: float = Field(ge=0.0, le=1.0)


class TowerSelectResponse(CamelModel):
    status: str = "ok"
    tower_id: str
    target_floor: int


class TowerModeResponse(CamelModel):
    status: str = "ok"
    mode: TowerMode


class RetreatConditionsResponse(CamelModel):
    status: str = "ok"
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
