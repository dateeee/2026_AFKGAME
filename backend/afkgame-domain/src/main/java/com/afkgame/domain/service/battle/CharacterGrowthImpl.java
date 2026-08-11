package com.afkgame.domain.service.battle;

import com.afkgame.domain.model.Character;
import com.afkgame.env.config.GameSettings;

/**
 * {@link CharacterGrowth} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。LV上限は {@link GameSettings} から読み、
 * 実装へ埋め込まない（profile.md §5 不変条件6「データ駆動」）。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、処理は未実装。</b>
 * 解消は製造①-ii（戦闘計算の Green。docs/backlog/java_migration.md STEP 3-B）で、
 * {@code @Service} も同じ回で付ける。
 */
public class CharacterGrowthImpl implements CharacterGrowth {

    private final GameSettings gameSettings;

    /**
     * 依存を受け取る。
     *
     * @param gameSettings LV上限の供給元
     */
    public CharacterGrowthImpl(GameSettings gameSettings) {
        this.gameSettings = gameSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExp(Character character, long amount) {
        throw new UnsupportedOperationException("製造①-ii（戦闘計算の Green）で実装する");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyLevelUp(Character character) {
        throw new UnsupportedOperationException("製造①-ii（戦闘計算の Green）で実装する");
    }
}
