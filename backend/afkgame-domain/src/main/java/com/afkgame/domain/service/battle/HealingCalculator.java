package com.afkgame.domain.service.battle;

import com.afkgame.domain.model.Character;

/**
 * 回復量の丸めと上限を適用する。
 *
 * <p>仕様: docs/tech/detail/tech_numeric.md §2「丸め規則一覧」の「回復量（スキル・リジェネ・
 * ポーション）」行・§4「適用順序」。ポーション自動使用の流れは
 * docs/tech/detail/tech_battle.md §3.1 手順2-f。
 *
 * <p>実装は {@link HealingCalculatorImpl}。
 */
public interface HealingCalculator {

    /**
     * 対象を回復し、実際に回復した量を返す。
     *
     * <p>戻り値を要求量そのものにすると maxHP の上限で捨てた分が戦闘ログとずれるため、
     * 適用後の差分を返す。
     *
     * @param target    回復対象。HPを更新する
     * @param rawAmount 丸める前の回復要求量
     * @return 実際に回復した量
     */
    int heal(Character target, double rawAmount);
}
