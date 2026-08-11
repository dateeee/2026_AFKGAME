package com.afkgame.domain.service.battle;

import com.afkgame.domain.model.Character;

/**
 * {@link HealingCalculator} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。クランプの順は「{@code floor} → 下限1 → 加算 →
 * maxHP で上限」であり、下限を上限より後に置くとHPが maxHP に張り付いた対象へ毎回1ずつ
 * 回復してしまう（tech_numeric.md §4）。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、処理は未実装。</b>
 * 解消は製造①-ii（戦闘計算の Green。docs/backlog/java_migration.md STEP 3-B）で、
 * {@code @Service} も同じ回で付ける。
 */
public class HealingCalculatorImpl implements HealingCalculator {

    /**
     * {@inheritDoc}
     */
    @Override
    public int heal(Character target, double rawAmount) {
        throw new UnsupportedOperationException("製造①-ii（戦闘計算の Green）で実装する");
    }
}
