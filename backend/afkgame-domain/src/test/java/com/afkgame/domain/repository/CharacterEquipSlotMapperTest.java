package com.afkgame.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.afkgame.domain.model.CharacterEquipSlot;

/**
 * {@code character_equip_slots} テーブルの Mapper のテスト。
 *
 * <p>列・複合主キーの正は docs/tech/basic/tech_db/item.md §2。主キーは {@code id} ではなく
 * ({@code character_id}, {@code slot})で、キャラ1体につき9スロット分の行を持つ。
 * {@code equipment_id} の NULL は未装備を表す。
 *
 * <p>観点: 未装備 / 装備済みの両側の往復・9スロットの一括登録・複合主キーの重複・他キャラの混入。
 */
class CharacterEquipSlotMapperTest extends MapperTestSupport {

    /** スロットの9種（docs/tech/basic/tech_db/item.md §1 の {@code equipment.slot}）。 */
    private static final List<String> SLOTS = List.of(
            "weapon", "shield", "head", "body", "arms", "waist", "legs", "ears", "ring");

    @Autowired
    private CharacterEquipSlotMapper characterEquipSlotMapper;

    private CharacterEquipSlot スロット(String characterId, String slot, String equipmentId) {
        CharacterEquipSlot equipSlot = new CharacterEquipSlot();
        equipSlot.setCharacterId(characterId);
        equipSlot.setSlot(slot);
        equipSlot.setEquipmentId(equipmentId);
        return equipSlot;
    }

    @Test
    void 未装備のスロットは装備IDがNULLのまま往復する() {
        String characterId = givenCharacter(givenPlayer());
        CharacterEquipSlot expected = スロット(characterId, "weapon", null);

        characterEquipSlotMapper.insert(expected);

        assertThat(characterEquipSlotMapper.selectByCharacterId(characterId))
                .singleElement().usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void 装備済みのスロットは装備IDを保持して往復する() {
        String playerId = givenPlayer();
        String characterId = givenCharacter(playerId);
        CharacterEquipSlot expected = スロット(characterId, "weapon", givenEquipment(playerId));

        characterEquipSlotMapper.insert(expected);

        assertThat(characterEquipSlotMapper.selectByCharacterId(characterId))
                .singleElement().usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void キャラクター1体につき9スロットを登録できる() {
        String characterId = givenCharacter(givenPlayer());
        SLOTS.forEach(slot -> characterEquipSlotMapper.insert(スロット(characterId, slot, null)));

        assertThat(characterEquipSlotMapper.selectByCharacterId(characterId))
                .extracting(CharacterEquipSlot::getSlot)
                .containsExactlyInAnyOrderElementsOf(SLOTS);
    }

    @Test
    void 同じキャラクターの同じスロットは2行目を作れない() {
        String characterId = givenCharacter(givenPlayer());
        characterEquipSlotMapper.insert(スロット(characterId, "weapon", null));

        assertThatThrownBy(() -> characterEquipSlotMapper.insert(スロット(characterId, "weapon", null)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void キャラクターIDで引くと他キャラクターのスロットは含まない() {
        String playerId = givenPlayer();
        String characterId = givenCharacter(playerId);
        characterEquipSlotMapper.insert(スロット(characterId, "weapon", null));
        characterEquipSlotMapper.insert(スロット(givenCharacter(playerId), "weapon", null));

        assertThat(characterEquipSlotMapper.selectByCharacterId(characterId))
                .extracting(CharacterEquipSlot::getCharacterId).containsExactly(characterId);
    }

    @Test
    void スロットを1件も持たないキャラクターIDで引くと空リストを返す() {
        assertThat(characterEquipSlotMapper.selectByCharacterId(givenCharacter(givenPlayer()))).isEmpty();
    }
}
