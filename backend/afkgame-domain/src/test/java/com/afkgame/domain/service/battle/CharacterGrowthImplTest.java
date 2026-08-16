package com.afkgame.domain.service.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.afkgame.domain.masterdata.CharacterTypeData;
import com.afkgame.domain.masterdata.CharacterTypes;
import com.afkgame.domain.model.Character;
import com.afkgame.env.config.GameSettings;

/**
 * {@link CharacterGrowthImpl} の単体テスト（EXP付与・しきい値到達・ステータス再計算・SP付与）。
 *
 * <p>仕様: docs/tech/detail/tech_numeric.md §5（分岐一覧）・§2「丸め規則一覧」・§3
 * 「キャップ・下限一覧」の「キャラLV」行、SP付与は docs/tech/detail/tech_party.md §6、
 * 経験値テーブルは docs/data/master/character.md §1.4（{@code required_exp = 100 × LV^1.5}）、
 * 成長率は同 §1.2。
 *
 * <p>分岐観点: しきい値へ到達する / しない、連鎖の途中でLV上限に当たる / 当たらない、
 * 成長率が整数 / 小数。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code CharacterGrowthImpl(CharacterTypes, GameSettings)} — 成長率は
 *       {@link CharacterTypes} から読む。LV上限は {@link GameSettings#maxPlayerLevel()}
 *       （profile.md §5 不変条件6「データ駆動」。定数を実装へ埋め込まない）</li>
 *   <li>{@code long CharacterGrowth#requiredExpToNextLevel(Character)} を<b>追加</b>する。
 *       {@link LapAnalyzerImpl} が {@code lapsToLevelUp} を常に {@link Integer#MAX_VALUE}
 *       で返しているのは、この口が無いため（同クラス Javadoc の未実装①）。配線は本テストの
 *       次セグメント</li>
 *   <li>{@code CharacterTypeData} へ {@code growthHp / growthAtk / growthDef / growthSpd}
 *       を追加した（{@code character_types.yml} も同時。holy の SPD・agile の DEF が
 *       1.5 のため {@code double}）</li>
 *   <li>{@code addExp} は加算後に到達判定を<b>繰り返す</b>（1回の呼び出しで複数レベル上がる）。
 *       各レベルアップは {@code applyLevelUp} と同じ再計算・SP付与を通す</li>
 *   <li>{@code exp} は<b>現在レベル内の累積</b>（tech_db/player.md §4）。到達時はしきい値を
 *       差し引いて持ち越す</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CharacterGrowthImplTest {

    /** 設定値は {@code afkgame.properties} の既定と同じ（tech_backend.md §4.2）。LV上限9999。 */
    private static final GameSettings SETTINGS = new GameSettings(
            60, 3, 1.0, 24, 100, 100, 50, 9999, Long.MAX_VALUE, null);

    /** LV上限の連鎖を短い数値で観察するための設定（上限3）。上限値そのものは設定から読む。 */
    private static final GameSettings CAP_AT_LV3 = new GameSettings(
            60, 3, 1.0, 24, 100, 100, 50, 3, Long.MAX_VALUE, null);

    /** 近接型。成長率は整数のみ（master/character.md §1.2）。 */
    private static final CharacterTypeData MELEE =
            new CharacterTypeData("melee", 100, 10, 5, 5, 0.05, 20, 3, 2, 1);

    /** 神聖型。SPD の成長率が 1.5 で、増分加算と再計算とで結果が分かれる（§5 #21）。 */
    private static final CharacterTypeData HOLY =
            new CharacterTypeData("holy", 95, 8, 5, 6, 0.05, 18, 2, 2, 1.5);

    @Mock
    private CharacterTypes characterTypes;

    // モックはコンストラクタへ手渡す（coding_standards_backend/test.md §5 #3）
    private CharacterGrowth growth() {
        return growthWith(SETTINGS);
    }

    private CharacterGrowth growthWith(GameSettings settings) {
        return new CharacterGrowthImpl(characterTypes, settings);
    }

    /** 成長率の引き当てはレベルアップ経路でしか起きないため lenient で置く。 */
    private void givenType(CharacterTypeData type) {
        lenient().when(characterTypes.get(type.id())).thenReturn(type);
    }

    private Character character(int level, long exp) {
        return character("melee", level, exp, 100, 100);
    }

    private Character character(String type, int level, long exp, int hp, int maxHp) {
        Character character = new Character();
        character.setId("char_001");
        character.setType(type);
        character.setLevel(level);
        character.setExp(exp);
        character.setHp(hp);
        character.setMaxHp(maxHp);
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

    @Nested
    @DisplayName("しきい値への到達")
    class TestLevelUpThreshold {

        /**
         * LV2 のしきい値（100）ちょうどで到達し、1足りなければ到達しない（`>=` で判定する）。
         * 到達時はしきい値を差し引いて持ち越すため、ちょうどのときEXPは0になる。
         *
         * <p>分岐: tech_numeric.md §5 #15,16
         */
        @ParameterizedTest(name = "LV1・EXP0 に +{0} → LV{1}・EXP{2}")
        @CsvSource({
            "100, 2,  0", // ちょうど到達: LVが上がり、しきい値を差し引く
            " 99, 1, 99", // 1足りない: 加算だけしてLVは据え置き
        })
        void test_しきい値ちょうどでレベルアップする(long gained, int expectedLevel, long expectedExp) {
            givenType(MELEE);
            Character character = character(1, 0);

            growth().addExp(character, gained);

            assertThat(character.getLevel()).isEqualTo(expectedLevel);
            assertThat(character.getExp()).isEqualTo(expectedExp);
        }

        /**
         * 1回の付与で2レベル分に届いたら、到達しなくなるまで繰り返しレベルアップする。
         * 383 = LV2 のしきい値100 + LV3 のしきい値283（master/character.md §1.4 の累計EXP）。
         *
         * <p>分岐: tech_numeric.md §5 #17
         */
        @Test
        @DisplayName("2レベル分のEXPで到達しなくなるまで繰り返しレベルアップする")
        void test_複数レベル分のEXPで連続してレベルアップする() {
            givenType(MELEE);
            Character character = character(1, 0);

            growth().addExp(character, 383);

            assertThat(character.getLevel()).isEqualTo(3);
            assertThat(character.getExp()).isZero();
        }

        /**
         * 連鎖の途中でLV上限に達したら、そこで止めて余剰EXPは手元に残す。
         * 上限3で 500 を付与すると 100（LV2）+ 283（LV3）を消費し、残り117で止まる。
         *
         * <p>分岐: tech_numeric.md §5 #18
         */
        @Test
        @DisplayName("連鎖中にLV上限へ達したら余剰EXPを残して止まる")
        void test_連鎖中にLV上限で止まり余剰EXPを残す() {
            givenType(MELEE);
            Character character = character(1, 0);

            growthWith(CAP_AT_LV3).addExp(character, 500);

            assertThat(character.getLevel()).isEqualTo(3);
            assertThat(character.getExp()).isEqualTo(117);
        }
    }

    @Nested
    @DisplayName("次レベルまでの必要EXP")
    class TestRequiredExp {

        /**
         * しきい値は `round(100 × LV^1.5)` で、そこから現在レベル内の累積EXPを引いた残りを返す。
         * LV2 は 282.84… なので四捨五入して283（`floor` なら282になり表と合わない）。
         *
         * <p>分岐: tech_numeric.md §5 #19,20,21
         */
        @ParameterizedTest(name = "LV{0}・EXP{1} → 残り{2}")
        @CsvSource({
            "1,   0, 100", // #20 100 × 1^1.5 = 100（丸めが起きない）
            "2,   0, 283", // #19 100 × 2^1.5 = 282.84… → 283
            "2, 100, 183", // #21 同じLVでも現在EXP分だけ残りが減る
        })
        void test_必要EXPは四捨五入したしきい値からの残り(int level, long exp, long expected) {
            Character character = character(level, exp);

            long actual = growth().requiredExpToNextLevel(character);

            assertThat(actual).isEqualTo(expected);
        }

        /**
         * LV上限に到達済みなら次のレベルは無いので、到達しないことを表す値を返す。
         * 周回数の算定（{@link LapAnalyzer}）が「LVアップしない」として扱えるようにする。
         *
         * <p>分岐: tech_numeric.md §5 #22
         */
        @Test
        @DisplayName("LV上限に到達済みなら到達しない値を返す")
        void test_LV上限では到達しない値を返す() {
            Character character = character(9999, 100);

            long actual = growth().requiredExpToNextLevel(character);

            assertThat(actual).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("ステータス再計算")
    class TestStatRecalculation {

        /**
         * ステータスは `floor(base + growth × (LV - 1))` で求め直す。
         * holy の SPD は base6・成長率1.5 で、LV3 は floor(6 + 3.0) = 9。
         * LVごとに増分を足し込むと floor(floor(7.5) + 1.5) = 8 になり値がずれる。
         *
         * <p>分岐: tech_numeric.md §5 #23
         */
        @Test
        @DisplayName("成長率が小数のタイプでもLV1基礎値から再計算する")
        void test_ステータスを成長率で再計算する() {
            givenType(HOLY);
            // LV2 時点の値（floor(95+18)=113 / floor(8+2)=10 / floor(5+2)=7 / floor(6+1.5)=7）
            Character character = character("holy", 2, 0, 113, 113);
            character.setBaseAtk(10);
            character.setBaseDef(7);
            character.setBaseSpd(7);

            growth().applyLevelUp(character);

            assertThat(character.getLevel()).isEqualTo(3);
            assertThat(character.getMaxHp()).isEqualTo(131);
            assertThat(character.getBaseAtk()).isEqualTo(12);
            assertThat(character.getBaseDef()).isEqualTo(9);
            assertThat(character.getBaseSpd()).isEqualTo(9);
        }

        /**
         * 成長率が整数のタイプ（melee）は同じ式で丸めが働かない。maxHP の上昇分は現在HPへも
         * 加算し、HP欠損量（ここでは70）を維持する。全回復にすると簡略計算の全滅判定が
         * オンラインtickとずれる（tech_offline.md §4.1）。
         *
         * <p>分岐: tech_numeric.md §5 #24,25
         */
        @Test
        @DisplayName("成長率が整数のタイプではmaxHPの上昇分を現在HPへも加算する")
        void test_maxHPの上昇分を現在HPへ加算する() {
            givenType(MELEE);
            // LV1 基礎値（master/character.md §1.2）。HPは70欠損した状態
            Character character = character("melee", 1, 0, 30, 100);
            character.setBaseAtk(10);
            character.setBaseDef(5);
            character.setBaseSpd(5);

            growth().applyLevelUp(character);

            assertThat(character.getMaxHp()).isEqualTo(120);
            assertThat(character.getHp()).isEqualTo(50);
            assertThat(character.getBaseAtk()).isEqualTo(13);
            assertThat(character.getBaseDef()).isEqualTo(7);
            assertThat(character.getBaseSpd()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("SP付与")
    class TestSkillPoint {

        /**
         * レベルアップ1回につきSPを1加算する。
         *
         * <p>分岐: tech_party.md §6 #1
         */
        @Test
        @DisplayName("レベルアップ1回でSPが1増える")
        void test_レベルアップでSPが1増える() {
            givenType(MELEE);
            Character character = character(1, 0);

            growth().applyLevelUp(character);

            assertThat(character.getSkillPoints()).isEqualTo(1);
        }

        /**
         * 同一tickで複数レベル上がったら上がったレベル数だけ加算し、上がらなければ変化しない。
         * 383 で2レベル（LV1→3）、99 では到達しない。
         *
         * <p>分岐: tech_party.md §6 #2,3
         */
        @ParameterizedTest(name = "LV1・EXP0 に +{0} → SP{1}")
        @CsvSource({
            "383, 2", // 2レベル上がる: 上がった数だけ加算する
            " 99, 0", // 到達しない: 変化なし
        })
        void test_上がったレベル数だけSPが増える(long gained, int expectedSkillPoints) {
            givenType(MELEE);
            Character character = character(1, 0);

            growth().addExp(character, gained);

            assertThat(character.getSkillPoints()).isEqualTo(expectedSkillPoints);
        }
    }
}
