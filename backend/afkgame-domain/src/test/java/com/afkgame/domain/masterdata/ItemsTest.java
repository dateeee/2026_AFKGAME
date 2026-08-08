package com.afkgame.domain.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * {@link Items} の単体テスト。
 *
 * <p>仕様: docs/data/master/item.md §3.1（ポーション定義）。
 * 実際に配布する {@code masterdata/items.yml} が定義書どおりの値かを固定する
 * （YAML は再ビルドなしで差し替えられるため、値の取り違えをここで検出する）。
 */
@Tag("unit")
class ItemsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("HPポーションを定義書どおりの値で公開する")
    void test_HPポーションを定義書どおりに公開する() {
        Items items = new Items(new MasterDataLoader(VALIDATOR));

        assertThat(items.all()).containsOnlyKeys("hp_potion");
        ItemData potion = items.all().get("hp_potion");
        assertThat(potion.name()).isEqualTo("HPポーション");
        assertThat(potion.category()).isEqualTo("potion");
        assertThat(potion.price()).isEqualTo(25);
        assertThat(potion.healRatio()).isEqualTo(0.20);
        assertThat(potion.stackLimit()).isEqualTo(99);
    }
}
