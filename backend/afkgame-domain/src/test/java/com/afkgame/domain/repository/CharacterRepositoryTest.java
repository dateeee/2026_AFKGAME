package com.afkgame.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.CharacterEquipSlot;

/**
 * {@link CharacterRepository} のテスト。
 *
 * <p>列・NULL 可否・複合主キーの正は docs/tech/basic/tech_db/player.md §4（{@code characters}）と
 * docs/tech/basic/tech_db/item.md §2（{@code character_equip_slots}）。{@code rarity} は Phase 3 の列で
 * V1 スキーマに無いため対象外（アーキテクチャ不変条件「Phase厳守」）。
 *
 * <p>観点: 主体 Entity（characters）と従 Entity（character_equip_slots）それぞれの全列往復・
 * 取得件数・他レコードの混入・一意制約・未登録の経路。従 Entity の操作を主体側の Repository が持つ
 * 構成は docs/process/coding_standards_backend/domain.md §3 #3。
 */
class CharacterRepositoryTest extends RepositoryTestSupport {

    /** スロットの9種（docs/tech/basic/tech_db/item.md §1 の {@code equipment.slot}）。 */
    private static final List<String> SLOTS = List.of(
            "weapon", "shield", "head", "body", "arms", "waist", "legs", "ears", "ring");

    @Autowired
    private CharacterRepository characterRepository;

    /** 主体 Entity: {@code characters}。 */
    @Nested
    class Testキャラクター {

        private Character キャラクター(String playerId, String name) {
            Character character = new Character();
            character.setId(uuid("character"));
            character.setPlayerId(playerId);
            character.setName(name);
            character.setType("melee");
            character.setLevel(1);
            character.setExp(0L);
            character.setHp(100);
            character.setMaxHp(100);
            character.setBaseAtk(10);
            character.setBaseDef(5);
            character.setBaseSpd(8);
            character.setLimitBreak(0);
            character.setSkillPoints(0);
            character.setCreatedAt(FIXED_NOW);
            return character;
        }

        @Test
        void キャラクターを登録するとすべての列が往復する() {
            Character expected = キャラクター(givenPlayer(), "冒険者");

            characterRepository.save(expected);

            assertThat(characterRepository.findById(expected.getId()))
                    .usingRecursiveComparison().isEqualTo(expected);
        }

        /**
         * 0件（初期化前・パーティ未編成）／1件（ゲスト作成直後）／2件（仲間加入後）を分ける。
         */
        @ParameterizedTest(name = "登録{0}件なら{0}件返す")
        @ValueSource(ints = {0, 1, 2})
        void プレイヤーIDで引くと登録件数分を返す(int count) {
            String playerId = givenPlayer();
            for (int i = 0; i < count; i++) {
                characterRepository.save(キャラクター(playerId, "冒険者" + i));
            }

            assertThat(characterRepository.findAllByPlayerId(playerId)).hasSize(count);
        }

        @Test
        void プレイヤーIDで引くと他プレイヤーのキャラクターは含まない() {
            String playerId = givenPlayer();
            Character mine = キャラクター(playerId, "自分のキャラ");
            characterRepository.save(mine);
            characterRepository.save(キャラクター(givenPlayer(), "他人のキャラ"));

            assertThat(characterRepository.findAllByPlayerId(playerId))
                    .extracting(Character::getId).containsExactly(mine.getId());
        }

        @Test
        void 未登録のIDで引くとnullを返す() {
            assertThat(characterRepository.findById("character_not_exists")).isNull();
        }
    }

    /** 従 Entity: {@code character_equip_slots}（主キーは character_id + slot の複合）。 */
    @Nested
    class Test装備スロット {

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

            characterRepository.saveEquipSlot(expected);

            assertThat(characterRepository.findAllEquipSlotsByCharacterId(characterId))
                    .singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void 装備済みのスロットは装備IDを保持して往復する() {
            String playerId = givenPlayer();
            String characterId = givenCharacter(playerId);
            CharacterEquipSlot expected = スロット(characterId, "weapon", givenEquipment(playerId));

            characterRepository.saveEquipSlot(expected);

            assertThat(characterRepository.findAllEquipSlotsByCharacterId(characterId))
                    .singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void キャラクター1体につき9スロットを登録できる() {
            String characterId = givenCharacter(givenPlayer());
            SLOTS.forEach(slot -> characterRepository.saveEquipSlot(スロット(characterId, slot, null)));

            assertThat(characterRepository.findAllEquipSlotsByCharacterId(characterId))
                    .extracting(CharacterEquipSlot::getSlot)
                    .containsExactlyInAnyOrderElementsOf(SLOTS);
        }

        @Test
        void 同じキャラクターの同じスロットは2行目を作れない() {
            String characterId = givenCharacter(givenPlayer());
            characterRepository.saveEquipSlot(スロット(characterId, "weapon", null));

            assertThatThrownBy(
                    () -> characterRepository.saveEquipSlot(スロット(characterId, "weapon", null)))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void キャラクターIDで引くと他キャラクターのスロットは含まない() {
            String playerId = givenPlayer();
            String characterId = givenCharacter(playerId);
            characterRepository.saveEquipSlot(スロット(characterId, "weapon", null));
            characterRepository.saveEquipSlot(スロット(givenCharacter(playerId), "weapon", null));

            assertThat(characterRepository.findAllEquipSlotsByCharacterId(characterId))
                    .extracting(CharacterEquipSlot::getCharacterId).containsExactly(characterId);
        }

        @Test
        void スロットを1件も持たないキャラクターIDで引くと空リストを返す() {
            assertThat(characterRepository
                    .findAllEquipSlotsByCharacterId(givenCharacter(givenPlayer()))).isEmpty();
        }
    }
}
