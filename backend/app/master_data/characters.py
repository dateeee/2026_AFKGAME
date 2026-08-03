"""キャラクター成長・レベル計算"""

from dataclasses import dataclass


@dataclass(frozen=True)
class GrowthRate:
    base_hp: int
    base_atk: int
    base_def: int
    base_spd: int
    hp_growth: int
    atk_growth: int
    def_growth: int
    spd_growth: int


GROWTH_RATES: dict[str, GrowthRate] = {
    "melee": GrowthRate(
        base_hp=100, base_atk=10, base_def=5, base_spd=5,
        hp_growth=20, atk_growth=3, def_growth=2, spd_growth=1,
    ),
}


def required_exp(level: int) -> int:
    """レベルアップに必要な経験値"""
    return int(100 * (level ** 1.5))


def calc_stats_for_level(char_type: str, level: int) -> dict[str, int]:
    """指定レベルのステータスを計算"""
    g = GROWTH_RATES[char_type]
    return {
        "max_hp": g.base_hp + g.hp_growth * (level - 1),
        "base_atk": g.base_atk + g.atk_growth * (level - 1),
        "base_def": g.base_def + g.def_growth * (level - 1),
        "base_spd": g.base_spd + g.spd_growth * (level - 1),
    }
