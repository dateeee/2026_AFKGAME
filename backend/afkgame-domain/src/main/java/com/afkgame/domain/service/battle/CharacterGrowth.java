package com.afkgame.domain.service.battle;

import com.afkgame.domain.model.Character;

/**
 * キャラクターの経験値付与とレベルアップを担う。
 *
 * <p>仕様: docs/tech/detail/tech_numeric.md §3「キャップ・下限一覧」の「キャラLV」行、
 * 経験値テーブルは docs/data/master/character.md §1.4、レベルアップ時のステータス再計算は
 * docs/tech/detail/tech_offline.md §4 手順3g。
 *
 * <p>実装は {@link CharacterGrowthImpl}。
 */
public interface CharacterGrowth {

    /**
     * 経験値を加算し、しきい値へ到達していればレベルアップさせる。
     *
     * <p>LV上限に到達しているキャラには経験値も加算しない（超過EXPは切り捨て。
     * tech_numeric.md §3）。
     *
     * @param character 対象キャラクター
     * @param amount    加算する経験値
     */
    void addExp(Character character, long amount);

    /**
     * レベルを1つ上げ、ステータスを再計算してSPを1加算する。
     *
     * <p>スキルの自動習得は行わない（tech_offline.md §4 手順3g）。
     *
     * @param character 対象キャラクター
     */
    void applyLevelUp(Character character);
}
