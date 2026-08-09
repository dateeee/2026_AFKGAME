package com.afkgame.domain.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * タイプ別 LV1 基礎ステータスのマスターデータ（{@code CharacterTypes}）の単体テスト。
 *
 * <p>数値の正は docs/data/master/character.md §1.2、参照関係の正は
 * docs/tech/detail/tech_auth.md §8.1。{@code character_types.yml} は
 * <strong>LV1 基礎値のみ</strong>を持ち、成長率はレベルアップ機能の移植時に追加する
 * （成長率のキーを足すと {@link MasterDataLoader} の未知キー検出で起動が止まる）。
 *
 * <p><strong>製造工程への申し送り（本テストが要求する表層）</strong>:
 * <ul>
 * <li>{@code record CharacterTypeData(String id, int hp, int atk, int def, int spd)}
 *     — {@code id} は {@code @NotBlank}、ステータス4種は {@code @Positive}</li>
 * <li>{@code @Component class CharacterTypes} — 既定の読み込み元は
 *     {@code masterdata/character_types.yml}。公開 API は
 *     {@code Map<String, CharacterTypeData> all()} と {@code boolean contains(String typeId)}</li>
 * <li>テストから異常系フィクスチャを読ませるため、リソースパスを受け取る
 *     <strong>パッケージプライベートのコンストラクタ</strong>
 *     {@code CharacterTypes(MasterDataLoader loader, String resourcePath)} を用意し、
 *     公開コンストラクタ {@code CharacterTypes(MasterDataLoader loader)} はそれへ委譲する</li>
 * </ul>
 */
@Tag("unit")
class CharacterTypesTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private final MasterDataLoader loader = new MasterDataLoader(VALIDATOR);

    private CharacterTypes characterTypes() {
        return new CharacterTypes(loader);
    }

    /**
     * 4タイプそれぞれの LV1 基礎値を、マスターの数値どおりに公開する。
     *
     * <p>初期キャラのタイプ（melee）が実在することが分岐 #3 の前提であり、
     * 引ける値がキャラ作成時のステータスになる（tech_auth.md §8.2 手順4）。
     *
     * <p>分岐: tech_auth.md §8.3 #3
     */
    @ParameterizedTest(name = "{0}: HP{1} ATK{2} DEF{3} SPD{4}")
    @CsvSource({
            "melee, 100, 10, 5, 5",   // 近接型。初期キャラ hero_001 のタイプ
            "magic,  80, 12, 4, 7",   // 魔力型
            "holy,   95,  8, 5, 6",   // 神聖型
            "agile,  85, 10, 4, 10",  // 敏捷型
    })
    @DisplayName("タイプ別 LV1 基礎ステータスを master/character.md §1.2 どおりに公開する")
    void test_タイプ別のLV1基礎値を公開する(String typeId, int hp, int atk, int def, int spd) {
        CharacterTypeData actual = characterTypes().all().get(typeId);

        assertThat(actual).as("タイプ %s が定義されていること", typeId).isNotNull();
        assertThat(actual.hp()).isEqualTo(hp);
        assertThat(actual.atk()).isEqualTo(atk);
        assertThat(actual.def()).isEqualTo(def);
        assertThat(actual.spd()).isEqualTo(spd);
    }

    /**
     * 定義されているタイプは4種ちょうどで、それ以外は存在しないと答える。
     *
     * <p>{@code contains} の真・偽の両側を押さえる（未知タイプの検出は分岐 #4 が使う）。
     *
     * <p>分岐: tech_auth.md §8.3 #3
     */
    @Test
    @DisplayName("定義済みタイプは4種のみで、未知タイプは contains が false を返す")
    void test_定義済みは4種のみ() {
        CharacterTypes actual = characterTypes();

        assertThat(actual.all()).containsOnlyKeys("melee", "magic", "holy", "agile");
        assertThat(actual.contains("melee")).isTrue();
        assertThat(actual.contains("unknown_type")).isFalse();
    }

    /**
     * 読み込み元が無ければ起動を中止する（マスターデータの欠損を実行時へ持ち込まない）。
     *
     * <p>分岐一覧の行ではなく、レジストリ共通の横断検証（{@code Items} と同じ扱い）。
     */
    @Test
    @DisplayName("リソースが存在しなければ例外（起動を中止する）")
    void test_リソース不在で例外() {
        assertThatThrownBy(() -> new CharacterTypes(loader, "masterdata/not-exists.yml"))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("masterdata/not-exists.yml");
    }
}
