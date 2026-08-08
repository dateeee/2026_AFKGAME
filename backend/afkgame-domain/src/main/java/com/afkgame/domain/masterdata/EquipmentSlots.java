package com.afkgame.domain.masterdata;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 装備スロット定義のマスターデータを保持するレジストリ。
 *
 * <p>スロットの正は docs/design/systems/equipment.md §2.4、参照関係の正は
 * docs/tech/detail/tech_auth.md §8.1。起動時に一度だけ YAML を読み込み、以降は不変 Map として
 * 公開する（docs/tech/basic/tech_structure.md §2「masterdata/」）。
 * 公開する並び順は装備画面の表示順にそのまま使うため、YAML の記載順を保つ。
 *
 * <p>件数は9種固定で、そろわなければ起動を中止する（tech_auth.md §8.3 #6）。
 */
@Component
public class EquipmentSlots {

    /** マスターデータ本体（{@code afkgame-domain} の {@code src/main/resources} 配下）。 */
    private static final String RESOURCE_PATH = "masterdata/equipment_slots.yml";

    /** 装備スロットの種類数。キャラ1体につきこの件数の行を作る（tech_db/item.md §2）。 */
    private static final int SLOT_COUNT = 9;

    private final Map<String, EquipmentSlotData> slots;

    /** コンストラクタが2つあるため、DI に使う側を {@code @Autowired} で明示する。 */
    @Autowired
    public EquipmentSlots(MasterDataLoader loader) {
        this(loader, RESOURCE_PATH);
    }

    /**
     * 読み込み元を指定して構築する。異常系フィクスチャを読ませるテスト専用。
     *
     * @param loader       マスターデータローダ
     * @param resourcePath クラスパス上の YAML
     * @throws MasterDataException リソース不在・パース失敗・空・スキーマ違反・ID重複、
     *                             または件数が {@code SLOT_COUNT} 種でない場合
     */
    EquipmentSlots(MasterDataLoader loader, String resourcePath) {
        Map<String, EquipmentSlotData> loaded =
                loader.load(resourcePath, EquipmentSlotData.class, EquipmentSlotData::id);
        if (loaded.size() != SLOT_COUNT) {
            throw new MasterDataException(resourcePath + ": 装備スロットは" + SLOT_COUNT
                    + "種でなければならない (" + loaded.size() + "種)");
        }
        this.slots = loaded;
    }

    /**
     * @return スロットIDをキーにした不変 Map（YAML の記載順を保つ）
     */
    public Map<String, EquipmentSlotData> all() {
        return slots;
    }
}
