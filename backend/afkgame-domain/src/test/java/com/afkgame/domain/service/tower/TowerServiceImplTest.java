package com.afkgame.domain.service.tower;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.terasoluna.gfw.common.exception.BusinessException;
import org.terasoluna.gfw.common.exception.ResourceNotFoundException;
import org.terasoluna.gfw.common.message.ResultMessage;

import com.afkgame.domain.masterdata.TowerData;
import com.afkgame.domain.masterdata.Towers;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.model.TowerClearRecord;
import com.afkgame.domain.repository.CharacterRepository;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.domain.repository.TowerClearRecordRepository;

/**
 * {@link TowerServiceImpl} の単体テスト（塔一覧の組み立てと入塔）。
 *
 * <p>仕様: docs/tech/detail/tech_tower/list.md §5・§6（一覧・解放判定）、
 * docs/tech/detail/tech_tower/select.md §7・§8（入塔）。解放判定・{@code cap} 式・
 * クリア記録の解決規則は docs/tech/detail/tech_tower.md §2、エラーコードは同 §4、
 * 状態の定義と操作可否は docs/tech/detail/tech_state.md §1・§4。
 *
 * <p>階進行（tick内・階クリア後）は tech_tower/progress.md、進行制御（retire / mode /
 * retreat-conditions）は同 control.md が持ち、いずれも本クラスの対象外
 * （テストリスト作成②-b・②-c の担当）。Bean Validation が決める分岐（select.md §8 #1・#2）は
 * {@code TowerSelectResourceTest}（afkgame-web）が持つ。
 *
 * <p>分岐観点: 解放判定（条件なし / 前提塔クリア済み / 未クリア）、クリア記録の有無、
 * 目標階上限の決まり方（開拓側 / 総階数側 / 総階数なし）、入塔の検証順（状態 → 対象 → 権利 →
 * 難易度 → 引数 → 前提）と各検証の通過側。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * 分岐一覧に関数シグネチャが無く、{@code tech_backend.md} §4.1 の service 一覧にも
 * {@code tower/} の内訳が無いため、表層を本 Javadoc で定義して製造②へ渡す
 * （.claude/project/test-list.md §3）。<b>別名の表層を新設しない</b>。
 * <ul>
 *   <li>{@code interface TowerService}: {@code List<TowerInfo> list(String playerId)} と
 *       {@code TowerSelection select(String playerId, TowerSelectCommand command)}。
 *       {@code retire} / {@code mode} / {@code retreat-conditions} は②-c が同じ
 *       インタフェースへ足す</li>
 *   <li>コンストラクタ注入: {@code TowerServiceImpl(Towers, TowerClearRecordRepository,
 *       PlayerRepository, CharacterRepository)}</li>
 *   <li><b>{@code @Service}・{@code @Transactional} は製造②で付ける</b>。{@link Towers} が
 *       {@code towers.yml} 未搭載で {@code @Component} を持たないため、実体が無いまま
 *       スキャンに載せると結合テストのコンテキスト起動が壊れる</li>
 *   <li>{@code record TowerInfo(String id, String name, String dungeonName, Integer totalFloors,
 *       String unlockTowerId, boolean unlocked, boolean cleared, int highestFloor,
 *       int targetFloorCap)}（フィールドの正は tech_tower.md §3）</li>
 *   <li>{@code record TowerSelectCommand(String towerId, int targetFloor, String mode,
 *       String difficulty)}・{@code record TowerSelection(String towerId, int targetFloor)}。
 *       {@code status} は Web 層が付けるため後者は持たない</li>
 *   <li>{@code Towers}（masterdata）に {@code TowerData get(String towerId)}（未定義は null）と
 *       {@code List<TowerData> all()}（TOWERS_OVERVIEW.md の番号順）を用意する。
 *       {@code record TowerData(String id, String name, String dungeonName, Integer totalFloors,
 *       String unlockTowerId, List<String> difficulties)}。{@code totalFloors = null} が
 *       階数無限（深淵の塔）、{@code difficulties} が空でないものがイベントダンジョン。
 *       {@code floorEncounters} は {@code FloorCatalog} を作る回に足す</li>
 *   <li>{@code TowerClearRecord}（Entity・列の正は tech_db/player.md §3）と
 *       {@code TowerClearRecordRepository}: {@code findAllByPlayerId} /
 *       {@code findByPlayerIdAndTowerId}（未挑戦は null）。<b>マッピング XML は製造②で書く</b>。
 *       行の作成・更新は<b>階クリア時</b>のため（tech_tower.md §2）、更新系は②-b が足す</li>
 *   <li>{@code PlayerRepository#updateTowerState(Player)} を足す。{@code updateTickState} を
 *       流用しない — あちらは {@code last_tick_at} も更新するため、入塔で未処理tickを
 *       食い潰してしまう（tech_tick.md §4）</li>
 *   <li><b>行ロック</b>: 検証の前に {@code PlayerRepository#findByIdForUpdate} で
 *       {@code players} 行を占有ロックする（tech_tower.md §1「排他」）。待機超過の
 *       {@code 503 BATTLE_TICK_BUSY} は tick と同じ経路のため本クラスでは検証しない</li>
 *   <li><b>エラーは gfw の例外</b>（coding_standards_backend/exception.md §2.1）。
 *       {@code TOWER_NOT_FOUND} だけ {@code ResourceNotFoundException}、ほかは
 *       {@code BusinessException} に {@code ResultMessages.error().add(code)} でコードだけを載せる。
 *       HTTP ステータスは Web 層の対応表が決めるので<b>ドメインは持たない</b></li>
 *   <li><b>塔IDの表記は未確定</b>。本クラスの {@code tower_goblin} 等は既存テスト
 *       （{@code BattleSimulatorImplTest} ほか）に合わせたフィクスチャ値で、
 *       tech_data.md §1.4 の例は {@code goblin_tower}、endgame.md は {@code abyss_tower} と
 *       食い違う。<b>{@code towers.yml} を書く製造②で表記をそろえ、既存テストの定数も
 *       同じ回で追随させる</b></li>
 *   <li>Phase 5〜 の行（list.md #8・#9、select.md #9・#10）は<b>マスターデータの形だけで
 *       分岐する</b>ように実装する。深淵の塔は {@code totalFloors = null} の null 分岐、
 *       イベントダンジョンは {@code difficulties} が空かどうかの分岐で表し、
 *       Phase 5 の塔そのもの（{@code towers.yml} への登録）は足さない
 *       （profile.md §5 不変条件5「Phase 厳守」・同6「データ駆動」）</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TowerServiceImplTest {

    private static final String PLAYER_ID = "player_001";

    /** Phase 1 の塔（最初から解放・全20階）。 */
    private static final String TOWER_ID = "tower_goblin";

    /** Phase 2 の塔（{@link #TOWER_ID} のボス討伐で解放・全30階）。 */
    private static final String NEXT_TOWER_ID = "tower_forest";

    /** 深淵の塔（Phase 5〜）。IDの正は docs/data/master/endgame.md §18。 */
    private static final String ABYSS_TOWER_ID = "abyss_tower";

    /** イベントダンジョン（Phase 5〜）。マスターデータ未作成のため本クラス内のフィクスチャ値。 */
    private static final String EVENT_TOWER_ID = "event_tower";

    /** イベントダンジョンの難易度（tech_data.md §1.1 のキー体系）。 */
    private static final List<String> DIFFICULTIES = List.of("beginner", "intermediate", "advanced");

    private static final int GOBLIN_TOWER_FLOORS = 20;

    private static final int FOREST_TOWER_FLOORS = 30;

    /** イベントダンジョンの階数（tech_tower/list.md §5 の表で10固定）。 */
    private static final int EVENT_TOWER_FLOORS = 10;

    @Mock
    private Towers towers;

    @Mock
    private TowerClearRecordRepository towerClearRecordRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private CharacterRepository characterRepository;

    // モックはコンストラクタへ手渡す（coding_standards_backend/test.md §5 #3）
    private TowerService service() {
        return new TowerServiceImpl(towers, towerClearRecordRepository, playerRepository,
                characterRepository);
    }

    /** 通常塔（難易度なし）。{@code unlockTowerId} が null なら最初から解放。 */
    private static TowerData tower(String id, Integer totalFloors, String unlockTowerId) {
        return new TowerData(id, id, "始まりのダンジョン", totalFloors, unlockTowerId, List.of());
    }

    /** 難易度別エントリへ展開される塔（イベントダンジョン。Phase 5〜）。 */
    private static TowerData eventTower() {
        return new TowerData(EVENT_TOWER_ID, EVENT_TOWER_ID, "終焉のダンジョン",
                EVENT_TOWER_FLOORS, null, DIFFICULTIES);
    }

    private static TowerClearRecord record(String towerId, boolean cleared, int highestFloor) {
        TowerClearRecord clearRecord = new TowerClearRecord();
        clearRecord.setId("record_" + towerId);
        clearRecord.setPlayerId(PLAYER_ID);
        clearRecord.setTowerId(towerId);
        clearRecord.setCleared(cleared);
        clearRecord.setHighestFloor(highestFloor);
        return clearRecord;
    }

    /** 塔外（{@code IDLE}）のプレイヤー。tech_state.md §1 の判定条件。 */
    private static Player idlePlayer() {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setTowerMode("auto_repeat");
        player.setRunGold(500);
        return player;
    }

    /** 探索中（{@code EXPLORING}）のプレイヤー。 */
    private static Player exploringPlayer() {
        Player player = idlePlayer();
        player.setCurrentTowerId(TOWER_ID);
        player.setCurrentFloor(3);
        player.setTargetFloor(5);
        return player;
    }

    private static Character hero(int hp) {
        Character character = new Character();
        character.setId("character_001");
        character.setPlayerId(PLAYER_ID);
        character.setName("勇者");
        character.setHp(hp);
        character.setMaxHp(100);
        return character;
    }

    private static TowerSelectCommand command(int targetFloor) {
        return new TowerSelectCommand(TOWER_ID, targetFloor, "auto_repeat", null);
    }

    private static List<String> codesOf(BusinessException ex) {
        return ex.getResultMessages().getList().stream().map(ResultMessage::getCode).toList();
    }

    @Nested
    @DisplayName("一覧の解放判定")
    class TestUnlock {

        /**
         * 解放条件を持たない塔は、記録が無くても解放済みで返る。
         *
         * <p>分岐: tech_tower/list.md §6 #1
         */
        @Test
        void test_解放条件が無い塔は常に解放済みになる() {
            when(towers.all()).thenReturn(List.of(tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null)));
            when(towerClearRecordRepository.findAllByPlayerId(PLAYER_ID)).thenReturn(List.of());

            List<TowerInfo> result = service().list(PLAYER_ID);

            assertThat(result).singleElement()
                    .extracting(TowerInfo::id, TowerInfo::unlocked)
                    .containsExactly(TOWER_ID, true);
        }

        /**
         * 前提塔のボスを討伐済み（{@code cleared = true}）なら解放される。
         *
         * <p>分岐: tech_tower/list.md §6 #2
         */
        @Test
        void test_前提塔をクリア済みなら解放される() {
            when(towers.all()).thenReturn(List.of(
                    tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null),
                    tower(NEXT_TOWER_ID, FOREST_TOWER_FLOORS, TOWER_ID)));
            when(towerClearRecordRepository.findAllByPlayerId(PLAYER_ID))
                    .thenReturn(List.of(record(TOWER_ID, true, GOBLIN_TOWER_FLOORS)));

            List<TowerInfo> result = service().list(PLAYER_ID);

            assertThat(result).filteredOn(info -> NEXT_TOWER_ID.equals(info.id()))
                    .singleElement()
                    .returns(true, TowerInfo::unlocked);
        }

        /**
         * 前提塔が未クリアなら未解放。到達しただけ（{@code cleared = false}）でも解放しない。
         *
         * <p>分岐: tech_tower/list.md §6 #3
         */
        @ParameterizedTest(name = "前提塔の記録あり={0} → 未解放")
        @ValueSource(booleans = {
            true,   // 記録はあるがボス未討伐（途中階まで到達しただけ）
            false,  // 記録そのものが無い（未挑戦）
        })
        void test_前提塔が未クリアなら未解放のままになる(boolean hasRecord) {
            when(towers.all()).thenReturn(List.of(
                    tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null),
                    tower(NEXT_TOWER_ID, FOREST_TOWER_FLOORS, TOWER_ID)));
            when(towerClearRecordRepository.findAllByPlayerId(PLAYER_ID)).thenReturn(
                    hasRecord ? List.of(record(TOWER_ID, false, 12)) : List.of());

            List<TowerInfo> result = service().list(PLAYER_ID);

            assertThat(result).filteredOn(info -> NEXT_TOWER_ID.equals(info.id()))
                    .singleElement()
                    .returns(false, TowerInfo::unlocked);
        }
    }

    @Nested
    @DisplayName("一覧のクリア記録と上限")
    class TestListRecord {

        /**
         * 記録がある塔は、その塔の {@code cleared}・{@code highestFloor} をそのまま返す。
         *
         * <p>分岐: tech_tower/list.md §6 #4
         */
        @Test
        void test_記録がある塔は記録の値を返す() {
            when(towers.all()).thenReturn(List.of(tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null)));
            when(towerClearRecordRepository.findAllByPlayerId(PLAYER_ID))
                    .thenReturn(List.of(record(TOWER_ID, true, 17)));

            List<TowerInfo> result = service().list(PLAYER_ID);

            assertThat(result).singleElement()
                    .extracting(TowerInfo::cleared, TowerInfo::highestFloor)
                    .containsExactly(true, 17);
        }

        /**
         * 記録が無い塔（未挑戦）は {@code cleared = false}・{@code highestFloor = 0} で返る。
         *
         * <p>分岐: tech_tower/list.md §6 #5
         */
        @Test
        void test_記録が無い塔は未挑戦として返る() {
            when(towers.all()).thenReturn(List.of(tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null)));
            when(towerClearRecordRepository.findAllByPlayerId(PLAYER_ID)).thenReturn(List.of());

            List<TowerInfo> result = service().list(PLAYER_ID);

            assertThat(result).singleElement()
                    .extracting(TowerInfo::cleared, TowerInfo::highestFloor)
                    .containsExactly(false, 0);
        }

        /**
         * 最上階まで到達していなければ、上限は開拓側（{@code highestFloor + 1}）で決まる。
         *
         * <p>分岐: tech_tower/list.md §6 #6
         */
        @Test
        void test_最上階に未到達なら上限は到達階の次になる() {
            when(towers.all()).thenReturn(List.of(tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null)));
            when(towerClearRecordRepository.findAllByPlayerId(PLAYER_ID))
                    .thenReturn(List.of(record(TOWER_ID, false, 19)));

            List<TowerInfo> result = service().list(PLAYER_ID);

            // highestFloor + 1 = 20 <= totalFloors 20 なので開拓側が採用される
            assertThat(result).singleElement().returns(20, TowerInfo::targetFloorCap);
        }

        /**
         * 最上階へ到達済みなら、上限は総階数でクランプされる（{@code +1} しない）。
         *
         * <p>分岐: tech_tower/list.md §6 #7
         */
        @Test
        void test_最上階へ到達済みなら上限は総階数でクランプされる() {
            when(towers.all()).thenReturn(List.of(tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null)));
            when(towerClearRecordRepository.findAllByPlayerId(PLAYER_ID))
                    .thenReturn(List.of(record(TOWER_ID, true, GOBLIN_TOWER_FLOORS)));

            List<TowerInfo> result = service().list(PLAYER_ID);

            // highestFloor + 1 = 21 > totalFloors 20 なので総階数側が採用される
            assertThat(result).singleElement().returns(GOBLIN_TOWER_FLOORS, TowerInfo::targetFloorCap);
        }

        /**
         * 総階数を持たない塔（深淵の塔）は {@code totalFloors = null} のまま、
         * 上限を {@code highestFloor + 1} だけで決める。
         *
         * <p>分岐: tech_tower/list.md §6 #8
         */
        @Test
        void test_総階数を持たない塔は上限を到達階の次だけで決める() {
            when(towers.all()).thenReturn(List.of(tower(ABYSS_TOWER_ID, null, null)));
            when(towerClearRecordRepository.findAllByPlayerId(PLAYER_ID))
                    .thenReturn(List.of(record(ABYSS_TOWER_ID, false, 87)));

            List<TowerInfo> result = service().list(PLAYER_ID);

            assertThat(result).singleElement()
                    .extracting(TowerInfo::totalFloors, TowerInfo::targetFloorCap)
                    .containsExactly(null, 88);
        }

        /**
         * イベントダンジョンは難易度ごとの独立エントリ3件へ展開し、
         * 記録を {@code {towerId}_{difficulty}} のキーで引く。
         *
         * <p>分岐: tech_tower/list.md §6 #9
         */
        @Test
        void test_イベントダンジョンは難易度別の3エントリへ展開される() {
            when(towers.all()).thenReturn(List.of(eventTower()));
            when(towerClearRecordRepository.findAllByPlayerId(PLAYER_ID))
                    .thenReturn(List.of(record(EVENT_TOWER_ID + "_intermediate", true, EVENT_TOWER_FLOORS)));

            List<TowerInfo> result = service().list(PLAYER_ID);

            assertThat(result).extracting(TowerInfo::id, TowerInfo::cleared).containsExactly(
                    tuple(EVENT_TOWER_ID + "_beginner", false),
                    tuple(EVENT_TOWER_ID + "_intermediate", true),
                    tuple(EVENT_TOWER_ID + "_advanced", false));
        }
    }

    @Nested
    @DisplayName("入塔の検証")
    class TestSelectValidation {

        /**
         * 入塔中（{@code EXPLORING} / {@code IN_BATTLE}）の select は拒否する。
         *
         * <p>分岐: tech_tower/select.md §8 #3
         */
        @Test
        void test_入塔中なら別の塔へ入れない() {
            when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(exploringPlayer());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service().select(PLAYER_ID, command(1)));

            assertThat(codesOf(ex)).containsExactly("TOWER_ALREADY_IN_TOWER");
        }

        /**
         * 塔外（{@code IDLE}）なら状態の検証を通過する。
         *
         * <p>分岐: tech_tower/select.md §8 #4
         */
        @Test
        void test_塔外なら状態の検証を通過する() {
            givenSelectablePlayer(0);

            assertThatCode(() -> service().select(PLAYER_ID, command(1)))
                    .doesNotThrowAnyException();
        }

        /**
         * 塔マスターに無い {@code towerId} は 404 相当の例外にする。
         *
         * <p>分岐: tech_tower/select.md §8 #5
         */
        @Test
        void test_未知の塔IDは見つからない扱いになる() {
            when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(idlePlayer());
            when(towers.get(TOWER_ID)).thenReturn(null);

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> service().select(PLAYER_ID, command(1)));

            assertThat(ex.getResultMessages().getList()).extracting(ResultMessage::getCode)
                    .containsExactly("TOWER_NOT_FOUND");
        }

        /**
         * 塔マスターに存在すれば対象の解決を通過する。
         *
         * <p>分岐: tech_tower/select.md §8 #6
         */
        @Test
        void test_定義済みの塔IDなら対象の解決を通過する() {
            givenSelectablePlayer(0);

            TowerSelection selection = service().select(PLAYER_ID, command(1));

            assertThat(selection.towerId()).isEqualTo(TOWER_ID);
        }

        /**
         * 未解放の塔への select は拒否する。
         *
         * <p>分岐: tech_tower/select.md §8 #7
         */
        @Test
        void test_未解放の塔へは入れない() {
            when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(idlePlayer());
            when(towers.get(NEXT_TOWER_ID))
                    .thenReturn(tower(NEXT_TOWER_ID, FOREST_TOWER_FLOORS, TOWER_ID));
            when(towerClearRecordRepository.findByPlayerIdAndTowerId(PLAYER_ID, TOWER_ID))
                    .thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class, () -> service()
                    .select(PLAYER_ID, new TowerSelectCommand(NEXT_TOWER_ID, 1, "auto_repeat", null)));

            assertThat(codesOf(ex)).containsExactly("TOWER_NOT_UNLOCKED");
        }

        /**
         * 前提塔をクリア済みなら解放の検証を通過する。
         *
         * <p>分岐: tech_tower/select.md §8 #8
         */
        @Test
        void test_解放済みの塔なら権利の検証を通過する() {
            when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(idlePlayer());
            when(towers.get(NEXT_TOWER_ID))
                    .thenReturn(tower(NEXT_TOWER_ID, FOREST_TOWER_FLOORS, TOWER_ID));
            when(towerClearRecordRepository.findByPlayerIdAndTowerId(PLAYER_ID, TOWER_ID))
                    .thenReturn(record(TOWER_ID, true, GOBLIN_TOWER_FLOORS));
            when(towerClearRecordRepository.findByPlayerIdAndTowerId(PLAYER_ID, NEXT_TOWER_ID))
                    .thenReturn(null);
            when(characterRepository.findAllByPlayerId(PLAYER_ID)).thenReturn(List.of(hero(100)));

            assertThatCode(() -> service()
                    .select(PLAYER_ID, new TowerSelectCommand(NEXT_TOWER_ID, 1, "auto_repeat", null)))
                    .doesNotThrowAnyException();
        }

        /**
         * 難易度の指定が塔種別と噛み合わなければ拒否する（イベントで欠落・通常塔で指定）。
         *
         * <p>分岐: tech_tower/select.md §8 #9
         */
        @ParameterizedTest(name = "イベントダンジョン={0} 難易度={1} → 不整合")
        @CsvSource(nullValues = "null", value = {
            "true,  null",      // イベントダンジョンなのに難易度が欠落している
            "false, beginner",  // 通常塔なのに難易度が指定されている
        })
        void test_難易度の指定が塔種別と合わなければ拒否する(boolean event, String difficulty) {
            String towerId = event ? EVENT_TOWER_ID : TOWER_ID;
            when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(idlePlayer());
            when(towers.get(towerId))
                    .thenReturn(event ? eventTower() : tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null));

            BusinessException ex = assertThrows(BusinessException.class, () -> service()
                    .select(PLAYER_ID, new TowerSelectCommand(towerId, 1, "auto_repeat", difficulty)));

            assertThat(codesOf(ex)).containsExactly("TOWER_INVALID_DIFFICULTY");
        }

        /**
         * 難易度の指定が塔種別と噛み合っていれば通過する（イベントで指定・通常塔で省略）。
         * イベントダンジョンの到達記録は難易度を畳み込んだキーで引く。
         *
         * <p>分岐: tech_tower/select.md §8 #10
         */
        @ParameterizedTest(name = "イベントダンジョン={0} 難易度={1} → 続行")
        @CsvSource(nullValues = "null", value = {
            "true,  beginner",  // イベントダンジョンで難易度を指定
            "false, null",      // 通常塔で難易度を省略
        })
        void test_難易度の指定が整合していれば検証を通過する(boolean event, String difficulty) {
            String towerId = event ? EVENT_TOWER_ID : TOWER_ID;
            String recordKey = event ? EVENT_TOWER_ID + "_" + difficulty : TOWER_ID;
            when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(idlePlayer());
            when(towers.get(towerId))
                    .thenReturn(event ? eventTower() : tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null));
            when(towerClearRecordRepository.findByPlayerIdAndTowerId(PLAYER_ID, recordKey))
                    .thenReturn(null);
            when(characterRepository.findAllByPlayerId(PLAYER_ID)).thenReturn(List.of(hero(100)));

            assertThatCode(() -> service()
                    .select(PLAYER_ID, new TowerSelectCommand(towerId, 1, "auto_repeat", difficulty)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("入塔の目標階とパーティ")
    class TestSelectFloorAndParty {

        /**
         * 目標階が上限を超えていれば拒否する。上限が開拓側で決まる場合と総階数側で決まる場合の両方。
         *
         * <p>分岐: tech_tower/select.md §8 #11
         */
        @ParameterizedTest(name = "到達階={0} 目標階={1} → 上限超過")
        @CsvSource({
            " 4, 6",   // cap = highestFloor + 1 = 5（開拓側が上限）
            "20, 21",  // cap = totalFloors = 20（総階数側が上限）
        })
        void test_目標階が上限を超えていれば入れない(int highestFloor, int targetFloor) {
            when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(idlePlayer());
            when(towers.get(TOWER_ID)).thenReturn(tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null));
            when(towerClearRecordRepository.findByPlayerIdAndTowerId(PLAYER_ID, TOWER_ID))
                    .thenReturn(record(TOWER_ID, false, highestFloor));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service().select(PLAYER_ID, command(targetFloor)));

            assertThat(codesOf(ex)).containsExactly("TOWER_INVALID_FLOOR");
        }

        /**
         * 目標階が上限ちょうどなら通過する。上限が開拓側・総階数側のどちらで決まる場合も同じ。
         *
         * <p>分岐: tech_tower/select.md §8 #12
         */
        @ParameterizedTest(name = "到達階={0} 目標階={1} → 続行")
        @CsvSource({
            " 4,  5",  // cap = highestFloor + 1 = 5 ちょうど
            "20, 20",  // cap = totalFloors = 20 ちょうど
        })
        void test_目標階が上限以内なら引数の検証を通過する(int highestFloor, int targetFloor) {
            givenSelectablePlayer(highestFloor);

            TowerSelection selection = service().select(PLAYER_ID, command(targetFloor));

            assertThat(selection.targetFloor()).isEqualTo(targetFloor);
        }

        /**
         * パーティ全員のHPが0なら入塔できない（tech_state.md §4 の入塔前提）。
         *
         * <p>分岐: tech_tower/select.md §8 #13
         */
        @Test
        void test_全員HP0なら入塔できない() {
            when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(idlePlayer());
            when(towers.get(TOWER_ID)).thenReturn(tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null));
            when(towerClearRecordRepository.findByPlayerIdAndTowerId(PLAYER_ID, TOWER_ID))
                    .thenReturn(null);
            when(characterRepository.findAllByPlayerId(PLAYER_ID)).thenReturn(List.of(hero(0)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service().select(PLAYER_ID, command(1)));

            assertThat(codesOf(ex)).containsExactly("TOWER_PARTY_WIPED");
        }

        /**
         * HPが残るキャラが1体でもいれば前提の検証を通過する。
         *
         * <p>分岐: tech_tower/select.md §8 #14
         */
        @Test
        void test_生存キャラが1体でもいれば前提の検証を通過する() {
            when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(idlePlayer());
            when(towers.get(TOWER_ID)).thenReturn(tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null));
            when(towerClearRecordRepository.findByPlayerIdAndTowerId(PLAYER_ID, TOWER_ID))
                    .thenReturn(null);
            when(characterRepository.findAllByPlayerId(PLAYER_ID))
                    .thenReturn(List.of(hero(0), hero(1)));

            assertThatCode(() -> service().select(PLAYER_ID, command(1)))
                    .doesNotThrowAnyException();
        }

        /**
         * 成立時は現在階を1へ、敵情報を null へ、探索セッションをリセットし、HPは変えない。
         *
         * <p>分岐: tech_tower/select.md §8 #15
         */
        @Test
        void test_入塔が成立すると状態を初期化する() {
            Player player = idlePlayer();
            Character survivor = hero(42);
            when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(player);
            when(towers.get(TOWER_ID)).thenReturn(tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null));
            when(towerClearRecordRepository.findByPlayerIdAndTowerId(PLAYER_ID, TOWER_ID))
                    .thenReturn(null);
            when(characterRepository.findAllByPlayerId(PLAYER_ID)).thenReturn(List.of(survivor));

            service().select(PLAYER_ID, command(1));

            assertThat(player.getCurrentTowerId()).isEqualTo(TOWER_ID);
            assertThat(player.getCurrentFloor()).isEqualTo(1);
            assertThat(player.getTargetFloor()).isEqualTo(1);
            assertThat(player.getTowerMode()).isEqualTo("auto_repeat");
            assertThat(player.getCurrentEnemyId()).isNull();
            assertThat(player.getCurrentEnemyHp()).isNull();
            // 探索セッション（run）は入塔でリセットする（tech_state.md §3）
            assertThat(player.getRunGold()).isZero();
            // 入塔で回復しない（select.md §7 手順8）
            assertThat(survivor.getHp()).isEqualTo(42);
            verify(playerRepository).updateTowerState(player);
        }
    }

    /** 通常塔へ入塔できる状態（塔外・解放済み・生存キャラあり）を用意する。 */
    private void givenSelectablePlayer(int highestFloor) {
        when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(idlePlayer());
        when(towers.get(TOWER_ID)).thenReturn(tower(TOWER_ID, GOBLIN_TOWER_FLOORS, null));
        when(towerClearRecordRepository.findByPlayerIdAndTowerId(PLAYER_ID, TOWER_ID))
                .thenReturn(highestFloor == 0 ? null : record(TOWER_ID, false, highestFloor));
        when(characterRepository.findAllByPlayerId(PLAYER_ID)).thenReturn(List.of(hero(100)));
    }
}
