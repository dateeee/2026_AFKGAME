package com.afkgame.domain.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.afkgame.domain.masterdata.CharacterTypeData;
import com.afkgame.domain.masterdata.CharacterTypes;
import com.afkgame.domain.masterdata.EquipmentSlotData;
import com.afkgame.domain.masterdata.EquipmentSlots;
import com.afkgame.domain.masterdata.InitialCharacterData;
import com.afkgame.domain.masterdata.InitialItemData;
import com.afkgame.domain.masterdata.InitialPlayer;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.CharacterEquipSlot;
import com.afkgame.domain.model.InventoryItem;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.model.PlayerSettings;
import com.afkgame.domain.repository.CharacterRepository;
import com.afkgame.domain.repository.PlayerRepository;

/**
 * {@link PlayerInitializationServiceImpl} の単体テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth/init.md §8.2「処理フロー」手順2〜6・§8.3「分岐一覧」。
 * 既定値の正は docs/tech/basic/tech_db/player.md §1（players）・§2（player_settings）・§4（characters）。
 *
 * <p>分岐観点: プレイヤー重複の 未作成 / 既存（#1・#2）、装備スロット9種の作成（#5）、
 * 初期所持アイテムの 0種 / 1種 / 2種以上（#7〜#9）。
 * マスターデータ自体の妥当性（#3・#4・#6・#10）は起動時のローダ検証が持つため、
 * 本クラスでは検証しない（{@code InitialPlayerTest}・{@code EquipmentSlotsTest} が担当）。
 *
 * <p>手順1（ユーザー作成）・手順7（トークン発行）・手順8（トランザクション境界）は
 * {@link AuthService} 側の責務であり、{@code AuthServiceImplTest} が持つ（#11・#12）。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PlayerInitializationServiceImplTest {

    /** 初期キャラの定義（initial_player.yml 相当。正は master/character.md §1.1）。 */
    private static final InitialCharacterData INITIAL_CHARACTER =
            new InitialCharacterData("hero_001", "勇者", "melee", 1);

    /** melee の LV1 基礎ステータス（character_types.yml 相当。正は master/character.md §1.2）。 */
    private static final CharacterTypeData MELEE =
            new CharacterTypeData("melee", 100, 10, 5, 5, 0.05);

    /** 装備スロット9種。順序は equipment_slots.yml（装備画面の表示順）に合わせる。 */
    private static final List<String> SLOT_IDS =
            List.of("weapon", "shield", "head", "body", "arms", "waist", "legs", "ears", "ring");

    private static final String USER_ID = "guest_001";

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private CharacterTypes characterTypes;

    @Mock
    private EquipmentSlots equipmentSlots;

    @Mock
    private InitialPlayer initialPlayer;

    /** 時刻は固定値で受け取る（実行のたびに結果が変わらないようにする）。 */
    private static final Instant FIXED_NOW = Instant.parse("2026-08-08T12:00:00Z");

    private PlayerInitializationService service() {
        return new PlayerInitializationServiceImpl(playerRepository, characterRepository,
                characterTypes, equipmentSlots, initialPlayer,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    /** 装備スロット9種のマスターデータ（記載順を保つ）。 */
    private static Map<String, EquipmentSlotData> nineSlots() {
        Map<String, EquipmentSlotData> slots = new LinkedHashMap<>();
        for (String id : SLOT_IDS) {
            slots.put(id, new EquipmentSlotData(id, id));
        }
        return slots;
    }

    /**
     * 正常系のマスターデータを仕込む。
     *
     * @param items 初期所持アイテムの定義（#7〜#9 で件数を変える）
     */
    private void givenMasterData(List<InitialItemData> items) {
        when(initialPlayer.character()).thenReturn(INITIAL_CHARACTER);
        when(initialPlayer.items()).thenReturn(items);
        when(characterTypes.all()).thenReturn(Map.of("melee", MELEE));
        when(equipmentSlots.all()).thenReturn(nineSlots());
    }

    /** 既定の初期所持アイテム（hp_potion×5。正は master/item.md §3.5）。 */
    private static List<InitialItemData> defaultItems() {
        return List.of(new InitialItemData("hp_potion", 5));
    }

    private Player capturedPlayer() {
        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        return captor.getValue();
    }

    private Character capturedCharacter() {
        ArgumentCaptor<Character> captor = ArgumentCaptor.forClass(Character.class);
        verify(characterRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("プレイヤー重複")
    class TestDuplicatePlayer {

        /**
         * 手順2・3を検証する（手順4はキャラ、手順5・6はスロット・アイテムの各テストが持つ）。
         *
         * <p>分岐: tech_auth/init.md §8.3 #1
         */
        @Test
        @DisplayName("未作成なら Player と PlayerSettings を既定値で作成する")
        void test_未作成ならPlayerとPlayerSettingsを既定値で作成する() {
            givenMasterData(defaultItems());

            service().initialize(USER_ID);

            Player player = capturedPlayer();
            assertThat(player.getUserId()).isEqualTo(USER_ID);
            // PK は UUID4（tech_db/player.md §1）。varchar(36) にちょうど収まる形式であること
            assertThat(UUID.fromString(player.getId())).hasToString(player.getId());
            assertThat(player.getGold()).isZero();
            assertThat(player.getTowerMode()).isEqualTo("auto_repeat");
            assertThat(player.getHpThreshold()).isEqualTo(0.3);
            assertThat(player.getRunGold()).isZero();
            assertThat(player.getHighestFloor()).isZero();
            assertThat(player.getLastTickAt()).isEqualTo(FIXED_NOW);
            assertThat(player.getCreatedAt()).isEqualTo(FIXED_NOW);
            // 塔外のため塔関連と交戦中の敵は NULL（tech_auth/init.md §8.2 手順2）
            assertThat(player.getCurrentTowerId()).isNull();
            assertThat(player.getCurrentFloor()).isNull();
            assertThat(player.getTargetFloor()).isNull();
            assertThat(player.getCurrentEnemyId()).isNull();
            assertThat(player.getCurrentEnemyHp()).isNull();

            ArgumentCaptor<PlayerSettings> settings = ArgumentCaptor.forClass(PlayerSettings.class);
            verify(playerRepository).saveSettings(settings.capture());
            assertThat(UUID.fromString(settings.getValue().getId()))
                    .hasToString(settings.getValue().getId());
            assertThat(settings.getValue().getPlayerId()).isEqualTo(player.getId());
            assertThat(settings.getValue().getPotionThreshold()).isEqualTo(0.3);
            assertThat(settings.getValue().getBattleLogCount()).isEqualTo(50);
            assertThat(settings.getValue().isToastEnabled()).isTrue();
            // 自動売却なしが既定（tech_db/player.md §2）
            assertThat(settings.getValue().getAutoSellRarity()).isNull();
        }

        /**
         * 手順4を検証する。ステータスはタイプ別 LV1 基礎値をそのまま写す。
         *
         * <p>分岐: tech_auth/init.md §8.3 #1
         */
        @Test
        @DisplayName("未作成なら初期キャラを1体、タイプ別 LV1 基礎値で作成する")
        void test_未作成なら初期キャラをLV1基礎値で1体作成する() {
            givenMasterData(defaultItems());

            service().initialize(USER_ID);

            Character character = capturedCharacter();
            assertThat(UUID.fromString(character.getId())).hasToString(character.getId());
            assertThat(character.getPlayerId()).isEqualTo(capturedPlayer().getId());
            assertThat(character.getName()).isEqualTo("勇者");
            assertThat(character.getType()).isEqualTo("melee");
            assertThat(character.getLevel()).isEqualTo(1);
            assertThat(character.getExp()).isZero();
            // melee の LV1 基礎値をそのまま写す（master/character.md §1.2）
            assertThat(character.getMaxHp()).isEqualTo(100);
            assertThat(character.getBaseAtk()).isEqualTo(10);
            assertThat(character.getBaseDef()).isEqualTo(5);
            assertThat(character.getBaseSpd()).isEqualTo(5);
            // 作成直後は全快（tech_auth/init.md §8.2 手順4）
            assertThat(character.getHp()).isEqualTo(character.getMaxHp());
            assertThat(character.getLimitBreak()).isZero();
            assertThat(character.getSkillPoints()).isZero();
            assertThat(character.getCreatedAt()).isEqualTo(FIXED_NOW);
        }

        /**
         * 一意制約違反は業務例外へ写さずそのまま伝播させる。
         *
         * <p>公開APIからは到達しない（ゲスト作成・本登録はいずれも新規ユーザーを作ってから初期化するため）。
         * クライアントが起こせない状態にエラーコードを新設せず、
         * 500 {@code INTERNAL_UNEXPECTED_ERROR} として扱う（tech_error_handling.md「AUTH_ コード一覧」に該当なし）。
         *
         * <p>分岐: tech_auth/init.md §8.3 #2
         */
        @Test
        @DisplayName("既にプレイヤーがあれば一意制約違反で中止し、以降の手順を実行しない")
        void test_既にプレイヤーがあれば一意制約違反で中止する() {
            // 実装が手順2より前にマスターデータを読んでも成立するよう lenient で置く
            lenient().when(initialPlayer.character()).thenReturn(INITIAL_CHARACTER);
            lenient().when(initialPlayer.items()).thenReturn(defaultItems());
            lenient().when(characterTypes.all()).thenReturn(Map.of("melee", MELEE));
            lenient().when(equipmentSlots.all()).thenReturn(nineSlots());
            doThrow(new DuplicateKeyException("uq_players_user_id"))
                    .when(playerRepository).save(any());

            assertThatThrownBy(() -> service().initialize(USER_ID))
                    .isInstanceOf(DuplicateKeyException.class);

            // 既存データを変更しない（手順3以降へ進まない）
            verify(playerRepository, never()).saveSettings(any());
            verify(characterRepository, never()).save(any());
            verify(characterRepository, never()).saveEquipSlot(any());
            verify(playerRepository, never()).saveItem(any());
        }
    }

    @Nested
    @DisplayName("装備スロット定義")
    class TestEquipmentSlots {

        /**
         * 手順5を検証する。9種でない場合（#6）は起動時のローダ検証が弾くため、ここでは扱わない。
         *
         * <p>分岐: tech_auth/init.md §8.3 #5
         */
        @Test
        @DisplayName("9種そろっていれば9行を未装備（equipment_id = NULL）で作成する")
        void test_装備スロットを9種すべて未装備で作成する() {
            givenMasterData(defaultItems());

            service().initialize(USER_ID);

            String characterId = capturedCharacter().getId();
            ArgumentCaptor<CharacterEquipSlot> slots =
                    ArgumentCaptor.forClass(CharacterEquipSlot.class);
            verify(characterRepository, times(9)).saveEquipSlot(slots.capture());
            assertThat(slots.getAllValues())
                    .extracting(CharacterEquipSlot::getSlot)
                    .containsExactlyInAnyOrderElementsOf(SLOT_IDS);
            assertThat(slots.getAllValues())
                    .allSatisfy(slot -> {
                        assertThat(slot.getCharacterId()).isEqualTo(characterId);
                        assertThat(slot.getEquipmentId()).isNull();
                    });
        }
    }

    @Nested
    @DisplayName("初期所持アイテム")
    class TestInitialItems {

        /**
         * 定義が空でも手順7（トークン発行）へ進めるよう、例外なく完了すること。
         *
         * <p>分岐: tech_auth/init.md §8.3 #7
         */
        @Test
        @DisplayName("定義が空ならアイテムを付与せず、初期化は完了する")
        void test_定義が空ならアイテムを付与しない() {
            givenMasterData(List.of());

            service().initialize(USER_ID);

            verify(playerRepository, never()).saveItem(any());
            // 手順2〜5は実行済み（アイテム0種で中断しない）
            verify(characterRepository).save(any());
            verify(characterRepository, times(9)).saveEquipSlot(any());
        }

        /**
         * 分岐: tech_auth/init.md §8.3 #8
         */
        @Test
        @DisplayName("定義が1種類ならその1種類を定義された個数で付与する")
        void test_定義が1種類なら定義された個数で付与する() {
            givenMasterData(List.of(new InitialItemData("hp_potion", 5)));

            service().initialize(USER_ID);

            ArgumentCaptor<InventoryItem> items = ArgumentCaptor.forClass(InventoryItem.class);
            verify(playerRepository).saveItem(items.capture());
            InventoryItem item = items.getValue();
            assertThat(UUID.fromString(item.getId())).hasToString(item.getId());
            assertThat(item.getPlayerId()).isEqualTo(capturedPlayer().getId());
            assertThat(item.getItemId()).isEqualTo("hp_potion");
            assertThat(item.getQuantity()).isEqualTo(5);
        }

        /**
         * アイテムIDの実在性はローダ検証（#10）が持つため、ここでは任意のIDでよい。
         *
         * <p>分岐: tech_auth/init.md §8.3 #9
         */
        @Test
        @DisplayName("定義が2種類以上なら種類ごとに1行ずつ付与する")
        void test_定義が2種類以上なら種類ごとに1行付与する() {
            givenMasterData(List.of(
                    new InitialItemData("hp_potion", 5),
                    new InitialItemData("mp_potion", 2)));

            service().initialize(USER_ID);

            ArgumentCaptor<InventoryItem> items = ArgumentCaptor.forClass(InventoryItem.class);
            verify(playerRepository, times(2)).saveItem(items.capture());
            assertThat(items.getAllValues())
                    .extracting(InventoryItem::getItemId, InventoryItem::getQuantity)
                    .containsExactlyInAnyOrder(tuple("hp_potion", 5), tuple("mp_potion", 2));
            // 同じ種類をまとめず、種類ごとに1行にする
            assertThat(items.getAllValues())
                    .extracting(InventoryItem::getId)
                    .doesNotHaveDuplicates();
        }
    }
}
