package com.afkgame.domain.repository;

import java.util.List;

import com.afkgame.domain.model.InventoryItem;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.model.PlayerSettings;

/**
 * {@code players} の Repository。
 *
 * <p>プレイヤー初期化（docs/tech/detail/tech_auth.md §8.2 手順2・3・6）で必要な操作のみを持つ。
 * 探索状態・所持金の更新、設定変更、所持品の増減は、該当機能を移植する際に追加する。
 *
 * <p>{@code player_settings}・{@code inventory_items} は {@code players} の従 Entity のため、
 * 専用の Repository を作らず本 Repository のメソッドとして持つ
 * （docs/process/coding_standards_backend/domain.md §3 #3）。
 */
public interface PlayerRepository {

    /**
     * IDでプレイヤーを取得する。
     *
     * @param id プレイヤーID
     * @return 該当プレイヤー。存在しなければ null
     */
    Player findById(String id);

    /**
     * ユーザーIDでプレイヤーを取得する。
     *
     * @param userId ユーザーID
     * @return 該当プレイヤー。存在しなければ null
     */
    Player findByUserId(String userId);

    /**
     * プレイヤーを登録する。
     *
     * @param player 登録するプレイヤー
     */
    void save(Player player);

    /**
     * プレイヤーIDで設定を取得する。
     *
     * @param playerId プレイヤーID
     * @return 該当設定。存在しなければ null
     */
    PlayerSettings findSettingsByPlayerId(String playerId);

    /**
     * 設定を登録する。
     *
     * @param settings 登録する設定
     */
    void saveSettings(PlayerSettings settings);

    /**
     * プレイヤーIDで所持品を取得する。
     *
     * @param playerId プレイヤーID
     * @return 該当プレイヤーの所持品。1件も無ければ空リスト
     */
    List<InventoryItem> findAllItemsByPlayerId(String playerId);

    /**
     * 所持品を1件登録する。
     *
     * @param item 登録する所持品
     */
    void saveItem(InventoryItem item);
}
