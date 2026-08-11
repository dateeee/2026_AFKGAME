package com.afkgame.domain.service.battle;

import org.springframework.stereotype.Service;

import com.afkgame.domain.model.Character;

/**
 * {@link HealingCalculator} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。クランプの順は「{@code floor} → 下限1 → 加算 →
 * maxHP で上限」であり、下限を上限より後に置くとHPが maxHP に張り付いた対象へ毎回1ずつ
 * 回復してしまう（tech_numeric.md §4）。
 */
@Service
public class HealingCalculatorImpl implements HealingCalculator {

    /** 回復量の下限。{@code floor} で0になっても最低1は回復する（tech_numeric.md §2）。 */
    private static final int MIN_HEAL = 1;

    /**
     * {@inheritDoc}
     */
    @Override
    public int heal(Character target, double rawAmount) {
        int amount = Math.max((int) Math.floor(rawAmount), MIN_HEAL);
        int before = target.getHp();
        int after = Math.min(before + amount, target.getMaxHp());
        target.setHp(after);
        return after - before;
    }
}
