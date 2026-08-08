package com.afkgame.domain.model;

/**
 * {@code character_equip_slots} テーブルの Entity。
 *
 * <p>列・複合主キーの正は docs/tech/basic/tech_db/item.md §2。主キーは {@code id} ではなく
 * ({@code characterId}, {@code slot}) で、キャラ1体につき9スロット分の行を持つ。
 */
public class CharacterEquipSlot {

    private String characterId;
    private String slot;
    private String equipmentId;

    public String getCharacterId() {
        return characterId;
    }

    public void setCharacterId(String characterId) {
        this.characterId = characterId;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    /**
     * @return 装備中の装備ID。未装備なら null（tech_db/item.md §2）
     */
    public String getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(String equipmentId) {
        this.equipmentId = equipmentId;
    }
}
