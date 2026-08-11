package com.afkgame.domain.service;

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
 * <p>分岐観点: 回復後のHPが maxHP を超える / 超えない。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code interface HealingCalculator}:
 *       {@code int heal(Character target, double rawAmount)} → <b>実際に回復した量</b>を返し、
 *       対象のHPを更新する。戻り値を回復量そのものにすると maxHP クランプで捨てた分が
 *       戦闘ログとずれるため、適用後の差分を返す</li>
 *   <li>{@code rawAmount} を {@code floor} してから加算し、HPを {@code [0, maxHP]} へ
 *       クランプする（tech_numeric.md §4 の「丸め → クランプ」の順）</li>
 *   <li>回復量の下限1（tech_numeric.md §2）は §5 の分岐一覧に行が無いため本工程で展開していない。
 *       製造で {@code floor} が0になる経路を作るなら、先に分岐一覧へ行を足す
 *       （.claude/project/test-list.md §3。完了報告の差し戻しを参照）</li>
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
}
