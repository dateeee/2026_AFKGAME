package com.afkgame.domain.service.battle;

import org.springframework.stereotype.Service;

import com.afkgame.domain.masterdata.CharacterTypes;
import com.afkgame.domain.model.Character;
import com.afkgame.env.config.GameSettings;

/**
 * {@link CharacterGrowth} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。LV上限は {@link GameSettings}、成長率は
 * {@link CharacterTypes} から読み、実装へ埋め込まない（profile.md §5 不変条件6「データ駆動」）。
 *
 * <p><b>しきい値到達時のレベルアップ・{@link #applyLevelUp(Character)}・
 * {@link #requiredExpToNextLevel(Character)} は未実装。</b>分岐一覧
 * （tech_numeric.md §5 #15〜#22・tech_party.md §6）と対応する Red は
 * {@link CharacterGrowthImplTest} が持つ。実装は製造工程で行う
 * （docs/backlog/java_migration.md STEP 3-B）。
 */
@Service
public class CharacterGrowthImpl implements CharacterGrowth {

    private final CharacterTypes characterTypes;

    private final GameSettings gameSettings;

    /**
     * 依存を受け取る。
     *
     * @param characterTypes 成長率の供給元（タイプ別マスター）
     * @param gameSettings   LV上限の供給元
     */
    public CharacterGrowthImpl(CharacterTypes characterTypes, GameSettings gameSettings) {
        this.characterTypes = characterTypes;
        this.gameSettings = gameSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExp(Character character, long amount) {
        if (character.getLevel() >= gameSettings.maxPlayerLevel()) {
            // 上限到達済みは超過EXPを切り捨てる（tech_numeric.md §3）。加算だけ続けると
            // 転生・限界突破の実装時に上限到達済みキャラのEXPが意味を持ってしまう。
            return;
        }
        character.setExp(character.getExp() + amount);
    }

    /**
     * {@inheritDoc}
     *
     * <p>未実装（理由と解消時期はクラス Javadoc）。
     */
    @Override
    public void applyLevelUp(Character character) {
        throw new UnsupportedOperationException("キャラ成長の製造工程で実装する");
    }

    /**
     * {@inheritDoc}
     *
     * <p>未実装（理由と解消時期はクラス Javadoc）。
     */
    @Override
    public long requiredExpToNextLevel(Character character) {
        throw new UnsupportedOperationException("キャラ成長の製造工程で実装する");
    }
}
