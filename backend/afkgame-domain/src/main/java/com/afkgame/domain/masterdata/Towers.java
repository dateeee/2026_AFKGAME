package com.afkgame.domain.masterdata;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 塔のマスターデータを保持するレジストリ。
 *
 * <p>数値の正は docs/data/towers/TOWERS_OVERVIEW.md「塔一覧」。起動時に一度だけ YAML を
 * 読み込み、以降は不変 Map として公開する（docs/tech/basic/tech_structure.md §2「masterdata/」）。
 * 読み込みに失敗した場合は Bean 生成が失敗し、アプリケーションは起動しない。
 */
@Component
public class Towers {

    /** マスターデータ本体（{@code afkgame-domain} の {@code src/main/resources} 配下）。 */
    private static final String RESOURCE_PATH = "masterdata/towers.yml";

    private final Map<String, TowerData> towers;

    /** コンストラクタが2つあるため、DI に使う側を {@code @Autowired} で明示する。 */
    @Autowired
    public Towers(MasterDataLoader loader) {
        this(loader, RESOURCE_PATH);
    }

    /**
     * 読み込み元を指定して構築する。異常系フィクスチャを読ませるテスト専用。
     *
     * @param loader       マスターデータローダ
     * @param resourcePath クラスパス上の YAML
     * @throws MasterDataException リソース不在・パース失敗・空・スキーマ違反・ID重複のいずれか
     */
    Towers(MasterDataLoader loader, String resourcePath) {
        this.towers = loader.load(resourcePath, TowerData.class, TowerData::id);
    }

    /**
     * 塔IDでマスターデータを取得する。
     *
     * @param towerId 塔ID
     * @return 該当する塔データ。定義が無ければ null
     */
    public TowerData get(String towerId) {
        return towers.get(towerId);
    }

    /**
     * 全塔をマスターデータの定義順で返す。
     *
     * <p>順序は TOWERS_OVERVIEW.md の番号順（一覧APIがこの順で応答する。
     * docs/tech/detail/tech_tower/list.md §5 手順2）。
     *
     * @return 塔データの一覧
     */
    public List<TowerData> all() {
        return List.copyOf(towers.values());
    }
}
