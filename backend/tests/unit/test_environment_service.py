"""単体テスト: 塔の環境効果（services/environment_service.py）

仕様: tech/detail/tech_skill.md §7（環境効果の適用）、tech/basic/tech_data.md §1.5（modifier定義と計算方法）、
      data/towers/003〜005（SPD-15% / 毒霧+ゴールド+20% / 敵ATK+10%+ポーション半減）
分岐観点:
  - 4種類の modifier（stat_modifier / recovery / restriction / bonus）それぞれの「定義あり・なし」
  - restriction は no_potion と potion_half で挙動が分かれる
  - modifiers が空配列（ダンジョン1）なら一切適用しない

毒霧（dot）は §2 #10〜#11 が持つため本モジュールでは扱わない。

本工程で定義する実装の表層:
  apply_stat_modifiers・floor_clear_recovery・adjust_potion_heal・apply_reward_bonus
"""

import pytest

from app.services import environment_service as env

pytestmark = pytest.mark.unit

SPD_DEBUFF = {"id": "spd_debuff_15", "type": "stat_modifier", "target": "player", "stat": "spd", "value": -0.15}
FLOOR_RECOVERY = {"id": "regen_per_floor", "type": "recovery", "trigger": "floor_clear", "value": 0.03}
NO_POTION = {"id": "no_potion", "type": "restriction", "value": "no_potion"}
POTION_HALF = {"id": "potion_half", "type": "restriction", "value": "potion_half"}
GOLD_BONUS = {"id": "gold_bonus_20", "type": "bonus", "target": "gold", "value": 0.2}


class TestStatModifier:
    def test_定義があればステータスに倍率を適用する(self):
        """分岐: tech_skill.md §7 #1 — effective_stat = base × (1 + value)"""
        stats = env.apply_stat_modifiers({"atk": 30, "def": 20, "spd": 20}, [SPD_DEBUFF])
        assert stats == {"atk": 30, "def": 20, "spd": 17}  # floor(20 × 0.85) = floor(17.0)

    def test_定義がなければ補正しない(self):
        """分岐: tech_skill.md §7 #2 — stat_modifier を持たない塔"""
        stats = env.apply_stat_modifiers({"atk": 30, "def": 20, "spd": 20}, [FLOOR_RECOVERY])
        assert stats == {"atk": 30, "def": 20, "spd": 20}


class TestFloorClearRecovery:
    def test_定義があれば階クリア後にmaxHP比で回復する(self):
        """分岐: tech_skill.md §7 #3 — heal = floor(maxHP × value)"""
        assert env.floor_clear_recovery(current_hp=50, max_hp=100, modifiers=[FLOOR_RECOVERY]) == 3

    def test_回復はmaxHPを超えない(self):
        """分岐: tech_skill.md §7 #3 — 上限クランプ（不足分まで）"""
        assert env.floor_clear_recovery(current_hp=99, max_hp=100, modifiers=[FLOOR_RECOVERY]) == 1

    def test_定義がなければ回復しない(self):
        """分岐: tech_skill.md §7 #4 — recovery を持たない塔"""
        assert env.floor_clear_recovery(current_hp=50, max_hp=100, modifiers=[SPD_DEBUFF]) == 0


class TestPotionRestriction:
    def test_no_potionならポーションを使用しない(self):
        """分岐: tech_skill.md §7 #5 — 自動使用そのものを行わない（None = 使用不可）"""
        assert env.adjust_potion_heal(50, [NO_POTION]) is None

    def test_potion_halfなら回復量を半減する(self):
        """分岐: tech_skill.md §7 #6 — floor(基本回復量 × 0.5)"""
        assert env.adjust_potion_heal(51, [POTION_HALF]) == 25  # floor(25.5)

    def test_potion_halfの回復量は最低1を保証する(self):
        """分岐: tech_skill.md §7 #6 — floor で0になっても1"""
        assert env.adjust_potion_heal(1, [POTION_HALF]) == 1  # floor(0.5) = 0 → 1

    def test_restrictionの定義がなければ通常どおり回復する(self):
        """分岐: tech_skill.md §7 #7 — 基本回復量をそのまま返す"""
        assert env.adjust_potion_heal(50, [SPD_DEBUFF]) == 50


class TestRewardBonus:
    def test_定義があれば報酬に倍率を適用して1回だけ丸める(self):
        """分岐: tech_skill.md §7 #8 — floor(base × (1 + value)) を1回だけ行う"""
        assert env.apply_reward_bonus(101, "gold", [GOLD_BONUS]) == 121  # floor(121.2)

    def test_定義がなければ報酬は基本値のまま(self):
        """分岐: tech_skill.md §7 #9 — bonus を持たない塔"""
        assert env.apply_reward_bonus(101, "gold", [SPD_DEBUFF]) == 101


class TestNoModifiers:
    def test_modifiersが空配列なら環境効果を一切適用しない(self):
        """分岐: tech_skill.md §7 #10 — ダンジョン1（ゴブリンの塔・森の塔）"""
        assert env.apply_stat_modifiers({"atk": 30, "spd": 20}, []) == {"atk": 30, "spd": 20}
        assert env.floor_clear_recovery(current_hp=50, max_hp=100, modifiers=[]) == 0
        assert env.adjust_potion_heal(50, []) == 50
        assert env.apply_reward_bonus(101, "gold", []) == 101
