"""単体テスト: スキル・状態異常の戦闘内処理（services/skill_service.py）

仕様: tech/detail/tech_skill.md §1（適用の一意化）・§2（行動可否・状態異常・DOT）・
      §3（アクティブスキル発動判定）・§5（ダメージ計算・パッシブ）・§6（バフ/デバフ管理）
分岐観点:
  - ターン開始の行動可否（戦闘不能・スタン・麻痺）と DOT／リジェネの増減
  - アクティブスキルの発動条件（沈黙・セット枠・CD・優先度）と1ターン1スキルの制限
  - ダメージ計算（クリティカル・スキルダメージ+%・最低保証・被ダメ軽減・反撃・多段・範囲）
  - バフ／状態異常の重複・延長・ターン減衰

本工程で定義する実装の表層（製造工程はこの形に実装する）:
  BattleActor / Buff / Status のデータ構造と、can_act・apply_turn_start・select_skill・
  start_cooldown・calc_skill_damage・effective_crit_rate・damage_reduction・try_counter・
  resolve_attack・apply_buff・apply_status・tick_effects
"""

import pytest

from app.services import skill_service as sk

from tests.unit.battle_doubles import SeqRng, make_actor, make_enemy

pytestmark = pytest.mark.unit

POISON_FOG = {"id": "poison_fog", "type": "dot", "trigger": "turn_start", "value": 0.02}


# ══════════════════════════════════════════════════════════════════
# §2 行動可否・状態異常・DOT
# ══════════════════════════════════════════════════════════════════


class TestCanAct:
    def test_HPが残っていれば行動判定に進む(self):
        """分岐: tech_skill.md §2 #1 — 生存しているアクターは行動できる"""
        assert sk.can_act(make_actor(hp=1), rng=SeqRng(1.0)) is True

    def test_戦闘不能なら行動をスキップする(self):
        """分岐: tech_skill.md §2 #2 — HP0 のアクターは行動しない"""
        assert sk.can_act(make_actor(hp=0), rng=SeqRng(0.0)) is False

    def test_麻痺で行動不能を引いたらスキップする(self):
        """分岐: tech_skill.md §2 #3 — 麻痺は毎ターン30%で行動不能"""
        actor = make_actor(statuses={"paralysis": sk.Status("paralysis", turns=2, source_id="e1")})
        assert sk.can_act(actor, rng=SeqRng(0.29)) is False  # 0.29 < 0.3 で行動不能

    def test_麻痺でも行動可能を引けば行動を継続する(self):
        """分岐: tech_skill.md §2 #4 — 30%を外せば通常どおり行動する"""
        actor = make_actor(statuses={"paralysis": sk.Status("paralysis", turns=2, source_id="e1")})
        assert sk.can_act(actor, rng=SeqRng(0.30)) is True  # 境界: 0.3 は行動可能側

    def test_麻痺でなければ判定せず行動を継続する(self):
        """分岐: tech_skill.md §2 #5 — 麻痺でないアクターは乱数を消費しない"""
        rng = SeqRng(0.0)  # 引かれたら必ず行動不能になる値
        assert sk.can_act(make_actor(), rng=rng) is True
        assert rng.calls == 0

    def test_スタン中は行動をスキップする(self):
        """分岐: tech_skill.md §2 #6 — スタンは確定で行動不能"""
        actor = make_actor(statuses={"stun": sk.Status("stun", turns=1, source_id="e1")})
        assert sk.can_act(actor, rng=SeqRng(1.0)) is False

    def test_スタンでなければ行動を継続する(self):
        """分岐: tech_skill.md §2 #7 — スタンが解けていれば行動できる"""
        assert sk.can_act(make_actor(), rng=SeqRng(1.0)) is True


class TestTurnStartDot:
    def test_毒状態はmaxHPの5パーセントを受ける(self):
        """分岐: tech_skill.md §2 #8 — 毒DOT = floor(maxHP×5%)"""
        actor = make_actor(max_hp=100, hp=100, statuses={"poison": sk.Status("poison", turns=3, source_id="e1")})
        result = sk.apply_turn_start(actor)
        assert result.poison_damage == 5
        assert actor.hp == 95

    def test_毒DOTは最低1ダメージを保証する(self):
        """分岐: tech_skill.md §2 #8 — floor で0になる小さな maxHP でも1"""
        actor = make_actor(max_hp=10, hp=10, statuses={"poison": sk.Status("poison", turns=3, source_id="e1")})
        assert sk.apply_turn_start(actor).poison_damage == 1  # floor(10×0.05)=0 → 1

    def test_毒状態でなければDOTを受けない(self):
        """分岐: tech_skill.md §2 #9 — 毒がなければダメージなし"""
        actor = make_actor(max_hp=100, hp=100)
        assert sk.apply_turn_start(actor).poison_damage == 0
        assert actor.hp == 100

    def test_毒霧の環境効果は毒と重複して適用される(self):
        """分岐: tech_skill.md §2 #10 — 環境毒霧 = floor(maxHP×2%)。毒とは別系統"""
        actor = make_actor(max_hp=100, hp=100, statuses={"poison": sk.Status("poison", turns=3, source_id="e1")})
        result = sk.apply_turn_start(actor, modifiers=[POISON_FOG])
        assert (result.poison_damage, result.env_damage) == (5, 2)
        assert actor.hp == 93  # 5 と 2 の両方を受ける

    def test_環境効果がなければ環境ダメージはない(self):
        """分岐: tech_skill.md §2 #11 — modifiers に dot がなければ0"""
        actor = make_actor(max_hp=100, hp=100)
        assert sk.apply_turn_start(actor, modifiers=[]).env_damage == 0

    def test_DOTでHPが0になったら以降の行動を行わない(self):
        """分岐: tech_skill.md §2 #12 — DOT死亡はポーションの再判定もしない"""
        actor = make_actor(max_hp=100, hp=5, statuses={"poison": sk.Status("poison", turns=3, source_id="e1")})
        result = sk.apply_turn_start(actor)
        assert result.died is True
        assert actor.hp == 0

    def test_DOT後もHPが残っていれば行動を継続する(self):
        """分岐: tech_skill.md §2 #13 — 残HPがあれば died は False"""
        actor = make_actor(max_hp=100, hp=6, statuses={"poison": sk.Status("poison", turns=3, source_id="e1")})
        result = sk.apply_turn_start(actor)
        assert result.died is False
        assert actor.hp == 1

    def test_リジェネ習得済みならターン開始時に回復する(self):
        """分岐: tech_skill.md §2 #14 — heal_p2 = floor(maxHP×3%)、maxHP上限"""
        actor = make_actor(max_hp=100, hp=50, learned=["heal_p2"])
        result = sk.apply_turn_start(actor)
        assert result.regen == 3
        assert actor.hp == 53

    def test_リジェネはmaxHPを超えて回復しない(self):
        """分岐: tech_skill.md §2 #14 — 上限クランプ（回復量は不足分まで）"""
        actor = make_actor(max_hp=100, hp=99, learned=["heal_p2"])
        assert sk.apply_turn_start(actor).regen == 1
        assert actor.hp == 100

    def test_リジェネ未習得なら回復しない(self):
        """分岐: tech_skill.md §2 #15 — パッシブ未習得は回復なし"""
        actor = make_actor(max_hp=100, hp=50)
        assert sk.apply_turn_start(actor).regen == 0
        assert actor.hp == 50


# ══════════════════════════════════════════════════════════════════
# §3 アクティブスキル発動判定
# ══════════════════════════════════════════════════════════════════


_enemy = make_enemy


class TestSelectSkill:
    def test_沈黙中はスキルを発動しない(self):
        """分岐: tech_skill.md §3 #1 — 沈黙は通常攻撃のみ"""
        actor = make_actor(
            learned=["sword_1"],
            slots=["sword_1"],
            cooldowns={"sword_1": 0},
            statuses={"silence": sk.Status("silence", turns=2, source_id="e1")},
        )
        assert sk.select_skill(actor, [actor], [_enemy()]) is None

    def test_沈黙でなければ発動判定に進む(self):
        """分岐: tech_skill.md §3 #2 — 沈黙が解けていれば条件判定へ"""
        actor = make_actor(learned=["sword_1"], slots=["sword_1"], cooldowns={"sword_1": 0})
        assert sk.select_skill(actor, [actor], [_enemy()]) == "sword_1"

    def test_セット枠が空なら通常攻撃を行う(self):
        """分岐: tech_skill.md §3 #3 — アクティブ未セットは None（通常攻撃）"""
        actor = make_actor(learned=["sword_1"], slots=[])
        assert sk.select_skill(actor, [actor], [_enemy()]) is None

    def test_セット済みスキルがあれば発動条件を判定する(self):
        """分岐: tech_skill.md §3 #4 — セット枠のスキルが判定対象になる"""
        actor = make_actor(learned=["magic_1"], slots=["magic_1"], cooldowns={"magic_1": 0})
        assert sk.select_skill(actor, [actor], [_enemy()]) == "magic_1"

    def test_HP0の味方がいてCD完了なら蘇生を最優先で発動する(self):
        """分岐: tech_skill.md §3 #5 — 蘇生は回復より優先"""
        actor = make_actor(
            learned=["heal_1", "heal_3"], slots=["heal_1", "heal_3"], cooldowns={"heal_1": 0, "heal_3": 0}
        )
        downed = make_actor("ally2", hp=0)
        assert sk.select_skill(actor, [actor, downed], [_enemy()]) == "heal_3"

    def test_HP0の味方がいなければ蘇生は発動しない(self):
        """分岐: tech_skill.md §3 #6 — 蘇生対象なしなら発動条件を満たさない"""
        actor = make_actor(learned=["heal_3"], slots=["heal_3"], cooldowns={"heal_3": 0})
        assert sk.select_skill(actor, [actor], [_enemy()]) is None

    def test_蘇生のCDが完了していなければ発動しない(self):
        """分岐: tech_skill.md §3 #7 — 対象がいても CD 未完了なら不発"""
        actor = make_actor(learned=["heal_3"], slots=["heal_3"], cooldowns={"heal_3": 1})
        downed = make_actor("ally2", hp=0)
        assert sk.select_skill(actor, [actor, downed], [_enemy()]) is None

    def test_HP40パーセント以下の味方がいてCD完了なら回復する(self):
        """分岐: tech_skill.md §3 #8 — 閾値ちょうど（40%）は発動側"""
        actor = make_actor(learned=["heal_1"], slots=["heal_1"], cooldowns={"heal_1": 0})
        hurt = make_actor("ally2", hp=40, max_hp=100)
        assert sk.select_skill(actor, [actor, hurt], [_enemy()]) == "heal_1"

    def test_全員のHPが40パーセント超なら回復しない(self):
        """分岐: tech_skill.md §3 #9 — 41% は発動しない側"""
        actor = make_actor(learned=["heal_1"], slots=["heal_1"], cooldowns={"heal_1": 0})
        hurt = make_actor("ally2", hp=41, max_hp=100)
        assert sk.select_skill(actor, [actor, hurt], [_enemy()]) is None

    def test_回復のCDが完了していなければ発動しない(self):
        """分岐: tech_skill.md §3 #10 — 閾値を割っていても CD 未完了なら不発"""
        actor = make_actor(learned=["heal_1"], slots=["heal_1"], cooldowns={"heal_1": 2})
        hurt = make_actor("ally2", hp=10, max_hp=100)
        assert sk.select_skill(actor, [actor, hurt], [_enemy()]) is None

    def test_バフはCD完了で発動する(self):
        """分岐: tech_skill.md §3 #11 — バフ/デバフは CD 完了だけが条件"""
        actor = make_actor(learned=["buff_1"], slots=["buff_1"], cooldowns={"buff_1": 0})
        assert sk.select_skill(actor, [actor], [_enemy()]) == "buff_1"

    def test_バフはCD未完了なら発動しない(self):
        """分岐: tech_skill.md §3 #12 — CD が残っていれば不発"""
        actor = make_actor(learned=["buff_1"], slots=["buff_1"], cooldowns={"buff_1": 1})
        assert sk.select_skill(actor, [actor], [_enemy()]) is None

    def test_攻撃スキルはCD完了で発動する(self):
        """分岐: tech_skill.md §3 #13 — 攻撃スキルも CD 完了だけが条件"""
        actor = make_actor(learned=["sword_1"], slots=["sword_1"], cooldowns={"sword_1": 0})
        assert sk.select_skill(actor, [actor], [_enemy()]) == "sword_1"

    def test_攻撃スキルはCD未完了なら発動しない(self):
        """分岐: tech_skill.md §3 #14 — CD が残っていれば通常攻撃"""
        actor = make_actor(learned=["sword_1"], slots=["sword_1"], cooldowns={"sword_1": 3})
        assert sk.select_skill(actor, [actor], [_enemy()]) is None

    def test_回復と攻撃が同時に成立したら回復を優先する(self):
        """分岐: tech_skill.md §3 #15 — 優先度 回復 > バフ/デバフ > 攻撃"""
        actor = make_actor(
            learned=["sword_1", "heal_1"],
            slots=["sword_1", "heal_1"],  # 枠1が攻撃でも優先度で回復が勝つ
            cooldowns={"sword_1": 0, "heal_1": 0},
        )
        hurt = make_actor("ally2", hp=10, max_hp=100)
        assert sk.select_skill(actor, [actor, hurt], [_enemy()]) == "heal_1"

    def test_同優先度が両枠で成立したらセット枠1を発動する(self):
        """分岐: tech_skill.md §3 #16 — 同優先度は配列順（枠1）で決める"""
        actor = make_actor(
            learned=["sword_1", "magic_1"],
            slots=["magic_1", "sword_1"],  # 枠1 = magic_1
            cooldowns={"sword_1": 0, "magic_1": 0},
        )
        assert sk.select_skill(actor, [actor], [_enemy()]) == "magic_1"

    def test_発動したスキルのCDは最大値に戻る(self):
        """分岐: tech_skill.md §3 #17 — sword_1 の CD は3ターン"""
        actor = make_actor(learned=["sword_1"], slots=["sword_1"], cooldowns={"sword_1": 0})
        sk.start_cooldown(actor, "sword_1")
        assert actor.cooldowns["sword_1"] == 3

    def test_発動しなかったスキルのCDは現在値のまま(self):
        """分岐: tech_skill.md §3 #18 — 減算はターン終了時に一律。判定では変えない"""
        actor = make_actor(learned=["sword_1"], slots=["sword_1"], cooldowns={"sword_1": 2})
        assert sk.select_skill(actor, [actor], [_enemy()]) is None
        assert actor.cooldowns["sword_1"] == 2

    def test_同一ターンに2つ目のスキルは発動しない(self):
        """分岐: tech_skill.md §3 #19 — 両枠が条件成立でも返るのは1つだけ"""
        actor = make_actor(
            learned=["sword_1", "magic_1"],
            slots=["sword_1", "magic_1"],
            cooldowns={"sword_1": 0, "magic_1": 0},
        )
        chosen = sk.select_skill(actor, [actor], [_enemy()])
        assert chosen == "sword_1"
        assert isinstance(chosen, str)  # リストではなく単一のスキルID

    def test_どのスキルも条件不成立なら通常攻撃を行う(self):
        """分岐: tech_skill.md §3 #20 — 全滅した条件判定は None を返す"""
        actor = make_actor(
            learned=["heal_1", "sword_1"],
            slots=["heal_1", "sword_1"],
            cooldowns={"heal_1": 0, "sword_1": 4},  # 回復は対象なし・攻撃はCD残
        )
        assert sk.select_skill(actor, [actor], [_enemy()]) is None


# ══════════════════════════════════════════════════════════════════
# §5 ダメージ計算・パッシブ
# ══════════════════════════════════════════════════════════════════


class TestSkillDamage:
    """ATK20・DEF10・sword_1（×1.5）の基本形: 20×1.5 − 10×0.5 = 25.0"""

    def test_クリティカルはDEF減算後に1_5倍する(self):
        """分岐: tech_skill.md §5 #1 — 乱数 < 実効クリ率でクリティカル"""
        attacker, defender = make_actor(), _enemy()
        damage = sk.calc_skill_damage(attacker, defender, "sword_1", rng=SeqRng(0.0))
        assert damage == 37  # floor(25.0 × 1.5)

    def test_クリティカル非発生なら等倍(self):
        """分岐: tech_skill.md §5 #2 — 乱数が実効クリ率以上なら等倍"""
        attacker, defender = make_actor(), _enemy()
        assert sk.calc_skill_damage(attacker, defender, "sword_1", rng=SeqRng(1.0)) == 25

    def test_合算クリ率が100パーセント超なら確定クリティカルになる(self):
        """分岐: tech_skill.md §5 #3 — 上限100%でクランプ"""
        actor = make_actor(buffs=[sk.Buff("buff_6", stat="crit_rate", value=1.0, turns=3, source_id="ally1")])
        assert sk.effective_crit_rate(actor, "sword_1") == 1.0  # 0.05 + 1.0 → 1.0

    def test_スキル固有のクリ率補正は合算に加える(self):
        """分岐: tech_skill.md §5 #4 — 渾身の一撃は +20%"""
        assert sk.effective_crit_rate(make_actor(), "sword_3") == pytest.approx(0.25)  # 0.05 + 0.20

    def test_魔力増幅を習得していればDEF減算後に1_15倍する(self):
        """分岐: tech_skill.md §5 #5 — スキルダメージ+15%"""
        attacker = make_actor(learned=["magic_p1"])
        damage = sk.calc_skill_damage(attacker, _enemy(), "sword_1", rng=SeqRng(1.0))
        assert damage == 28  # floor(25.0 × 1.15) = floor(28.75)

    def test_魔力増幅が未習得なら等倍(self):
        """分岐: tech_skill.md §5 #6 — 未習得はスキルダメージ+%なし"""
        assert sk.calc_skill_damage(make_actor(), _enemy(), "sword_1", rng=SeqRng(1.0)) == 25

    def test_味方から敵への最低保証は1(self):
        """分岐: tech_skill.md §5 #7 — 計算結果が1未満でも1を与える"""
        attacker = make_actor(atk=1)
        defender = _enemy()
        defender.defense = 100
        assert sk.calc_skill_damage(attacker, defender, "sword_1", rng=SeqRng(1.0)) == 1

    def test_敵から味方への最低保証は0(self):
        """分岐: tech_skill.md §5 #8 — 敵→味方は0まで下がる（1保証はしない）"""
        attacker = _enemy(atk=1)
        defender = make_actor(defense=100)
        assert sk.calc_skill_damage(attacker, defender, "sword_1", rng=SeqRng(1.0)) == 0

    def test_被ダメ軽減パッシブは乗算合算する(self):
        """分岐: tech_skill.md §5 #9 — surv_p2（-10%）と軽減バフ（-10%）で 1−0.9×0.9"""
        actor = make_actor(
            learned=["surv_p2"],
            buffs=[sk.Buff("surv_3", stat="damage_reduction", value=0.1, turns=2, source_id="ally1")],
        )
        assert sk.damage_reduction(actor) == pytest.approx(0.19)

    def test_実効軽減率は80パーセントでクランプする(self):
        """分岐: tech_skill.md §5 #10 — 下限ダメージ倍率 0.2"""
        actor = make_actor(
            buffs=[
                sk.Buff("surv_3", stat="damage_reduction", value=0.5, turns=2, source_id="ally1"),
                sk.Buff("buff_5", stat="damage_reduction", value=0.5, turns=2, source_id="ally2"),
                sk.Buff("heal_6", stat="damage_reduction", value=0.5, turns=2, source_id="ally3"),
            ]
        )
        assert sk.damage_reduction(actor) == pytest.approx(0.8)  # 1−0.5³=0.875 → 0.8

    def test_反撃は30パーセントを引けばATKの半分で返す(self):
        """分岐: tech_skill.md §5 #11 — surv_2 の反撃は ATK×0.5"""
        defender = make_actor(atk=20, learned=["surv_2"])
        attacker = _enemy()
        assert sk.try_counter(defender, attacker, source="normal_attack", rng=SeqRng(0.29)) == 10

    @pytest.mark.parametrize(
        ("source", "roll"),
        [
            ("normal_attack", 0.30),  # 境界: 0.3 は非発生側
            ("counter", 0.0),         # 反撃の連鎖は防止する
            ("dot", 0.0),             # DOT 被弾には反応しない
            ("environment", 0.0),     # 環境ダメージにも反応しない
        ],
    )
    def test_反撃しない条件(self, source, roll):
        """分岐: tech_skill.md §5 #12 — 確率を外した場合と、反撃対象外の被弾"""
        defender = make_actor(atk=20, learned=["surv_2"])
        assert sk.try_counter(defender, _enemy(), source=source, rng=SeqRng(roll)) is None

    def test_多段スキルはヒットごとに独立して抽選する(self):
        """分岐: tech_skill.md §5 #13 — 連続斬りは2ヒット。同一対象に2回ヒットもあり得る"""
        attacker = make_actor()
        enemies = [_enemy("enemy1"), _enemy("enemy2")]
        # 対象抽選 → クリ判定 をヒットごとに消費する（1発目クリ・2発目非クリ）
        hits = sk.resolve_attack(attacker, "sword_2", enemies, rng=SeqRng(0.0, 0.0, 0.6, 1.0))
        assert len(hits) == 2
        assert [h.crit for h in hits] == [True, False]

    def test_単発スキルは1回だけ計算する(self):
        """分岐: tech_skill.md §5 #14 — 強撃は1ヒット"""
        hits = sk.resolve_attack(make_actor(), "sword_1", [_enemy()], rng=SeqRng(1.0))
        assert len(hits) == 1

    def test_範囲スキルは全生存敵にスキル倍率をそのまま適用する(self):
        """分岐: tech_skill.md §5 #15 — 追加の×0.7を掛けない（§1 #10）"""
        attacker = make_actor(atk=20)
        enemies = [_enemy("enemy1"), _enemy("enemy2"), _enemy("enemy3", hp=0)]
        hits = sk.resolve_attack(attacker, "magic_2", enemies, rng=SeqRng(1.0))
        assert [h.target_id for h in hits] == ["enemy1", "enemy2"]  # 戦闘不能は対象外
        assert all(h.damage == 19 for h in hits)  # floor(20×1.2 − 10×0.5) = floor(19.0)

    def test_単体スキルは選択した1体のみに適用する(self):
        """分岐: tech_skill.md §5 #16 — 範囲でなければ対象は1体"""
        enemies = [_enemy("enemy1"), _enemy("enemy2")]
        hits = sk.resolve_attack(make_actor(), "sword_1", enemies, rng=SeqRng(1.0))
        assert len(hits) == 1

    def test_合算クリ率が100パーセント以下なら合算値をそのまま使う(self):
        """分岐: tech_skill.md §5 #17 — クランプしない側"""
        actor = make_actor(buffs=[sk.Buff("buff_6", stat="crit_rate", value=0.15, turns=3, source_id="ally1")])
        assert sk.effective_crit_rate(actor, "sword_1") == pytest.approx(0.20)  # 0.05 + 0.15

    def test_固有補正のないスキルは基礎とパッシブとバフの合算で判定する(self):
        """分岐: tech_skill.md §5 #18 — スキル固有クリ率を持たない sword_1"""
        actor = make_actor(
            learned=["sword_p2"],  # クリティカル率+15%（パッシブ）
            buffs=[sk.Buff("buff_6", stat="crit_rate", value=0.10, turns=3, source_id="ally1")],
        )
        assert sk.effective_crit_rate(actor, "sword_1") == pytest.approx(0.30)  # 0.05+0.15+0.10


# ══════════════════════════════════════════════════════════════════
# §6 バフ/デバフ・状態異常の管理
# ══════════════════════════════════════════════════════════════════


class TestBuffManagement:
    def test_同一スキルの再付与は効果値と残りターンを上書きする(self):
        """分岐: tech_skill.md §6 #1 — 後発で上書き（加算しない）"""
        target = make_actor("ally2")
        source = make_actor("ally1")
        sk.apply_buff(target, sk.Buff("buff_1", stat="atk", value=0.2, turns=1, source_id="ally1"), source)
        sk.apply_buff(target, sk.Buff("buff_1", stat="atk", value=0.2, turns=3, source_id="ally1"), source)
        assert len(target.buffs) == 1
        assert target.buffs[0].turns == 3

    def test_異なるスキルの同一ステータスバフは共存して加算する(self):
        """分岐: tech_skill.md §6 #2 — 上限なしで加算"""
        target = make_actor("ally2")
        source = make_actor("ally1")
        sk.apply_buff(target, sk.Buff("buff_1", stat="atk", value=0.2, turns=3, source_id="ally1"), source)
        sk.apply_buff(target, sk.Buff("buff_3", stat="atk", value=0.15, turns=4, source_id="ally1"), source)
        assert len(target.buffs) == 2
        assert sum(b.value for b in target.buffs) == pytest.approx(0.35)

    def test_バフ延長パッシブは自分が付与したバフを1ターン延ばす(self):
        """分岐: tech_skill.md §6 #3 — buff_p1 習得者の付与は持続+1"""
        target = make_actor("ally2")
        source = make_actor("ally1", learned=["buff_p1"])
        sk.apply_buff(target, sk.Buff("buff_1", stat="atk", value=0.2, turns=3, source_id="ally1"), source)
        assert target.buffs[0].turns == 4

    def test_バフ延長は他キャラが付与したバフには効かない(self):
        """分岐: tech_skill.md §6 #4 — 延長は付与者のパッシブで決まる"""
        target = make_actor("ally2", learned=["buff_p1"])  # 受け手が持っていても延びない
        source = make_actor("ally1")
        sk.apply_buff(target, sk.Buff("buff_1", stat="atk", value=0.2, turns=3, source_id="ally1"), source)
        assert target.buffs[0].turns == 3

    def test_弱体延長パッシブは自分が付与したデバフを1ターン延ばす(self):
        """分岐: tech_skill.md §6 #5 — debuff_p1 習得者の状態異常も対象"""
        target = _enemy()
        source = make_actor("ally1", learned=["debuff_p1"])
        assert sk.apply_status(target, "poison", turns=3, chance=1.0, source=source, rng=SeqRng(0.0)) is True
        assert target.statuses["poison"].turns == 4

    def test_弱体延長は他キャラが付与したデバフには効かない(self):
        """分岐: tech_skill.md §6 #6 — 付与者が未習得なら延長しない"""
        target = _enemy()
        source = make_actor("ally1")
        sk.apply_status(target, "poison", turns=3, chance=1.0, source=source, rng=SeqRng(0.0))
        assert target.statuses["poison"].turns == 3

    def test_残りターンが0になった効果は解除される(self):
        """分岐: tech_skill.md §6 #7 — 1→0 で消滅"""
        actor = make_actor(
            buffs=[sk.Buff("buff_1", stat="atk", value=0.2, turns=1, source_id="ally1")],
            statuses={"poison": sk.Status("poison", turns=1, source_id="e1")},
        )
        sk.tick_effects(actor)
        assert actor.buffs == []
        assert "poison" not in actor.statuses

    def test_残りターンが1以上の効果は継続する(self):
        """分岐: tech_skill.md §6 #8 — 2→1 は残る"""
        actor = make_actor(
            buffs=[sk.Buff("buff_1", stat="atk", value=0.2, turns=2, source_id="ally1")],
            statuses={"poison": sk.Status("poison", turns=2, source_id="e1")},
        )
        sk.tick_effects(actor)
        assert actor.buffs[0].turns == 1
        assert actor.statuses["poison"].turns == 1

    def test_確定付与は80パーセントキャップの例外として必ず付与する(self):
        """分岐: tech_skill.md §6 #9 — 付与率100%明記のスキル（毒付与）"""
        target = _enemy()
        source = make_actor("ally1")
        assert sk.apply_status(target, "poison", turns=3, chance=1.0, source=source, rng=SeqRng(0.99)) is True

    def test_確率付与は合算付与率の上限80パーセントで判定する(self):
        """分岐: tech_skill.md §6 #10 — 0.95 指定でも 0.8 でしか通らない"""
        source = make_actor("ally1")
        assert sk.apply_status(_enemy(), "stun", turns=1, chance=0.95, source=source, rng=SeqRng(0.79)) is True
        assert sk.apply_status(_enemy(), "stun", turns=1, chance=0.95, source=source, rng=SeqRng(0.80)) is False

    def test_同一状態異常の再付与は残り持続ターンを上書きする(self):
        """分岐: tech_skill.md §6 #11 — 加算せず上書き（§1 #8）"""
        target = _enemy(); target.statuses["poison"] = sk.Status("poison", turns=1, source_id="ally1")
        source = make_actor("ally1")
        sk.apply_status(target, "poison", turns=3, chance=1.0, source=source, rng=SeqRng(0.0))
        assert target.statuses["poison"].turns == 3
        assert len(target.statuses) == 1

    def test_未付与の状態異常は新規に付与される(self):
        """分岐: tech_skill.md §6 #12 — 残りターン = 持続ターン"""
        target = _enemy()
        sk.apply_status(target, "paralysis", turns=2, chance=1.0, source=make_actor("ally1"), rng=SeqRng(0.0))
        assert target.statuses["paralysis"].turns == 2
