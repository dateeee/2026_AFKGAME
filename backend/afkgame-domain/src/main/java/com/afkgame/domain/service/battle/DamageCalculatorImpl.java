package com.afkgame.domain.service.battle;

import java.util.Random;

import org.springframework.stereotype.Service;

/**
 * {@link DamageCalculator} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。計算順は
 * {@code ATK × (1 + variance) − DEF × 0.5} → クリティカル倍率 → {@code floor} →
 * 下限クランプで、途中では丸めない（tech_numeric.md §4）。
 *
 * <p>乱数は分岐に依らず「①ダメージ分散 → ②クリティカル判定」の2回を消費する
 * （tech_rng.md §3。クリティカル判定は分岐ではなく毎回行うため、消費数は一定）。
 */
@Service
public class DamageCalculatorImpl implements DamageCalculator {

    /** ダメージ分散の下限。{@code [0,1)} の乱数を {@code [-0.1, 0.1)} へ写す（tech_rng.md §1 #1）。 */
    private static final double VARIANCE_MIN = -0.1;

    /** ダメージ分散の幅（下限 {@code -0.1} から上限 {@code +0.1} まで）。 */
    private static final double VARIANCE_RANGE = 0.2;

    /** DEF の減算係数（tech_battle.md §3.1 手順2-j の {@code DEF × 0.5}）。 */
    private static final double DEF_FACTOR = 0.5;

    /** クリティカル時の倍率。DEF 減算後に乗算する（tech_battle.md §3.1 手順2-j）。 */
    private static final double CRITICAL_MULTIPLIER = 1.5;

    /** 味方→敵の最低ダメージ（tech_numeric.md §2）。 */
    private static final long MIN_DAMAGE_TO_ENEMY = 1L;

    /** 敵→味方の最低ダメージ。0ダメージを許容する（tech_numeric.md §2）。 */
    private static final long MIN_DAMAGE_TO_ALLY = 0L;

    /**
     * {@inheritDoc}
     */
    @Override
    public long calculate(int atk, int def, double critRate, DamageDirection direction,
            Random rng) {
        double variance = VARIANCE_MIN + VARIANCE_RANGE * rng.nextDouble();
        double raw = atk * (1 + variance) - def * DEF_FACTOR;
        if (rng.nextDouble() < critRate) {
            raw = raw * CRITICAL_MULTIPLIER;
        }
        long lowerBound = direction == DamageDirection.ALLY_TO_ENEMY
                ? MIN_DAMAGE_TO_ENEMY
                : MIN_DAMAGE_TO_ALLY;
        return Math.max((long) Math.floor(raw), lowerBound);
    }
}
