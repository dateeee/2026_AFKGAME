package com.afkgame.domain.service.tower;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.afkgame.domain.masterdata.TowerData;
import com.afkgame.domain.masterdata.TowerModifier;
import com.afkgame.domain.masterdata.Towers;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.model.TowerClearRecord;
import com.afkgame.domain.repository.TowerClearRecordRepository;
import com.afkgame.domain.service.battle.FloorProgression;

/**
 * {@link FloorProgressionImpl} の単体テスト（階クリア後の階進行）。
 *
 * <p>仕様: docs/tech/detail/tech_tower/progress.md §9（判定順序）・§10（分岐一覧）。
 * {@code cap} 式は docs/tech/detail/tech_tower.md §2、状態の定義と不変条件・探索セッションは
 * docs/tech/detail/tech_state.md §1・§3、クリア記録の列は
 * docs/tech/basic/tech_db/player.md §3。
 *
 * <p>本クラスが対象にするのは {@code onEnemyDefeated}（階クリア）だけである。
 * {@code ensureEncounter} はエンカウント抽選（tech_battle.md §3.2）、
 * {@code onPartyWiped} は全滅ペナルティ（tech_state.md §3）で、いずれも分岐一覧の所在が
 * 本書の外にあるため対象外（前者は tick API を作る回、後者はテストリスト作成②-c の担当）。
 *
 * <p>分岐観点: 塔別・通算の最高到達階の更新、最上階クリアの記録、環境効果 {@code recovery} の
 * 適用と上限クランプ、目標階の上限追従（3条件）、目標到達の判定と進行モード、
 * HP閾値撤退の判定（閾値ちょうど・無効化）と撤退モード。
 *
 * <p>階進行は<b>乱数を消費しない</b>（progress.md 前文）。引数の {@link Random} は
 * {@link FloorProgression} の取り決めどおり渡すだけで、固定シードで生成する。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * 分岐一覧に関数シグネチャが無いため、表層を本 Javadoc で定義して製造②へ渡す
 * （.claude/project/test-list.md §3）。②-a が定義済みの表層に<b>別名を新設しない</b>。
 * <ul>
 *   <li>{@code FloorProgressionImpl implements FloorProgression}（継ぎ目の定義は
 *       {@code service.battle}、実装は {@code service.tower}。{@code FloorCatalog} と同じ配置）。
 *       コンストラクタ注入は {@code FloorProgressionImpl(Towers, TowerClearRecordRepository)} で、
 *       {@code ensureEncounter} / {@code onPartyWiped} が要る依存は担当セグメントが足す</li>
 *   <li><b>{@code @Service} は製造②で付ける</b>（{@link Towers} が {@code towers.yml} 未搭載で
 *       {@code @Component} を持たないため。{@code TowerServiceImpl} と同じ理由）</li>
 *   <li>{@link TowerClearRecordRepository} に更新系2件を足した:
 *       {@code save}（初クリアで行を作成）と {@code updateProgress}（{@code cleared} と
 *       {@code highest_floor} の更新）。<b>マッピング XML は製造②で書く</b>。
 *       {@code highest_floor_at} は<b>Phase 5・未実装</b>のため書かない（tech_db/player.md §3）</li>
 *   <li>{@link TowerData} に {@code List<TowerModifier> modifiers} を足した（本テストが最初の
 *       読み手。定義形式は tech_data.md §1.5）。{@code recovery} の判定は {@code type} で行い、
 *       Phase 1〜2 の塔は空リストなので回復の分岐を通らない（profile.md §5 不変条件5・6）</li>
 *   <li><b>{@code effectiveMaxHp} は Phase 1 では {@code maxHp} と同値</b>（装備未装着。
 *       tech_data/game_state.md §1.1.2）。装備込みの実効値を供給するのは装備を載せる
 *       Phase 2〜で、そのとき回復上限とHP閾値の分母を差し替える</li>
 *   <li><b>永続化は呼び出し側（tick）が行う</b>ため {@code PlayerRepository} を注入しない。
 *       本クラスが書き換える {@code players} の列（現在の塔・現在階・目標階・敵情報・
 *       {@code run_gold}・{@code highest_floor}）は {@code updateTickState} の UPDATE に
 *       すべて含まれている</li>
 *   <li><b>セッションのリセットは {@code runGold} だけ</b>を対象にする。tech_state.md §3 の
 *       {@code runItems} / {@code runEquipmentIds} は {@code Player} にまだ列が無く、
 *       ドロップを載せる Phase 2〜で足す。セッションへの<b>加算</b>側（撃破ゴールドの
 *       {@code runGold} 累積）は tech_state.md §5 を持つ②-c の担当で、本クラスは持たない</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FloorProgressionImplTest {

    private static final String PLAYER_ID = "player_001";

    /** Phase 1 の塔。表記の正は製造②の {@code towers.yml}（②-a の申し送り）。 */
    private static final String TOWER_ID = "tower_goblin";

    private static final int TOTAL_FLOORS = 20;

    /** 周回モード（tech_tower/control.md の {@code mode}）。 */
    private static final String AUTO_REPEAT = "auto_repeat";

    private static final String STOP_ON_CLEAR = "stop_on_clear";

    /** HP閾値撤退のしきい値（既定値。tech_db/player.md §1）。 */
    private static final double HP_THRESHOLD = 0.3;

    /** 探索セッション中に獲得済みのゴールド。確定リセットで0になる。 */
    private static final long RUN_GOLD = 120L;

    private static final int MAX_HP = 100;

    /** 環境効果の回復率。{@code 100 × 0.035 = 3.5} で切り捨てが効く値を置く。 */
    private static final double RECOVERY_RATE = 0.035;

    /** 階進行は乱数を消費しないが、引数として渡すため固定シードで作る。 */
    private final Random rng = new Random(20260816L);

    @Mock
    private Towers towers;

    @Mock
    private TowerClearRecordRepository towerClearRecordRepository;

    // モックはコンストラクタへ手渡す（coding_standards_backend/test.md §5 #3）
    private FloorProgression progression() {
        return new FloorProgressionImpl(towers, towerClearRecordRepository);
    }

    /** 環境効果を持たない塔。{@code totalFloors = null} は階数無限（深淵の塔）。 */
    private static TowerData tower(Integer totalFloors) {
        return tower(totalFloors, List.of());
    }

    private static TowerData tower(Integer totalFloors, List<TowerModifier> modifiers) {
        return new TowerData(TOWER_ID, TOWER_ID, "始まりのダンジョン", totalFloors, null,
                List.of(), modifiers);
    }

    /** 階クリア後に発火する回復効果（tech_data.md §1.5 の {@code regen_per_floor}）。 */
    private static TowerModifier recovery(double value) {
        return new TowerModifier("regen_per_floor", "recovery", "floor_clear", value);
    }

    private static TowerClearRecord record(int highestFloor, boolean cleared) {
        TowerClearRecord clearRecord = new TowerClearRecord();
        clearRecord.setId("record_" + TOWER_ID);
        clearRecord.setPlayerId(PLAYER_ID);
        clearRecord.setTowerId(TOWER_ID);
        clearRecord.setCleared(cleared);
        clearRecord.setHighestFloor(highestFloor);
        return clearRecord;
    }

    /**
     * 階クリア直後のプレイヤー（{@code currentFloor} がクリアした階）。
     * 敵は撃破済みなので {@code currentEnemyHp = 0} で場に残っている（tech_battle.md §3.1）。
     */
    private static Player player(int clearedFloor, int targetFloor, String mode) {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setCurrentTowerId(TOWER_ID);
        player.setCurrentFloor(clearedFloor);
        player.setTargetFloor(targetFloor);
        player.setTowerMode(mode);
        player.setHpThreshold(HP_THRESHOLD);
        player.setRunGold(RUN_GOLD);
        player.setCurrentEnemyId("goblin");
        player.setCurrentEnemyHp(0);
        return player;
    }

    private static Character hero(int hp) {
        Character character = new Character();
        character.setId("character_001");
        character.setPlayerId(PLAYER_ID);
        character.setName("勇者");
        character.setHp(hp);
        character.setMaxHp(MAX_HP);
        return character;
    }

    /** 塔マスターと、その塔のクリア記録（{@code null} は未挑戦）を用意する。 */
    private void givenTower(Integer totalFloors, TowerClearRecord clearRecord) {
        givenTower(tower(totalFloors), clearRecord);
    }

    private void givenTower(TowerData towerData, TowerClearRecord clearRecord) {
        when(towers.get(TOWER_ID)).thenReturn(towerData);
        when(towerClearRecordRepository.findByPlayerIdAndTowerId(PLAYER_ID, TOWER_ID))
                .thenReturn(clearRecord);
    }

    /** 書き戻されたクリア記録を捕まえる（未挑戦なら作成、記録があれば更新）。 */
    private TowerClearRecord persistedRecord(boolean hasRecord) {
        ArgumentCaptor<TowerClearRecord> captor = ArgumentCaptor.forClass(TowerClearRecord.class);
        if (hasRecord) {
            verify(towerClearRecordRepository).updateProgress(captor.capture());
        } else {
            verify(towerClearRecordRepository).save(captor.capture());
        }
        return captor.getValue();
    }

    @Nested
    @DisplayName("クリア記録の更新")
    class TestClearRecord {

        /**
         * 塔別の最高到達階を超えたら記録を更新する。記録が無ければ作成してから更新する。
         *
         * <p>分岐: tech_tower/progress.md §10 #1
         */
        @ParameterizedTest(name = "記録あり={0} 到達階={1} → 最高到達階={3}")
        @CsvSource({
            "true,  5, 5, 5",  // 4階まで踏破済みの塔で5階をクリアした
            "false, 1, 1, 1",  // 未挑戦の塔（highestFloor = 0 と比較）で初クリア
        })
        void test_塔別の最高到達階を超えたら記録を更新する(boolean hasRecord, int clearedFloor,
                int targetFloor, int expected) {
            Player player = player(clearedFloor, targetFloor, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, hasRecord ? record(clearedFloor - 1, false) : null);

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(persistedRecord(hasRecord))
                    .extracting(TowerClearRecord::getPlayerId, TowerClearRecord::getTowerId,
                            TowerClearRecord::getHighestFloor)
                    .containsExactly(PLAYER_ID, TOWER_ID, expected);
        }

        /**
         * 既踏の階を再クリアしても記録は変わらない。
         *
         * <p>分岐: tech_tower/progress.md §10 #2
         */
        @Test
        void test_既踏の階を再クリアしても記録を変更しない() {
            Player player = player(5, 5, AUTO_REPEAT);
            TowerClearRecord existing = record(9, false);
            givenTower(TOTAL_FLOORS, existing);

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(existing.getHighestFloor()).isEqualTo(9);
        }

        /**
         * 全塔通算の最高到達階（表示用）も、クリアした階が上回れば更新する。
         *
         * <p>分岐: tech_tower/progress.md §10 #3
         */
        @Test
        void test_通算の最高到達階を超えたら更新する() {
            Player player = player(5, 5, AUTO_REPEAT);
            player.setHighestFloor(3);
            givenTower(TOTAL_FLOORS, record(4, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(player.getHighestFloor()).isEqualTo(5);
        }

        /**
         * 別の塔でより深く潜っていれば、通算の最高到達階は下がらない。
         *
         * <p>分岐: tech_tower/progress.md §10 #4
         */
        @Test
        void test_通算の最高到達階を超えなければ変更しない() {
            Player player = player(5, 5, AUTO_REPEAT);
            player.setHighestFloor(7);
            givenTower(TOTAL_FLOORS, record(9, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(player.getHighestFloor()).isEqualTo(7);
        }

        /**
         * 最上階のボスを討伐したら {@code cleared} を立てる（次の塔の解放条件になる）。
         *
         * <p>分岐: tech_tower/progress.md §10 #5
         */
        @Test
        void test_最上階をクリアしたら踏破済みにする() {
            Player player = player(TOTAL_FLOORS, TOTAL_FLOORS, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(TOTAL_FLOORS - 1, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(persistedRecord(true).isCleared()).isTrue();
        }

        /**
         * 最上階でなければ {@code cleared} は立たない。総階数を持たない塔（深淵の塔）は
         * 何階まで進んでも踏破済みにならない。
         *
         * <p>分岐: tech_tower/progress.md §10 #6
         */
        @ParameterizedTest(name = "総階数={0} クリア階={1} → 踏破済みにしない")
        @CsvSource(nullValues = "null", value = {
            "20,   19",  // 最上階の1つ手前
            "null, 87",  // 深淵の塔（総階数なし。Phase 5〜）は判定対象外
        })
        void test_最上階でなければ踏破済みにしない(Integer totalFloors, int clearedFloor) {
            Player player = player(clearedFloor, clearedFloor, AUTO_REPEAT);
            givenTower(totalFloors, record(clearedFloor - 1, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(persistedRecord(true).isCleared()).isFalse();
        }
    }

    @Nested
    @DisplayName("環境効果の回復")
    class TestRecovery {

        /**
         * {@code recovery} を持つ塔では、階クリア後に生存者を {@code floor(maxHP × value)} 回復する。
         * 上限は {@code effectiveMaxHp}（Phase 1 は {@code maxHp} と同値）。
         *
         * <p>分岐: tech_tower/progress.md §10 #7
         */
        @ParameterizedTest(name = "HP={0} → {1}")
        @CsvSource({
            " 50,  53",  // floor(100 × 0.035) = 3（切り捨て）
            " 99, 100",  // 上限でクランプする
            "  0,   0",  // 戦闘不能のメンバーは対象外（生存者のみ）
        })
        void test_回復効果を持つ塔では階クリア後に生存者が回復する(int hp, int expected) {
            Player player = player(5, 5, AUTO_REPEAT);
            Character character = hero(hp);
            givenTower(tower(TOTAL_FLOORS, List.of(recovery(RECOVERY_RATE))), record(4, false));

            progression().onEnemyDefeated(player, List.of(character), rng);

            assertThat(character.getHp()).isEqualTo(expected);
        }

        /**
         * 環境効果を持たない塔（ダンジョン1）では回復しない。
         *
         * <p>分岐: tech_tower/progress.md §10 #8
         */
        @Test
        void test_回復効果が無ければHPは変わらない() {
            Player player = player(5, 5, AUTO_REPEAT);
            Character character = hero(50);
            givenTower(TOTAL_FLOORS, record(4, false));

            progression().onEnemyDefeated(player, List.of(character), rng);

            assertThat(character.getHp()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("目標階の上限追従")
    class TestTargetFollow {

        /**
         * 新しい階をクリアし、目標階が上限に一致していて、総階数で頭打ちでもなければ、
         * 目標階を新しい上限へ追従させる（実質 +1）。
         *
         * <p>分岐: tech_tower/progress.md §10 #9
         */
        @Test
        void test_3条件が揃えば目標階を新しい上限へ追従させる() {
            // 旧cap = min(4 + 1, 20) = 5 に目標階が一致 → 新cap = min(5 + 1, 20) = 6
            Player player = player(5, 5, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(4, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(player.getTargetFloor()).isEqualTo(6);
        }

        /**
         * 既踏の階を再クリアしただけなら上限は動かないので追従しない。
         *
         * <p>分岐: tech_tower/progress.md §10 #10
         */
        @Test
        void test_既踏の階の再クリアでは追従しない() {
            // 旧cap = min(9 + 1, 20) = 10 に目標階は一致するが、5階は既踏
            Player player = player(5, 10, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(9, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(player.getTargetFloor()).isEqualTo(10);
        }

        /**
         * 目標階を上限より低く設定して周回している場合は、その階を維持する。
         *
         * <p>分岐: tech_tower/progress.md §10 #11
         */
        @Test
        void test_目標階が上限より低ければ追従しない() {
            // 旧cap = 10 に対して目標階5（プレイヤーが意図した周回階）
            Player player = player(5, 5, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(9, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(player.getTargetFloor()).isEqualTo(5);
        }

        /**
         * 総階数で頭打ちなら（新cap = 旧cap）追従しない。
         *
         * <p>分岐: tech_tower/progress.md §10 #12
         */
        @Test
        void test_総階数で頭打ちなら追従しない() {
            // 旧cap = min(19 + 1, 20) = 20、新cap = min(20 + 1, 20) = 20 で変わらない
            Player player = player(TOTAL_FLOORS, TOTAL_FLOORS, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(TOTAL_FLOORS - 1, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(player.getTargetFloor()).isEqualTo(TOTAL_FLOORS);
        }
    }

    @Nested
    @DisplayName("目標到達と進行モード")
    class TestGoalReached {

        /**
         * 次の階が総階数または目標階を超えるなら目標到達として進行モードで分岐する
         * （次階へ送らない）。
         *
         * <p>分岐: tech_tower/progress.md §10 #13
         */
        @ParameterizedTest(name = "到達階={0} 目標階={1} クリア階={2} → 目標到達")
        @CsvSource({
            " 9,  5,  5",  // nextFloor 6 > 目標階5
            "19, 20, 20",  // nextFloor 21 > 総階数20（目標階が最上階の周回）
        })
        void test_次の階が上限か目標階を超えたら目標到達になる(int highestFloor, int targetFloor,
                int clearedFloor) {
            Player player = player(clearedFloor, targetFloor, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(highestFloor, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(player.getCurrentFloor()).isEqualTo(1);
        }

        /**
         * 目標階に届いていなければ目標到達にせず、HP閾値の判定へ進む。
         *
         * <p>分岐: tech_tower/progress.md §10 #14
         */
        @Test
        void test_目標階に届かなければ次階送りへ進む() {
            Player player = player(5, 10, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(9, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            // HPは満タンなので撤退せず、手順6の次階送りに到達する
            assertThat(player.getCurrentFloor()).isEqualTo(6);
            assertThat(player.getCurrentEnemyId()).isNull();
            assertThat(player.getCurrentEnemyHp()).isNull();
        }

        /**
         * 目標到達時の {@code auto_repeat} は1階へ戻して塔に留まり、探索セッションは継続する。
         *
         * <p>分岐: tech_tower/progress.md §10 #15
         */
        @Test
        void test_目標到達時のauto_repeatは1階から周回を続ける() {
            Player player = player(5, 5, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(9, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            assertThat(player.getCurrentTowerId()).isEqualTo(TOWER_ID);
            assertThat(player.getCurrentFloor()).isEqualTo(1);
            assertThat(player.getCurrentEnemyId()).isNull();
            assertThat(player.getCurrentEnemyHp()).isNull();
            // 周回をまたいで累積する（tech_state.md §3）
            assertThat(player.getRunGold()).isEqualTo(RUN_GOLD);
        }

        /**
         * 目標到達時の {@code stop_on_clear} は塔・敵情報をクリアして {@code IDLE} へ戻し、
         * 探索セッションを確定（没収なし）してリセットする。
         *
         * <p>分岐: tech_tower/progress.md §10 #16
         */
        @Test
        void test_目標到達時のstop_on_clearは塔を出てIDLEへ戻る() {
            Player player = player(5, 5, STOP_ON_CLEAR);
            givenTower(TOTAL_FLOORS, record(9, false));

            progression().onEnemyDefeated(player, List.of(hero(MAX_HP)), rng);

            // 塔の対は同時にnull（tech_state.md §1.1）
            assertThat(player.getCurrentTowerId()).isNull();
            assertThat(player.getCurrentFloor()).isNull();
            assertThat(player.getTargetFloor()).isNull();
            assertThat(player.getCurrentEnemyId()).isNull();
            assertThat(player.getCurrentEnemyHp()).isNull();
            assertThat(player.getRunGold()).isZero();
        }
    }

    @Nested
    @DisplayName("HP閾値による撤退")
    class TestHpThresholdRetreat {

        /**
         * 目標未到達でも、パーティのHP合計が閾値を下回れば撤退する。
         * 判定量は在籍パーティ全員の合計で、HP0のメンバーも分母に含む。
         *
         * <p>分岐: tech_tower/progress.md §10 #17
         */
        @Test
        void test_HP合計が閾値を下回れば撤退する() {
            Player player = player(5, 10, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(9, false));

            // Σhp = 25 < Σmax 200 × 0.3 = 60（戦闘不能の1体も分母に入る）
            progression().onEnemyDefeated(player, List.of(hero(0), hero(25)), rng);

            assertThat(player.getRunGold()).isZero();
            assertThat(player.getCurrentFloor()).isEqualTo(1);
        }

        /**
         * 閾値0は撤退の無効化。HPがいくら減っていても次階へ送る。
         *
         * <p>分岐: tech_tower/progress.md §10 #18
         */
        @Test
        void test_閾値0なら撤退の判定をしない() {
            Player player = player(5, 10, AUTO_REPEAT);
            player.setHpThreshold(0);
            givenTower(TOTAL_FLOORS, record(9, false));

            progression().onEnemyDefeated(player, List.of(hero(1)), rng);

            assertThat(player.getCurrentFloor()).isEqualTo(6);
            assertThat(player.getRunGold()).isEqualTo(RUN_GOLD);
        }

        /**
         * 閾値ちょうどは撤退しない（比較は「未満」）。
         *
         * <p>分岐: tech_tower/progress.md §10 #19
         */
        @ParameterizedTest(name = "HP={0} → 次階送り")
        @CsvSource({
            "30",  // 100 × 0.3 = 30 ちょうど（未満でないので撤退しない）
            "31",  // 閾値より上
        })
        void test_HP合計が閾値以上なら次階へ送る(int hp) {
            Player player = player(5, 10, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(9, false));

            progression().onEnemyDefeated(player, List.of(hero(hp)), rng);

            assertThat(player.getCurrentFloor()).isEqualTo(6);
            assertThat(player.getRunGold()).isEqualTo(RUN_GOLD);
        }

        /**
         * 撤退時の {@code auto_repeat} は塔に留まり、1階から新しいセッションを始める。
         *
         * <p>分岐: tech_tower/progress.md §10 #20
         */
        @Test
        void test_撤退時のauto_repeatは1階から新しいセッションを始める() {
            Player player = player(5, 10, AUTO_REPEAT);
            givenTower(TOTAL_FLOORS, record(9, false));

            // Σhp = 29 < 30
            progression().onEnemyDefeated(player, List.of(hero(29)), rng);

            assertThat(player.getCurrentTowerId()).isEqualTo(TOWER_ID);
            assertThat(player.getCurrentFloor()).isEqualTo(1);
            assertThat(player.getCurrentEnemyId()).isNull();
            assertThat(player.getCurrentEnemyHp()).isNull();
            assertThat(player.getRunGold()).isZero();
        }

        /**
         * 撤退時の {@code stop_on_clear} は塔・敵情報をクリアして {@code IDLE} へ戻る。
         *
         * <p>分岐: tech_tower/progress.md §10 #21
         */
        @Test
        void test_撤退時のstop_on_clearは塔を出てIDLEへ戻る() {
            Player player = player(5, 10, STOP_ON_CLEAR);
            givenTower(TOTAL_FLOORS, record(9, false));

            progression().onEnemyDefeated(player, List.of(hero(29)), rng);

            assertThat(player.getCurrentTowerId()).isNull();
            assertThat(player.getCurrentFloor()).isNull();
            assertThat(player.getTargetFloor()).isNull();
            assertThat(player.getCurrentEnemyId()).isNull();
            assertThat(player.getCurrentEnemyHp()).isNull();
            assertThat(player.getRunGold()).isZero();
        }
    }
}
