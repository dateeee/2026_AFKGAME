package com.afkgame.domain.service.tower;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.terasoluna.gfw.common.exception.SystemException;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;

/**
 * {@link TowerStateValidatorImpl} の単体テスト（進行状態の不変条件）。
 *
 * <p>仕様: docs/tech/detail/tech_state.md §1.1（不変条件）・§5 #4（分岐一覧）。
 * 状態の判定条件は同 §1、例外の分類と応答への変換は
 * docs/process/coding_standards_backend/exception.md §1・§3 #2・§4。
 *
 * <p>分岐観点: §1.1 の6条件のうち<b>プレイヤーとパーティだけで判定できるもの</b>
 * （塔の対・敵の対・敵の従属・現在階の下限・HPの上下限）に違反した状態と、
 * すべて満たした状態。
 *
 * <p><b>ERRORログはここで検証しない。</b> 分類2（システム例外）のログはハンドラが
 * 1回だけ出すのが規約で（exception.md §5 #1「分類1は送出元で WARN、分類2・3はハンドラで
 * ERROR。同じ例外を両方で出さない」）、送出元のドメインで {@code ListAppender} を張ると
 * その規約に反する実装を要求してしまう。ERRORログ + 500 の応答は
 * {@code ApiExceptionHandlerTest} が既に押さえており、本クラスは<b>送出</b>だけを見る
 * （{@code EncounterSelectorImplTest} と同じ形）。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * 分岐一覧に関数シグネチャが無いため、表層を本 Javadoc で定義して製造②へ渡す
 * （.claude/project/test-list.md §3）。既存の表層に<b>別名を新設しない</b>。
 * <ul>
 *   <li>{@code interface TowerStateValidator}:
 *       {@code void validate(Player player, List<Character> party)}。実装は
 *       {@code TowerStateValidatorImpl}（どちらも {@code domain.service.tower}）。
 *       <b>依存を持たない</b>ため、呼ぶ側（ゲーム状態の応答構築・tick）がそのまま注入できる</li>
 *   <li><b>{@code @Service} は製造②で付ける</b>（{@code TowerServiceImpl}・
 *       {@code FloorProgressionImpl} とスキャンへ載る単位をそろえる。結合テストの
 *       コンテキスト起動を壊さないための順序）</li>
 *   <li>違反時は {@code SystemException}（gfw。exception.md §2.1）を送出する。
 *       <b>コード {@code INTERNAL_STATE_INVARIANT_VIOLATED} は新規</b>のため、製造では
 *       docs/tech/basic/tech_error_handling.md の「エラーコード体系」へ追加する
 *       （{@code INTERNAL_} は応答に出ずログの {@code error_code} にだけ残るので、
 *       {@code ErrorCatalog} へは登録しない＝同ファイルの注記）。メッセージには
 *       どの条件が壊れたかを特定できる識別子を入れる（exception.md §3 #2）</li>
 *   <li><b>{@code targetFloor ≤ min(塔別 highestFloor + 1, 塔の総階数)} は本クラスの対象外</b>。
 *       上限の算出に塔マスターとクリア記録が要り、依存なしという設計と両立しない。
 *       同じ {@code cap} 式を既に持つ {@code FloorProgressionImpl}（tech_tower.md §2）側で
 *       閉じるか、上限を引数で受ける形にするかは製造②の判断とし、決めた側へ
 *       tech_state.md §1.1 から参照を張る</li>
 *   <li><b>排他（{@code bossRush.active} と {@code currentTowerId}）も対象外</b>。
 *       ボスラッシュは Phase 5 で、{@code Player} に対応する列がまだ無い（tech_state.md §1）</li>
 * </ul>
 */
@Tag("unit")
class TowerStateValidatorImplTest {

    private static final String PLAYER_ID = "player_001";

    private static final String TOWER_ID = "tower_goblin";

    private static final String ENEMY_ID = "enemy_goblin";

    /** 不変条件違反を表すエラーコード（申し送りのとおり製造②で仕様へ登録する）。 */
    private static final String INVARIANT_VIOLATED = "INTERNAL_STATE_INVARIANT_VIOLATED";

    private static final int MAX_HP = 100;

    private TowerStateValidator validator() {
        return new TowerStateValidatorImpl();
    }

    /** 進行状態を列の値から組み立てる（塔外は塔の3列が同時に null になる）。 */
    private static Player player(String towerId, Integer currentFloor, Integer targetFloor,
            String enemyId, Integer enemyHp) {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setCurrentTowerId(towerId);
        player.setCurrentFloor(currentFloor);
        player.setTargetFloor(targetFloor);
        player.setCurrentEnemyId(enemyId);
        player.setCurrentEnemyHp(enemyHp);
        return player;
    }

    private static List<Character> party(int hp) {
        Character character = new Character();
        character.setId("character_001");
        character.setPlayerId(PLAYER_ID);
        character.setName("勇者");
        character.setHp(hp);
        character.setMaxHp(MAX_HP);
        return List.of(character);
    }

    @Nested
    @DisplayName("不変条件の違反")
    class TestViolation {

        /**
         * 不変条件（§1.1）を破ったデータを読んだら、データ不整合として
         * {@code SystemException} を送出する。黙って補正して処理を続けない。
         *
         * <p>Phase 1 の列で表現できる5条件を等価クラスとして並べる。
         * {@code effectiveMaxHp} は Phase 1 では {@code maxHp} と同値
         * （装備未装着。tech_data/game_state.md §1.1.2）。
         *
         * <p>分岐: tech_state.md §5 #4
         */
        @ParameterizedTest(name = "{5}")
        @CsvSource(nullValues = "null", value = {
            // 塔の対: 3列は同時に null か同時に非 null（現在階だけが欠けている）
            "tower_goblin, null, 5,    null,         null, 塔IDがあるのに現在階がない",
            // 敵の対: 敵IDと残HPは同時に null か同時に非 null
            "tower_goblin, 3,    5,    enemy_goblin, null, 敵IDがあるのに残HPがない",
            // 敵の従属: 敵がいるなら塔にいる
            "null,         null, null, enemy_goblin, 30,   塔外なのに敵がいる",
            // 階の範囲: 1 ≤ currentFloor
            "tower_goblin, 0,    5,    null,         null, 現在階が1未満",
        })
        void test_不変条件に違反した進行状態は例外になる(String towerId, Integer currentFloor,
                Integer targetFloor, String enemyId, Integer enemyHp, String description) {
            Player player = player(towerId, currentFloor, targetFloor, enemyId, enemyHp);

            SystemException ex = assertThrows(SystemException.class,
                    () -> validator().validate(player, party(50)));

            assertThat(ex.getCode()).isEqualTo(INVARIANT_VIOLATED);
        }

        /**
         * HPは {@code 0 ≤ hp ≤ effectiveMaxHp} の範囲に収まる。上下どちらへ外れても
         * データ不整合として扱う（回復・被弾の計算が破綻した痕跡のため補正しない）。
         *
         * <p>分岐: tech_state.md §5 #4
         */
        @ParameterizedTest(name = "HP={0}")
        @CsvSource({
            "101",  // 実効最大値（100）を1超える
            " -1",  // 下限を1下回る
        })
        void test_HPが範囲外のキャラがいれば例外になる(int hp) {
            Player player = player(TOWER_ID, 3, 5, null, null);

            SystemException ex = assertThrows(SystemException.class,
                    () -> validator().validate(player, party(hp)));

            assertThat(ex.getCode()).isEqualTo(INVARIANT_VIOLATED);
        }
    }

    @Nested
    @DisplayName("不変条件を満たす状態")
    class TestValidState {

        /**
         * すべての条件を満たしていれば何もしない。境界（HP0 と HP上限ちょうど）も
         * 正常側に含む。
         *
         * <p>「違反したら落とす」だけを書くと、常に落とす実装でもテストが通ってしまうため
         * 正常側を対にして置く。
         *
         * <p>分岐: tech_state.md §5 #4
         */
        @ParameterizedTest(name = "HP={0}")
        @CsvSource({
            "  0",  // 全滅直後（下限ちょうど）
            "100",  // 上限ちょうど
        })
        void test_不変条件を満たす進行状態は素通りする(int hp) {
            Player player = player(TOWER_ID, 3, 5, ENEMY_ID, 30);

            assertThatCode(() -> validator().validate(player, party(hp)))
                    .doesNotThrowAnyException();
        }

        /**
         * 塔外（{@code IDLE}）は塔・敵の列がすべて null で、これも不変条件を満たす。
         *
         * <p>分岐: tech_state.md §5 #4
         */
        @Test
        void test_塔外で全列がnullなら素通りする() {
            Player player = player(null, null, null, null, null);

            assertThatCode(() -> validator().validate(player, party(MAX_HP)))
                    .doesNotThrowAnyException();
        }
    }
}
