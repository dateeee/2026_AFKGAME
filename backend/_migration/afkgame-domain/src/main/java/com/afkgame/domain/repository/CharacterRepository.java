package com.afkgame.domain.repository;

import java.util.List;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.CharacterEquipSlot;

/**
 * {@code characters} の Repository。
 *
 * <p>プレイヤー初期化（docs/tech/detail/tech_auth.md §8.2 手順4・5）で必要な操作のみを持つ。
 * 経験値・レベルの更新、装備の着脱は、該当機能を移植する際に追加する。
 *
 * <p>{@code character_equip_slots} は {@code characters} の従 Entity のため、
 * 専用の Repository を作らず本 Repository のメソッドとして持つ
 * （docs/process/coding_standards_backend/domain.md §3 #3）。
 */
public interface CharacterRepository {

    /**
     * IDでキャラクターを取得する。
     *
     * @param id キャラクターID
     * @return 該当キャラクター。存在しなければ null
     */
    Character findById(String id);

    /**
     * プレイヤーIDでキャラクターを取得する。
     *
     * @param playerId プレイヤーID
     * @return 該当プレイヤーのキャラクター。1件も無ければ空リスト
     */
    List<Character> findAllByPlayerId(String playerId);

    /**
     * キャラクターを登録する。
     *
     * @param character 登録するキャラクター
     */
    void save(Character character);

    /**
     * キャラクターIDで装備スロットを取得する。
     *
     * @param characterId キャラクターID
     * @return 該当キャラクターの装備スロット。1件も無ければ空リスト
     */
    List<CharacterEquipSlot> findAllEquipSlotsByCharacterId(String characterId);

    /**
     * 装備スロットを1件登録する。
     *
     * @param equipSlot 登録する装備スロット
     */
    void saveEquipSlot(CharacterEquipSlot equipSlot);
}
