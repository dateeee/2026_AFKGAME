"""単体テスト: 装備マスターデータ・ドロップ生成（master_data/equipment.py）

仕様: design/systems/equipment.md「レアリティ出現率（階層依存）」「ドロップ率」
      「装備の基礎ステータス値」「HP吸収装備」、data/master/equipment.md「6. 装備基礎値」
分岐観点:
  - _roll_drop: 通常敵/ボス敵の率テーブル参照、判定の当たり / 外れ（率ちょうどは外れ）
  - _roll_rarity: 最初に当たる / 上位を外して途中に当たる / 全て外れて common / 階層補正
  - _roll_stats: 4種全付与 / 抽選で一部のみ付与 / 計算値1未満のクランプ
  - generate_equipment_drop: ドロップ無し / HP吸収の有無 / 両手武器フラグの引き継ぎ
"""

import pytest

from app.master_data import equipment as me

pytestmark = pytest.mark.unit


class TestGetEquipmentBase:
    def test_既知IDならベース定義を返す(self):
        base = me.get_equipment_base("sword")
        assert (base.name, base.slot, base.is_two_handed) == ("剣", "weapon", False)

    def test_未知IDはKeyError(self):
        with pytest.raises(KeyError):
            me.get_equipment_base("no_such_base")


class TestRollDrop:
    def test_通常敵は通常ドロップ率で判定される(self, monkeypatch):
        captured = {}

        def fake_uniform(a, b):
            captured["range"] = (a, b)
            return 0.15

        monkeypatch.setattr(me.random, "uniform", fake_uniform)
        monkeypatch.setattr(me.random, "random", lambda: 0.10)  # 率 0.15 未満 → ドロップ
        assert me._roll_drop(is_boss=False) is True
        assert captured["range"] == me.NORMAL_ENEMY_DROP_RATE

    def test_ボス敵はボス用ドロップ率で判定される(self, monkeypatch):
        captured = {}

        def fake_uniform(a, b):
            captured["range"] = (a, b)
            return 0.6

        monkeypatch.setattr(me.random, "uniform", fake_uniform)
        monkeypatch.setattr(me.random, "random", lambda: 0.6)  # 率ちょうど → ドロップしない
        assert me._roll_drop(is_boss=True) is False
        assert captured["range"] == me.BOSS_ENEMY_DROP_RATE


class TestRollRarity:
    def test_全ての抽選に外れるとコモンになる(self, monkeypatch):
        monkeypatch.setattr(me.random, "random", lambda: 1.0)
        assert me._roll_rarity(floor=1) == "common"

    def test_最初の抽選に当たると最上位レアリティになる(self, monkeypatch):
        monkeypatch.setattr(me.random, "random", lambda: 0.0)
        assert me._roll_rarity(floor=1) == "legendary"

    def test_上位を外して途中の抽選に当たるとその段階になる(self, monkeypatch):
        # floor=0 は補正なし: legendary(0.005) は外れ、epic(0.04) に当たる
        monkeypatch.setattr(me.random, "random", lambda: 0.01)
        assert me._roll_rarity(floor=0) == "epic"

    def test_高層階では階層補正で上位が出やすくなる(self, monkeypatch):
        # 0.007 は legendary 基礎率 0.005 を上回るが、20階では 0.005×(1+0.05×20)=0.01 を下回る
        monkeypatch.setattr(me.random, "random", lambda: 0.007)
        assert me._roll_rarity(floor=0) == "epic"
        assert me._roll_rarity(floor=20) == "legendary"


class TestRollStats:
    def test_抽選数が4なら全ステータスが付与される(self, monkeypatch):
        captured = {}

        def fake_randint(a, b):
            captured["range"] = (a, b)
            return 4

        monkeypatch.setattr(me.random, "randint", fake_randint)
        result = me._roll_stats("common", enemy_level=10)
        # 基礎値 floor(10×1.5+2)=17、コモン倍率1.0、修正 atk1.0 / def0.7 / hp5.0 / spd0.3
        assert result == {"stat_atk": 17, "stat_def": 11, "stat_hp": 85, "stat_spd": 5}
        tier = me.RARITY_TIERS["common"]
        assert captured["range"] == (tier["stats_min"], tier["stats_max"])

    def test_抽選数が4未満なら選ばれなかった枠はNoneになる(self, monkeypatch):
        monkeypatch.setattr(me.random, "randint", lambda a, b: 1)
        monkeypatch.setattr(me.random, "sample", lambda seq, k: ["hp"])
        result = me._roll_stats("common", enemy_level=10)
        assert result == {"stat_atk": None, "stat_def": None, "stat_hp": 85, "stat_spd": None}

    def test_計算値が1未満なら1にクランプされる(self, monkeypatch):
        monkeypatch.setattr(me.random, "randint", lambda a, b: 4)
        result = me._roll_stats("common", enemy_level=1)
        # spd = floor(floor(1×1.5+2) × 1.0 × 0.3) = floor(0.9) = 0 → 1 にクランプ
        assert result["stat_spd"] == 1


class TestGenerateEquipmentDrop:
    @pytest.fixture
    def fixed_rolls(self, monkeypatch):
        """ドロップ判定・レアリティ・ステータス抽選を固定する"""
        monkeypatch.setattr(me, "_roll_drop", lambda is_boss: True)
        monkeypatch.setattr(me, "_roll_rarity", lambda floor: "rare")
        monkeypatch.setattr(
            me,
            "_roll_stats",
            lambda rarity, enemy_level: {
                "stat_atk": 9,
                "stat_def": None,
                "stat_hp": None,
                "stat_spd": None,
            },
        )

    def test_ドロップ判定に外れるとNoneを返す(self, monkeypatch):
        monkeypatch.setattr(me, "_roll_drop", lambda is_boss: False)
        assert me.generate_equipment_drop(enemy_level=5, floor=3, is_boss=False) is None

    def test_HP吸収の抽選に当たると吸収率が付与される(self, fixed_rolls, monkeypatch):
        monkeypatch.setattr(me.random, "choice", lambda seq: seq[0])  # slot=weapon → 剣
        monkeypatch.setattr(me.random, "random", lambda: 0.0)  # LIFESTEAL_CHANCE 未満
        monkeypatch.setattr(me.random, "uniform", lambda a, b: 0.05)
        drop = me.generate_equipment_drop(enemy_level=5, floor=3, is_boss=True)
        assert drop == {
            "base_id": "sword",
            "slot": "weapon",
            "rarity": "rare",
            "level": 5,
            "enhance_level": 0,
            "is_two_handed": False,
            "lifesteal": 0.05,
            "stat_atk": 9,
            "stat_def": None,
            "stat_hp": None,
            "stat_spd": None,
        }

    def test_HP吸収の抽選に外れると吸収率は付かない(self, fixed_rolls, monkeypatch):
        monkeypatch.setattr(me.random, "choice", lambda seq: seq[-1])  # slot=ring → 指輪
        monkeypatch.setattr(me.random, "random", lambda: 1.0)
        drop = me.generate_equipment_drop(enemy_level=5, floor=3, is_boss=False)
        assert drop["lifesteal"] is None
        assert (drop["base_id"], drop["slot"]) == ("ring", "ring")

    def test_両手武器を引くとフラグが引き継がれる(self, fixed_rolls, monkeypatch):
        def pick(seq):
            if isinstance(seq[0], str):
                return "weapon"  # スロット抽選
            return next(b for b in seq if b.is_two_handed)  # 両手武器（大剣）

        monkeypatch.setattr(me.random, "choice", pick)
        monkeypatch.setattr(me.random, "random", lambda: 1.0)
        drop = me.generate_equipment_drop(enemy_level=5, floor=1, is_boss=False)
        assert drop["is_two_handed"] is True
        assert drop["base_id"] == "greatsword"
