package com.afkgame.web.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.ActiveProfiles;

import com.afkgame.domain.masterdata.ItemData;
import com.afkgame.domain.masterdata.Items;
import com.afkgame.domain.masterdata.MasterDataException;
import com.afkgame.domain.masterdata.MasterDataLoader;
import com.afkgame.env.config.GameProperties;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * 設定値のバインドとマスターデータの起動時ロードが、実際のアプリケーション起動で成立することの統合テスト。
 *
 * <p>仕様: docs/tech/basic/tech_structure.md §4「設定値」（{@code afkgame.*} の値）、
 * docs/tech/nonfunctional/tech_operations.md §12.2（環境変数と起動時バリデーション）、
 * docs/backlog/java_migration.md §5「マスターデータ」（不正なら起動を中止する）。
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class MasterDataIntegrationTest {

    @Autowired
    private GameProperties gameProperties;

    @Autowired
    private Items items;

    @Test
    @DisplayName("application.yml の afkgame.* が GameProperties へ束縛される")
    void test_ゲーム設定値が束縛される() {
        assertThat(gameProperties.tickIntervalSeconds()).isEqualTo(60);
        assertThat(gameProperties.turnsPerTick()).isEqualTo(3);
        assertThat(gameProperties.offlineEfficiency()).isEqualTo(1.0);
        assertThat(gameProperties.maxOfflineHours()).isEqualTo(24);
        assertThat(gameProperties.fastCalcThreshold()).isEqualTo(100);
        assertThat(gameProperties.maxBattleLogRecords()).isEqualTo(100);
        assertThat(gameProperties.maxLogPerResponse()).isEqualTo(50);
        assertThat(gameProperties.maxPlayerLevel()).isEqualTo(9999);
        assertThat(gameProperties.maxGold()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("BATTLE_RNG_SEED 未設定なら乱数シードは束縛されない")
    void test_乱数シードは既定で未設定() {
        assertThat(gameProperties.battleRngSeed()).isNull();
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

        new ApplicationContextRunner()
                .withBean(InvalidItems.class, () -> new InvalidItems(loader))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(MasterDataException.class));
    }

    /**
     * {@link Items} と同じく生成時にマスターデータを読み込む Bean（不正な YAML を指す）。
     *
     * <p>{@code @Configuration} を入れ子にすると {@code @SpringBootTest} がそちらを
     * テスト本体の構成として採用してしまうため、Bean は {@code withBean} で直接登録する。
     */
    static class InvalidItems {

        InvalidItems(MasterDataLoader loader) {
            loader.load("masterdata-invalid/items.yml", ItemData.class, ItemData::id);
        }
    }
}
