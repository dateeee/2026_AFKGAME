package com.afkgame.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DamageCalculatorImpl} の単体テスト（通常攻撃1発ぶんのダメージ）。
 *
 * <p>仕様: docs/tech/detail/tech_rng.md §5（分岐一覧）・§1「境界の統一規約」、
 * 計算式は docs/tech/detail/tech_battle.md §3.1 手順2-j、丸めと下限は
 * docs/tech/detail/tech_numeric.md §2・§4。
 *
 * <p>分岐観点: 確率判定 {@code r < p} の両端（0% / 100%）、クリティカルの発生・非発生
 * （境界そのものの直前・直後）、ダメージ分散の上下限。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code interface DamageCalculator}:
 *       {@code long calculate(int atk, int def, double critRate, DamageDirection direction, Random rng)}。
 *       {@code enum DamageDirection { ALLY_TO_ENEMY, ENEMY_TO_ALLY }}</li>
 *   <li>乱数の<b>消費順序</b>は「①ダメージ分散 → ②クリティカル判定」の1回ずつ
 *       （tech_rng.md §3「消費順序の固定」。tech_battle.md §3.1 の記述順）。
 *       分散は {@code nextDouble()} の {@code [0,1)} を {@code [-0.1, 0.1)} へ写す
 *       （{@code variance = -0.1 + 0.2 × r}）</li>
 *   <li>計算順は {@code ATK × (1 + variance) − DEF × 0.5} → クリティカル倍率1.5 →
 *       {@code floor} → 下限クランプ（tech_numeric.md §4。途中で丸めない）</li>
 *   <li>クリティカル率は<b>引数で受ける</b>（実装内の定数にしない ＝ tech_rng.md §6）。
 *       <b>合算値の切り捨ては呼び出し前に済ませる</b>ので、本インタフェースは実効値
 *       （0〜1.0）を受け取る前提にする（tech_numeric.md §4「確率のクランプは乱数判定の前」。
 *       切り捨てそのものは {@link StatCalculatorImplTest} が持つ）</li>
 *   <li>被ダメ軽減の乗算（tech_battle.md §3.1 手順2-j）は軽減率の供給元がパッシブ・装備
 *       （Phase 2〜3）のため引数に持たない。Phase を跨いで足すときに引数を追加する</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DamageCalculatorImplTest {

    /** DEF を0にして「分散とクリティカルだけがダメージを動かす」形にする。 */
    private static final int ATK = 100;

    private static final int DEF = 0;

    /** 試行回数。tech_rng.md §5 #1 が指定する 1,000 回。 */
    private static final int TRIALS = 1_000;

    /** 分散のみのダメージ帯 {@code [ATK×0.9, ATK×1.1)} を floor した範囲。 */
    private static final long NORMAL_MIN = 90L;

    private static final long NORMAL_MAX = 109L;

    /** クリティカル（×1.5）のダメージ帯。通常帯と重ならないため発生の有無を判別できる。 */
    private static final long CRIT_MIN = 135L;

    private static final long CRIT_MAX = 164L;

    @Mock
    private Random random;

    private DamageCalculator calculator() {
        return new DamageCalculatorImpl();
    }

    /** 実乱数での試行。シードを固定して結果を決定的にする（tech_rng.md §3）。 */
    private List<Long> damagesOf(double critRate) {
        DamageCalculator calculator = calculator();
        Random seeded = new Random(20260811L);
        return IntStream.range(0, TRIALS)
                .mapToObj(i -> calculator.calculate(ATK, DEF, critRate, DamageDirection.ALLY_TO_ENEMY, seeded))
                .toList();
    }

    @Nested
    @DisplayName("確率判定の両端")
    class TestProbabilityBounds {

        /**
         * {@code r < p} 規約により {@code p = 0} の事象は決して発生しない（tech_rng.md §1）。
         * {@code r <= p} で実装すると {@code r = 0.0} を引いた回で発生してしまう。
         *
         * <p>分岐: tech_rng.md §5 #1
         */
        @Test
        void test_クリティカル率0なら1000回試行しても発生しない() {
            assertThat(damagesOf(0.0))
                    .hasSize(TRIALS)
                    .allSatisfy(damage -> assertThat(damage).isBetween(NORMAL_MIN, NORMAL_MAX));
        }

        /**
         * {@code p = 1} は必ず発生する（{@code nextDouble()} は 1.0 を返さないため
         * {@code r < 1.0} が常に真）。
         *
         * <p>分岐: tech_rng.md §5 #2
         */
        @Test
        void test_クリティカル率1なら1000回試行してすべて発生する() {
            assertThat(damagesOf(1.0))
                    .hasSize(TRIALS)
                    .allSatisfy(damage -> assertThat(damage).isBetween(CRIT_MIN, CRIT_MAX));
        }
    }

    @Nested
    @DisplayName("クリティカルの境界")
    class TestCriticalBoundary {

        /**
         * クリティカル率5%の境界そのもの。{@code r < crit_rate} なので
         * {@code 0.05} ちょうどは<b>発生しない</b>側に入る。
         * 分散の乱数は 0.5（{@code variance = 0}）に固定し、倍率だけを見る。
         *
         * <p>分岐: tech_rng.md §5 #3
         */
        @ParameterizedTest(name = "クリティカル判定の乱数={0} → ダメージ{1}")
        @CsvSource({
            "0.0499999, 150", // 直前: r < 0.05 で発生（100 × 1.5）
            "0.05,      100", // 境界ちょうど: r < 0.05 が偽で非発生
        })
        void test_クリティカル率の境界で発生と非発生が分かれる(double critRoll, long expected) {
            when(random.nextDouble()).thenReturn(0.5, critRoll);

            long damage = calculator()
                    .calculate(ATK, DEF, 0.05, DamageDirection.ALLY_TO_ENEMY, random);

            assertThat(damage).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("ダメージ分散の上下限")
    class TestVarianceBounds {

        /**
         * 分散は {@code [-0.1, 0.1)} の半開区間。下限 {@code -0.1} は取り得るが、
         * 上限 {@code +0.1} は取り得ない（{@code nextDouble()} が 1.0 を返さないため）。
         * クリティカル率0で倍率の影響を除いている。
         *
         * <p>分岐: tech_rng.md §5 #4
         */
        @ParameterizedTest(name = "分散の乱数={0} → ダメージ{1}")
        @CsvSource({
            "0.0,          90",  // 下限 r = -0.1 → 100 × 0.9
            "0.9999999999, 109", // 上限直前 r = 0.0999… → 109.99… を floor
        })
        void test_分散の上下限が計算式へ反映される(double varianceRoll, long expected) {
            when(random.nextDouble()).thenReturn(varianceRoll, 0.9);

            long damage = calculator()
                    .calculate(ATK, DEF, 0.0, DamageDirection.ALLY_TO_ENEMY, random);

            assertThat(damage).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("丸めと下限クランプ")
    class TestRoundingAndFloor {

        /**
         * DEF が高くダメージ素値が負になっても、最低ダメージ保証で下限まで戻す。
         * 下限は向きで分かれる（味方→敵は1、敵→味方は0）。
         * {@code ATK=1・DEF=100}・分散0で素値は {@code 1 − 50 = −49}。
         *
         * <p>分岐: tech_numeric.md §5 #1,2
         */
        @ParameterizedTest(name = "{0} → ダメージ{1}")
        @CsvSource({
            "ALLY_TO_ENEMY, 1", // 味方→敵は最低1ダメージ保証
            "ENEMY_TO_ALLY, 0", // 敵→味方は0ダメージを許容
        })
        void test_素値が負なら向きごとの下限へクランプされる(DamageDirection direction, long expected) {
            when(random.nextDouble()).thenReturn(0.5, 0.9);

            long damage = calculator().calculate(1, 100, 0.0, direction, random);

            assertThat(damage).isEqualTo(expected);
        }

        /**
         * 小数の素値は {@code floor} してから下限クランプへ渡す（tech_numeric.md §4 の順序）。
         * 素値 0.9 は {@code floor} で0になり下限クランプで1へ戻る（クランプが働く側）。
         * 素値 2.9 は {@code floor} で2となり、クランプは働かない（働けば1になってしまう）。
         *
         * <p>分散の乱数は 0.52（{@code variance = +0.004}）に固定し、
         * {@code ATK × 1.004 = 100.4} から DEF の半分を引いて素値を作る。
         *
         * <p>分岐: tech_numeric.md §5 #3,4
         */
        @ParameterizedTest(name = "DEF={0} → ダメージ{1}")
        @CsvSource({
            "199, 1", // 素値 100.4 − 99.5 = 0.9 → floor で0 → 下限クランプで1
            "195, 2", // 素値 100.4 − 97.5 = 2.9 → floor で2（下限クランプは働かない）
        })
        void test_素値を丸めてから下限クランプへ渡す(int def, long expected) {
            when(random.nextDouble()).thenReturn(0.52, 0.9);

            long damage = calculator().calculate(ATK, def, 0.0, DamageDirection.ALLY_TO_ENEMY, random);

            assertThat(damage).isEqualTo(expected);
        }
    }
}
