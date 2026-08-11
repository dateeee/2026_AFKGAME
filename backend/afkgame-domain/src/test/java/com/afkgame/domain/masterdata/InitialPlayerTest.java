package com.afkgame.domain.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * プレイヤー初期化に使うマスターデータ（{@code InitialPlayer}）の単体テスト。
 *
 * <p>初期キャラの正は docs/data/master/character.md §1.1、初期所持アイテムの正は
 * docs/data/master/item.md §3.5、参照関係の正は docs/tech/detail/tech_auth/init.md §8.1。
 * ゲストの表示名（{@code 冒険者}）は tech_auth/init.md §8.2 手順1 が正でマスターデータには持たない。
 *
 * <p>他ファイルへの参照（タイプ・アイテムID）が解決できるかは<strong>起動時に</strong>検証し、
 * 実行時には再検証しない（tech_auth/init.md §8.2 末尾）。そのため #4・#10 は
 * サービスではなく本レジストリの構築時に落ちる。
 *
 * <p><strong>製造工程への申し送り（本テストが要求する表層）</strong>:
 * <ul>
 * <li>{@code record InitialPlayerData(InitialCharacterData character, List<InitialItemData> items)}
 *     — {@code character} は {@code @NotNull @Valid}、{@code items} は {@code @NotNull @Valid}
 *     （空リストは許可する。定義が空の経路は #7 が持つ）</li>
 * <li>{@code record InitialCharacterData(String id, String name, String type, int level)}
 *     — {@code id}・{@code name}・{@code type} は {@code @NotBlank}、{@code level} は {@code @Positive}</li>
 * <li>{@code record InitialItemData(String id, int quantity)}
 *     — {@code id} は {@code @NotBlank}、{@code quantity} は {@code @Positive}</li>
 * <li>{@code @Component class InitialPlayer} — 既定の読み込み元は
 *     {@code masterdata/initial_player.yml}。単一オブジェクトのため
 *     {@code MasterDataLoader#loadSingle} で読む。公開 API は
 *     {@code InitialCharacterData character()} と {@code List<InitialItemData> items()}（不変リスト）</li>
 * <li>公開コンストラクタは
 *     {@code InitialPlayer(MasterDataLoader loader, CharacterTypes characterTypes, Items items)}。
 *     テストから異常系フィクスチャを読ませるため、リソースパスを受け取る
 *     <strong>パッケージプライベートのコンストラクタ</strong>
 *     {@code InitialPlayer(MasterDataLoader loader, String resourcePath, CharacterTypes characterTypes, Items items)}
 *     を用意する</li>
 * <li>構築時に「タイプが {@code CharacterTypes} に実在するか」「アイテムIDが {@code Items} に実在するか」を
 *     検証し、解決できなければ {@link MasterDataException}。メッセージには<strong>解決できなかったID</strong>を含める</li>
 * </ul>
 */
@Tag("unit")
class InitialPlayerTest {

    private static final String RESOURCE_PATH = "masterdata/initial_player.yml";

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private final MasterDataLoader loader = new MasterDataLoader(VALIDATOR);
    private final CharacterTypes characterTypes = new CharacterTypes(loader);
    private final Items items = new Items(loader);

    private InitialPlayer initialPlayer(String resourcePath) {
        return new InitialPlayer(loader, resourcePath, characterTypes, items);
    }

    @Nested
    @DisplayName("初期キャラのタイプ定義")
    class TestCharacterType {

        /**
         * タイプがタイプ別マスターに実在すれば、その LV1 基礎値まで引ける。
         *
         * <p>ここで引ける値が、初期化のキャラ作成（tech_auth/init.md §8.2 手順4）で
         * {@code hp = max_hp} として書き写される基礎値になる。
         *
         * <p>分岐: tech_auth/init.md §8.3 #3
         */
        @Test
        @DisplayName("タイプがマスターに実在すれば、その LV1 基礎値を引ける")
        void test_タイプが実在すればLV1基礎値を引ける() {
            InitialCharacterData actual = initialPlayer(RESOURCE_PATH).character();

            assertThat(actual.id()).isEqualTo("hero_001");
            assertThat(actual.name()).isEqualTo("勇者");
            assertThat(actual.type()).isEqualTo("melee");
            assertThat(actual.level()).isEqualTo(1);

            CharacterTypeData baseStats = characterTypes.all().get(actual.type());
            assertThat(baseStats).as("初期キャラのタイプが解決できること").isNotNull();
            assertThat(baseStats.hp()).isEqualTo(100);
            assertThat(baseStats.atk()).isEqualTo(10);
            assertThat(baseStats.def()).isEqualTo(5);
            assertThat(baseStats.spd()).isEqualTo(5);
        }

        /**
         * タイプがマスターに無ければ、構築時に落として起動を中止する。
         *
         * <p>分岐: tech_auth/init.md §8.3 #4
         */
        @Test
        @DisplayName("タイプがマスターに無ければ例外（起動を中止する）")
        void test_タイプが未定義なら例外() {
            assertThatThrownBy(
                    () -> initialPlayer("masterdata-invalid/initial_player-unknown-type.yml"))
                    .isInstanceOf(MasterDataException.class)
                    .hasMessageContaining("unknown_type");
        }
    }

    @Nested
    @DisplayName("初期所持アイテム")
    class TestInitialItems {

        /**
         * アイテムIDがアイテム定義にあれば、IDと個数をそのまま公開する（分岐 #10 の偽側）。
         *
         * <p>付与そのもの（種類ごとに1行）は #7〜#9 の担当で、
         * 移行 STEP 3-A-1c のプレイヤー初期化サービスで押さえる。
         *
         * <p>分岐: tech_auth/init.md §8.3 #10
         */
        @Test
        @DisplayName("アイテムIDがアイテム定義にあれば、IDと個数を公開する")
        void test_アイテムIDが実在すれば読み込める() {
            var actual = initialPlayer(RESOURCE_PATH).items();

            assertThat(actual).hasSize(1);
            assertThat(actual.get(0).id()).isEqualTo("hp_potion");
            assertThat(actual.get(0).quantity()).isEqualTo(5);
        }

        /**
         * アイテムIDがアイテム定義に無ければ、構築時に落として起動を中止する。
         *
         * <p>分岐: tech_auth/init.md §8.3 #10
         */
        @Test
        @DisplayName("アイテムIDがアイテム定義に無ければ例外（起動を中止する）")
        void test_アイテムIDが未定義なら例外() {
            assertThatThrownBy(
                    () -> initialPlayer("masterdata-invalid/initial_player-unknown-item.yml"))
                    .isInstanceOf(MasterDataException.class)
                    .hasMessageContaining("unknown_item");
        }

        /**
         * アイテムIDが重複していれば、構築時に落として起動を中止する。
         *
         * <p>実在検査だけでは通ってしまい、ゲスト作成のたびに
         * {@code uq_inventory_items_player_item} 違反で 500 になる（新規ユーザーを1人も作れない）。
         *
         * <p>分岐: tech_auth/init.md §8.3 #11
         */
        @Test
        @DisplayName("アイテムIDが重複していれば例外（起動を中止する）")
        void test_アイテムIDが重複なら例外() {
            assertThatThrownBy(
                    () -> initialPlayer("masterdata-invalid/initial_player-duplicate-item.yml"))
                    .isInstanceOf(MasterDataException.class)
                    .hasMessageContaining("hp_potion");
        }

        /**
         * 公開するリストは不変（実行時の書き換えを許さない）。
         *
         * <p>分岐一覧の行ではなく、レジストリ共通の横断検証（{@code Items} と同じ扱い）。
         */
        @Test
        @DisplayName("公開するリストは不変")
        void test_公開するリストは不変() {
            var actual = initialPlayer(RESOURCE_PATH).items();

            assertThatThrownBy(() -> actual.add(actual.get(0)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
