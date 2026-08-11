package com.afkgame.domain.service.battle;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;

import com.afkgame.domain.model.Character;

/**
 * {@link TargetSelector} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。生存者への絞り込みは本クラスが行う
 * （tech_battle.md §3.3）。候補全体の件数で抽選すると戦闘不能者が当たり、そのターンが空振りする。
 */
@Service
public class TargetSelectorImpl implements TargetSelector {

    /**
     * {@inheritDoc}
     */
    @Override
    public Character selectRandom(List<Character> candidates, Random rng) {
        List<Character> survivors = candidates.stream()
                .filter(candidate -> candidate.getHp() > 0)
                .toList();
        if (survivors.isEmpty()) {
            // 呼び出し側が全滅判定で打ち切る契約（tech_battle.md §5 #4・#5）のため、
            // ここへ到達すること自体がバグ。分類3として扱う（exception.md §3 #4）。
            throw new IllegalStateException("生存者が0体の状態でターゲット抽選が呼ばれた");
        }
        return survivors.get(rng.nextInt(survivors.size()));
    }
}
