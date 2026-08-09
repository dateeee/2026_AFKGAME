package com.afkgame.domain.rng;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.afkgame.env.config.GameSettings;

/**
 * {@link RandomFactory} の単体テスト。
 *
 * <p>仕様: docs/tech/detail/tech_rng.md §2（RNGの扱い）・§3（再現性の範囲）。
 *
 * <p>分岐観点: シード指定あり / なし。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class RandomFactoryTest {

    /** 設定値のうち本テストで意味を持つのは {@code battleRngSeed} だけ。他は application.yml の既定値。 */
    private static GameSettings properties(Long battleRngSeed) {
        return new GameSettings(60, 3, 1.0, 24, 100, 100, 50, 9999, Long.MAX_VALUE, battleRngSeed);
    }

    private static List<Integer> sequenceOf(Random random) {
        return IntStream.range(0, 10).map(i -> random.nextInt()).boxed().toList();
    }

    @Test
    @DisplayName("シード未設定なら呼び出しごとに独立した乱数源を返す")
    void test_シード未設定なら独立した乱数源を返す() {
        RandomFactory factory = new RandomFactory(properties(null));

        Random first = factory.create();
        Random second = factory.create();

        assertThat(first).isNotSameAs(second);
        assertThat(sequenceOf(first)).isNotEqualTo(sequenceOf(second));
    }

    @Test
    @DisplayName("シード設定時は同じ乱数列を再生する（調査用）")
    void test_シード設定時は同じ乱数列を再生する() {
        RandomFactory factory = new RandomFactory(properties(42L));

        assertThat(sequenceOf(factory.create()))
                .isEqualTo(sequenceOf(new Random(42L)))
                .isEqualTo(sequenceOf(factory.create()));
    }
}
