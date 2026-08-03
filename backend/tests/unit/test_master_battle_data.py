"""単体テスト: 戦闘マスターデータ（master_data/enemies.py, master_data/towers.py）

仕様: data/master/character.md（敵ステータス）、design/systems/dungeon.md（塔・エンカウント）
乱数を含む抽選は monkeypatch / seed で固定して分岐を網羅する。
"""

import random

import pytest

from app.master_data import towers as towers_module
from app.master_data.enemies import ENEMIES, get_enemy
from app.master_data.towers import TOWERS, get_tower, roll_encounter

pytestmark = pytest.mark.unit


class TestGetEnemy:
    def test_IDから敵データを返す(self):
        slime = get_enemy("slime")
        assert (slime.id, slime.name, slime.level) == ("slime", "スライム", 1)
        assert (slime.hp, slime.atk, slime.def_, slime.spd) == (20, 5, 2, 3)
        assert (slime.gold, slime.exp) == (5, 10)
        assert slime.is_boss is False  # 既定はボスではない

    @pytest.mark.parametrize("enemy_id", ["goblin_king", "behemoth"])
    def test_各塔のボスにはボスフラグが立つ(self, enemy_id):
        assert get_enemy(enemy_id).is_boss is True

    def test_未知のIDはKeyError(self):
        with pytest.raises(KeyError):
            get_enemy("no_such_enemy")


class TestEnemyTable:
    def test_キーとIDが一致する(self):
        assert all(key == enemy.id for key, enemy in ENEMIES.items())

    def test_ボスは各塔に1体だけ定義されている(self):
        bosses = [e.id for e in ENEMIES.values() if e.is_boss]
        assert bosses == ["goblin_king", "behemoth"]


class TestGetTower:
    def test_IDから塔データを返す(self):
        tower = get_tower("goblin_tower")
        assert (tower.name, tower.total_floors) == ("ゴブリンの塔", 20)
        assert tower.unlock_tower_id is None  # 解放条件なし

    def test_解放条件を持つ塔がある(self):
        forest = get_tower("forest_tower")
        assert forest.total_floors == 30
        assert forest.unlock_tower_id == "goblin_tower"

    def test_未知のIDはKeyError(self):
        with pytest.raises(KeyError):
            get_tower("no_such_tower")


class TestEncounterTable:
    @pytest.mark.parametrize("tower_id", sorted(TOWERS))
    def test_1階から最上階まで漏れなく定義されている(self, tower_id):
        tower = TOWERS[tower_id]
        assert sorted(tower.floor_encounters) == list(range(1, tower.total_floors + 1))

    @pytest.mark.parametrize("tower_id", sorted(TOWERS))
    def test_エンカウント先の敵が実在し重みが正(self, tower_id):
        for floor, pool in TOWERS[tower_id].floor_encounters.items():
            assert pool, f"{tower_id} {floor}F のプールが空"
            for enemy_id, weight in pool:
                assert enemy_id in ENEMIES, f"{tower_id} {floor}F に未定義の敵 {enemy_id}"
                assert weight > 0

    @pytest.mark.parametrize("tower_id", sorted(TOWERS))
    def test_最上階はボス単独(self, tower_id):
        tower = TOWERS[tower_id]
        pool = tower.floor_encounters[tower.total_floors]
        assert len(pool) == 1
        assert get_enemy(pool[0][0]).is_boss is True

    @pytest.mark.parametrize("tower_id", sorted(TOWERS))
    def test_ボスは最上階にしか出現しない(self, tower_id):
        tower = TOWERS[tower_id]
        for floor, pool in tower.floor_encounters.items():
            if floor == tower.total_floors:
                continue
            assert not any(get_enemy(e).is_boss for e, _ in pool), f"{tower_id} {floor}F にボス"


class TestRollEncounter:
    def test_フロアのプールと重みで抽選する(self, monkeypatch):
        captured: dict = {}

        def _choices(population, weights, k):
            captured.update(population=population, weights=weights, k=k)
            return [population[1]]

        monkeypatch.setattr(towers_module.DEFAULT_RNG, "choices", _choices)

        enemy = roll_encounter("goblin_tower", 1)

        assert captured["population"] == ["slime", "goblin"]
        assert captured["weights"] == [70, 30]
        assert captured["k"] == 1
        assert enemy.id == "goblin"  # 抽選結果がそのまま敵データになる

    @pytest.mark.parametrize(
        "tower_id,floor,expected",
        [("goblin_tower", 1, {"slime", "goblin"}), ("forest_tower", 1, {"wild_boar", "giant_snake"})],
    )
    def test_プール内の敵だけが出る(self, tower_id, floor, expected):
        random.seed(20260802)
        rolled = {roll_encounter(tower_id, floor).id for _ in range(200)}
        assert rolled == expected  # 重み付きなのでどちらも出うる

    @pytest.mark.parametrize("tower_id", sorted(TOWERS))
    def test_最上階では必ずボスが出る(self, tower_id):
        random.seed(1234)
        top = TOWERS[tower_id].total_floors
        assert all(roll_encounter(tower_id, top).is_boss for _ in range(20))

    def test_存在しないフロアはKeyError(self):
        with pytest.raises(KeyError):
            roll_encounter("goblin_tower", 999)

    def test_存在しない塔はKeyError(self):
        with pytest.raises(KeyError):
            roll_encounter("no_such_tower", 1)
