package com.afkgame.domain.masterdata;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
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

    /** コンストラクタが2つあるため、DI に使う側を {@code @Autowired} で明示する。 */
    @Autowired
    public Items(MasterDataLoader loader) {
        this(loader, RESOURCE_PATH);
    }

    /**
     * 読み込み元を指定して構築する。異常系フィクスチャを読ませるテスト専用。
     *
     * @param loader       マスターデータローダ
     * @param resourcePath クラスパス上の YAML
     * @throws MasterDataException リソース不在・パース失敗・空・スキーマ違反・ID重複のいずれか
     */
    Items(MasterDataLoader loader, String resourcePath) {
        this.items = loader.load(resourcePath, ItemData.class, ItemData::id);
    }

    /**
     * @return アイテムIDをキーにした不変 Map
     */
    public Map<String, ItemData> all() {
        return items;
    }

    /**
     * 指定のアイテムが定義されているかを返す。
     *
     * @param itemId アイテムID
     * @return 定義されていれば {@code true}
     */
    public boolean contains(String itemId) {
        return items.containsKey(itemId);
    }
}
