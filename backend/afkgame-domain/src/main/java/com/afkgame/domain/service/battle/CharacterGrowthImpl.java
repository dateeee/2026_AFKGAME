package com.afkgame.domain.service.battle;

import org.springframework.stereotype.Service;

import com.afkgame.domain.masterdata.CharacterTypeData;
import com.afkgame.domain.masterdata.CharacterTypes;
import com.afkgame.domain.model.Character;
import com.afkgame.env.config.GameSettings;

/**
 * {@link CharacterGrowth} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。LV上限は {@link GameSettings}、成長率は
 * {@link CharacterTypes} から読み、実装へ埋め込まない（profile.md §5 不変条件6「データ駆動」）。
 *
 * <p>対応する分岐一覧は tech_numeric.md §5 #11・#12・#15〜#25 と tech_party.md §6、
 * 検証は {@link CharacterGrowthImplTest} が持つ。
 */
@Service
public class CharacterGrowthImpl implements CharacterGrowth {

    /**
     * しきい値 {@code round(係数 × LV^指数)} の係数（docs/data/master/character.md §1.4
     * {@code required_exp = 100 * (level ^ 1.5)}）。タイプ非依存の式なのでマスターデータの列に
     * 持たせず、式の定数としてここに置く。
     */
    private static final double REQUIRED_EXP_COEFFICIENT = 100.0;

    /** 同じくしきい値の指数。1より大きいためLVが上がるほど必要EXPが逓増する。 */
    private static final double REQUIRED_EXP_EXPONENT = 1.5;

    /** レベルアップ1回あたりのSP付与量（tech_party.md §6 #1）。 */
    private static final int SKILL_POINTS_PER_LEVEL = 1;

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
        long threshold = thresholdOf(character.getLevel());
        // 1回の付与で複数レベル上がりうるため、到達しなくなるまで繰り返す（§5 #17）
        while (character.getExp() >= threshold) {
            character.setExp(character.getExp() - threshold);
            applyLevelUp(character);
            if (character.getLevel() >= gameSettings.maxPlayerLevel()) {
                // 連鎖の途中で上限に達したら余剰EXPは手元に残す（§5 #18）。以降の付与は
                // 冒頭のガードで捨てられる
                return;
            }
            threshold = thresholdOf(character.getLevel());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>LV上限の判定は持たない。上限で止める責務は {@link #addExp(Character, long)} 側にあり、
     * 本メソッドは「1レベル上げる」ことだけを行う。
     */
    @Override
    public void applyLevelUp(Character character) {
        character.setLevel(character.getLevel() + 1);
        CharacterTypeData type = characterTypes.get(character.getType());
        int maxHp = statAt(type.hp(), type.growthHp(), character.getLevel());
        // maxHP の上昇分を現在HPへも移し、HP欠損量を維持する（§5 #25）。全回復にすると
        // 簡略計算の全滅判定がオンラインtickとずれる（tech_offline.md §4.1）
        character.setHp(character.getHp() + maxHp - character.getMaxHp());
        character.setMaxHp(maxHp);
        character.setBaseAtk(statAt(type.atk(), type.growthAtk(), character.getLevel()));
        character.setBaseDef(statAt(type.def(), type.growthDef(), character.getLevel()));
        character.setBaseSpd(statAt(type.spd(), type.growthSpd(), character.getLevel()));
        character.setSkillPoints(character.getSkillPoints() + SKILL_POINTS_PER_LEVEL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long requiredExpToNextLevel(Character character) {
        if (character.getLevel() >= gameSettings.maxPlayerLevel()) {
            return Long.MAX_VALUE;
        }
        return thresholdOf(character.getLevel()) - character.getExp();
    }

    /**
     * 次のレベルへ上がるためのしきい値を求める（§5 #19・#20）。
     *
     * <p>丸めは {@code round}（tech_numeric.md §2「レベルアップ必要EXP」）。{@code floor} だと
     * LV2 が282になり docs/data/master/character.md §1.4 の表と合わない。
     *
     * @param level 現在のレベル
     * @return そのレベルから次のレベルへ上がるのに要するEXP
     */
    private static long thresholdOf(int level) {
        return Math.round(REQUIRED_EXP_COEFFICIENT * Math.pow(level, REQUIRED_EXP_EXPONENT));
    }

    /**
     * 指定レベルのステータスを LV1 基礎値から求め直す（§5 #23・#24）。
     *
     * <p>LVごとの増分を足し込むと {@code floor} が二重に働き、成長率が小数のタイプで値がずれる。
     *
     * @param base   LV1 の基礎値
     * @param growth 1LVあたりの上昇量
     * @param level  対象レベル
     * @return {@code floor(base + growth × (level − 1))}
     */
    private static int statAt(int base, double growth, int level) {
        return (int) Math.floor(base + growth * (level - 1));
    }
}
