package com.afkgame.web.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.afkgame.domain.masterdata.ItemData;
import com.afkgame.domain.masterdata.Items;
import com.afkgame.domain.masterdata.MasterDataException;
import com.afkgame.domain.masterdata.MasterDataLoader;
import com.afkgame.env.config.GameSettings;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * 設定値のバインドとマスターデータの起動時ロードが、実際のアプリケーション起動で成立することの統合テスト。
 *
 * <p>仕様: docs/tech/basic/tech_backend.md §4.2「設定値」（{@code afkgame.*} の値）、
 * docs/tech/nonfunctional/tech_operations.md §12.2（環境変数と起動時バリデーション）、
 * docs/backlog/java_migration.md §2「マスターデータ」（不正なら起動を中止する）。
 *
 * <p>コンテキストの起こし方は {@link WebIntegrationTestSupport}。
 */
class MasterDataIntegrationTest extends WebIntegrationTestSupport {

    @Autowired
    private GameSettings gameSettings;

    @Autowired
    private Items items;

    @Test
    @DisplayName("afkgame.properties の afkgame.* が GameSettings へ束縛される")
    void test_ゲーム設定値が束縛される() {
        assertThat(gameSettings.tickIntervalSeconds()).isEqualTo(60);
        assertThat(gameSettings.turnsPerTick()).isEqualTo(3);
        assertThat(gameSettings.offlineEfficiency()).isEqualTo(1.0);
        assertThat(gameSettings.maxOfflineHours()).isEqualTo(24);
        assertThat(gameSettings.fastCalcThreshold()).isEqualTo(100);
        assertThat(gameSettings.maxBattleLogRecords()).isEqualTo(100);
        assertThat(gameSettings.maxLogPerResponse()).isEqualTo(50);
        assertThat(gameSettings.maxPlayerLevel()).isEqualTo(9999);
        assertThat(gameSettings.maxGold()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("BATTLE_RNG_SEED 未設定なら乱数シードは束縛されない")
    void test_乱数シードは既定で未設定() {
        assertThat(gameSettings.battleRngSeed()).isNull();
    }

    @Test
    @DisplayName("起動時にマスターデータの YAML が読み込まれ、不変 Map で公開される")
    void test_マスターデータが起動時に読み込まれる() {
        assertThat(items.all()).containsOnlyKeys("hp_potion");
    }

    @Test
    @DisplayName("マスターデータが不正ならコンテキストの起動に失敗する")
    void test_不正なマスターデータで起動に失敗する() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        MasterDataLoader loader = new MasterDataLoader(validator);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(InvalidItems.class, () -> new InvalidItems(loader));

            assertThatThrownBy(context::refresh)
                    .isInstanceOf(BeansException.class)
                    .hasRootCauseInstanceOf(MasterDataException.class);
        }
    }

    /**
     * {@link Items} と同じく生成時にマスターデータを読み込む Bean（不正な YAML を指す）。
     *
     * <p>本体のコンテキストを壊さずに「起動が中止される」ことだけを確かめるため、検証専用の
     * 空のコンテキストへ {@code registerBean} で直接登録する。
     */
    static class InvalidItems {

        InvalidItems(MasterDataLoader loader) {
            loader.load("masterdata-invalid/items.yml", ItemData.class, ItemData::id);
        }
    }
}
