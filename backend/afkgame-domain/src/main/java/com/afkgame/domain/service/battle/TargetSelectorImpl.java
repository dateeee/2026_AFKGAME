package com.afkgame.domain.service.battle;

import java.util.List;
import java.util.Random;

import com.afkgame.domain.model.Character;

/**
 * {@link TargetSelector} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。生存者への絞り込みは本クラスが行う
 * （tech_battle.md §3.3）。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、処理は未実装。</b>
 * 解消は製造①-ii（戦闘計算の Green。docs/backlog/java_migration.md STEP 3-B）で、
 * {@code @Service} も同じ回で付ける。
 */
public class TargetSelectorImpl implements TargetSelector {

    /**
     * {@inheritDoc}
     */
    @Override
    public Character selectRandom(List<Character> candidates, Random rng) {
        throw new UnsupportedOperationException("製造①-ii（戦闘計算の Green）で実装する");
    }
}
