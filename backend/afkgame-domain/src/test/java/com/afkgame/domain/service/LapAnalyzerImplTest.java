package com.afkgame.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.afkgame.domain.masterdata.EnemyData;
import com.afkgame.domain.masterdata.ItemData;
import com.afkgame.domain.masterdata.Items;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.model.PlayerSettings;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.env.config.GameSettings;

/**
 * {@link LapAnalyzerImpl} の単体テスト（簡略計算の期待値計算式）。
 *
 * <p>仕様: docs/tech/detail/tech_offline.md §4.1（期待値計算式）・§6（分岐一覧）。
 * 周回ループの制御（全滅・在庫・レベルアップ・残tick）は呼び出し側の
 * {@link OfflineCalculatorImplTest} が §5 で持ち、丸めと下限の規約は
 * docs/tech/detail/tech_numeric.md §2・§4。スキル・パッシブ・挑発に依存する分岐は
 * tech_offline.md §7（Phase 1 の編成では到達しないため、テストは Phase 3 の製造で書く）。
 *
 * <p>分岐観点: 期待与ダメージの下限（`base_hit` が1未満 / 1以上）、クリティカル期待値
 * （実効率が0より大きい / 0）、期待被ダメージの下限（負 / 0以上）、撃破ターン数の
 * 切り上げ（割り切れない / 割り切れる）、ポーション消費数（余裕HPを超える / 以下）。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code interface LapAnalyzer}:
 *       {@code LapAnalysis analyze(Player player, List<Character> party, int potionStock)} と
 *       {@code record LapAnalysis(...)} は {@link OfflineCalculatorImplTest} が定義済み。
 *       <b>別名の表層を新設しない</b></li>
 *   <li>コンストラクタ注入: {@code LapAnalyzerImpl(FloorCatalog, StatCalculator, CharacterGrowth,
 *       Items, PlayerRepository, GameSettings)}</li>
 *   <li>{@code interface FloorCatalog}: 階のデータを読む継ぎ目。
 *       {@code List<EnemyData> enemiesOf(String towerId, int floor)}（Phase 1 は1階1体、
 *       Phase 3〜 は1-3体）と {@code int targetFloorCap(Player player)}
 *       （{@code min(塔別highestFloor + 1, 総階数)}）。
 *       <b>中身はセグメント②の担当</b>（tech_tower.md）。本クラスは読み出しだけを持つ</li>
 *   <li>1周回は「1階〜目標階を通しで攻略」（§4 手順3a）。
 *       {@code ticksPerLap = ceil(Σ階の撃破ターン数 ÷ }{@link GameSettings#turnsPerTick()}{@code )}
 *       （丸めの正は tech_numeric.md §2「撃破ターン数・必要tick数」）</li>
 *   <li><b>クリティカル率の合算値の供給元は未定</b>（Phase 1 の基礎5%。
 *       {@link BattleSimulatorImplTest} の申し送りと同じ論点で、決めるのは製造①）。
 *       本クラスは合算値を {@code StatCalculator#effectiveCritRate} へ通した<b>実効値</b>だけを使い、
 *       供給元を知らない（tech_numeric.md §4「確率のクランプは乱数判定の前」）</li>
 *   <li>{@code ATK_eff} は Phase 1 では {@link Character#getBaseAtk()}。装備・バフが入る
 *       Phase 2〜3 で tech_battle.md §3.1.1 の最終ステータス算出へ差し替える</li>
 *   <li>ポーション回復量は {@code Items} の {@code hp_potion} の {@code healRatio} ×
 *       {@code maxHP} を {@code floor}（下限1。tech_numeric.md §2）、自動使用閾値は
 *       {@code PlayerRepository#findSettingsByPlayerId} の {@code potionThreshold}</li>
 *   <li>{@code lapsToLevelUp}（§4 手順3b・3c）は {@code CharacterGrowth} から次レベルまでの
 *       必要EXPを取って求める。到達しないなら {@link Integer#MAX_VALUE}。
 *       レベルアップ側の分岐は §5 #7・#8 が持つため本クラスでは検証しない</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class LapAnalyzerImplTest {

    private static final String PLAYER_ID = "player_001";

    private static final String TOWER_ID = "tower_goblin";

    private static final String POTION_ID = "hp_potion";

    /** 設定値は {@code afkgame.properties} の既定と同じ（tech_backend.md §4.2）。 */
    private static final GameSettings SETTINGS = new GameSettings(
            60, 3, 1.0, 24, 100, 100, 50, 9999, Long.MAX_VALUE, null);

    /** 味方の攻撃力。{@code base_hit = max(20 − DEF_enemy × 0.5, 1)} の左辺。 */
    private static final int HERO_ATK = 20;

    private static final int HERO_MAX_HP = 100;

    /** ポーション自動使用閾値。余裕HP = {@code 100 × (1 − 0.3)} = 70。 */
    private static final double POTION_THRESHOLD = 0.3;

    /** {@code healRatio} 0.3 × maxHP 100 → 1個あたり30回復。 */
    private static final ItemData POTION = new ItemData(POTION_ID, "HPポーション", "consumable", 50, 0.3, 99);

    /** 在庫は全テストで足りる数にし、在庫切れの分岐（§5 #6）を持ち込まない。 */
    private static final int POTION_STOCK = 99;

    @Mock
    private FloorCatalog floorCatalog;

    @Mock
    private StatCalculator statCalculator;

    @Mock
    private CharacterGrowth characterGrowth;

    @Mock
    private Items items;

    @Mock
    private PlayerRepository playerRepository;

    // モックはコンストラクタへ手渡す（coding_standards_backend/test.md §5 #3）
    private LapAnalyzer analyzer() {
        return new LapAnalyzerImpl(floorCatalog, statCalculator, characterGrowth, items,
                playerRepository, SETTINGS);
    }

    private Player givenPlayer() {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setCurrentTowerId(TOWER_ID);
        player.setCurrentFloor(1);
        player.setTargetFloor(1);
        return player;
    }

    private Character givenHero(int baseDef) {
        Character hero = new Character();
        hero.setId("char_001");
        hero.setPlayerId(PLAYER_ID);
        hero.setLevel(1);
        hero.setExp(0L);
        hero.setHp(HERO_MAX_HP);
        hero.setMaxHp(HERO_MAX_HP);
        hero.setBaseAtk(HERO_ATK);
        hero.setBaseDef(baseDef);
        return hero;
    }

    /**
     * 目標階（1階）の敵編成。{@code hp} で撃破ターン数、{@code atk} で期待被ダメージ、
     * {@code def} で {@code base_hit} が動く。
     */
    private void givenFloorEnemy(int hp, int atk, int def) {
        EnemyData enemy = new EnemyData("goblin", "ゴブリン", 1, hp, atk, def, 10, 12L, 8L);
        when(floorCatalog.enemiesOf(TOWER_ID, 1)).thenReturn(List.of(enemy));
    }

    /** 実効クリティカル率。合算値の供給元は製造①で決めるので、実効値だけを与える。 */
    private void givenEffectiveCritRate(double critRate) {
        when(statCalculator.effectiveCritRate(anyDouble())).thenReturn(critRate);
    }

    private void givenPotionSettings() {
        PlayerSettings settings = new PlayerSettings();
        settings.setPlayerId(PLAYER_ID);
        settings.setPotionThreshold(POTION_THRESHOLD);
        when(playerRepository.findSettingsByPlayerId(PLAYER_ID)).thenReturn(settings);
        when(items.all()).thenReturn(Map.of(POTION_ID, POTION));
    }

    @Nested
    @DisplayName("期待与ダメージの下限")
    class TestBaseHitFloor {

        /**
         * 敵のDEFが高くても期待与ダメージは1を下回らない（{@code max(..., 1)}）。
         * 下限が無いと0以下になり、撃破ターン数の除算がゼロ除算・無限周回になる。
         * 与ダメが変われば撃破ターン数が変わり、周回の被ダメ合計に表れる。
         *
         * <p>分岐: tech_offline.md §6 #1,2
         */
        @ParameterizedTest(name = "敵DEF{0} → 純被ダメ{1}")
        @CsvSource({
            "100, 60", // 20 − 50 → 下限1 → 12ターンで撃破 → 5 × 12
            " 20, 10", // 20 − 10 = 10 → 2ターンで撃破 → 5 × 2
        })
        void test_期待与ダメージは1を下回らない(int enemyDef, long expectedNetDamage) {
            Player player = givenPlayer();
            List<Character> party = List.of(givenHero(10));
            givenFloorEnemy(12, 10, enemyDef);
            givenEffectiveCritRate(0.0);
            givenPotionSettings();

            LapAnalysis analysis = analyzer().analyze(player, party, POTION_STOCK);

            assertThat(analysis.netDamagePerLap()).isEqualTo(expectedNetDamage);
        }
    }

    @Nested
    @DisplayName("クリティカル期待値")
    class TestCritFactor {

        /**
         * クリティカルは発生確率を倍率へ均した {@code crit_factor = 1 + crit_rate × 0.5} で織り込む
         * （簡略計算は乱数を使わない。tech_rng.md §4）。実効率0なら倍率1.0で期待与ダメージを変えない。
         *
         * <p>分岐: tech_offline.md §6 #3,4
         */
        @ParameterizedTest(name = "実効クリティカル率{0} → 純被ダメ{1}")
        @CsvSource({
            "0.4, 50", // base_hit 10 × 1.2 = 12 → 10ターンで撃破 → 5 × 10
            "0.0, 60", // base_hit 10 × 1.0 = 10 → 12ターンで撃破 → 5 × 12
        })
        void test_クリティカル率を倍率へ均して期待与ダメージへ乗じる(double critRate, long expectedNetDamage) {
            Player player = givenPlayer();
            List<Character> party = List.of(givenHero(10));
            givenFloorEnemy(120, 10, 20);
            givenEffectiveCritRate(critRate);
            givenPotionSettings();

            LapAnalysis analysis = analyzer().analyze(player, party, POTION_STOCK);

            assertThat(analysis.netDamagePerLap()).isEqualTo(expectedNetDamage);
        }
    }

    @Nested
    @DisplayName("期待被ダメージの下限")
    class TestTakenFloor {

        /**
         * 味方のDEFが敵のATKを上回っても被ダメージは0で止める（負にして回復にしない）。
         * 敵→味方の下限は0（味方→敵の1とは別。tech_numeric.md §2）。
         *
         * <p>分岐: tech_offline.md §6 #5,6
         */
        @ParameterizedTest(name = "味方DEF{0} → 純被ダメ{1}")
        @CsvSource({
            "60,  0", // 10 − 30 → 負 → 0 として合算
            "10, 15", // 10 −  5 = 5 → 3ターン分
        })
        void test_期待被ダメージは0を下回らない(int heroDef, long expectedNetDamage) {
            Player player = givenPlayer();
            List<Character> party = List.of(givenHero(heroDef));
            givenFloorEnemy(24, 10, 20);
            givenEffectiveCritRate(0.0);
            givenPotionSettings();

            LapAnalysis analysis = analyzer().analyze(player, party, POTION_STOCK);

            assertThat(analysis.netDamagePerLap()).isEqualTo(expectedNetDamage);
        }
    }

    @Nested
    @DisplayName("撃破ターン数")
    class TestTurnsToClear {

        /**
         * 撃破ターン数は {@code ceil}（tech_numeric.md §2）。切り捨てると
         * 残りHPのある敵を倒したことにしてしまい、周回の被ダメージを過小評価する。
         *
         * <p>分岐: tech_offline.md §6 #7,8
         */
        @ParameterizedTest(name = "敵HP{0} → 純被ダメ{1}")
        @CsvSource({
            "21, 15", // 21 ÷ 10 = 2.1 → 切り上げて3ターン
            "20, 10", // 20 ÷ 10 = 2   → そのまま2ターン
        })
        void test_割り切れない撃破ターン数は切り上げる(int enemyHp, long expectedNetDamage) {
            Player player = givenPlayer();
            List<Character> party = List.of(givenHero(10));
            givenFloorEnemy(enemyHp, 10, 20);
            givenEffectiveCritRate(0.0);
            givenPotionSettings();

            LapAnalysis analysis = analyzer().analyze(player, party, POTION_STOCK);

            assertThat(analysis.netDamagePerLap()).isEqualTo(expectedNetDamage);
        }
    }

    @Nested
    @DisplayName("ポーション消費数")
    class TestPotionsPerLap {

        /**
         * ポーションは自動使用閾値を割ってから使う。余裕HP（{@code Σ maxHP × (1 − 閾値)} = 70）に
         * 収まる周回では消費0とし、超えた分だけを回復量30で割って切り上げる。
         * 余裕HPを見ずに消費させると、在庫が実際より早く尽きて全滅判定（§5 #6）が前倒しになる。
         *
         * <p>分岐: tech_offline.md §6 #9,10
         */
        @ParameterizedTest(name = "敵HP{0} → 消費数{1}")
        @CsvSource({
            "200, 1", // 20ターン → 純被ダメ100 → (100 − 70) ÷ 30 = 1
            "120, 0", // 12ターン → 純被ダメ 60 → 余裕HP以下なので使わない
        })
        void test_余裕HPを超えた分だけポーションを消費する(int enemyHp, int expectedPotions) {
            Player player = givenPlayer();
            List<Character> party = List.of(givenHero(10));
            givenFloorEnemy(enemyHp, 10, 20);
            givenEffectiveCritRate(0.0);
            givenPotionSettings();

            LapAnalysis analysis = analyzer().analyze(player, party, POTION_STOCK);

            assertThat(analysis.potionsPerLap()).isEqualTo(expectedPotions);
        }
    }
}
