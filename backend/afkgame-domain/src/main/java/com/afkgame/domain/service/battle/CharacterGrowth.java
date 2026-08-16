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
     * <p>ステータスは {@code floor(base + growth × (LV - 1))} で求め直す（増分加算にしない。
     * tech_numeric.md §5 #21）。maxHP の上昇分は現在HPへも加算し、HP欠損量を維持する（#22）。
     * スキルの自動習得は行わない（tech_offline.md §4 手順3g）。
     *
     * @param character 対象キャラクター
     */
    void applyLevelUp(Character character);

    /**
     * 次のレベルまでに残り何EXP必要かを返す。
     *
     * <p>しきい値は {@code round(100 × LV^1.5)}（tech_numeric.md §2・§5 #19、
     * 経験値テーブルは docs/data/master/character.md §1.4）。{@code exp} は現在レベル内の
     * 累積EXPなので、残りEXPはしきい値との差になる（tech_db/player.md §4）。
     *
     * @param character 対象キャラクター
     * @return 次のレベルまでの残りEXP。LV上限に到達済みなら {@link Long#MAX_VALUE}（到達しない）
     */
    long requiredExpToNextLevel(Character character);
}
