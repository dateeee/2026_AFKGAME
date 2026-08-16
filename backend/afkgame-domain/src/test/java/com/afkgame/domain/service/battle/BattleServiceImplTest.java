package com.afkgame.domain.service.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.terasoluna.gfw.common.exception.BusinessException;
import org.terasoluna.gfw.common.message.ResultMessage;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.repository.CharacterRepository;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.env.config.GameSettings;
import com.afkgame.env.logging.LogKey;
import com.afkgame.env.logging.LogReason;
import com.afkgame.env.logging.LoggerName;

/**
 * {@link BattleServiceImpl} の単体テスト（tick の外枠）。
 *
 * <p>仕様: docs/tech/detail/tech_tick.md §1〜§4（未処理tick数の決定・上限クランプ・同時実行制御・
 * トランザクション境界）、処理方式の切り替えは docs/tech/detail/tech_offline.md §2。
 * 1tick 内の戦闘の中身は本クラスの担当外（tech_battle.md・①-b セグメント）。
 *
 * <p>分岐観点: 経過時間と未処理tick数（0 / 1 / 端数繰り越し）、正規シミュレーションと簡略計算の
 * 切り替え境界（100 / 101）、24時間クランプ（ちょうど / 超過）、時刻の巻き戻し、戦闘を行わない経路
 * （塔外待機・パーティ空）、ロック競合、処理中の例外。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * tick / offline のサービスは backend に存在せず、分岐一覧にも関数シグネチャが無いため、
 * 表層を本 Javadoc で定義して製造へ渡す（.claude/project/test-list.md §3）。
 * <ul>
 *   <li>{@code BattleService#tick(String playerId)} → {@link TickResult}。
 *       実装クラス {@code BattleServiceImpl} に {@code @Transactional} を付け、
 *       1リクエスト = 1トランザクションにする（§4）</li>
 *   <li>コンストラクタ注入:
 *       {@code BattleServiceImpl(PlayerRepository, CharacterRepository, BattleSimulator,
 *       OfflineCalculator, GameSettings, Clock)}。
 *       tick 間隔・24時間上限・簡略計算しきい値は {@link GameSettings} から読む
 *       （profile.md §5 不変条件6「データ駆動」。定数を実装へ埋め込まない）。
 *       現在時刻は {@link Clock} で外から受ける（coding_standards_backend/test.md §3 #1）</li>
 *   <li>{@code record TickResult(int pendingTicks, boolean capped, BattleOutcome outcome,
 *       OfflineSummary offlineSummary)}。戦闘処理を行わなかった経路では
 *       {@code outcome} / {@code offlineSummary} とも {@code null}。
 *       正規シミュレーションなら {@code outcome} のみ、簡略計算なら {@code offlineSummary} のみが入る
 *       （tech_offline.md §2「個別ログは生成せずサマリーのみ返却」）</li>
 *   <li>{@code record BattleOutcome(long gold, long exp)}。個別戦闘ログの要素型は tech_battle.md
 *       （①-b セグメント）で決まるため本記録にはまだ含めない。本クラスは outcome を
 *       <b>素通しするだけ</b>で中身を見ない</li>
 *   <li>{@code interface BattleSimulator}: {@code BattleOutcome simulate(Player, List<Character>, int ticks)}
 *       （正規シミュレーション。tech_battle.md）</li>
 *   <li>{@code interface OfflineCalculator}: {@code OfflineSummary calculate(Player, List<Character>, int ticks)}
 *       と {@code void applyIdleRegen(List<Character> party, int ticks)}（tech_offline.md §4）。
 *       表層の詳細は {@link OfflineCalculatorImplTest} が持つ</li>
 *   <li>{@code PlayerRepository} へ2メソッドを追加する:
 *       {@code Player findByIdForUpdate(String id)}（マッピング XML が {@code SELECT ... FOR UPDATE}
 *       を発行する。§3.1）と {@code void updateTickState(Player player)}（{@code last_tick_at} と
 *       探索状態の一括反映）</li>
 *   <li>{@code LoggerName.BATTLE("afkgame.battle")} を追加する
 *       （名前の正は docs/tech/basic/tech_logging/fields.md「ロガー名体系」）</li>
 *   <li>{@code LogReason.CLOCK_SKEW("clock_skew")} を追加する（§1.1）</li>
 *   <li>エラーコード {@code BATTLE_TICK_BUSY} を {@code afkgame-web} の {@code ErrorCatalog} へ
 *       503 で登録する（tech_api/common.md §「503」）。<b>本クラスはコードだけを見る</b>
 *       （ステータスは Web 層のテストの担当。test-patterns.md「例外・エラーレスポンス」）</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BattleServiceImplTest {

    /** 現在時刻。{@link Clock#fixed} で固定し、経過時間は {@code lastTickAt} を過去へずらして作る。 */
    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");

    private static final String PLAYER_ID = "player_001";

    private static final String TOWER_ID = "goblin_tower";

    /**
     * 設定値は {@code afkgame.properties} の既定と同じ（tech_backend.md §4.2）。
     * tick間隔60秒・最大放置24時間・簡略計算しきい値100。
     */
    private static final GameSettings SETTINGS = new GameSettings(
            60, 3, 1.0, 24, 100, 100, 50, 9999, Long.MAX_VALUE, null);

    /** 正規シミュレーションの戻り値。本クラスは素通しの同一性しか見ない。 */
    private static final BattleOutcome OUTCOME = new BattleOutcome(120L, 45L);

    /** 簡略計算の戻り値。本クラスは素通しの同一性しか見ない。 */
    private static final OfflineSummary SUMMARY =
            new OfflineSummary(9000L, 3400L, List.of("hp_potion"), false, 101, 12);

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private BattleSimulator battleSimulator;

    @Mock
    private OfflineCalculator offlineCalculator;

    // モックはコンストラクタへ手渡す（coding_standards_backend/test.md §5 #3）
    private BattleService service() {
        return new BattleServiceImpl(playerRepository, characterRepository, battleSimulator,
                offlineCalculator, SETTINGS, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 塔を探索中のプレイヤー。{@code lastTickAt} を過去へずらして経過時間を作る。 */
    private Player givenPlayerLastTickedAt(Instant lastTickAt) {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setCurrentTowerId(TOWER_ID);
        player.setCurrentFloor(3);
        player.setTargetFloor(5);
        player.setLastTickAt(lastTickAt);
        when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(player);
        return player;
    }

    private List<Character> givenParty() {
        Character hero = new Character();
        hero.setId("char_001");
        hero.setPlayerId(PLAYER_ID);
        hero.setLevel(1);
        hero.setHp(100);
        hero.setMaxHp(100);
        hero.setBaseAtk(20);
        hero.setBaseDef(10);
        List<Character> party = List.of(hero);
        when(characterRepository.findAllByPlayerId(PLAYER_ID)).thenReturn(party);
        return party;
    }

    @Nested
    @DisplayName("未処理tick数の決定")
    class TestPendingTicks {

        /**
         * 60秒未満は1tickも成立しない。戦闘処理を行わず、基準時刻も動かさない
         * （動かすと経過時間が切り捨てられ、進行が遅れる。§1「端数繰り越しの根拠」）。
         *
         * <p>消化すべきtickが無い以上、簡略計算にも入らない（0周の入口はここで閉じる。
         * tech_offline.md §5 #13「簡略計算は呼び出されない（tick数算定・処理方式選択で除外済み）」）。
         *
         * <p>分岐: tech_tick.md §5 #1
         * <p>分岐: tech_offline.md §5 #13
         */
        @Test
        void test_60秒未満なら未処理tickは0で基準時刻を動かさない() {
            Instant base = NOW.minusSeconds(59);
            Player player = givenPlayerLastTickedAt(base);

            TickResult result = service().tick(PLAYER_ID);

            assertThat(result.pendingTicks()).isZero();
            assertThat(result.outcome()).isNull();
            assertThat(result.offlineSummary()).isNull();
            assertThat(player.getLastTickAt()).isEqualTo(base);
            verify(playerRepository, never()).updateTickState(any());
            verify(battleSimulator, never()).simulate(any(), anyList(), anyInt());
            verify(offlineCalculator, never()).calculate(any(), anyList(), anyInt());
        }

        /**
         * 境界そのもの。60秒ちょうどで1tick成立する（{@code floor(60 / 60) = 1}）。
         *
         * <p>分岐: tech_tick.md §5 #2
         */
        @Test
        void test_60秒ちょうどで1tick成立する() {
            Instant base = NOW.minusSeconds(60);
            Player player = givenPlayerLastTickedAt(base);
            List<Character> party = givenParty();
            when(battleSimulator.simulate(player, party, 1)).thenReturn(OUTCOME);

            TickResult result = service().tick(PLAYER_ID);

            assertThat(result.pendingTicks()).isEqualTo(1);
            assertThat(player.getLastTickAt()).isEqualTo(base.plusSeconds(60));
        }

        /**
         * 端数は切り捨てず繰り越す。119秒なら1tickだけ処理し、余り59秒は次回へ残す
         * （{@code lastTickAt ← lastTickAt + 1 × 60秒}。{@code now} を代入しない。§1）。
         *
         * <p>分岐: tech_tick.md §5 #3
         */
        @Test
        void test_119秒なら1tick処理して59秒を繰り越す() {
            Instant base = NOW.minusSeconds(119);
            Player player = givenPlayerLastTickedAt(base);
            List<Character> party = givenParty();
            when(battleSimulator.simulate(player, party, 1)).thenReturn(OUTCOME);

            TickResult result = service().tick(PLAYER_ID);

            assertThat(result.pendingTicks()).isEqualTo(1);
            // now（= base + 119秒）ではなく base + 60秒。差の59秒が次回の元手になる
            assertThat(player.getLastTickAt()).isEqualTo(base.plusSeconds(60));
            assertThat(player.getLastTickAt()).isNotEqualTo(NOW);
            verify(playerRepository).updateTickState(player);
        }

        /** ログまで検証するため、受け皿の付け外しが要る観点だけを内側へ分ける。 */
        @Nested
        @DisplayName("時刻の巻き戻し")
        class TestClockSkew {

            /** ログ出力そのものを検証するための受け皿（coding_standards_backend/test.md §5 の1）。 */
            private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

            private ch.qos.logback.classic.Logger battleLogger;

            @BeforeEach
            void setUp() {
                logs.start();
                battleLogger = (ch.qos.logback.classic.Logger)
                        LoggerFactory.getLogger(LoggerName.BATTLE.loggerName());
                battleLogger.addAppender(logs);
            }

            @AfterEach
            void tearDown() {
                battleLogger.detachAppender(logs);
                logs.stop();
            }

            /**
             * サーバー時刻の巻き戻し・データ不整合で {@code lastTickAt > now} になった場合、
             * 負のtick数を作らず0として扱い、基準時刻も動かさない。
             * 黙って握りつぶすと原因が追えないため、切り分け用に WARNING を残す（§1.1）。
             *
             * <p>分岐: tech_tick.md §5 #8
             */
            @Test
            void test_基準時刻が未来なら未処理tickを0としWARNINGを残す() {
                Instant base = NOW.plusSeconds(300);
                Player player = givenPlayerLastTickedAt(base);

                TickResult result = service().tick(PLAYER_ID);

                assertThat(result.pendingTicks()).isZero();
                assertThat(player.getLastTickAt()).isEqualTo(base);
                verify(playerRepository, never()).updateTickState(any());

                assertThat(logs.list).hasSize(1);
                ILoggingEvent event = logs.list.get(0);
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMDCPropertyMap())
                        .containsEntry(LogKey.REASON.field(), LogReason.CLOCK_SKEW.value());
            }
        }
    }

    @Nested
    @DisplayName("計算方式の切り替え")
    class TestCalculationMode {

        /**
         * 境界そのもの。しきい値100ちょうどまでは1tickずつの正規シミュレーションで処理する
         * （個別ログを生成する経路）。
         *
         * <p>分岐: tech_tick.md §5 #4
         * <p>分岐: tech_offline.md §5 #1
         */
        @Test
        void test_未処理tickが100なら正規シミュレーションで処理する() {
            Player player = givenPlayerLastTickedAt(NOW.minusSeconds(100 * 60));
            List<Character> party = givenParty();
            when(battleSimulator.simulate(player, party, 100)).thenReturn(OUTCOME);

            TickResult result = service().tick(PLAYER_ID);

            assertThat(result.pendingTicks()).isEqualTo(100);
            assertThat(result.outcome()).isEqualTo(OUTCOME);
            assertThat(result.offlineSummary()).isNull();
            verify(offlineCalculator, never()).calculate(any(), anyList(), anyInt());
        }

        /**
         * しきい値を1超えたら簡略計算へ切り替える。個別ログは作らずサマリーだけを返す
         * （tech_offline.md §2）。
         *
         * <p>分岐: tech_tick.md §5 #5
         * <p>分岐: tech_offline.md §5 #2
         */
        @Test
        void test_未処理tickが101なら簡略計算で処理する() {
            Player player = givenPlayerLastTickedAt(NOW.minusSeconds(101 * 60));
            List<Character> party = givenParty();
            when(offlineCalculator.calculate(player, party, 101)).thenReturn(SUMMARY);

            TickResult result = service().tick(PLAYER_ID);

            assertThat(result.pendingTicks()).isEqualTo(101);
            assertThat(result.offlineSummary()).isEqualTo(SUMMARY);
            assertThat(result.outcome()).isNull();
            verify(battleSimulator, never()).simulate(any(), anyList(), anyInt());
        }
    }

    @Nested
    @DisplayName("24時間の上限クランプ")
    class TestCap {

        /**
         * 境界そのもの。24時間ちょうど（1,440tick）は上限内なので切り詰めない。
         * {@code capped} を立てないため、UI に「24時間分まで計算されました」は出ない（§2）。
         *
         * <p>分岐: tech_tick.md §5 #6
         */
        @Test
        void test_24時間ちょうどは1440tickでcappedにならない() {
            Instant base = NOW.minus(Duration.ofHours(24));
            Player player = givenPlayerLastTickedAt(base);
            List<Character> party = givenParty();
            when(offlineCalculator.calculate(player, party, 1440)).thenReturn(SUMMARY);

            TickResult result = service().tick(PLAYER_ID);

            assertThat(result.pendingTicks()).isEqualTo(1440);
            assertThat(result.capped()).isFalse();
            // 端数が無いため、繰り越し規則で進めた結果がちょうど now と一致する
            assertThat(player.getLastTickAt()).isEqualTo(NOW);
        }

        /**
         * 24時間を超えた分は破棄する。繰り越すと「24時間ごとにログインすれば無限に蓄積できる」
         * ことになり放置上限が無意味になるため、ここだけ §1 の繰り越し規則の例外で
         * {@code lastTickAt ← now} とする（§2）。
         *
         * <p>分岐: tech_tick.md §5 #7
         */
        @Test
        void test_24時間超は1440tickへ切り詰めcappedを立てて超過分を破棄する() {
            Instant base = NOW.minus(Duration.ofHours(25));
            Player player = givenPlayerLastTickedAt(base);
            List<Character> party = givenParty();
            when(offlineCalculator.calculate(player, party, 1440)).thenReturn(SUMMARY);

            TickResult result = service().tick(PLAYER_ID);

            // 生の未処理tickは1,500だが1,440で頭打ちにする
            assertThat(result.pendingTicks()).isEqualTo(1440);
            assertThat(result.capped()).isTrue();
            // 繰り越しなら base + 1,440分（= now の1時間前）になる。超過分を捨てるので now
            assertThat(player.getLastTickAt()).isEqualTo(NOW);
            assertThat(player.getLastTickAt()).isNotEqualTo(base.plus(Duration.ofMinutes(1440)));
        }
    }

    @Nested
    @DisplayName("戦闘を行わない経路")
    class TestNonBattle {

        /**
         * 塔の外で待機している間（{@code currentTowerId} が null）は戦闘せず、
         * HP自然回復だけを適用する（tech_offline.md §4 手順4）。
         *
         * <p>分岐: tech_tick.md §5 #9
         */
        @Test
        void test_塔外待機中は戦闘せずHP自然回復だけを適用する() {
            Instant base = NOW.minusSeconds(5 * 60);
            Player player = givenPlayerLastTickedAt(base);
            player.setCurrentTowerId(null);
            player.setCurrentFloor(null);
            player.setTargetFloor(null);
            List<Character> party = givenParty();

            TickResult result = service().tick(PLAYER_ID);

            assertThat(result.pendingTicks()).isEqualTo(5);
            assertThat(result.outcome()).isNull();
            assertThat(result.offlineSummary()).isNull();
            verify(offlineCalculator).applyIdleRegen(party, 5);
            verify(battleSimulator, never()).simulate(any(), anyList(), anyInt());
            verify(offlineCalculator, never()).calculate(any(), anyList(), anyInt());
            // 待機tickを回復に消化したので基準時刻は進む（進めないと次回に二重で回復する）
            assertThat(player.getLastTickAt()).isEqualTo(base.plusSeconds(300));
        }

        /**
         * パーティが1体もいなければ戦闘は成立しない。状態だけを返す。
         *
         * <p>戦闘も回復も起こらないが、{@code lastTickAt} は §1 の繰り越し規則どおり進める（§5 #10）。
         * 据え置くと未処理tickが際限なく積み上がり、編成を戻した瞬間に §2 のクランプが働いて
         * {@code capped=true}（切り詰めた）を返してしまう。
         *
         * <p>分岐: tech_tick.md §5 #10
         */
        @Test
        void test_パーティが空なら戦闘せず状態だけを返す() {
            Instant base = NOW.minusSeconds(5 * 60);
            Player player = givenPlayerLastTickedAt(base);
            when(characterRepository.findAllByPlayerId(PLAYER_ID)).thenReturn(List.of());

            TickResult result = service().tick(PLAYER_ID);

            assertThat(result.pendingTicks()).isEqualTo(5);
            assertThat(result.outcome()).isNull();
            assertThat(result.offlineSummary()).isNull();
            verify(battleSimulator, never()).simulate(any(), anyList(), anyInt());
            verify(offlineCalculator, never()).calculate(any(), anyList(), anyInt());
            assertThat(player.getLastTickAt()).isEqualTo(base.plusSeconds(300));
        }
    }

    @Nested
    @DisplayName("同時実行制御とトランザクション")
    class TestConcurrency {

        /**
         * 行ロックの待機が {@code lock_timeout}（5秒）を超えると DB が例外を返す。
         * これを握りつぶさず {@code BATTLE_TICK_BUSY} のビジネス例外へ変換する（§3.1）。
         *
         * <p>Service はコードだけを持ち、503 への対応付けは Web 層が決める
         * （test-patterns.md「例外・エラーレスポンス」）。
         *
         * <p>分岐: tech_tick.md §5 #11
         */
        @Test
        void test_行ロックの競合はBATTLE_TICK_BUSYになる() {
            when(playerRepository.findByIdForUpdate(PLAYER_ID))
                    .thenThrow(new CannotAcquireLockException("lock_timeout"));

            BattleService battleService = service();
            BusinessException ex =
                    assertThrows(BusinessException.class, () -> battleService.tick(PLAYER_ID));

            assertThat(ex.getResultMessages().getList())
                    .extracting(ResultMessage::getCode)
                    .containsExactly("BATTLE_TICK_BUSY");
            verify(playerRepository, never()).updateTickState(any());
        }

        /**
         * tick処理中の例外は握りつぶさず伝播させ、{@code @Transactional} に全ロールバックさせる。
         * {@code last_tick_at} が進まないため、次回リクエストで同じ区間を再計算できる（§4）。
         * 「報酬は入ったが基準時刻が進んでいない」部分コミットを作らないための分岐。
         *
         * <p>分岐: tech_tick.md §5 #12
         */
        @Test
        void test_tick処理中の例外は伝播し基準時刻を進めない() {
            Instant base = NOW.minusSeconds(3 * 60);
            Player player = givenPlayerLastTickedAt(base);
            List<Character> party = givenParty();
            when(battleSimulator.simulate(player, party, 3))
                    .thenThrow(new IllegalStateException("敵マスターの取得に失敗"));

            BattleService battleService = service();
            assertThrows(IllegalStateException.class, () -> battleService.tick(PLAYER_ID));

            assertThat(player.getLastTickAt()).isEqualTo(base);
            verify(playerRepository, never()).updateTickState(any());
        }
    }
}
