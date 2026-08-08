package com.afkgame.domain.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * {@link MasterDataLoader} の単体テスト。
 *
 * <p>仕様: docs/backlog/java_migration.md §2「マスターデータ」・§5。
 * マスターデータの不備は**起動時に**すべて例外へ落とし、実行時に欠損の分岐を作らない。
 *
 * <p>分岐観点: リソース不在 / パース失敗 / 空 / スキーマ違反 / ID重複 / 正常。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class MasterDataLoaderTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private final MasterDataLoader loader = new MasterDataLoader(VALIDATOR);

    private Map<String, ItemData> load(String resourcePath) {
        return loader.load(resourcePath, ItemData.class, ItemData::id);
    }

    @Nested
    @DisplayName("load（正常系）")
    class TestLoadSuccess {

        @Test
        @DisplayName("YAML を record へ読み込み、IDをキーにした Map で返す")
        void test_マスターデータをIDキーのMapで返す() {
            Map<String, ItemData> actual = load("masterdata/items.yml");

            assertThat(actual).containsOnlyKeys("hp_potion");
            assertThat(actual.get("hp_potion").id()).isEqualTo("hp_potion");
        }

        @Test
        @DisplayName("返す Map は不変（実行時の書き換えを許さない）")
        void test_返すMapは不変() {
            Map<String, ItemData> actual = load("masterdata/items.yml");

            assertThatThrownBy(() -> actual.put("dummy", actual.get("hp_potion")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("load（異常系: いずれも起動を中止させる）")
    class TestLoadFailure {

        @Test
        @DisplayName("リソースが存在しなければ例外")
        void test_リソース不在で例外() {
            assertThatThrownBy(() -> load("masterdata/not-exists.yml"))
                    .isInstanceOf(MasterDataException.class)
                    .hasMessageContaining("masterdata/not-exists.yml");
        }

        @Test
        @DisplayName("スキーマに無いキーがあれば例外（項目名の誤りを見逃さない）")
        void test_未知のキーで例外() {
            assertThatThrownBy(() -> load("masterdata-invalid/unknown-key.yml"))
                    .isInstanceOf(MasterDataException.class)
                    .hasMessageContaining("masterdata-invalid/unknown-key.yml");
        }

        @Test
        @DisplayName("1件も定義が無ければ例外")
        void test_空のマスターデータで例外() {
            assertThatThrownBy(() -> load("masterdata-invalid/empty.yml"))
                    .isInstanceOf(MasterDataException.class)
                    .hasMessageContaining("masterdata-invalid/empty.yml");
        }

        @Test
        @DisplayName("必須項目が空なら例外（違反した項目名をメッセージに含める）")
        void test_スキーマ違反で例外() {
            assertThatThrownBy(() -> load("masterdata-invalid/blank-name.yml"))
                    .isInstanceOf(MasterDataException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("IDが重複していれば例外（先勝ち・後勝ちで握りつぶさない）")
        void test_ID重複で例外() {
            assertThatThrownBy(() -> load("masterdata-invalid/duplicate-id.yml"))
                    .isInstanceOf(MasterDataException.class)
                    .hasMessageContaining("hp_potion");
        }
    }
}
