"""プレイヤー・ゲーム状態スキーマ"""

from __future__ import annotations

from typing import Literal

from pydantic import Field

from app.config import POTION_THRESHOLD_MAX, POTION_THRESHOLD_MIN
from app.schemas import CamelModel
from app.schemas.equipment import EquipmentResponse


class CharacterResponse(CamelModel):
    id: str
    name: str
    type: str
    level: int
    exp: int
    hp: int
    max_hp: int
    base_atk: int
    base_def: int
    base_spd: int
    effective_max_hp: int | None = None


class PlayerResponse(CamelModel):

    id: str
    gold: int
    current_tower_id: str | None
    current_floor: int | None
    target_floor: int | None
    tower_mode: str
    hp_threshold: float
    highest_floor: int = 0
    current_enemy_id: str | None = None
    current_enemy_hp: int | None = None


class EnemyInfo(CamelModel):
    id: str
    name: str
    hp: int
    max_hp: int
    level: int


class SettingsResponse(CamelModel):
    potion_threshold: float
    battle_log_count: int
    toast_enabled: bool
    auto_sell_rarity: str | None


class SettingsUpdate(CamelModel):
    """設定更新。値域の正は ui.md §設定画面（範囲外は 422 / tech_api.md §エラー）"""

    # ポーション自動使用閾値: 10%〜50%（systems/battle.md §213）
    potion_threshold: float | None = Field(
        default=None, ge=POTION_THRESHOLD_MIN, le=POTION_THRESHOLD_MAX
    )
    # 戦闘ログ表示件数: 20 / 50 / 100（上限はDB保存件数100件）
    battle_log_count: Literal[20, 50, 100] | None = None
    toast_enabled: bool | None = None
    # 空文字・null はオートセル無効へのリセット（routers/game.py で None へ正規化）
    auto_sell_rarity: (
        Literal["common", "uncommon", "rare", "epic", "legendary", ""] | None
    ) = None


class TowerClearInfo(CamelModel):
    cleared: bool
    highest_floor: int


class GameStateResponse(CamelModel):
    player: PlayerResponse
    characters: list[CharacterResponse]
    settings: SettingsResponse
    potions: dict[str, int]
    towers_cleared: dict[str, TowerClearInfo]
    current_enemy: EnemyInfo | None = None
    equipment: list[EquipmentResponse] = []
    equipped: dict[str, str | None] = {}
