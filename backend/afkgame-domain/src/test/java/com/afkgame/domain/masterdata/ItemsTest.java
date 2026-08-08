package com.afkgame.domain.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private final MasterDataLoader loader = new MasterDataLoader(VALIDATOR);

    @Test
    @DisplayName("HPポーションを定義書どおりの値で公開する")
    void test_HPポーションを定義書どおりに公開する() {
        Items items = new Items(loader);

        assertThat(items.all()).containsOnlyKeys("hp_potion");
        ItemData potion = items.all().get("hp_potion");
        assertThat(potion.name()).isEqualTo("HPポーション");
        assertThat(potion.category()).isEqualTo("potion");
        assertThat(potion.price()).isEqualTo(25);
        assertThat(potion.healRatio()).isEqualTo(0.20);
        assertThat(potion.stackLimit()).isEqualTo(99);
    }

    /**
     * 定義済みアイテムは1種のみで、未知のIDは contains が false を返す。
     *
     * <p>{@code contains} の真・偽の両側を押さえる（未知アイテムの検出は
     * {@link InitialPlayer} の構築時検証が使う）。
     */
    @Test
    @DisplayName("定義済みアイテムは1種のみで、未知IDは contains が false を返す")
    void test_定義済みは1種のみ() {
        Items actual = new Items(loader);

        assertThat(actual.contains("hp_potion")).isTrue();
        assertThat(actual.contains("unknown_item")).isFalse();
    }

    /**
     * 読み込み元が無ければ起動を中止する（マスターデータの欠損を実行時へ持ち込まない）。
     *
     * <p>分岐一覧の行ではなく、レジストリ共通の横断検証（{@code CharacterTypes} と同じ扱い）。
     */
    @Test
    @DisplayName("リソースが存在しなければ例外（起動を中止する）")
    void test_リソース不在で例外() {
        assertThatThrownBy(() -> new Items(loader, "masterdata/not-exists.yml"))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("masterdata/not-exists.yml");
    }
}
