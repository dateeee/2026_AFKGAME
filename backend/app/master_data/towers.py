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

TOWERS: dict[str, TowerData] = {
    "goblin_tower": TowerData(
        id="goblin_tower",
        name="ゴブリンの塔",
        dungeon_name="始まりのダンジョン",
        total_floors=20,
        floor_encounters=_GOBLIN_TOWER_ENCOUNTERS,
        unlock_tower_id=None,
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
