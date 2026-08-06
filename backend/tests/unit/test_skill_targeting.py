"""単体テスト: ターゲット選択と挑発（services/skill_service.py）

仕様: tech/detail/tech_skill.md §4（ターゲット選択・挑発）、
      tech/detail/tech_battle.md §3.3（既定則の擬似コード）、
      data/skills/006_生存術系統.md（挑発率50%・範囲攻撃には無効）
分岐観点:
  - 候補は生存者のみ。スキル詳細の「対象」欄が既定則より優先する
  - 同条件が並んだときのタイブレークはキャラID順（乱数を使わない）
  - 挑発は合算率が80%以下か超過かで抽選のしかたが変わる。範囲攻撃には効かない
  - 蘇生の対象はHP0の味方のうちキャラID順で最初の1体

本工程で定義する実装の表層: alive_candidates・select_targets・select_attack_target・select_revive_target
"""

import pytest

from app.services import skill_service as sk

from tests.unit.battle_doubles import SeqRng, make_actor, make_enemy

pytestmark = pytest.mark.unit

_enemy = make_enemy


def _tauntable(actor_id, taunt_rate):
    """挑発中の味方（surv_1 の挑発率をバフとして保持する）"""
    return make_actor(
        actor_id,
        buffs=[sk.Buff("surv_1", stat="taunt", value=taunt_rate, turns=3, source_id=actor_id)],
    )


class TestCandidates:
    def test_HP0の対象は候補から除外される(self):
        """分岐: tech_skill.md §4 #1 — 候補は生存者のみ"""
        allies = [make_actor("ally1"), make_actor("ally2", hp=0), make_actor("ally3")]
        assert [a.id for a in sk.alive_candidates(allies)] == ["ally1", "ally3"]

    def test_全員生存なら全員が候補になる(self):
        """分岐: tech_skill.md §4 #2 — 誰も除外されない"""
        allies = [make_actor("ally1"), make_actor("ally2")]
        assert [a.id for a in sk.alive_candidates(allies)] == ["ally1", "ally2"]

    def test_回復スキルはHP0の味方を選ばない(self):
        """分岐: tech_skill.md §4 #1 — HP割合0でも戦闘不能者は回復対象外"""
        actor = make_actor("ally1", hp=100)
        downed = make_actor("ally2", hp=0)
        hurt = make_actor("ally3", hp=30, max_hp=100)
        targets = sk.select_targets(actor, "heal_1", [actor, downed, hurt], [], rng=SeqRng(0.0))
        assert [t.id for t in targets] == ["ally3"]


class TestTargetRules:
    def test_固有規則を持つスキルはランダム2体を選ぶ(self):
        """分岐: tech_skill.md §4 #3 — sword_2（連続斬り）の対象欄は「ランダム2体」"""
        actor = make_actor("ally1")
        enemies = [_enemy("enemy1"), _enemy("enemy2")]
        targets = sk.select_targets(actor, "sword_2", [actor], enemies, rng=SeqRng(0.0, 0.6))
        assert [t.id for t in targets] == ["enemy1", "enemy2"]

    def test_固有規則のATK最高の敵を選ぶ(self):
        """分岐: tech_skill.md §4 #3 — debuff_1（威圧）の対象欄は「ATK最高の敵」"""
        actor = make_actor("ally1")
        enemies = [_enemy("enemy1", atk=10), _enemy("enemy2", atk=30)]
        targets = sk.select_targets(actor, "debuff_1", [actor], enemies, rng=SeqRng(1.0))
        assert [t.id for t in targets] == ["enemy2"]

    def test_一般表記の単体攻撃はHP割合最大の敵を選ぶ(self):
        """分岐: tech_skill.md §4 #4 — 既定則（各個撃破を避ける）"""
        actor = make_actor("ally1")
        enemies = [_enemy("enemy1", hp=30, max_hp=100), _enemy("enemy2", hp=90, max_hp=100)]
        targets = sk.select_targets(actor, "sword_1", [actor], enemies, rng=SeqRng(1.0))
        assert [t.id for t in targets] == ["enemy2"]

    def test_一般表記の回復はHP割合最低の味方を選ぶ(self):
        """分岐: tech_skill.md §4 #4 — 既定則（回復＝HP割合最低）"""
        actor = make_actor("ally1", hp=100, max_hp=100)
        hurt = make_actor("ally2", hp=20, max_hp=100)
        targets = sk.select_targets(actor, "heal_1", [actor, hurt], [], rng=SeqRng(1.0))
        assert [t.id for t in targets] == ["ally2"]

    def test_通常攻撃はランダム1体を選ぶ(self):
        """分岐: tech_skill.md §4 #4 — 既定則（通常攻撃＝ランダム1体）"""
        actor = make_actor("ally1")
        enemies = [_enemy("enemy1"), _enemy("enemy2")]
        targets = sk.select_targets(actor, None, [actor], enemies, rng=SeqRng(0.6))
        assert [t.id for t in targets] == ["enemy2"]


class TestTieBreak:
    def test_同条件のターゲットが複数ならキャラID順で決める(self):
        """分岐: tech_skill.md §4 #5 — 乱数を使わず ID 昇順で確定させる"""
        actor = make_actor("ally1")
        enemies = [_enemy("enemy2", hp=50, max_hp=100), _enemy("enemy1", hp=50, max_hp=100)]
        targets = sk.select_targets(actor, "sword_1", [actor], enemies, rng=SeqRng(0.0))
        assert [t.id for t in targets] == ["enemy1"]

    def test_候補が1体ならその1体を選ぶ(self):
        """分岐: tech_skill.md §4 #6 — 比較の余地がない場合"""
        actor = make_actor("ally1")
        targets = sk.select_targets(actor, "sword_1", [actor], [_enemy("enemy1")], rng=SeqRng(0.0))
        assert [t.id for t in targets] == ["enemy1"]


class TestTaunt:
    def test_合算80パーセント以下で挑発ロールに入れば挑発者を選ぶ(self):
        """分岐: tech_skill.md §4 #7 — 挑発率の累積区間に応じて選ぶ"""
        attacker = _enemy("enemy1")
        allies = [_tauntable("ally1", 0.5), make_actor("ally2")]
        assert sk.select_attack_target(attacker, allies, rng=SeqRng(0.49)).id == "ally1"

    def test_合算80パーセント以下でロールを外れたら通常のランダム選択になる(self):
        """分岐: tech_skill.md §4 #8 — 残り50%は通常抽選（挑発者も候補に残る）"""
        attacker = _enemy("enemy1")
        allies = [_tauntable("ally1", 0.5), make_actor("ally2")]
        # 1回目 0.50 で挑発ロールを外れ、2回目 0.6 で通常抽選 → 候補2件の index 1
        assert sk.select_attack_target(attacker, allies, rng=SeqRng(0.50, 0.6)).id == "ally2"

    def test_合算挑発率が80パーセント超なら80パーセントを比率で按分する(self):
        """分岐: tech_skill.md §4 #9 — 0.5+0.5=1.0 → 各 0.4 の区間になる"""
        attacker = _enemy("enemy1")
        allies = [_tauntable("ally1", 0.5), _tauntable("ally2", 0.5), make_actor("ally3")]
        assert sk.select_attack_target(attacker, allies, rng=SeqRng(0.39)).id == "ally1"
        assert sk.select_attack_target(attacker, allies, rng=SeqRng(0.40)).id == "ally2"

    def test_合算80パーセント超でロールを外れたら通常のランダム選択になる(self):
        """分岐: tech_skill.md §4 #10 — 残り20%（0.8以上）は通常抽選"""
        attacker = _enemy("enemy1")
        allies = [_tauntable("ally1", 0.5), _tauntable("ally2", 0.5), make_actor("ally3")]
        # 1回目 0.80 で按分区間を外れ、2回目 0.7 で通常抽選 → 候補3件の index 2
        assert sk.select_attack_target(attacker, allies, rng=SeqRng(0.80, 0.7)).id == "ally3"

    def test_範囲攻撃には挑発が効かない(self):
        """分岐: tech_skill.md §4 #11 — 全生存対象に適用する"""
        attacker = _enemy("enemy1")
        allies = [_tauntable("ally1", 0.5), make_actor("ally2"), make_actor("ally3", hp=0)]
        targets = sk.select_targets(attacker, "magic_2", [attacker], allies, rng=SeqRng(0.0))
        assert [t.id for t in targets] == ["ally1", "ally2"]


class TestReviveTarget:
    def test_HP0の味方が複数ならキャラID順で最初の1体を蘇生する(self):
        """分岐: tech_skill.md §4 #12 — §1 #7 の一意化どおり ID 昇順"""
        allies = [make_actor("ally3", hp=0), make_actor("ally1"), make_actor("ally2", hp=0)]
        assert sk.select_revive_target(allies).id == "ally2"

    def test_HP0の味方が1体ならその1体を蘇生する(self):
        """分岐: tech_skill.md §4 #13 — 候補が1件の経路"""
        allies = [make_actor("ally1"), make_actor("ally2", hp=0)]
        assert sk.select_revive_target(allies).id == "ally2"
