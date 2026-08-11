package com.afkgame.domain.service.battle;

import org.springframework.stereotype.Service;

import com.afkgame.domain.model.Character;
import com.afkgame.env.config.GameSettings;

/**
 * {@link CharacterGrowth} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。LV上限は {@link GameSettings} から読み、
 * 実装へ埋め込まない（profile.md §5 不変条件6「データ駆動」）。
 *
 * <p><b>しきい値到達時のレベルアップと {@link #applyLevelUp(Character)} は未実装。</b>
 * 到達側の分岐が分岐一覧に無く（tech_numeric.md §5 は #11・#12 の上限判定だけを持ち、
 * 周回中のレベルアップは tech_offline.md §5 #7・#8 が {@code OfflineCalculator} 側で持つ）、
 * ステータス再計算に要る成長率も {@code character_types.yml} が未搭載のため
 * （同ファイルの注記「成長率はレベルアップ機能の移植時に追加する」）。
 * 解消は製造①-iii（tick・オフラインの Green。docs/backlog/java_migration.md STEP 3-B）。
 */
@Service
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
        throw new UnsupportedOperationException("製造①-iii（tick・オフラインの Green）で実装する");
    }
}
