"""単体テスト: 戦闘サービス（services/battle_service.py）

仕様: tech/tech_battle.md（戦闘処理）、design/systems/battle.md
乱数を含むロジックは monkeypatch で固定して分岐を網羅する（development_process.md §3.5）。
"""

import pytest

from app.master_data.enemies import EnemyData
from app.models.item import InventoryItem
from app.models.player import TowerClearRecord
from app.services import battle_service as bs

pytestmark = pytest.mark.unit


@pytest.fixture
def no_variance(monkeypatch):
    """ダメージ分散・クリティカル・装備ドロップを固定する"""
    monkeypatch.setattr(bs.random, "uniform", lambda a, b: 0.0)
    monkeypatch.setattr(bs.random, "random", lambda: 1.0)  # クリティカル発生せず
    monkeypatch.setattr(bs, "try_drop", lambda *a, **kw: (None, None))


def _weak_enemy(is_boss: bool = False) -> EnemyData:
    """1撃で倒せる敵（SPDはプレイヤーより遅く、先攻を取らせる）"""
    return EnemyData("test_enemy", "テスト敵", 1, 1, 1, 0, 0, 10, 5, is_boss=is_boss)


@pytest.fixture
def one_shot(monkeypatch, no_variance):
    """常に「1撃で倒せる通常敵」が出るようにする"""

    def _set(is_boss: bool = False) -> EnemyData:
        enemy = _weak_enemy(is_boss)
        monkeypatch.setattr(bs, "roll_encounter", lambda tower_id, floor: enemy)
        monkeypatch.setattr(bs, "get_enemy", lambda enemy_id: enemy)
        return enemy

    return _set


class TestCalcDamage:
    def test_プレイヤーの最低ダメージは1(self, monkeypatch):
        monkeypatch.setattr(bs.random, "uniform", lambda a, b: 0.0)
        monkeypatch.setattr(bs.random, "random", lambda: 1.0)
        # atk=1, def=100 → 生値は負。味方は1保証（game_spec §2.2）
        assert bs._calc_damage(1, 100, is_player=True) == (1, False)

    def test_敵の最低ダメージは0(self, monkeypatch):
        monkeypatch.setattr(bs.random, "uniform", lambda a, b: 0.0)
        monkeypatch.setattr(bs.random, "random", lambda: 1.0)
        assert bs._calc_damage(1, 100, is_player=False) == (0, False)

    def test_クリティカルで1_5倍になる(self, monkeypatch):
        monkeypatch.setattr(bs.random, "uniform", lambda a, b: 0.0)
        monkeypatch.setattr(bs.random, "random", lambda: 0.0)  # < 0.05 → クリティカル
        damage, is_crit = bs._calc_damage(100, 0, is_player=True)
        assert (damage, is_crit) == (150, True)

    def test_分散が下振れすると減る(self, monkeypatch):
        monkeypatch.setattr(bs.random, "uniform", lambda a, b: -0.1)
        monkeypatch.setattr(bs.random, "random", lambda: 1.0)
        assert bs._calc_damage(100, 0, is_player=True)[0] == 90


class TestCheckLevelUp:
    def test_必要EXP未満ならレベルアップしない(self, character):
        character.exp = 99  # LV1→2 の必要EXPは 100
        assert bs._check_level_up(character) == 0
        assert character.level == 1

    def test_レベルアップでステータスが再計算されHPが全回復する(self, character):
        character.hp = 1
        character.exp = 100
        assert bs._check_level_up(character) == 1
        assert character.level == 2
        assert character.exp == 0
        assert character.max_hp == 120  # base_hp 100 + hp_growth 20
        assert character.base_atk == 13
        assert character.hp == character.max_hp

    def test_一度に複数レベル上がる(self, character):
        character.exp = 100 + 282 + 519  # LV1→2→3→4 の必要EXP合計
        assert bs._check_level_up(character) == 3
        assert character.level == 4


class TestUsePotion:
    def test_所持していなければ使用しない(self, db, player, character):
        db.query(InventoryItem).filter_by(player_id=player.id).delete()
        db.commit()
        assert bs._use_potion(player, character, 100, db) is False

    def test_所持数0なら使用しない(self, db, player, character):
        item = db.query(InventoryItem).filter_by(player_id=player.id, item_id="hp_potion").first()
        item.quantity = 0
        db.commit()
        assert bs._use_potion(player, character, 100, db) is False

    def test_回復し所持数が減る(self, db, player, character):
        character.hp = 10
        assert bs._use_potion(player, character, 100, db) is True
        assert character.hp > 10
        item = db.query(InventoryItem).filter_by(player_id=player.id, item_id="hp_potion").first()
        assert item.quantity == 4

    def test_最大HPを超えて回復しない(self, db, player, character):
        character.hp = 99
        bs._use_potion(player, character, 100, db)
        assert character.hp == 100


class TestRecoverHp:
    def test_回復量を返す(self, character):
        character.hp = 10
        # floor(100 × 0.02 + 5 × 0.5) = 4
        assert bs._recover_hp(character, 100, 5) == 4
        assert character.hp == 14

    def test_最大HPで頭打ちになる(self, character):
        character.hp = 99
        assert bs._recover_hp(character, 100, 5) == 1
        assert character.hp == 100


class TestUpdateTowerRecord:
    def test_記録がなければ作成する(self, db, player):
        assert bs._update_tower_record(player, "goblin_tower", 3, False, db) == 0
        db.commit()
        record = db.query(TowerClearRecord).filter_by(tower_id="goblin_tower").first()
        assert record.highest_floor == 3
        assert record.cleared is False

    def test_更新前の最高階を返す(self, db, player, tower_record):
        tower_record("goblin_tower", highest_floor=5)
        assert bs._update_tower_record(player, "goblin_tower", 6, False, db) == 5

    def test_既踏の階では最高階を下げない(self, db, player, tower_record):
        record = tower_record("goblin_tower", highest_floor=10)
        bs._update_tower_record(player, "goblin_tower", 4, False, db)
        db.commit()
        assert record.highest_floor == 10

    def test_ボス討伐でクリア済みになる(self, db, player, tower_record):
        record = tower_record("goblin_tower", highest_floor=19)
        bs._update_tower_record(player, "goblin_tower", 20, True, db)
        db.commit()
        assert record.cleared is True


class TestProcessTickOutsideTower:
    def test_塔外ではHPが自然回復する(self, db, player, character):
        character.hp = 10
        result = bs.process_tick(player, character, db)
        assert character.hp > 10
        assert result.battle_logs[0][0]["type"] == "recovery"

    def test_満タンならログを出さない(self, db, player, character):
        character.hp = character.max_hp
        result = bs.process_tick(player, character, db)
        assert result.battle_logs == []


class TestProcessTickTargetFloorFollow:
    """上限追従（design/systems/battle.md）— オンライン・オフライン共通の tick 処理内で行う"""

    def _enter(self, player, character, floor=1, target=1, mode="auto_repeat"):
        player.current_tower_id = "goblin_tower"
        player.current_floor = floor
        player.target_floor = target
        player.tower_mode = mode
        player.hp_threshold = 0.0  # 撤退させない
        character.base_atk = 9999  # 1撃で倒す

    def test_目標階が上限と一致していれば追従して次の階へ進む(self, db, player, character, one_shot):
        one_shot()
        self._enter(player, character, floor=1, target=1)
        result = bs.process_tick(player, character, db)

        follow = [e for log in result.battle_logs for e in log if e["type"] == "target_floor_follow"]
        assert follow, "上限追従ログが出ていない"
        assert player.target_floor > 1
        assert player.current_floor > 1  # 追従により周回せず開拓が進む

    def test_目標階を低く設定していれば追従せず周回する(self, db, player, character, one_shot, tower_record):
        one_shot()
        tower_record("goblin_tower", highest_floor=10)  # 上限は11、目標は3
        self._enter(player, character, floor=3, target=3)
        result = bs.process_tick(player, character, db)

        follow = [e for log in result.battle_logs for e in log if e["type"] == "target_floor_follow"]
        assert follow == []
        assert player.target_floor == 3
        # 目標到達 → auto_repeat で1階から再突入。目標階を超えて進むことはない
        types = [e["type"] for log in result.battle_logs for e in log]
        assert "tower_restart" in types
        assert player.current_floor <= 3

    def test_既踏の階を周回しても追従しない(self, db, player, character, one_shot, tower_record):
        one_shot()
        tower_record("goblin_tower", highest_floor=10)
        self._enter(player, character, floor=1, target=11)  # 目標 == 上限だが既踏階を周回中
        bs.process_tick(player, character, db)
        assert player.target_floor == 11

    def test_最上階では追従せず目標到達になる(self, db, player, character, one_shot, tower_record):
        one_shot(is_boss=True)
        tower_record("goblin_tower", highest_floor=19)
        self._enter(player, character, floor=20, target=20, mode="stop_on_clear")
        result = bs.process_tick(player, character, db)

        assert player.target_floor is None  # stop_on_clear で塔を出る
        assert player.current_tower_id is None
        types = [e["type"] for log in result.battle_logs for e in log]
        assert "target_floor_follow" not in types
        assert "tower_target_reached" in types


class TestProcessTickProgression:
    def test_目標到達時に自動周回で1階へ戻る(self, db, player, character, one_shot, tower_record):
        one_shot()
        tower_record("goblin_tower", highest_floor=10)
        player.current_tower_id = "goblin_tower"
        player.current_floor = 5
        player.target_floor = 5
        player.tower_mode = "auto_repeat"
        player.hp_threshold = 0.0
        character.base_atk = 9999

        result = bs.process_tick(player, character, db)
        types = [e["type"] for log in result.battle_logs for e in log]
        assert "tower_restart" in types
        assert player.current_floor < 5  # 1階へ戻って再開している
        assert player.current_tower_id == "goblin_tower"

    def test_全滅でペナルティを受け塔を出る(self, db, player, character, monkeypatch, no_variance):
        strong = EnemyData("boss", "強敵", 99, 9999, 9999, 9999, 9999, 0, 0)
        monkeypatch.setattr(bs, "roll_encounter", lambda tower_id, floor: strong)
        monkeypatch.setattr(bs, "get_enemy", lambda enemy_id: strong)

        player.current_tower_id = "goblin_tower"
        player.current_floor = 1
        player.target_floor = 1
        player.gold = 500
        player.run_gold = 200
        character.exp = 80

        result = bs.process_tick(player, character, db)

        assert result.defeated is True
        assert character.exp == 40      # 蓄積EXPの50%ロスト
        assert player.gold == 300       # 塔内取得ゴールドをロスト
        assert character.hp == 1        # HP1で復活
        assert player.current_tower_id is None
