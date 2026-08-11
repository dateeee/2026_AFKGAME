package com.afkgame.domain.service.battle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.afkgame.domain.model.Character;

/**
 * {@link HealingCalculatorImpl} の単体テスト（回復量の丸めと上限）。
 *
 * <p>仕様: docs/tech/detail/tech_numeric.md §5（分岐一覧）・§2「丸め規則一覧」の
 * 「回復量（スキル・リジェネ・ポーション）」行（{@code floor} / 下限1 / HPは maxHP まで）。
 * ポーション自動使用そのものの流れは docs/tech/detail/tech_battle.md §3.1 手順2-f、
 * 簡略計算側の消費モデルは tech_offline.md §4.1（{@link OfflineCalculatorImplTest}）。
 *
 * <p>分岐観点: 回復後のHPが maxHP を超える / 超えない、{@code floor} した回復量が0 / 1以上。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code interface HealingCalculator}:
 *       {@code int heal(Character target, double rawAmount)} → <b>実際に回復した量</b>を返し、
 *       対象のHPを更新する。戻り値を回復量そのものにすると maxHP クランプで捨てた分が
 *       戦闘ログとずれるため、適用後の差分を返す</li>
 *   <li>{@code rawAmount} を {@code floor} してから加算し、HPを {@code [0, maxHP]} へ
 *       クランプする（tech_numeric.md §4 の「丸め → クランプ」の順）</li>
 *   <li>クランプの順は「{@code floor} → 下限1 → 加算 → maxHP で上限」。下限を上限より後に置くと、
 *       HPが maxHP に張り付いた対象へ毎回1ずつ回復してしまう（#5 の期待値と食い違う）</li>
 * </ul>
 */
@Tag("unit")
class HealingCalculatorImplTest {

    private static final int MAX_HP = 100;

    private HealingCalculator calculator() {
        return new HealingCalculatorImpl();
    }

    private Character character(int hp) {
        Character character = new Character();
        character.setId("char_001");
        character.setHp(hp);
        character.setMaxHp(MAX_HP);
        return character;
    }

    @Nested
    @DisplayName("回復量の上限")
    class TestHealCap {

        /**
         * 回復後のHPが maxHP を超えたら maxHP でクランプし、実際に回復した分だけを返す。
         * 超えない場合は {@code floor} した回復量をそのまま加算する。
         *
         * <p>分岐: tech_numeric.md §5 #5,6
         */
        @ParameterizedTest(name = "HP{0} に {1} 回復 → HP{2}・回復量{3}")
        @CsvSource({
            "90, 30.7, 100, 10", // 上限超過: floor で30 → maxHP でクランプ（実回復は10）
            "50, 30.7,  80, 30", // 上限内: floor で30 をそのまま加算
        })
        void test_回復後のHPをmaxHPでクランプする(int hp, double rawAmount, int expectedHp, int expectedHealed) {
            Character target = character(hp);

            int healed = calculator().heal(target, rawAmount);

            assertThat(target.getHp()).isEqualTo(expectedHp);
            assertThat(healed).isEqualTo(expectedHealed);
        }
    }

    @Nested
    @DisplayName("回復量の下限")
    class TestHealFloor {

        /**
         * {@code floor} して0になっても最低1は回復する（tech_numeric.md §2「回復量」の下限1）。
         * 下限が無いと、倍率の低いリジェネや低レベルの回復スキルが
         * 「発動したのにHPが1も動かない」状態になり、期待値計算（tech_offline.md §4.1）とも食い違う。
         * maxHP に張り付いた対象は #5 側の上限クランプが先に効くため、ここはHPに余裕のある状態で見る。
         *
         * <p>分岐: tech_numeric.md §5 #13,14
         */
        @ParameterizedTest(name = "HP{0} に {1} 回復 → HP{2}・回復量{3}")
        @CsvSource({
            "50, 0.9, 51, 1", // floor で0 → 下限1へクランプ
            "50, 2.7, 52, 2", // floor で2 → 下限は働かない
        })
        void test_floorで0になる回復量は1へクランプする(int hp, double rawAmount, int expectedHp, int expectedHealed) {
            Character target = character(hp);

            int healed = calculator().heal(target, rawAmount);

            assertThat(target.getHp()).isEqualTo(expectedHp);
            assertThat(healed).isEqualTo(expectedHealed);
        }
    }
}
