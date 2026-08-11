package com.afkgame.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.afkgame.domain.masterdata.Enemies;
import com.afkgame.domain.masterdata.EnemyData;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.rng.RandomFactory;
import com.afkgame.env.config.GameSettings;

/**
 * {@link BattleSimulatorImpl} の単体テスト（1tick内のターン処理）。
 *
 * <p>仕様: docs/tech/detail/tech_battle.md §5（1tick内のターン処理）・§3.1 手順1〜2。
 * 乱数源の扱いは docs/tech/detail/tech_rng.md §2。未処理tick数の算定と処理方式の選択は
 * 呼び出し側の {@link BattleServiceImplTest} が持ち、ダメージ計算の中身は
 * {@link DamageCalculatorImplTest}、ターゲット抽選は {@link TargetSelectorImplTest} が持つ。
 *
 * <p>分岐観点: 行動順（同速のタイブレーク / 敵が速い）、行動前の打ち切り判定
 * （両者生存 / 味方全滅 / 敵が場にいない）、リクエストごとの乱数源の独立。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * 分岐一覧に関数シグネチャが無く、{@code tech_backend.md} §4.1 の service 一覧にも
 * {@code BattleService} 以下の内訳が無いため、表層を本 Javadoc で定義して製造へ渡す
 * （.claude/project/test-list.md §3）。
 * <ul>
 *   <li>{@code BattleSimulator#simulate(Player, List<Character>, int ticks)} → {@link BattleOutcome}
 *       は ①-a（{@link BattleServiceImplTest}）が定義済み。<b>別名の表層を新設しない</b></li>
 *   <li>コンストラクタ注入:
 *       {@code BattleSimulatorImpl(TargetSelector, DamageCalculator, FloorProgression, Enemies,
 *       RandomFactory, GameSettings)}。1tickあたりのターン数は {@link GameSettings#turnsPerTick()}
 *       から読む（profile.md §5 不変条件6「データ駆動」）</li>
 *   <li><b>乱数源</b>: {@code simulate} の入口で {@link RandomFactory#create()} を1回だけ呼び、
 *       得た {@link Random} を協調オブジェクトへ<b>メソッド引数で</b>引き渡す（tech_rng.md §2）。
 *       ①-a が定義した {@code BattleServiceImpl} のコンストラクタに {@code RandomFactory} が無いため、
 *       「1リクエスト = 1インスタンス」の生成点は本クラスになる。
 *       静的・共有インスタンスを参照しない（同 §2）</li>
 *   <li>{@code interface DamageCalculator}:
 *       {@code long calculate(int atk, int def, double critRate, DamageDirection direction, Random rng)}。
 *       {@code enum DamageDirection { ALLY_TO_ENEMY, ENEMY_TO_ALLY }}（最低ダメージ保証の下限が
 *       1 と 0 で分かれる。tech_numeric.md §2）</li>
 *   <li>{@code interface TargetSelector}:
 *       {@code Character selectRandom(List<Character> candidates, Random rng)}（生存者から1体）。
 *       Phase 1 は敵1体固定のため、味方側のターゲット決定にだけ使う（tech_battle.md §3.3）</li>
 *   <li>{@code interface FloorProgression}: 階進行の継ぎ目。
 *       {@code void ensureEncounter(Player player, Random rng)}（交戦相手がいなければエンカウント）、
 *       {@code void onEnemyDefeated(Player player, List<Character> party, Random rng)}、
 *       {@code void onPartyWiped(Player player, List<Character> party)}。
 *       <b>中身はセグメント②の担当</b>（tech_tower/progress.md）。本クラスは呼び出しと、
 *       呼び出し後に敵が場から除かれた場合の打ち切りだけを持つ</li>
 *   <li>{@code Enemies}（masterdata）に {@code EnemyData get(String enemyId)} を用意する。
 *       {@code record EnemyData(String id, String name, int level, int hp, int atk, int def,
 *       int spd, long gold, long exp)}（列の正は docs/data/towers/000_テンプレート.md §2）。
 *       既存の {@code Items} と同じ登録レジストリの形にそろえる</li>
 *   <li><b>報酬の付与は本クラスが行う</b>（§3.1 手順2-l）。撃破した敵の {@code gold} を
 *       プレイヤーへ加算し、{@link GameSettings#maxGold()} で<b>飽和</b>させる
 *       （例外を送出しない。tech_numeric.md §3）。EXP の付与は
 *       {@code CharacterGrowth#addExp}（{@link CharacterGrowthImplTest}）へ委譲する。
 *       ①-a の {@code BattleServiceImpl} は {@link BattleOutcome} を素通しするだけで
 *       加算を行わないため、加算点は本クラスになる</li>
 *   <li><b>クリティカル率の供給元が未定</b>: Phase 1 の基礎値5%（docs/design/systems/battle.md
 *       「戦闘ルール」）は {@link GameSettings} にも master data にも鍵が無い。
 *       tech_rng.md §6 が「プレイヤー・敵で共通の定数にしない」を求めているため、
 *       製造では外から与える形（マスターデータまたは設定値）にする。
 *       本クラスは供給元を検証しないので {@code anyDouble()} で受ける</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BattleSimulatorImplTest {

    private static final String PLAYER_ID = "player_001";

    private static final String TOWER_ID = "tower_goblin";

    private static final String ENEMY_ID = "enemy_goblin";

    /**
     * 設定値は {@code afkgame.properties} の既定（tech_backend.md §4.2）と同じだが、
     * <b>1tickあたりのターン数だけ 1 にする</b>（既定は3）。本クラスの観点は
     * 「1ターン内のアクター進行」（tech_battle.md §5 の対象範囲）であり、
     * 3ターン回すと2ターン目以降の行動がアサーションに混ざるため。
     */
    private static final GameSettings SETTINGS = new GameSettings(
            60, 1, 1.0, 24, 100, 100, 50, 9999, Long.MAX_VALUE, null);

    /** ゴールド上限だけを 100 へ落とした設定。飽和の境界を実値で作るために使う。 */
    private static final GameSettings CAPPED_GOLD_SETTINGS = new GameSettings(
            60, 1, 1.0, 24, 100, 100, 50, 9999, 100L, null);

    /** 敵マスター。SPD10 を基準に、味方SPDを 10（同速）/ 9（敵が速い）で振り分ける。 */
    private static final EnemyData GOBLIN =
            new EnemyData(ENEMY_ID, "ゴブリン", 1, 60, 30, 5, 10, 12L, 8L);

    private static final int HERO_ATK = 20;

    private static final int HERO_DEF = 10;

    private static final int HERO_MAX_HP = 100;

    @Mock
    private TargetSelector targetSelector;

    @Mock
    private DamageCalculator damageCalculator;

    @Mock
    private FloorProgression floorProgression;

    @Mock
    private Enemies enemies;

    @Mock
    private RandomFactory randomFactory;

    /** 乱数そのものは協調オブジェクト側で固定するため、ここでは同一性の確認にだけ使う。 */
    private final Random rng = new Random(42);

    // モックはコンストラクタへ手渡す（coding_standards_backend/test.md §5 #3）
    private BattleSimulator simulator() {
        return simulatorWith(SETTINGS);
    }

    private BattleSimulator simulatorWith(GameSettings settings) {
        return new BattleSimulatorImpl(targetSelector, damageCalculator, floorProgression, enemies,
                randomFactory, settings);
    }

    /** 交戦中のプレイヤー（塔内・敵と対峙中）。 */
    private Player givenPlayerInBattle() {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setCurrentTowerId(TOWER_ID);
        player.setCurrentFloor(3);
        player.setTargetFloor(5);
        player.setTowerMode("auto_repeat");
        player.setCurrentEnemyId(ENEMY_ID);
        player.setCurrentEnemyHp(GOBLIN.hp());
        return player;
    }

    private List<Character> givenParty(int spd) {
        Character hero = new Character();
        hero.setId("char_001");
        hero.setPlayerId(PLAYER_ID);
        hero.setLevel(1);
        hero.setHp(HERO_MAX_HP);
        hero.setMaxHp(HERO_MAX_HP);
        hero.setBaseAtk(HERO_ATK);
        hero.setBaseDef(HERO_DEF);
        hero.setBaseSpd(spd);
        return List.of(hero);
    }

    private void givenEnemyMaster() {
        when(enemies.get(ENEMY_ID)).thenReturn(GOBLIN);
        when(randomFactory.create()).thenReturn(rng);
    }

    /** 味方 → 敵の一撃。 */
    private void givenAllyHits(long damage) {
        when(damageCalculator.calculate(eq(HERO_ATK), eq(GOBLIN.def()), anyDouble(),
                eq(DamageDirection.ALLY_TO_ENEMY), same(rng))).thenReturn(damage);
    }

    /** 敵 → 味方の一撃。ターゲット選択を経由するため候補の受け渡しもここで固定する。 */
    private void givenEnemyHits(List<Character> party, long damage) {
        when(targetSelector.selectRandom(party, rng)).thenReturn(party.get(0));
        when(damageCalculator.calculate(eq(GOBLIN.atk()), eq(HERO_DEF), anyDouble(),
                eq(DamageDirection.ENEMY_TO_ALLY), same(rng))).thenReturn(damage);
    }

    @Nested
    @DisplayName("行動順の決定")
    class TestTurnOrder {

        /**
         * SPD降順のソートで同速になった場合は味方を先にする（固定タイブレーク。§3.1 手順1）。
         * 双方の一撃を非致命にして、両者が行動する中での順序だけを見る。
         *
         * <p>分岐: tech_battle.md §5 #1
         */
        @Test
        void test_味方SPDが敵と同じなら味方が先行する() {
            Player player = givenPlayerInBattle();
            List<Character> party = givenParty(GOBLIN.spd());
            givenEnemyMaster();
            givenAllyHits(10L);
            givenEnemyHits(party, 7L);

            simulator().simulate(player, party, 1);

            InOrder inOrder = inOrder(damageCalculator);
            inOrder.verify(damageCalculator).calculate(eq(HERO_ATK), eq(GOBLIN.def()), anyDouble(),
                    eq(DamageDirection.ALLY_TO_ENEMY), same(rng));
            inOrder.verify(damageCalculator).calculate(eq(GOBLIN.atk()), eq(HERO_DEF), anyDouble(),
                    eq(DamageDirection.ENEMY_TO_ALLY), same(rng));
        }

        /**
         * 味方が敵より遅ければ敵が先行する。
         *
         * <p>分岐: tech_battle.md §5 #2
         */
        @Test
        void test_味方SPDが敵より低いなら敵が先行する() {
            Player player = givenPlayerInBattle();
            List<Character> party = givenParty(GOBLIN.spd() - 1);
            givenEnemyMaster();
            givenAllyHits(10L);
            givenEnemyHits(party, 7L);

            simulator().simulate(player, party, 1);

            InOrder inOrder = inOrder(damageCalculator);
            inOrder.verify(damageCalculator).calculate(eq(GOBLIN.atk()), eq(HERO_DEF), anyDouble(),
                    eq(DamageDirection.ENEMY_TO_ALLY), same(rng));
            inOrder.verify(damageCalculator).calculate(eq(HERO_ATK), eq(GOBLIN.def()), anyDouble(),
                    eq(DamageDirection.ALLY_TO_ENEMY), same(rng));
        }
    }

    @Nested
    @DisplayName("行動前の打ち切り判定")
    class TestAbortCheck {

        /**
         * 味方・敵ともに生存していれば、そのアクターの行動を実行する（§3.1 手順2）。
         * 双方のダメージが相手のHPへ反映される。
         *
         * <p>分岐: tech_battle.md §5 #3
         */
        @Test
        void test_双方が生存していれば両者の行動を実行する() {
            Player player = givenPlayerInBattle();
            List<Character> party = givenParty(GOBLIN.spd());
            givenEnemyMaster();
            givenAllyHits(10L);
            givenEnemyHits(party, 7L);

            simulator().simulate(player, party, 1);

            assertThat(player.getCurrentEnemyHp()).isEqualTo(GOBLIN.hp() - 10);
            assertThat(party.get(0).getHp()).isEqualTo(HERO_MAX_HP - 7);
            verify(floorProgression, never()).onEnemyDefeated(any(), anyList(), any());
            verify(floorProgression, never()).onPartyWiped(any(), anyList());
        }

        /**
         * 敵の一撃で味方のHPが0になったら、以降のアクター（＝反撃するはずだった味方）の行動を
         * 行わず全滅処理へ移る（§3.1 手順2-a・手順6）。
         * 死んだ味方が反撃すると「倒されたのに1発返す」ことになり、撤退判定もずれる。
         *
         * <p>味方が行動しない以上、敵HPは減らない。強制撤退そのものの中身は
         * {@code FloorProgression}（セグメント②）の担当なので、呼び出しだけを見る。
         *
         * <p>分岐: tech_battle.md §5 #4
         */
        @Test
        void test_味方のHPが0になったら以降の行動を行わず全滅処理へ移る() {
            Player player = givenPlayerInBattle();
            List<Character> party = givenParty(GOBLIN.spd() - 1);
            givenEnemyMaster();
            givenEnemyHits(party, HERO_MAX_HP);

            simulator().simulate(player, party, 1);

            assertThat(party.get(0).getHp()).isZero();
            assertThat(player.getCurrentEnemyHp()).isEqualTo(GOBLIN.hp());
            verify(damageCalculator, never()).calculate(anyInt(), anyInt(), anyDouble(),
                    eq(DamageDirection.ALLY_TO_ENEMY), any());
            verify(floorProgression).onPartyWiped(player, party);
        }

        /**
         * 撃破済みの敵は同一ターン内に反撃しない（§3.1 手順2-i）。
         * 味方が先行して敵HPを0にしたら、敵の行動は行わない。
         *
         * <p>分岐: tech_battle.md §5 #5
         */
        @Test
        void test_味方の一撃で敵HPが0になったら敵は同一ターン内に反撃しない() {
            Player player = givenPlayerInBattle();
            List<Character> party = givenParty(GOBLIN.spd());
            givenEnemyMaster();
            givenAllyHits(GOBLIN.hp());

            simulator().simulate(player, party, 1);

            assertThat(player.getCurrentEnemyHp()).isZero();
            assertThat(party.get(0).getHp()).isEqualTo(HERO_MAX_HP);
            verify(damageCalculator, never()).calculate(anyInt(), anyInt(), anyDouble(),
                    eq(DamageDirection.ENEMY_TO_ALLY), any());
            verify(floorProgression).onEnemyDefeated(player, party, rng);
        }

        /**
         * 撃破後の階進行（階クリア・1階への再突入・撤退）で敵が場から除かれた直後も、
         * 敵の行動を行わない。敵HPだけを見る判定では、敵ID・敵HPがともに {@code null} に
         * なった状態を捕捉できず {@code NullPointerException} になる（§5 の注記）。
         *
         * <p>場から除く処理そのものはセグメント②の {@code FloorProgression} が持つため、
         * ここではモックに同じ状態変化をさせて打ち切りだけを確かめる。
         *
         * <p>分岐: tech_battle.md §5 #5
         */
        @Test
        void test_階進行で敵が場から除かれたら敵は行動しない() {
            Player player = givenPlayerInBattle();
            List<Character> party = givenParty(GOBLIN.spd());
            givenEnemyMaster();
            givenAllyHits(GOBLIN.hp());
            doAnswer(invocation -> {
                // 目標階に到達して撤退した直後の状態（tech_db/player.md §1 の備考）
                player.setCurrentEnemyId(null);
                player.setCurrentEnemyHp(null);
                return null;
            }).when(floorProgression).onEnemyDefeated(player, party, rng);

            simulator().simulate(player, party, 1);

            assertThat(player.getCurrentEnemyId()).isNull();
            assertThat(player.getCurrentEnemyHp()).isNull();
            assertThat(party.get(0).getHp()).isEqualTo(HERO_MAX_HP);
            verify(damageCalculator, never()).calculate(anyInt(), anyInt(), anyDouble(),
                    eq(DamageDirection.ENEMY_TO_ALLY), any());
        }
    }

    @Nested
    @DisplayName("撃破報酬の付与")
    class TestReward {

        /**
         * 敵撃破のゴールドは上限で<b>飽和</b>させる（加算しても増えず、例外も出さない。
         * tech_numeric.md §3「ゴールド」）。上限に達していなければそのまま加算する。
         * 上限超過を例外にすると、上限到達後はプレイが停止してしまう。
         *
         * <p>上限（{@code MAX_GOLD} = {@code 2^63−1}）そのものでは加算の桁あふれと
         * 飽和の区別がつかないため、設定値を 100 へ落として境界を作る
         * （数値は {@link GameSettings} から読む ＝ データ駆動）。
         *
         * <p>分岐: tech_numeric.md §5 #9,10
         */
        @ParameterizedTest(name = "所持金{0} + 撃破報酬12 → {1}")
        @CsvSource({
            "95, 100", // 上限超過 → 上限で飽和（107にならない）
            "50,  62", // 上限以下 → そのまま加算
        })
        void test_撃破報酬のゴールドは上限で飽和する(long initialGold, long expectedGold) {
            Player player = givenPlayerInBattle();
            player.setGold(initialGold);
            List<Character> party = givenParty(GOBLIN.spd());
            givenEnemyMaster();
            givenAllyHits(GOBLIN.hp());

            simulatorWith(CAPPED_GOLD_SETTINGS).simulate(player, party, 1);

            assertThat(player.getGold()).isEqualTo(expectedGold);
        }
    }

    @Nested
    @DisplayName("乱数源の独立")
    class TestRandomSource {

        /**
         * 乱数源はリクエスト（1回の {@code simulate}）ごとに生成し、その1インスタンスだけを
         * 協調オブジェクトへ引き渡す（tech_rng.md §2）。静的・共有インスタンスを参照していれば
         * 2回目の呼び出しが1回目と同じ乱数源を使い、並行リクエスト間で干渉する。
         *
         * <p>敵を一撃で倒して1ターンを閉じ、1回の {@code simulate} につき
         * ダメージ計算が1回だけ走る形にしている。
         *
         * <p>分岐: tech_rng.md §5 #8
         */
        @Test
        void test_simulateごとに独立した乱数源を生成して引き渡す() {
            Random first = new Random(1);
            Random second = new Random(2);
            when(randomFactory.create()).thenReturn(first, second);
            when(enemies.get(ENEMY_ID)).thenReturn(GOBLIN);
            when(damageCalculator.calculate(eq(HERO_ATK), eq(GOBLIN.def()), anyDouble(),
                    eq(DamageDirection.ALLY_TO_ENEMY), any())).thenReturn((long) GOBLIN.hp());

            BattleSimulator simulator = simulator();
            simulator.simulate(givenPlayerInBattle(), givenParty(GOBLIN.spd()), 1);
            simulator.simulate(givenPlayerInBattle(), givenParty(GOBLIN.spd()), 1);

            verify(randomFactory, times(2)).create();
            verify(damageCalculator).calculate(anyInt(), anyInt(), anyDouble(), any(), same(first));
            verify(damageCalculator).calculate(anyInt(), anyInt(), anyDouble(), any(), same(second));
        }
    }
}
