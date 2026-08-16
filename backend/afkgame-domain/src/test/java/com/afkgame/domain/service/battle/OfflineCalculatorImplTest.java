package com.afkgame.domain.service.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.InventoryItem;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.env.config.GameSettings;

/**
 * {@link OfflineCalculatorImpl} の単体テスト（101tick以上の簡略計算）。
 *
 * <p>仕様: docs/tech/detail/tech_offline.md §4（簡略計算の詳細アルゴリズム）・§4.1（期待値計算式）。
 * 未処理tick数の算定と24時間キャップは tech_tick.md §5 が持ち、
 * 処理方式の選択（§5 #1・#2）と「0周なら呼ばれない」（§5 #13）は
 * 呼び出し側の {@link BattleServiceImplTest} が持つ。
 *
 * <p>分岐観点: 全滅判定（超過 / 以下の境界）、ポーション在庫（あり / 尽きた）、
 * レベルアップ（到達 / 未到達）、目標階の上限追従（一致 / 未満）、
 * 残tick（全滅で破棄 / 消化しきり）、周回数（1周 / 2周以上）。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code OfflineCalculator#calculate(Player player, List<Character> party, int ticks)}
 *       → {@link OfflineSummary}。
 *       {@code record OfflineSummary(long gold, long exp, List<String> itemIds, boolean wiped,
 *       int ticksConsumed, int laps)}。{@code ticksConsumed} は<b>周回に消化したtick数</b>で、
 *       全滅で破棄した残tickは含めない（§4 手順4 の待機扱いへ回す）</li>
 *   <li>{@code OfflineCalculator#applyIdleRegen(List<Character> party, int ticks)}:
 *       §4 手順4 の HP自然回復 {@code HP += (maxHP × 0.02 + DEF × 0.5) × 待機tick数}（maxHP で上限）。
 *       塔外待機の tick（tech_tick.md §5 #9）と、全滅で破棄した残tick（§5 #11）の両方から使う</li>
 *   <li>コンストラクタ注入:
 *       {@code OfflineCalculatorImpl(LapAnalyzer, CharacterGrowth, PlayerRepository, GameSettings)}</li>
 *   <li>{@code interface LapAnalyzer}:
 *       {@code LapAnalysis analyze(Player player, List<Character> party, int potionStock)}。
 *       §4 手順3a〜3c と §4.1 の期待値計算（期待与ダメ・期待被ダメ・回復・ポーション消費モデル）を
 *       ここへ閉じ込め、本クラスは<b>周回ループの制御だけ</b>を持つ。
 *       {@code record LapAnalysis(int ticksPerLap, long netDamagePerLap, int potionsPerLap,
 *       long goldPerLap, long expPerLap, List<String> itemIdsPerLap, int lapsToLevelUp,
 *       boolean clearsNewFloor, int targetFloorCap)}。
 *       {@code expPerLap} は<b>キャラ1体あたり</b>、{@code lapsToLevelUp} は最も早いLVアップまでの
 *       周回数（到達しないなら {@link Integer#MAX_VALUE}）、
 *       {@code targetFloorCap} は {@code min(塔別highestFloor + 1, 総階数)}
 *       （塔別クリア記録の参照はセグメント②の tech_tower.md 側で実装する）</li>
 *   <li>{@code interface CharacterGrowth}: {@code void applyLevelUp(Character character)}。
 *       §4 手順3g のステータス再計算と SP+1 を担う（スキルの自動習得はしない）</li>
 *   <li>ポーション在庫は {@code PlayerRepository#findAllItemsByPlayerId} の
 *       {@code hp_potion} の数量を起点にし、周回ごとに減算する（§4.1「ポーション消費モデル」）</li>
 * </ul>
 *
 * <p>§4.1 の期待値計算式そのもの（base_hit・crit_factor・E_taken・撃破ターン数・消費数）は
 * tech_offline.md §6 へ行を足し、{@link LapAnalyzerImplTest} が持つ。スキル・パッシブ・挑発に
 * 依存する分岐（skill_factor・範囲攻撃・軽減の上限・挑発の按分・回復の差し引き）は §7 で、
 * Phase 1 の編成では到達しないためマーカーを付けていない（Phase 3 の製造で展開する）。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OfflineCalculatorImplTest {

    private static final String PLAYER_ID = "player_001";

    private static final String TOWER_ID = "goblin_tower";

    /** 設定値は {@code afkgame.properties} の既定と同じ（tech_backend.md §4.2）。 */
    private static final GameSettings SETTINGS = new GameSettings(
            60, 3, 1.0, 24, 100, 100, 50, 9999, Long.MAX_VALUE, null);

    /** 1周に必要なtick数。全テストで共通にし、周回数を tick 数から読めるようにする。 */
    private static final int TICKS_PER_LAP = 20;

    /** パーティ合計HP。全滅判定（§5 #3・#4）の境界そのもの。 */
    private static final int PARTY_HP = 300;

    @Mock
    private LapAnalyzer lapAnalyzer;

    @Mock
    private CharacterGrowth characterGrowth;

    @Mock
    private PlayerRepository playerRepository;

    private Player player;

    private Character hero;

    private List<Character> party;

    // モックはコンストラクタへ手渡す（coding_standards_backend/test.md §5 #3）
    private OfflineCalculator calculator() {
        return new OfflineCalculatorImpl(lapAnalyzer, characterGrowth, playerRepository, SETTINGS);
    }

    private Player givenPlayer(int targetFloor) {
        player = new Player();
        player.setId(PLAYER_ID);
        player.setCurrentTowerId(TOWER_ID);
        player.setCurrentFloor(targetFloor);
        player.setTargetFloor(targetFloor);
        player.setHighestFloor(5);
        return player;
    }

    private List<Character> givenParty() {
        hero = new Character();
        hero.setId("char_001");
        hero.setPlayerId(PLAYER_ID);
        hero.setLevel(1);
        hero.setExp(100L);
        hero.setHp(PARTY_HP);
        hero.setMaxHp(PARTY_HP);
        hero.setBaseAtk(20);
        hero.setBaseDef(10);
        hero.setSkillPoints(0);
        party = List.of(hero);
        return party;
    }

    private void givenPotionStock(int quantity) {
        InventoryItem potion = new InventoryItem();
        potion.setId("inv_001");
        potion.setPlayerId(PLAYER_ID);
        potion.setItemId("hp_potion");
        potion.setQuantity(quantity);
        when(playerRepository.findAllItemsByPlayerId(PLAYER_ID)).thenReturn(List.of(potion));
    }

    /** レベルアップの実体（ステータス再計算・SP+1）は {@link CharacterGrowth} の担当。 */
    private void givenLevelUpRaisesSkillPoint() {
        doAnswer(invocation -> {
            Character target = invocation.getArgument(0);
            target.setLevel(target.getLevel() + 1);
            target.setSkillPoints(target.getSkillPoints() + 1);
            return null;
        }).when(characterGrowth).applyLevelUp(any());
    }

    /**
     * 1周回の分析結果。{@code netDamagePerLap} と {@code lapsToLevelUp} 以外は
     * どのテストでも同じ値にし、着目している分岐だけが動くようにする。
     */
    private static LapAnalysis lap(long netDamagePerLap, int lapsToLevelUp) {
        return lap(netDamagePerLap, lapsToLevelUp, false, 6);
    }

    private static LapAnalysis lap(long netDamagePerLap, int lapsToLevelUp,
            boolean clearsNewFloor, int targetFloorCap) {
        return new LapAnalysis(TICKS_PER_LAP, netDamagePerLap, 1, 500L, 200L,
                List.of("iron_sword"), lapsToLevelUp, clearsNewFloor, targetFloorCap);
    }

    @Nested
    @DisplayName("全滅判定")
    class TestWipe {

        /**
         * 1周で受ける純被ダメージがパーティ合計HPを超えたら全滅として打ち切る。
         * 打ち切りの前に §4「全滅時のペナルティ」を適用する
         * （ゴールド・アイテムは今回の探索分をすべて失い、EXP は現在レベル内の蓄積の50%を減らす。
         * レベルダウンはしない。塔からは強制撤退する）。
         *
         * <p>2周ぶんの報酬を積んでから全滅させ、「積んだ報酬が実際に失われる」ことまで見る
         * （1周目で全滅させると gold=0 が素通りしてしまう）。
         *
         * <p>ペナルティの<b>適用順</b>の正は tech_state.md §3（セグメント②）。
         *
         * <p>分岐: tech_offline.md §5 #3
         */
        @Test
        void test_純被ダメがパーティ合計HPを超えたら全滅ペナルティを適用して打ち切る() {
            givenPlayer(4);
            givenParty();
            givenPotionStock(5);
            givenLevelUpRaisesSkillPoint();
            // 1周目・2周目は生存（LVアップで区切る）、3周目の分析で全滅する
            when(lapAnalyzer.analyze(any(), anyList(), anyInt()))
                    .thenReturn(lap(100L, 2), lap(PARTY_HP + 100L, 2));

            OfflineSummary summary = calculator().calculate(player, party, 100);

            assertThat(summary.wiped()).isTrue();
            // 塔探索中に得たゴールドとアイテムはすべて失う（§4 ペナルティ #2・#3）
            assertThat(summary.gold()).isZero();
            assertThat(summary.itemIds()).isEmpty();
            // 100（初期）+ 200 × 2周 = 500 を反映してから50%減算（§4 ペナルティ #1）
            assertThat(hero.getExp()).isEqualTo(250L);
            assertThat(hero.getLevel()).isEqualTo(2); // レベルダウンはしない
            // 強制撤退（§4 ペナルティ #4）
            assertThat(player.getCurrentTowerId()).isNull();
        }

        /**
         * 境界そのもの。純被ダメージがパーティ合計HPと<b>等しい</b>場合は全滅ではない
         * （§4「合計HPを下回る場合」＝ 超えたときだけ全滅。{@code >} であって {@code >=} ではない）。
         *
         * <p>分岐: tech_offline.md §5 #4
         */
        @Test
        void test_純被ダメがパーティ合計HP以下なら周回を継続する() {
            givenPlayer(4);
            givenParty();
            givenPotionStock(5);
            when(lapAnalyzer.analyze(any(), anyList(), anyInt()))
                    .thenReturn(lap(PARTY_HP, Integer.MAX_VALUE));

            OfflineSummary summary = calculator().calculate(player, party, 100);

            assertThat(summary.wiped()).isFalse();
            assertThat(summary.laps()).isEqualTo(5);
            assertThat(player.getCurrentTowerId()).isEqualTo(TOWER_ID);
        }
    }

    @Nested
    @DisplayName("ポーション在庫")
    class TestPotionStock {

        /**
         * 在庫があるうちは周回ごとに消費数を減算し、減った在庫で次の周回を分析する
         * （§4.1「所持数から周回ごとに減算し」）。
         *
         * <p>分岐: tech_offline.md §5 #5
         */
        @Test
        void test_在庫があるうちは周回ごとに消費数を減算する() {
            givenPlayer(4);
            givenParty();
            givenPotionStock(5);
            givenLevelUpRaisesSkillPoint();
            // 1周ごとに区切って分析させ、在庫の受け渡しを観測できるようにする
            when(lapAnalyzer.analyze(any(), anyList(), anyInt())).thenReturn(lap(100L, 1));

            calculator().calculate(player, party, TICKS_PER_LAP * 2);

            // 1周目は在庫5、2周目は消費1を引いた4で分析される
            verify(lapAnalyzer).analyze(player, party, 5);
            verify(lapAnalyzer).analyze(player, party, 4);
        }

        /**
         * 在庫が尽きた後の周回はポーション回復なしで全滅判定する（§4.1）。
         * 回復が無くなったぶん純被ダメージが増え、そこで全滅する経路。
         *
         * <p>分岐: tech_offline.md §5 #6
         */
        @Test
        void test_在庫が尽きたら回復なしで全滅判定する() {
            givenPlayer(4);
            givenParty();
            givenPotionStock(1);
            givenLevelUpRaisesSkillPoint();
            // 在庫1で1周 → 在庫0の分析ではポーション回復が無く純被ダメがHPを超える
            when(lapAnalyzer.analyze(any(), anyList(), anyInt()))
                    .thenReturn(lap(100L, 1), lap(PARTY_HP + 100L, 1));

            OfflineSummary summary = calculator().calculate(player, party, 100);

            verify(lapAnalyzer).analyze(player, party, 0);
            assertThat(summary.wiped()).isTrue();
            assertThat(summary.laps()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("レベルアップ")
    class TestLevelUp {

        /**
         * 周回EXPが必要EXPに達したらステータスを再計算し、以降の周回を新しいステータスで分析する
         * （§4 手順3g）。SPは加算するが、スキルの自動習得はしない。
         *
         * <p>分岐: tech_offline.md §5 #7
         */
        @Test
        void test_必要EXPに到達したらレベルアップを反映して再分析する() {
            givenPlayer(4);
            givenParty();
            givenPotionStock(5);
            givenLevelUpRaisesSkillPoint();
            when(lapAnalyzer.analyze(any(), anyList(), anyInt())).thenReturn(lap(100L, 2));

            calculator().calculate(player, party, TICKS_PER_LAP * 2);

            verify(characterGrowth).applyLevelUp(hero);
            assertThat(hero.getLevel()).isEqualTo(2);
            assertThat(hero.getSkillPoints()).isEqualTo(1);
            // レベルアップ後は据え置きの期待値を使い回さず、新ステータスで分析し直す
            verify(lapAnalyzer, times(2)).analyze(any(), anyList(), anyInt());
        }

        /**
         * 必要EXPに届かなければステータスは据え置き、同じ期待値のまま周回を続ける（§4 手順3d）。
         * 分析のやり直しも起きない。
         *
         * <p>分岐: tech_offline.md §5 #8
         */
        @Test
        void test_必要EXPに未到達ならステータス据え置きで継続する() {
            givenPlayer(4);
            givenParty();
            givenPotionStock(5);
            // 5周ぶんのtickしか無く、LVアップは10周先
            when(lapAnalyzer.analyze(any(), anyList(), anyInt())).thenReturn(lap(100L, 10));

            OfflineSummary summary = calculator().calculate(player, party, TICKS_PER_LAP * 5);

            verify(characterGrowth, never()).applyLevelUp(any());
            assertThat(hero.getLevel()).isEqualTo(1);
            assertThat(summary.laps()).isEqualTo(5);
            verify(lapAnalyzer, times(1)).analyze(any(), anyList(), anyInt());
        }
    }

    @Nested
    @DisplayName("目標階の上限追従")
    class TestTargetFloorFollow {

        /**
         * 目標階が上限（{@code min(塔別highestFloor + 1, 総階数)}）と一致している場合に限り、
         * 新しい階をクリアしたら目標階も +1 する（§4 手順3g・systems/battle.md「目標階設定」）。
         *
         * <p>分岐: tech_offline.md §5 #9
         */
        @Test
        void test_目標階が上限と一致していれば新しい階のクリアで追従する() {
            givenPlayer(6); // 目標階 = 上限6
            givenParty();
            givenPotionStock(5);
            givenLevelUpRaisesSkillPoint();
            when(lapAnalyzer.analyze(any(), anyList(), anyInt()))
                    .thenReturn(lap(100L, 1, true, 6));

            calculator().calculate(player, party, TICKS_PER_LAP);

            assertThat(player.getTargetFloor()).isEqualTo(7);
        }

        /**
         * 目標階が上限より低い場合は、プレイヤーが意図して下げた設定なので追従しない
         * （§4「目標階の固定」）。
         *
         * <p>分岐: tech_offline.md §5 #10
         */
        @Test
        void test_目標階が上限より低ければ追従しない() {
            givenPlayer(4); // 目標階4 < 上限6
            givenParty();
            givenPotionStock(5);
            givenLevelUpRaisesSkillPoint();
            when(lapAnalyzer.analyze(any(), anyList(), anyInt()))
                    .thenReturn(lap(100L, 1, true, 6));

            calculator().calculate(player, party, TICKS_PER_LAP);

            assertThat(player.getTargetFloor()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("残tickの扱い")
    class TestRemainingTicks {

        /**
         * 全滅で打ち切った場合、残りのtickは周回に使わず破棄し、
         * 塔外待機として §4 手順4 のHP自然回復だけを適用する（§4 ペナルティ #5）。
         *
         * <p>回復量 {@code (300 × 0.02 + 10 × 0.5) × 60 = 660} は maxHP を大きく超えるため、
         * 全滅直後のHPがいくつであっても maxHP でクランプされる（§4 手順4「HPはmaxHPを上限とする」）。
         *
         * <p>分岐: tech_offline.md §5 #11
         */
        @Test
        void test_全滅で打ち切ったら残tickを破棄しHP自然回復だけを適用する() {
            givenPlayer(4);
            givenParty();
            givenPotionStock(5);
            givenLevelUpRaisesSkillPoint();
            when(lapAnalyzer.analyze(any(), anyList(), anyInt()))
                    .thenReturn(lap(100L, 2), lap(PARTY_HP + 100L, 2));

            OfflineSummary summary = calculator().calculate(player, party, 100);

            // 2周（40tick）だけ消化し、残り60tickは周回に使わない
            assertThat(summary.laps()).isEqualTo(2);
            assertThat(summary.ticksConsumed()).isEqualTo(TICKS_PER_LAP * 2);
            assertThat(hero.getHp()).isEqualTo(PARTY_HP);
        }

        /**
         * 全滅しなければ、渡されたtickを周回で消化しきって終了する（§4 手順3f・5）。
         *
         * <p>分岐: tech_offline.md §5 #12
         */
        @Test
        void test_全滅しなければ渡されたtickを消化しきる() {
            givenPlayer(4);
            givenParty();
            givenPotionStock(5);
            when(lapAnalyzer.analyze(any(), anyList(), anyInt()))
                    .thenReturn(lap(100L, Integer.MAX_VALUE));

            OfflineSummary summary = calculator().calculate(player, party, TICKS_PER_LAP * 5);

            assertThat(summary.wiped()).isFalse();
            assertThat(summary.ticksConsumed()).isEqualTo(TICKS_PER_LAP * 5);
        }
    }

    @Nested
    @DisplayName("周回ループ")
    class TestLapLoop {

        /**
         * 1周ちょうどで消化しきる場合は、その周回の結果だけを反映する（累積の起点）。
         *
         * <p>分岐: tech_offline.md §5 #14
         */
        @Test
        void test_1周で消化しきったらその周回の結果だけを反映する() {
            givenPlayer(4);
            givenParty();
            givenPotionStock(5);
            when(lapAnalyzer.analyze(any(), anyList(), anyInt()))
                    .thenReturn(lap(100L, Integer.MAX_VALUE));

            OfflineSummary summary = calculator().calculate(player, party, TICKS_PER_LAP);

            assertThat(summary.laps()).isEqualTo(1);
            assertThat(summary.gold()).isEqualTo(500L);
            assertThat(summary.exp()).isEqualTo(200L);
            assertThat(summary.itemIds()).containsExactly("iron_sword");
        }

        /**
         * 2周以上continueする場合は、報酬・EXP・ポーション消費を周回ごとに累積する
         * （1周分を最後に掛けるのではなく、周回ごとに積む）。
         *
         * <p>分岐: tech_offline.md §5 #15
         */
        @Test
        void test_2周以上なら周回ごとに報酬とEXPとポーション消費を累積する() {
            givenPlayer(4);
            givenParty();
            givenPotionStock(5);
            when(lapAnalyzer.analyze(any(), anyList(), anyInt()))
                    .thenReturn(lap(100L, Integer.MAX_VALUE));

            OfflineSummary summary = calculator().calculate(player, party, TICKS_PER_LAP * 3);

            assertThat(summary.laps()).isEqualTo(3);
            assertThat(summary.gold()).isEqualTo(500L * 3);
            assertThat(summary.exp()).isEqualTo(200L * 3);
            assertThat(summary.itemIds()).containsExactly("iron_sword", "iron_sword", "iron_sword");
        }
    }
}
