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
 * 装備スロット定義のマスターデータ（{@code EquipmentSlots}）の単体テスト。
 *
 * <p>スロットの正は docs/design/systems/equipment.md §2.4「装備スロット（9スロット）」、
 * スロットIDの正は docs/tech/basic/tech_db/item.md §1 の {@code equipment.slot}。
 * 参照関係は docs/tech/detail/tech_auth.md §8.1。
 * 件数は9種固定で、そろわなければ<strong>起動時に</strong>中止する（§8.3 #6）。
 *
 * <p><strong>製造工程への申し送り（本テストが要求する表層）</strong>:
 * <ul>
 * <li>{@code record EquipmentSlotData(String id, String name)}
 *     — いずれも {@code @NotBlank}</li>
 * <li>{@code @Component class EquipmentSlots} — 既定の読み込み元は
 *     {@code masterdata/equipment_slots.yml}。公開 API は
 *     {@code Map<String, EquipmentSlotData> all()}（YAML の記載順を保つ不変 Map）</li>
 * <li>コンストラクタで件数が9でなければ {@link MasterDataException} を投げる。
 *     例外メッセージには<strong>実際の件数</strong>を含める（どちらへ外れたか分かるように）</li>
 * <li>テストから異常系フィクスチャを読ませるため、リソースパスを受け取る
 *     <strong>パッケージプライベートのコンストラクタ</strong>
 *     {@code EquipmentSlots(MasterDataLoader loader, String resourcePath)} を用意する</li>
 * </ul>
 */
@Tag("unit")
class EquipmentSlotsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private final MasterDataLoader loader = new MasterDataLoader(VALIDATOR);

    /**
     * 9種そろっていれば読み込みが成功する（分岐 #6 の偽側）。
     *
     * <p>並び順は装備画面の表示順にそのまま使うため、記載順を保つことまで確かめる。
     * 作成される行そのもの（{@code equipment_id = NULL} の9行）は #5 の担当で、
     * 移行 STEP 3-A-1c のプレイヤー初期化サービスで押さえる。
     *
     * <p>分岐: tech_auth.md §8.3 #6
     */
    @Test
    @DisplayName("9種そろっていれば YAML の記載順どおりに公開する")
    void test_9種そろっていれば読み込める() {
        EquipmentSlots actual = new EquipmentSlots(loader);

        assertThat(actual.all()).hasSize(9);
        assertThat(actual.all().keySet()).containsExactly(
                "weapon", "shield", "head", "body", "arms", "waist", "legs", "ears", "ring");
    }

    /**
     * 9種でなければ起動を中止する（分岐 #6 の真側）。
     *
     * <p>不足・過剰の両方を弾く。件数が固定のため 0周・1周の行は置かない
     * （tech_auth.md §8.3 の「WARN許容 #5・#6」）。
     *
     * <p>分岐: tech_auth.md §8.3 #6
     */
    @ParameterizedTest(name = "{1}種なら例外")
    @CsvSource({
            "masterdata-invalid/equipment_slots-8.yml,   8",   // ring が欠落（不足）
            "masterdata-invalid/equipment_slots-10.yml, 10",   // 未定義の cape が余分（過剰）
    })
    @DisplayName("9種でなければ例外（不足・過剰の両方を弾く）")
    void test_9種でなければ例外(String resourcePath, int actualCount) {
        assertThatThrownBy(() -> new EquipmentSlots(loader, resourcePath))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining(String.valueOf(actualCount));
    }

    /**
     * 読み込み元が無ければ起動を中止する（マスターデータの欠損を実行時へ持ち込まない）。
     *
     * <p>分岐一覧の行ではなく、レジストリ共通の横断検証（{@code Items} と同じ扱い）。
     */
    @Test
    @DisplayName("リソースが存在しなければ例外（起動を中止する）")
    void test_リソース不在で例外() {
        assertThatThrownBy(() -> new EquipmentSlots(loader, "masterdata/not-exists.yml"))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("masterdata/not-exists.yml");
    }
}
