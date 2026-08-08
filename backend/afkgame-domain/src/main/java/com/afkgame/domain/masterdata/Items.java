package com.afkgame.domain.masterdata;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * アイテムのマスターデータを保持するレジストリ。
 *
 * <p>起動時に一度だけ YAML を読み込み、以降は不変 Map として公開する
 * （docs/tech/basic/tech_structure.md §2「masterdata/」）。
 * 読み込みに失敗した場合は Bean 生成が失敗し、アプリケーションは起動しない。
 */
@Component
public class Items {

    /** マスターデータ本体（{@code afkgame-domain} の {@code src/main/resources} 配下）。 */
    private static final String RESOURCE_PATH = "masterdata/items.yml";

    private final Map<String, ItemData> items;

    public Items(MasterDataLoader loader) {
        this.items = loader.load(RESOURCE_PATH, ItemData.class, ItemData::id);
    }

    /**
     * @return アイテムIDをキーにした不変 Map
     */
    public Map<String, ItemData> all() {
        return items;
    }
}
