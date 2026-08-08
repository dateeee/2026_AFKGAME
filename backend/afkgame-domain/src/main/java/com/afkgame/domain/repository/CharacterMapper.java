package com.afkgame.domain.repository;

import java.util.List;

import com.afkgame.domain.model.Character;

/**
 * {@code characters} テーブルの Mapper。
 *
 * <p>プレイヤー初期化（docs/tech/detail/tech_auth.md §8.2 手順4）で必要な操作のみを持つ。
 * 経験値・レベルの更新は、該当機能を移植する際に追加する。
 */
public interface CharacterMapper {

    /**
     * IDでキャラクターを取得する。
     *
     * @param id キャラクターID
     * @return 該当キャラクター。存在しなければ null
     */
    Character selectById(String id);

    /**
     * プレイヤーIDでキャラクターを取得する。
     *
     * @param playerId プレイヤーID
     * @return 該当プレイヤーのキャラクター。1件も無ければ空リスト
     */
    List<Character> selectByPlayerId(String playerId);

    /**
     * キャラクターを登録する。
     *
     * @param character 登録するキャラクター
     */
    void insert(Character character);
}
