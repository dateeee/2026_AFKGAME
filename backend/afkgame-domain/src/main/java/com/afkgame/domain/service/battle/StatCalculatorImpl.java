package com.afkgame.domain.service.battle;

/**
 * {@link StatCalculator} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。上限値そのものは
 * docs/design/systems/battle.md「確率・軽減率の上限」が正。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、処理は未実装。</b>
 * 解消は製造①-ii（戦闘計算の Green。docs/backlog/java_migration.md STEP 3-B）で、
 * {@code @Service} も同じ回で付ける。
 */
public class StatCalculatorImpl implements StatCalculator {

    /**
     * {@inheritDoc}
     */
    @Override
    public double effectiveCritRate(double summedCritRate) {
        throw new UnsupportedOperationException("製造①-ii（戦闘計算の Green）で実装する");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double effectiveDamageReduction(double summedReduction) {
        throw new UnsupportedOperationException("製造①-ii（戦闘計算の Green）で実装する");
    }
}
