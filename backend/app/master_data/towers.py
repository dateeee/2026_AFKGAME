"""塔マスターデータ"""

import random
from dataclasses import dataclass, field

from app.master_data.enemies import EnemyData, get_enemy


@dataclass(frozen=True)
class TowerData:
    id: str
    name: str
    dungeon_name: str
    total_floors: int
    # フロア番号 → [(enemy_id, weight), ...]
    floor_encounters: dict[int, list[tuple[str, int]]]
    unlock_tower_id: str | None = None


# ゴブリンの塔 フロア別エンカウント（仕様書 001_ゴブリンの塔.md 準拠）
_GOBLIN_TOWER_ENCOUNTERS: dict[int, list[tuple[str, int]]] = {
    1:  [("slime", 70), ("goblin", 30)],
    2:  [("slime", 50), ("goblin", 50)],
    3:  [("slime", 30), ("goblin", 50), ("wolf", 20)],
    4:  [("goblin", 40), ("wolf", 40), ("goblin_archer", 20)],
    5:  [("goblin", 30), ("wolf", 40), ("goblin_archer", 30)],
    6:  [("wolf", 30), ("goblin_archer", 50), ("dire_wolf", 20)],
    7:  [("wolf", 20), ("goblin_archer", 45), ("dire_wolf", 35)],
    8:  [("goblin_archer", 35), ("dire_wolf", 50), ("wolf", 15)],
    9:  [("goblin_archer", 30), ("dire_wolf", 50), ("hobgoblin", 20)],
    10: [("dire_wolf", 50), ("goblin_archer", 35), ("wolf", 15)],
    11: [("dire_wolf", 30), ("hobgoblin", 50), ("wolf_leader", 20)],
    12: [("hobgoblin", 45), ("wolf_leader", 35), ("dire_wolf", 20)],
    13: [("hobgoblin", 35), ("wolf_leader", 45), ("goblin_shaman", 20)],
    14: [("wolf_leader", 35), ("hobgoblin", 30), ("goblin_shaman", 35)],
    15: [("wolf_leader", 30), ("goblin_shaman", 40), ("hobgoblin", 30)],
    16: [("goblin_shaman", 45), ("wolf_leader", 35), ("hobgoblin", 20)],
    17: [("goblin_shaman", 50), ("wolf_leader", 35), ("hobgoblin", 15)],
    18: [("goblin_shaman", 45), ("wolf_leader", 40), ("hobgoblin", 15)],
    19: [("goblin_shaman", 50), ("wolf_leader", 50)],
    20: [("goblin_king", 100)],
}

# 森の塔 フロア別エンカウント（仕様書 002_森の塔.md 準拠）
_FOREST_TOWER_ENCOUNTERS: dict[int, list[tuple[str, int]]] = {
    1:  [("wild_boar", 70), ("giant_snake", 30)],
    2:  [("wild_boar", 50), ("giant_snake", 50)],
    3:  [("giant_snake", 45), ("wild_boar", 30), ("forest_bear", 25)],
    4:  [("forest_bear", 45), ("giant_snake", 30), ("wild_boar", 25)],
    5:  [("wild_boar", 35), ("giant_snake", 35), ("forest_bear", 30)],
    6:  [("forest_bear", 45), ("giant_snake", 30), ("wild_boar", 25)],
    7:  [("forest_bear", 40), ("giant_snake", 35), ("wild_boar", 25)],
    8:  [("griffin", 40), ("forest_bear", 30), ("giant_snake", 30)],
    9:  [("griffin", 45), ("forest_bear", 30), ("giant_snake", 25)],
    10: [("griffin", 40), ("treant", 30), ("forest_bear", 30)],
    11: [("treant", 40), ("griffin", 35), ("forest_bear", 25)],
    12: [("griffin", 40), ("treant", 35), ("forest_bear", 25)],
    13: [("treant", 40), ("griffin", 35), ("giant_snake", 25)],
    14: [("treant", 40), ("griffin", 30), ("forest_bear", 30)],
    15: [("chimera", 40), ("treant", 30), ("griffin", 30)],
    16: [("chimera", 40), ("griffin", 30), ("treant", 30)],
    17: [("chimera", 35), ("treant", 35), ("griffin", 30)],
    18: [("chimera", 40), ("treant", 35), ("griffin", 25)],
    19: [("manticore", 35), ("chimera", 35), ("treant", 30)],
    20: [("manticore", 40), ("chimera", 30), ("treant", 30)],
    21: [("manticore", 40), ("chimera", 35), ("griffin", 25)],
    22: [("manticore", 35), ("chimera", 35), ("treant", 30)],
    23: [("manticore", 40), ("chimera", 35), ("treant", 25)],
    24: [("manticore", 40), ("treant", 30), ("chimera", 30)],
    25: [("manticore", 40), ("chimera", 35), ("treant", 25)],
    26: [("manticore", 40), ("chimera", 30), ("treant", 30)],
    27: [("manticore", 35), ("chimera", 35), ("treant", 30)],
    28: [("manticore", 40), ("chimera", 35), ("treant", 25)],
    29: [("manticore", 45), ("chimera", 30), ("treant", 25)],
    30: [("behemoth", 100)],
}

TOWERS: dict[str, TowerData] = {
    "goblin_tower": TowerData(
        id="goblin_tower",
        name="ゴブリンの塔",
        dungeon_name="始まりのダンジョン",
        total_floors=20,
        floor_encounters=_GOBLIN_TOWER_ENCOUNTERS,
        unlock_tower_id=None,
    ),
    "forest_tower": TowerData(
        id="forest_tower",
        name="森の塔",
        dungeon_name="始まりのダンジョン",
        total_floors=30,
        floor_encounters=_FOREST_TOWER_ENCOUNTERS,
        unlock_tower_id="goblin_tower",
    ),
}


def get_tower(tower_id: str) -> TowerData:
    return TOWERS[tower_id]


def roll_encounter(tower_id: str, floor: int) -> EnemyData:
    """フロアのエンカウントプールからランダムに敵を選出"""
    tower = TOWERS[tower_id]
    pool = tower.floor_encounters[floor]
    enemy_ids = [e[0] for e in pool]
    weights = [e[1] for e in pool]
    chosen_id = random.choices(enemy_ids, weights=weights, k=1)[0]
    return get_enemy(chosen_id)
