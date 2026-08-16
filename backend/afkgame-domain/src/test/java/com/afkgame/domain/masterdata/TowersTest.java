package com.afkgame.domain.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * {@link Towers} の単体テスト。
 *
 * <p>仕様: docs/data/towers/TOWERS_OVERVIEW.md「塔一覧」と
 * docs/data/towers/001_ゴブリンの塔.md §1（基本情報）。
 * 実際に配布する {@code masterdata/towers.yml} が定義書どおりの値かを固定する
 * （YAML は再ビルドなしで差し替えられるため、値の取り違えをここで検出する）。
 */
@Tag("unit")
class TowersTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private final MasterDataLoader loader = new MasterDataLoader(VALIDATOR);

    @Test
    @DisplayName("ゴブリンの塔を定義書どおりの値で公開する")
    void test_ゴブリンの塔を定義書どおりに公開する() {
        TowerData actual = new Towers(loader).get("goblin_tower");

        assertThat(actual).isNotNull();
        assertThat(actual.name()).isEqualTo("ゴブリンの塔");
        assertThat(actual.dungeonName()).isEqualTo("始まりのダンジョン");
        assertThat(actual.totalFloors()).isEqualTo(20);
        assertThat(actual.unlockTowerId()).isNull();
        assertThat(actual.difficulties()).isEmpty();
        assertThat(actual.modifiers()).isEmpty();
    }

    /**
     * 一覧は YAML の記載順で返す（塔一覧APIがこの順で応答する。tech_tower/list.md §5 手順2）。
     *
     * <p>Phase 1 で載せるのはゴブリンの塔だけで、Phase 2 以降の塔は該当 Phase で足す
     * （profile.md §5 不変条件5「Phase 厳守」）。
     */
    @Test
    @DisplayName("all は TOWERS_OVERVIEW の番号順で Phase 1 の塔を返す")
    void test_allは定義順で返す() {
        assertThat(new Towers(loader).all())
                .extracting(TowerData::id)
                .containsExactly("goblin_tower");
    }

    /**
     * 未定義の塔IDは {@code null} を返す（{@code TOWER_NOT_FOUND} の判定に使う）。
     *
     * <p>{@code CharacterTypes#get} と同じレジストリの形で、例外は投げない。
     */
    @Test
    @DisplayName("未定義の塔IDは null を返す")
    void test_未定義の塔IDはnull() {
        assertThat(new Towers(loader).get("unknown_tower")).isNull();
    }

    /**
     * 読み込み元が無ければ起動を中止する（マスターデータの欠損を実行時へ持ち込まない）。
     *
     * <p>分岐一覧の行ではなく、レジストリ共通の横断検証（{@link Items} と同じ扱い）。
     */
    @Test
    @DisplayName("リソースが存在しなければ例外（起動を中止する）")
    void test_リソース不在で例外() {
        assertThatThrownBy(() -> new Towers(loader, "masterdata/not-exists.yml"))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("masterdata/not-exists.yml");
    }
}
