package com.afkgame.domain.repository;

import java.util.List;

import com.afkgame.domain.model.InventoryItem;

/**
 * {@code inventory_items} テーブルの Mapper。
 *
 * <p>プレイヤー初期化（docs/tech/detail/tech_auth.md §8.2 手順6）で必要な操作のみを持つ。
 * 個数の増減は、該当機能を移植する際に追加する。
 */
public interface InventoryItemMapper {

    /**
     * プレイヤーIDで所持品を取得する。
     *
     * @param playerId プレイヤーID
     * @return 該当プレイヤーの所持品。1件も無ければ空リスト
     */
    List<InventoryItem> selectByPlayerId(String playerId);

    /**
     * 所持品を1件登録する。
     *
     * @param item 登録する所持品
     */
    void insert(InventoryItem item);
}
