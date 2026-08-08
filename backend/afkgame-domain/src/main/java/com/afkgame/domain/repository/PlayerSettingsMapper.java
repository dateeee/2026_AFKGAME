package com.afkgame.domain.repository;

import com.afkgame.domain.model.PlayerSettings;

/**
 * {@code player_settings} テーブルの Mapper。
 *
 * <p>プレイヤー初期化（docs/tech/detail/tech_auth.md §8.2 手順3）で必要な操作のみを持つ。
 * 設定変更は、該当機能を移植する際に追加する。
 */
public interface PlayerSettingsMapper {

    /**
     * プレイヤーIDで設定を取得する。
     *
     * @param playerId プレイヤーID
     * @return 該当設定。存在しなければ null
     */
    PlayerSettings selectByPlayerId(String playerId);

    /**
     * 設定を登録する。
     *
     * @param settings 登録する設定
     */
    void insert(PlayerSettings settings);
}
