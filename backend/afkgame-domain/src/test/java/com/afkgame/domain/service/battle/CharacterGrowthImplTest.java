package com.afkgame.domain.service.battle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.afkgame.domain.model.Character;
import com.afkgame.env.config.GameSettings;

/**
 * {@link CharacterGrowthImpl} の単体テスト（EXP付与とLV上限）。
 *
 * <p>仕様: docs/tech/detail/tech_numeric.md §5（分岐一覧）・§3「キャップ・下限一覧」の
 * 「キャラLV」行、経験値テーブルは docs/data/master/character.md §1.4
 * （{@code required_exp = 100 × LV^1.5}。LV1→2 は100）。
 *
 * <p>分岐観点: LV上限に到達している / していない。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code interface CharacterGrowth} は ①-a（{@link OfflineCalculatorImplTest}）が
 *       {@code void applyLevelUp(Character character)} で定義済み。本クラスは同インタフェースへ
 *       {@code void addExp(Character character, long amount)} を<b>追加</b>する
 *       （別名の表層を新設しない）。EXP加算・しきい値到達時のLVアップ・LV上限の判定を担う</li>
 *   <li>コンストラクタ注入: {@code CharacterGrowthImpl(GameSettings)}。
 *       LV上限は {@link GameSettings#maxPlayerLevel()}（9999）から読む
 *       （profile.md §5 不変条件6「データ駆動」。定数を実装へ埋め込まない）</li>
 *   <li>LV上限では<b>EXPも加算しない</b>（超過EXPは切り捨て。tech_numeric.md §3 の
 *       「超過EXPは切り捨て」）。加算だけ続けると転生・限界突破の実装時に
 *       上限到達済みキャラのEXPが意味を持ってしまう</li>
 * </ul>
 */
@Tag("unit")
class CharacterGrowthImplTest {

    /** 設定値は {@code afkgame.properties} の既定と同じ（tech_backend.md §4.2）。LV上限9999。 */
    private static final GameSettings SETTINGS = new GameSettings(
            60, 3, 1.0, 24, 100, 100, 50, 9999, Long.MAX_VALUE, null);

    private CharacterGrowth growth() {
        return new CharacterGrowthImpl(SETTINGS);
    }

    private Character character(int level, long exp) {
        Character character = new Character();
        character.setId("char_001");
        character.setLevel(level);
        character.setExp(exp);
        character.setHp(100);
        character.setMaxHp(100);
        return character;
    }

    @Nested
    @DisplayName("LV上限")
    class TestLevelCap {

        /**
         * LV上限（9999）に達したキャラはEXPを獲得してもLVが増えず、EXPも加算しない。
         * 上限未満なら加算する（LV1でEXP+10 はLV2のしきい値100に届かないためLVは変わらない）。
         *
         * <p>分岐: tech_numeric.md §5 #11,12
         */
        @ParameterizedTest(name = "LV{0}・EXP{1} に +{2} → EXP{3}")
        @CsvSource({
            "9999, 100, 500, 100", // 上限到達: EXPを捨てる
            "1,      0,  10,  10", // 上限未満: 加算する（しきい値100未満なのでLVは据え置き）
        })
        void test_LV上限ではEXPを加算しない(int level, long exp, long gained, long expectedExp) {
            Character character = character(level, exp);

            growth().addExp(character, gained);

            assertThat(character.getExp()).isEqualTo(expectedExp);
            assertThat(character.getLevel()).isEqualTo(level);
        }
    }
}
