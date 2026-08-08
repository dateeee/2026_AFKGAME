"""キャラクター成長・レベル計算・確定入手キャラ

正は docs/data/master/character.md §1.2（基礎ステータスと成長率）・§7.1（Phase 3 確定入手キャラ）。
"""

import math
from dataclasses import dataclass


@dataclass(frozen=True)
class GrowthRate:
    base_hp: int
    base_atk: int
    base_def: int
    base_spd: int
    # 成長率は 1.5 刻みを取りうるため float（LV n = base + growth × (n-1) を floor）
    hp_growth: float
    atk_growth: float
    def_growth: float
    spd_growth: float


GROWTH_RATES: dict[str, GrowthRate] = {
    "melee": GrowthRate(
        base_hp=100, base_atk=10, base_def=5, base_spd=5,
        hp_growth=20, atk_growth=3, def_growth=2, spd_growth=1,
    ),
    "magic": GrowthRate(
        base_hp=80, base_atk=12, base_def=4, base_spd=7,
        hp_growth=12, atk_growth=4, def_growth=1, spd_growth=2,
    ),
    "holy": GrowthRate(
        base_hp=95, base_atk=8, base_def=5, base_spd=6,
        hp_growth=18, atk_growth=2, def_growth=2, spd_growth=1.5,
    ),
    "agile": GrowthRate(
        base_hp=85, base_atk=10, base_def=4, base_spd=10,
        hp_growth=14, atk_growth=3, def_growth=1.5, spd_growth=3,
    ),
}


@dataclass(frozen=True)
class CharacterUnlock:
    """塔クリアで確定入手するキャラクター（character.md §7.1）"""

    master_id: str
    name: str
    type: str


#: (塔ID, 階) → 入手キャラ。条件のない階はキーを持たない
#: `scout_001` ハヤテ（獣の塔10F）は獣の塔の実装時に追加する（塔IDが未定義のため）
FLOOR_CHARACTERS: dict[tuple[str, int], CharacterUnlock] = {
    ("forest_tower", 15): CharacterUnlock("mage_001", "アカネ", "magic"),
    ("forest_tower", 30): CharacterUnlock("healer_001", "シロナ", "holy"),
}


def required_exp(level: int) -> int:
    """レベルアップに必要な経験値"""
    return int(100 * (level ** 1.5))


def calc_stats_for_level(char_type: str, level: int) -> dict[str, int]:
    """指定レベルのステータスを計算（丸めは floor。tech_numeric.md §2）"""
    g = GROWTH_RATES[char_type]
    steps = level - 1
    return {
        "max_hp": math.floor(g.base_hp + g.hp_growth * steps),
        "base_atk": math.floor(g.base_atk + g.atk_growth * steps),
        "base_def": math.floor(g.base_def + g.def_growth * steps),
        "base_spd": math.floor(g.base_spd + g.spd_growth * steps),
    }
