package com.afkgame.domain.repository;

import com.afkgame.domain.model.Player;

/**
 * {@code players} テーブルの Mapper。
 *
 * <p>プレイヤー初期化（docs/tech/detail/tech_auth.md §8.2 手順2）で必要な操作のみを持つ。
 * 探索状態・所持金の更新は、該当機能を移植する際に追加する。
 */
public interface PlayerMapper {

    /**
     * IDでプレイヤーを取得する。
     *
     * @param id プレイヤーID
     * @return 該当プレイヤー。存在しなければ null
     */
    Player selectById(String id);

    /**
     * ユーザーIDでプレイヤーを取得する。
     *
     * @param userId ユーザーID
     * @return 該当プレイヤー。存在しなければ null
     */
    Player selectByUserId(String userId);

    /**
     * プレイヤーを登録する。
     *
     * @param player 登録するプレイヤー
     */
    void insert(Player player);
}
