package com.afkgame.domain.service.battle;

import org.springframework.stereotype.Service;

/**
 * {@link StatCalculator} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。上限値そのものは
 * docs/design/systems/battle.md「確率・軽減率の上限」が正。
 */
@Service
public class StatCalculatorImpl implements StatCalculator {

    /** クリティカル率の上限。超過分は切り捨てる（tech_numeric.md §3）。 */
    private static final double MAX_CRIT_RATE = 1.0;

    /** 被ダメージ軽減率の上限。最終ダメージ倍率の下限0.2に対応する（tech_numeric.md §3）。 */
    private static final double MAX_DAMAGE_REDUCTION = 0.8;

    /**
     * {@inheritDoc}
     */
    @Override
    public double effectiveCritRate(double summedCritRate) {
        return Math.min(summedCritRate, MAX_CRIT_RATE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double effectiveDamageReduction(double summedReduction) {
        return Math.min(summedReduction, MAX_DAMAGE_REDUCTION);
    }
}
