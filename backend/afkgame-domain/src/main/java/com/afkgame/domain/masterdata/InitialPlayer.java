package com.afkgame.domain.masterdata;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * プレイヤー初期化に使うマスターデータを保持するレジストリ。
 *
 * <p>値の正は docs/data/master/character.md §1.1 と docs/data/master/item.md §3.5、
 * 参照関係の正は docs/tech/detail/tech_auth.md §8.1。起動時に一度だけ YAML を読み込み、
 * 以降は不変の値として公開する（docs/tech/basic/tech_structure.md §2「masterdata/」）。
 *
 * <p>他ファイルへの参照（タイプ・アイテムID）が解決できるかは<strong>構築時に</strong>検証し、
 * 実行時には再検証しない（tech_auth.md §8.2 末尾）。そのため §8.3 の #4・#10 は
 * 初期化サービスではなく本レジストリの構築時に落ちる。
 *
 * <p>ゲストの表示名（{@code 冒険者}）は tech_auth.md §8.2 手順1 が正で、
 * マスターデータには持たない。
 */
@Component
public class InitialPlayer {

    /** マスターデータ本体（{@code afkgame-domain} の {@code src/main/resources} 配下）。 */
    private static final String RESOURCE_PATH = "masterdata/initial_player.yml";

    private final InitialCharacterData character;

    private final List<InitialItemData> items;

    /** コンストラクタが2つあるため、DI に使う側を {@code @Autowired} で明示する。 */
    @Autowired
    public InitialPlayer(MasterDataLoader loader, CharacterTypes characterTypes, Items items) {
        this(loader, RESOURCE_PATH, characterTypes, items);
    }

    /**
     * 読み込み元を指定して構築する。異常系フィクスチャを読ませるテスト専用。
     *
     * @param loader         マスターデータローダ
     * @param resourcePath   クラスパス上の YAML
     * @param characterTypes タイプ別マスター（初期キャラのタイプの解決に使う）
     * @param itemMaster     アイテム定義（初期所持アイテムのIDの解決に使う）
     * @throws MasterDataException リソース不在・パース失敗・スキーマ違反、
     *                             またはタイプ・アイテムIDが解決できない場合
     */
    InitialPlayer(MasterDataLoader loader, String resourcePath, CharacterTypes characterTypes,
            Items itemMaster) {
        InitialPlayerData data = loader.loadSingle(resourcePath, InitialPlayerData.class);
        if (!characterTypes.contains(data.character().type())) {
            throw new MasterDataException(resourcePath + ": 初期キャラのタイプがタイプ別マスターに無い ("
                    + data.character().type() + ")");
        }
        for (InitialItemData item : data.items()) {
            if (!itemMaster.all().containsKey(item.id())) {
                throw new MasterDataException(
                        resourcePath + ": 初期所持アイテムのIDがアイテム定義に無い (" + item.id() + ")");
            }
        }
        this.character = data.character();
        this.items = List.copyOf(data.items());
    }

    /**
     * @return 初期キャラクター1体の定義
     */
    public InitialCharacterData character() {
        return character;
    }

    /**
     * @return 初期所持アイテムの定義（種類ごとに1件）の不変リスト
     */
    public List<InitialItemData> items() {
        return items;
    }
}
