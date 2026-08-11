package com.afkgame.domain.service.battle;

import java.util.Random;

/**
 * {@link DamageCalculator} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。計算順は
 * {@code ATK × (1 + variance) − DEF × 0.5} → クリティカル倍率 → {@code floor} →
 * 下限クランプで、途中では丸めない（tech_numeric.md §4）。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、処理は未実装。</b>
 * 解消は製造①-ii（戦闘計算の Green。docs/backlog/java_migration.md STEP 3-B）で、
 * {@code @Service} も同じ回で付ける。
 */
public class DamageCalculatorImpl implements DamageCalculator {

    /**
     * {@inheritDoc}
     */
    @Override
    public long calculate(int atk, int def, double critRate, DamageDirection direction,
            Random rng) {
        throw new UnsupportedOperationException("製造①-ii（戦闘計算の Green）で実装する");
    }
}
