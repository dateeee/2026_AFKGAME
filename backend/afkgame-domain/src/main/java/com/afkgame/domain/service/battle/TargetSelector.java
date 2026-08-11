package com.afkgame.domain.service.battle;

import java.util.List;
import java.util.Random;

import com.afkgame.domain.model.Character;

/**
 * 通常攻撃のターゲットを抽選する。
 *
 * <p>仕様: docs/tech/detail/tech_battle.md §3.3（選択規則）、確率判定の境界規約は
 * docs/tech/detail/tech_rng.md §1 #3。
 *
 * <p>実装は {@link TargetSelectorImpl}。
 */
public interface TargetSelector {

    /**
     * 候補のうち生存している1体を等確率で選ぶ。
     *
     * <p>候補は絞り込まずに渡してよく、{@code hp > 0} の生存者だけを対象にするのは実装側が行う。
     * 生存者が0体の呼び出しは、呼び出し側が全滅判定で打ち切っている前提のため到達しない経路であり、
     * {@link IllegalStateException} になる。
     *
     * @param candidates 候補（生存・戦闘不能を問わない）
     * @param rng        このリクエストの乱数源
     * @return 選ばれた1体
     */
    Character selectRandom(List<Character> candidates, Random rng);
}
