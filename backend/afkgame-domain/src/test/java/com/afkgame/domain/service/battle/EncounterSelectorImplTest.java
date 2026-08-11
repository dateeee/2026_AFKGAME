package com.afkgame.domain.service.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.terasoluna.gfw.common.exception.SystemException;

import com.afkgame.domain.service.battle.EncounterSelector.EncounterEntry;

/**
 * {@link EncounterSelectorImpl} の単体テスト（エンカウントの重み付き抽選）。
 *
 * <p>仕様: docs/tech/detail/tech_rng.md §5（分岐一覧）・§1 #7、抽選の位置づけは
 * docs/tech/detail/tech_battle.md §3.2「エンカウント抽選ロジック」。
 * 敵の出現数（Phase 3〜）の分岐は tech_skill.md §8 が持つため本クラスの対象外
 * （Phase 1 は1階1体固定。docs/data/towers/000_テンプレート.md §5）。
 *
 * <p>分岐観点: 累積境界の直前・直後で選ばれる要素が切り替わること、重み合計が0のプール。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code interface EncounterSelector}:
 *       {@code String select(List<EncounterEntry> pool, Random rng)} →
 *       抽選された敵ID。{@code record EncounterEntry(String enemyId, int weight)} は
 *       同インタフェースの入れ子にする（階層データ {@code floorEncounters} を読む部分は
 *       塔マスターの担当＝セグメント②なので、本インタフェースはプールだけを受け取る）</li>
 *   <li>抽選は {@code rng.nextInt(重み合計)} を1回消費し、プールの並び順に重みを累積して
 *       最初に {@code roll < 累積} となった要素を返す（乱数を1回だけ消費する形にそろえる。
 *       tech_rng.md §3「消費順序の固定」）</li>
 *   <li>重み合計が0以下ならマスターデータ不正として {@code SystemException}
 *       （コード {@code INTERNAL_MASTER_DATA_INVALID}）を送出する。
 *       起動時ではなくリクエスト中の検知なので {@code MasterDataException} は使わない
 *       （coding_standards_backend/exception.md §3 #2・#3）。
 *       <b>コード自体が新規</b>のため、製造では docs/tech/basic/tech_error_handling.md の
 *       コード体系（{@code INTERNAL_} プレフィックス）へ追加する</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class EncounterSelectorImplTest {

    private static final String GOBLIN = "enemy_goblin";

    private static final String WOLF = "enemy_wolf";

    /** 重み合計100のプール。累積境界は 30（ゴブリン → ウルフの切り替え点）。 */
    private static final List<EncounterEntry> POOL =
            List.of(new EncounterEntry(GOBLIN, 30), new EncounterEntry(WOLF, 70));

    @Mock
    private Random random;

    private EncounterSelector selector() {
        return new EncounterSelectorImpl();
    }

    @Nested
    @DisplayName("重み付き抽選")
    class TestWeightedSelection {

        /**
         * 各要素が重みの比率どおりに選ばれる。累積境界の直前・直後を与え、
         * 切り替わりが1つずれていない（オフバイワン）ことまで見る。
         *
         * <p>分岐: tech_rng.md §5 #5
         */
        @ParameterizedTest(name = "roll={0} → {1}")
        @CsvSource({
            "0,  enemy_goblin", // 下限
            "29, enemy_goblin", // 累積30の直前
            "30, enemy_wolf",   // 累積境界ちょうど（次の要素へ移る）
            "99, enemy_wolf",   // 上限（重み合計100の直前）
        })
        void test_累積境界で選ばれる要素が切り替わる(int roll, String expected) {
            when(random.nextInt(100)).thenReturn(roll);

            assertThat(selector().select(POOL, random)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("プールの不正")
    class TestInvalidPool {

        /**
         * 重み合計が0のプールは抽選できない。マスターデータの不正として例外にする
         * （0除算・{@code nextInt(0)} の {@code IllegalArgumentException} で落ちると
         * 原因が追えないため、識別できるコードを付ける）。
         * 抽選に入る前に検知するので乱数は消費しない。
         *
         * <p>分岐: tech_rng.md §5 #6
         */
        @Test
        void test_重み合計が0のプールはマスターデータ不正として例外になる() {
            List<EncounterEntry> zeroWeighted =
                    List.of(new EncounterEntry(GOBLIN, 0), new EncounterEntry(WOLF, 0));

            SystemException ex = assertThrows(SystemException.class,
                    () -> selector().select(zeroWeighted, random));

            assertThat(ex.getCode()).isEqualTo("INTERNAL_MASTER_DATA_INVALID");
            verifyNoInteractions(random);
        }
    }
}
