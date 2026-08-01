"""敵マスターデータ"""

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
    # ── ゴブリンの塔（001_ゴブリンの塔.md） ──
    "slime": EnemyData("slime", "スライム", 1, 20, 5, 2, 3, 5, 10),
    "goblin": EnemyData("goblin", "ゴブリン", 2, 35, 8, 4, 5, 8, 18),
    "wolf": EnemyData("wolf", "オオカミ", 3, 30, 12, 3, 8, 10, 22),
    "goblin_archer": EnemyData("goblin_archer", "ゴブリンアーチャー", 4, 40, 14, 5, 7, 12, 28),
    "dire_wolf": EnemyData("dire_wolf", "ダイアウルフ", 6, 60, 18, 8, 10, 18, 40),
    "hobgoblin": EnemyData("hobgoblin", "ホブゴブリン", 8, 90, 22, 12, 7, 25, 55),
    "wolf_leader": EnemyData("wolf_leader", "ウルフリーダー", 9, 100, 25, 10, 12, 30, 65),
    "goblin_shaman": EnemyData("goblin_shaman", "ゴブリンシャーマン", 10, 80, 28, 8, 9, 28, 60),
    "goblin_king": EnemyData("goblin_king", "ゴブリンキング", 12, 200, 32, 16, 8, 80, 150, is_boss=True),
    # ── 森の塔（002_森の塔.md） ──
    "wild_boar": EnemyData("wild_boar", "ワイルドボア", 13, 110, 24, 12, 7, 15, 35),
    "giant_snake": EnemyData("giant_snake", "大蛇", 14, 80, 28, 8, 12, 18, 42),
    "forest_bear": EnemyData("forest_bear", "森の熊", 15, 130, 26, 14, 6, 20, 48),
    "griffin": EnemyData("griffin", "グリフォン", 17, 120, 34, 12, 14, 28, 65),
    "treant": EnemyData("treant", "トレント", 19, 180, 30, 20, 4, 35, 80),
    "chimera": EnemyData("chimera", "キメラ", 20, 140, 40, 14, 11, 38, 88),
    "manticore": EnemyData("manticore", "マンティコア", 22, 160, 38, 18, 12, 45, 100),
    "behemoth": EnemyData("behemoth", "ベヒーモス", 25, 500, 45, 24, 10, 150, 300, is_boss=True),
}


def get_enemy(enemy_id: str) -> EnemyData:
    return ENEMIES[enemy_id]
