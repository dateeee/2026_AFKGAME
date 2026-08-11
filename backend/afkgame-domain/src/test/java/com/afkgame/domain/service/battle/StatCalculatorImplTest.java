package com.afkgame.domain.service.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link StatCalculatorImpl} の単体テスト（確率・軽減率の上限）。
 *
 * <p>仕様: docs/tech/detail/tech_numeric.md §5（分岐一覧）・§3「キャップ・下限一覧」・
 * §4「適用順序」、上限値のゲーム仕様は docs/design/systems/battle.md「確率・軽減率の上限」。
 *
 * <p>分岐観点: 合算値が上限を超える / 超えない。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code interface StatCalculator}:
 *       {@code double effectiveCritRate(double summedCritRate)}（上限1.0）と
 *       {@code double effectiveDamageReduction(double summedReduction)}（上限0.8）</li>
 *   <li>切り捨ては<b>乱数判定・ダメージ計算へ渡す前</b>に済ませる（tech_numeric.md §4 の注記。
 *       {@code r < p} 規約が成立する前提を作るため）。したがって
 *       {@code DamageCalculator} が受け取るのは実効値であり、上限の判断を持たない
 *       （{@link DamageCalculatorImplTest} の申し送りを参照）</li>
 *   <li>合算そのもの（装備・パッシブ・バフの足し合わせ）は Phase 2〜3 で供給元が増えるため
 *       本インタフェースは<b>合算済みの値を受け取る</b>形にし、上限だけを担う</li>
 * </ul>
 */
@Tag("unit")
class StatCalculatorImplTest {

    private StatCalculator calculator() {
        return new StatCalculatorImpl();
    }

    @Nested
    @DisplayName("確率・軽減率の上限")
    class TestUpperBound {

        /**
         * クリティカル率は合算で上限100%。超過分は切り捨てる。
         * 上限以下はそのまま通す（切り捨てが常時働くと確定クリティカル以外が壊れる）。
         *
         * <p>分岐: tech_numeric.md §5 #7
         */
        @ParameterizedTest(name = "合算={0} → 実効={1}")
        @CsvSource({
            "1.2, 1.0", // 上限超過 → 切り捨て
            "1.0, 1.0", // 上限ちょうど → そのまま
            "0.05, 0.05", // 基礎値のみ → そのまま
        })
        void test_クリティカル率は1_0で切り捨てる(double summed, double expected) {
            assertThat(calculator().effectiveCritRate(summed)).isCloseTo(expected, within(1e-9));
        }

        /**
         * 被ダメ軽減の実効値は上限80%（最終ダメージ倍率の下限0.2）。
         * 上限を外すと軽減100%＝無敵が成立してしまう。
         *
         * <p>分岐: tech_numeric.md §5 #8
         */
        @ParameterizedTest(name = "合算={0} → 実効={1}")
        @CsvSource({
            "0.95, 0.8", // 上限超過 → 切り捨て
            "0.8, 0.8", // 上限ちょうど → そのまま
            "0.3, 0.3", // 上限未満 → そのまま
        })
        void test_被ダメ軽減率は0_8で切り捨てる(double summed, double expected) {
            assertThat(calculator().effectiveDamageReduction(summed)).isCloseTo(expected, within(1e-9));
        }
    }
}
