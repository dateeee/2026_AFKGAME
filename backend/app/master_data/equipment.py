"""装備マスターデータ・ドロップ生成"""

import math
import random
from dataclasses import dataclass

from app.config import (
    BOSS_ENEMY_DROP_RATE,
    FLOOR_RARITY_BONUS,
    LIFESTEAL_CHANCE,
    LIFESTEAL_RANGE,
    NORMAL_ENEMY_DROP_RATE,
    RARITY_BASE_RATES,
    RARITY_TIERS,
    STAT_MODIFIERS,
)


@dataclass(frozen=True)
class EquipmentBase:
    id: str
    name: str
    slot: str
    is_two_handed: bool = False


# ── 武器 ──
WEAPONS: list[EquipmentBase] = [
    # 片手武器
    EquipmentBase("sword", "剣", "weapon"),
    EquipmentBase("dagger", "短剣", "weapon"),
    EquipmentBase("mace", "メイス", "weapon"),
    # 両手武器
    EquipmentBase("greatsword", "大剣", "weapon", is_two_handed=True),
    EquipmentBase("spear", "槍", "weapon", is_two_handed=True),
    EquipmentBase("bow", "弓", "weapon", is_two_handed=True),
    EquipmentBase("staff", "杖", "weapon", is_two_handed=True),
]

# ── 防具・アクセサリ ──
ARMOR: list[EquipmentBase] = [
    EquipmentBase("shield", "盾", "shield"),
    EquipmentBase("helm", "兜", "head"),
    EquipmentBase("chest_armor", "鎧", "body"),
    EquipmentBase("gauntlets", "小手", "arms"),
    EquipmentBase("belt", "帯", "waist"),
    EquipmentBase("greaves", "脛当て", "legs"),
    EquipmentBase("earring", "耳飾り", "ears"),
    EquipmentBase("ring", "指輪", "ring"),
]

ALL_EQUIPMENT: list[EquipmentBase] = WEAPONS + ARMOR

_EQUIPMENT_BY_ID: dict[str, EquipmentBase] = {e.id: e for e in ALL_EQUIPMENT}
_EQUIPMENT_BY_SLOT: dict[str, list[EquipmentBase]] = {}
for _e in ALL_EQUIPMENT:
    _EQUIPMENT_BY_SLOT.setdefault(_e.slot, []).append(_e)

EQUIPMENT_SLOTS = ["weapon", "shield", "head", "body", "arms", "waist", "legs", "ears", "ring"]


def get_equipment_base(base_id: str) -> EquipmentBase:
    return _EQUIPMENT_BY_ID[base_id]


def _roll_drop(is_boss: bool) -> bool:
    rate_range = BOSS_ENEMY_DROP_RATE if is_boss else NORMAL_ENEMY_DROP_RATE
    rate = random.uniform(*rate_range)
    return random.random() < rate


def _roll_rarity(floor: int) -> str:
    for rarity, base_prob in RARITY_BASE_RATES:
        adjusted = base_prob * (1 + FLOOR_RARITY_BONUS * floor)
        if random.random() < adjusted:
            return rarity
    return "common"


def _roll_stats(rarity: str, enemy_level: int) -> dict:
    """ランダムにステータスを決定。戻り値: {stat_atk, stat_def, stat_hp, stat_spd}"""
    tier = RARITY_TIERS[rarity]
    stat_count = random.randint(tier["stats_min"], tier["stats_max"])

    stat_keys = list(STAT_MODIFIERS.keys())
    if stat_count >= 4:
        chosen = stat_keys[:]
    else:
        chosen = random.sample(stat_keys, stat_count)

    base_value = math.floor(enemy_level * 1.5 + 2)
    multiplier = tier["multiplier"]

    result = {}
    for key in stat_keys:
        if key in chosen:
            value = math.floor(base_value * multiplier * STAT_MODIFIERS[key])
            result[f"stat_{key}"] = max(1, value)
        else:
            result[f"stat_{key}"] = None
    return result


def generate_equipment_drop(
    enemy_level: int,
    floor: int,
    is_boss: bool,
) -> dict | None:
    """装備ドロップを試行。ドロップしなければ None。
    戻り値は Equipment モデル作成用の dict。
    """
    if not _roll_drop(is_boss):
        return None

    rarity = _roll_rarity(floor)

    # ランダムにスロットを選び、そのスロットからベースを選ぶ
    slot = random.choice(EQUIPMENT_SLOTS)
    bases = _EQUIPMENT_BY_SLOT[slot]
    base = random.choice(bases)

    stats = _roll_stats(rarity, enemy_level)

    # HP吸収
    lifesteal = None
    if random.random() < LIFESTEAL_CHANCE:
        lifesteal = round(random.uniform(*LIFESTEAL_RANGE), 3)

    return {
        "base_id": base.id,
        "slot": slot,
        "rarity": rarity,
        "level": enemy_level,
        "enhance_level": 0,
        "is_two_handed": base.is_two_handed,
        "lifesteal": lifesteal,
        **stats,
    }
