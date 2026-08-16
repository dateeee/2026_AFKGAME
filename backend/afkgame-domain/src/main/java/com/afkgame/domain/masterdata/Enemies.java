package com.afkgame.domain.masterdata;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 敵のマスターデータを保持するレジストリ。
 *
 * <p>列の正は docs/data/towers/000_テンプレート.md §2。起動時に一度だけ YAML を読み込み、
 * 以降は不変 Map として公開する（docs/tech/basic/tech_structure.md §2「masterdata/」）。
 * 読み込みに失敗した場合は Bean 生成が失敗し、アプリケーションは起動しない。
 */
@Component
public class Enemies {

    /** マスターデータ本体（{@code afkgame-domain} の {@code src/main/resources} 配下）。 */
    private static final String RESOURCE_PATH = "masterdata/enemies.yml";

    private final Map<String, EnemyData> enemies;

    /** コンストラクタが2つあるため、DI に使う側を {@code @Autowired} で明示する。 */
    @Autowired
    public Enemies(MasterDataLoader loader) {
        this(loader, RESOURCE_PATH);
    }

    /**
     * 読み込み元を指定して構築する。異常系フィクスチャを読ませるテスト専用。
     *
     * @param loader       マスターデータローダ
     * @param resourcePath クラスパス上の YAML
     * @throws MasterDataException リソース不在・パース失敗・空・スキーマ違反・ID重複のいずれか
     */
    Enemies(MasterDataLoader loader, String resourcePath) {
        this.enemies = loader.load(resourcePath, EnemyData.class, EnemyData::id);
    }

    /**
     * 敵IDでマスターデータを取得する。
     *
     * @param enemyId 敵ID
     * @return 該当する敵データ。定義が無ければ null
     */
    public EnemyData get(String enemyId) {
        return enemies.get(enemyId);
    }
}
