package com.afkgame.domain.repository;

import java.util.List;

import com.afkgame.domain.model.InventoryItem;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.model.PlayerSettings;

/**
 * {@code players} の Repository。
 *
 * <p>プレイヤー初期化（docs/tech/detail/tech_auth/init.md §8.2 手順2・3・6）で必要な操作のみを持つ。
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
     * IDでプレイヤーを取得し、その行を占有ロックする。
     *
     * <p>tick 処理の多重実行を防ぐため、マッピング XML が {@code SELECT ... FOR UPDATE} を
     * 発行する（docs/tech/detail/tech_tick.md §3.1）。トランザクション内から呼ぶこと。
     *
     * @param id プレイヤーID
     * @return 該当プレイヤー。存在しなければ null
     */
    Player findByIdForUpdate(String id);

    /**
     * tick 処理の結果として進んだ状態を反映する。
     *
     * <p>{@code last_tick_at} と探索状態（現在階・交戦中の敵・周回ゴールドなど）をまとめて更新する
     * （docs/tech/detail/tech_tick.md §4）。
     *
     * @param player 更新するプレイヤー
     */
    void updateTickState(Player player);

    /**
     * 塔操作の結果として変わった探索状態を反映する。
     *
     * <p>入塔・リタイア・モード変更が更新する塔フィールド（現在の塔・現在階・目標階・周回モード・
     * 交戦中の敵・周回ゴールド）だけを書き戻す（docs/tech/detail/tech_tower/select.md §7 手順8）。
     * {@link #updateTickState} を流用しない — あちらは {@code last_tick_at} も更新するため、
     * 塔操作で未処理tickを食い潰してしまう（docs/tech/detail/tech_tick.md §4）。
     *
     * <p><b>マッピング XML はテストリスト作成②-a では未作成で、製造②で書く。</b>
     *
     * @param player 更新するプレイヤー
     */
    void updateTowerState(Player player);

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
