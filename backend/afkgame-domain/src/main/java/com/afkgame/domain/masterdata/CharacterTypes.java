package com.afkgame.domain.masterdata;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * キャラクタータイプ別 LV1 基礎ステータスのマスターデータを保持するレジストリ。
 *
 * <p>数値の正は docs/data/master/character.md §1.2、参照関係の正は
 * docs/tech/detail/tech_auth.md §8.1。起動時に一度だけ YAML を読み込み、以降は不変 Map として
 * 公開する（docs/tech/basic/tech_structure.md §2「masterdata/」）。
 * 読み込みに失敗した場合は Bean 生成が失敗し、アプリケーションは起動しない。
 */
@Component
public class CharacterTypes {

    /** マスターデータ本体（{@code afkgame-domain} の {@code src/main/resources} 配下）。 */
    private static final String RESOURCE_PATH = "masterdata/character_types.yml";

    private final Map<String, CharacterTypeData> characterTypes;

    /** コンストラクタが2つあるため、DI に使う側を {@code @Autowired} で明示する。 */
    @Autowired
    public CharacterTypes(MasterDataLoader loader) {
        this(loader, RESOURCE_PATH);
    }

    /**
     * 読み込み元を指定して構築する。異常系フィクスチャを読ませるテスト専用。
     *
     * @param loader       マスターデータローダ
     * @param resourcePath クラスパス上の YAML
     * @throws MasterDataException リソース不在・パース失敗・空・スキーマ違反・ID重複のいずれか
     */
    CharacterTypes(MasterDataLoader loader, String resourcePath) {
        this.characterTypes =
                loader.load(resourcePath, CharacterTypeData.class, CharacterTypeData::id);
    }

    /**
     * @return タイプIDをキーにした不変 Map（YAML の記載順を保つ）
     */
    public Map<String, CharacterTypeData> all() {
        return characterTypes;
    }

    /**
     * 指定のタイプが定義されているかを返す。
     *
     * @param typeId タイプID
     * @return 定義されていれば {@code true}
     */
    public boolean contains(String typeId) {
        return characterTypes.containsKey(typeId);
    }
}
