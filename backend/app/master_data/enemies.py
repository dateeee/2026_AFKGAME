"""敵マスターデータ（ゴブリンの塔）"""

from dataclasses import dataclass


@dataclass(frozen=True)
class EnemyData:
    id: str
    name: str
    level: int
    hp: int
    atk: int
    def_: int
    spd: int
    gold: int
    exp: int
    is_boss: bool = False


ENEMIES: dict[str, EnemyData] = {
    "slime": EnemyData("slime", "スライム", 1, 20, 5, 2, 3, 5, 10),
    "goblin": EnemyData("goblin", "ゴブリン", 2, 35, 8, 4, 5, 8, 18),
    "wolf": EnemyData("wolf", "ウルフ", 3, 30, 12, 3, 8, 10, 22),
    "goblin_archer": EnemyData("goblin_archer", "ゴブリンアーチャー", 4, 40, 14, 5, 7, 12, 28),
    "dire_wolf": EnemyData("dire_wolf", "ダイアウルフ", 6, 60, 18, 8, 10, 18, 40),
    "hobgoblin": EnemyData("hobgoblin", "ホブゴブリン", 8, 90, 22, 12, 7, 25, 55),
    "wolf_leader": EnemyData("wolf_leader", "ウルフリーダー", 9, 100, 25, 10, 12, 30, 65),
    "goblin_shaman": EnemyData("goblin_shaman", "ゴブリンシャーマン", 10, 80, 28, 8, 9, 28, 60),
    "goblin_king": EnemyData("goblin_king", "ゴブリンキング", 12, 200, 32, 16, 8, 80, 150, is_boss=True),
}


def get_enemy(enemy_id: str) -> EnemyData:
    return ENEMIES[enemy_id]
