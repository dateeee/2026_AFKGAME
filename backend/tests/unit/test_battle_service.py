"""単体テスト: 戦闘サービス（services/battle_service.py）

仕様: tech/tech_battle.md（戦闘処理）、design/systems/battle.md
乱数を含むロジックは monkeypatch で固定して分岐を網羅する（development_process.md §3.5）。
"""

from types import SimpleNamespace

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


# ─────────────────────────────────────────────────────────────
# 以下、未達分岐の補完（tech_battle.md / tech_offline.md）
# ─────────────────────────────────────────────────────────────


def _enter_tower(player, *, floor=1, target=20, mode="auto_repeat", hp_threshold=0.0):
    """塔に入っている状態を作る（退却させない既定値）"""
    player.current_tower_id = "goblin_tower"
    player.current_floor = floor
    player.target_floor = target
    player.tower_mode = mode
    player.hp_threshold = hp_threshold


def _log_types(result) -> list[str]:
    return [e["type"] for log in result.battle_logs for e in log]


def _remove_potions(db, player) -> None:
    db.query(InventoryItem).filter_by(player_id=player.id, item_id="hp_potion").delete()
    db.commit()


class TestTickResultAccumulate:
    """tick結果の合算（オフライン一括処理でルーターが使う）"""

    def test_カウンタとリストを合算する(self):
        base = bs.TickResult(
            battle_logs=[[{"type": "attack"}]],
            total_gold=10, total_exp=5, enemies_defeated=1, potions_used=1,
            levels_gained=1, floors_cleared=1,
            equipment_drops=["eq1"], equipment_auto_sold=[{"gold": 3}],
        )
        other = bs.TickResult(
            battle_logs=[[{"type": "defeat"}]],
            total_gold=7, total_exp=2, enemies_defeated=2, potions_used=0,
            levels_gained=0, floors_cleared=3,
            equipment_drops=["eq2"], equipment_auto_sold=[{"gold": 4}],
        )

        base.accumulate(other)

        assert len(base.battle_logs) == 2
        assert (base.total_gold, base.total_exp) == (17, 7)
        assert (base.enemies_defeated, base.potions_used) == (3, 1)
        assert (base.levels_gained, base.floors_cleared) == (1, 4)
        assert base.equipment_drops == ["eq1", "eq2"]
        assert base.equipment_auto_sold == [{"gold": 3}, {"gold": 4}]
        assert base.defeated is False  # 相手が全滅していなければ立たない

    def test_相手が全滅していれば全滅フラグを引き継ぐ(self):
        base = bs.TickResult()
        base.accumulate(bs.TickResult(defeated=True))
        assert base.defeated is True


class TestTargetFloorCapUnlimited:
    def test_総階数を持たない塔は到達階プラス1が上限(self):
        # total_floors=None は無限塔（深淵の塔、Phase 5〜）
        assert bs.target_floor_cap(7, None) == 8
        assert bs.target_floor_cap(0, None) == 1


class TestGetTowerHighestFloor:
    def test_記録がなければ0(self, db, player):
        assert bs.get_tower_highest_floor(player, "goblin_tower", db) == 0

    def test_記録があれば到達階を返す(self, db, player, tower_record):
        tower_record("goblin_tower", highest_floor=12)
        assert bs.get_tower_highest_floor(player, "goblin_tower", db) == 12

    def test_記録があっても到達階0なら0(self, db, player, tower_record):
        tower_record("goblin_tower", highest_floor=0)
        assert bs.get_tower_highest_floor(player, "goblin_tower", db) == 0


class TestGetPotionCount:
    def test_所持数を返す(self, db, player):
        assert bs._get_potion_count(player, db) == 5

    def test_未所持なら0(self, db, player):
        _remove_potions(db, player)
        assert bs._get_potion_count(player, db) == 0


class TestProcessTickRecovery:
    def test_回復量が0ならログを出さない(self, db, player, character):
        # floor(max_hp 10 × 0.02 + def 0 × 0.5) = 0 → 回復せずログも出さない
        character.max_hp = 10
        character.hp = 9
        character.base_def = 0

        result = bs.process_tick(player, character, db)

        assert character.hp == 9
        assert result.battle_logs == []


class TestProcessTickEncounter:
    def test_継続中の敵はエンカウントし直さない(self, db, player, character, no_variance):
        _enter_tower(player)
        player.current_enemy_id = "slime"
        player.current_enemy_hp = 20
        character.base_atk = 9999

        result = bs.process_tick(player, character, db)

        first = result.battle_logs[0][0]
        assert first["type"] == "attack"  # 先頭が encounter ではない = 引き継いでいる
        assert first["target"] == "スライム"

    def test_HP0のまま塔にいると行動もログもない(self, db, player, character):
        """1tick中に一度も行動できないケース（tick_logs が空のまま返る）"""
        _remove_potions(db, player)
        _enter_tower(player)
        player.current_enemy_id = "slime"
        player.current_enemy_hp = 20
        character.hp = 0

        result = bs.process_tick(player, character, db)

        assert result.battle_logs == []
        assert character.hp == 0
        assert player.current_enemy_hp == 20  # 敵も減っていない


class TestProcessTickPotion:
    def test_設定がなければ閾値0_5でポーションを使う(self, db, player, character, one_shot):
        one_shot()
        db.delete(player.settings)
        db.commit()
        db.refresh(player)
        assert player.settings is None

        _enter_tower(player)
        character.hp = 40  # 100 × 0.5 = 50 以下 → 使用

        result = bs.process_tick(player, character, db)

        assert "potion" in _log_types(result)
        assert result.potions_used >= 1
        assert character.hp > 40

    def test_所持していなければ使用ログを出さない(self, db, player, character, one_shot):
        one_shot()
        _remove_potions(db, player)
        _enter_tower(player)
        character.hp = 10  # 設定閾値 0.3 を下回るが在庫なし

        result = bs.process_tick(player, character, db)

        assert "potion" not in _log_types(result)
        assert result.potions_used == 0


class TestProcessTickLifesteal:
    @pytest.fixture
    def with_lifesteal(self, monkeypatch):
        def _set(lifesteal: float):
            monkeypatch.setattr(
                bs, "get_effective_stats",
                lambda ch, db: {
                    "atk": ch.base_atk, "def": ch.base_def, "spd": ch.base_spd,
                    "hp_bonus": 0, "lifesteal": lifesteal,
                },
            )

        return _set

    def test_吸収量が1以上ならHPを回復してログを出す(self, db, player, character, one_shot, with_lifesteal):
        one_shot()
        with_lifesteal(0.5)
        _enter_tower(player)
        character.hp = 50  # ダメージ10 → 吸収5

        result = bs.process_tick(player, character, db)

        heals = [e for log in result.battle_logs for e in log if e["type"] == "lifesteal"]
        assert heals and heals[0]["amount"] == 5
        assert character.hp > 50

    def test_吸収量が0未満に切り捨てられればログを出さない(self, db, player, character, one_shot, with_lifesteal):
        one_shot()
        with_lifesteal(0.01)  # floor(10 × 0.01) = 0
        _enter_tower(player)
        character.hp = 50

        result = bs.process_tick(player, character, db)

        assert "lifesteal" not in _log_types(result)
        assert character.hp == 50

    def test_吸収なしならログを出さない(self, db, player, character, one_shot, with_lifesteal):
        one_shot()
        with_lifesteal(0.0)
        _enter_tower(player)
        character.hp = 50

        result = bs.process_tick(player, character, db)

        assert "lifesteal" not in _log_types(result)


class TestProcessTickEnemySurvives:
    def test_敵が生き残れば敵の反撃に移る(self, db, player, character, monkeypatch, no_variance):
        tanky = EnemyData("tanky", "硬い敵", 5, 1000, 1, 0, 0, 10, 5)
        monkeypatch.setattr(bs, "roll_encounter", lambda tower_id, floor: tanky)
        monkeypatch.setattr(bs, "get_enemy", lambda enemy_id: tanky)
        _enter_tower(player)

        result = bs.process_tick(player, character, db)

        types = _log_types(result)
        assert "defeat" not in types
        assert types.count("attack") == 6  # 3ターン × (プレイヤー + 敵)
        assert player.current_enemy_hp == 1000 - 10 * 3
        assert character.hp == 100  # 敵ATK1 vs DEF5 → 0ダメージ


class TestProcessTickEquipmentDrop:
    def test_ドロップがログと結果に載る(self, db, player, character, one_shot, monkeypatch):
        one_shot()
        dropped = SimpleNamespace(base_id="iron_sword", rarity="rare", slot="weapon")
        monkeypatch.setattr(bs, "try_drop", lambda *a, **kw: (dropped, None))
        _enter_tower(player)

        result = bs.process_tick(player, character, db)

        assert dropped in result.equipment_drops
        drop_logs = [e for log in result.battle_logs for e in log if e["type"] == "equipment_drop"]
        assert drop_logs[0] == {
            "type": "equipment_drop", "name": "iron_sword", "rarity": "rare", "slot": "weapon",
        }
        assert "equipment_auto_sold" not in _log_types(result)

    def test_自動売却がログと結果に載る(self, db, player, character, one_shot, monkeypatch):
        one_shot()
        sold = {"name": "折れた剣", "rarity": "common", "gold": 12}
        monkeypatch.setattr(bs, "try_drop", lambda *a, **kw: (None, sold))
        _enter_tower(player)

        result = bs.process_tick(player, character, db)

        assert sold in result.equipment_auto_sold
        types = _log_types(result)
        assert "equipment_auto_sold" in types
        assert "equipment_drop" not in types


class TestProcessTickLevelUp:
    def test_戦闘中のレベルアップでステータスを再計算する(self, db, player, character, one_shot):
        one_shot()  # 撃破ごとに EXP 5
        _enter_tower(player)
        character.exp = 99  # LV1→2 の必要EXPは 100

        result = bs.process_tick(player, character, db)

        level_ups = [e for log in result.battle_logs for e in log if e["type"] == "level_up"]
        assert level_ups, "レベルアップログが出ていない"
        assert result.levels_gained >= 1
        assert character.level >= 2
        assert level_ups[0]["stats"]["max_hp"] == character.max_hp  # 再計算後の実効値
        assert level_ups[0]["stats"]["atk"] == character.base_atk


class TestProcessTickRetreat:
    """HP閾値による退却（game_spec §2.2）"""

    def _setup(self, db, player, character, mode):
        _remove_potions(db, player)
        _enter_tower(player, floor=3, target=5, mode=mode, hp_threshold=0.9)
        character.hp = 10  # 100 × 0.9 = 90 を下回る

    def test_自動周回なら1階から再スタートする(self, db, player, character, one_shot, tower_record):
        one_shot()
        tower_record("goblin_tower", highest_floor=10)  # 上限追従を起こさない
        self._setup(db, player, character, "auto_repeat")

        result = bs.process_tick(player, character, db)

        types = _log_types(result)
        assert "retreat_hp" in types
        assert "tower_restart" in types
        assert player.current_tower_id == "goblin_tower"
        assert player.current_floor == 1
        assert player.run_gold == 0

    def test_停止モードなら塔を出る(self, db, player, character, one_shot, tower_record):
        one_shot()
        tower_record("goblin_tower", highest_floor=10)
        self._setup(db, player, character, "stop_on_clear")

        result = bs.process_tick(player, character, db)

        types = _log_types(result)
        assert "retreat_hp" in types
        assert "tower_restart" not in types
        assert player.current_tower_id is None
        assert player.current_floor is None
        assert player.target_floor is None
        assert player.run_gold == 0
