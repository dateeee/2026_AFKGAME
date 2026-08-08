package com.afkgame.domain.repository;

import java.util.List;

import com.afkgame.domain.model.CharacterEquipSlot;

/**
 * {@code character_equip_slots} テーブルの Mapper。
 *
 * <p>プレイヤー初期化（docs/tech/detail/tech_auth.md §8.2 手順5）で必要な操作のみを持つ。
 * 装備の着脱は、該当機能を移植する際に追加する。
 */
public interface CharacterEquipSlotMapper {

    /**
     * キャラクターIDで装備スロットを取得する。
     *
     * @param characterId キャラクターID
     * @return 該当キャラクターの装備スロット。1件も無ければ空リスト
     */
    List<CharacterEquipSlot> selectByCharacterId(String characterId);

    /**
     * 装備スロットを1件登録する。
     *
     * @param equipSlot 登録する装備スロット
     */
    void insert(CharacterEquipSlot equipSlot);
}
