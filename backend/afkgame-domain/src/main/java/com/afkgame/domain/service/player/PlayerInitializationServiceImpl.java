package com.afkgame.domain.service.player;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.afkgame.domain.masterdata.CharacterTypeData;
import com.afkgame.domain.masterdata.CharacterTypes;
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
 * {@link PlayerInitializationService} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。本クラスは手順2〜6（tech_auth/init.md §8.2）の既定値と
 * 採番方式を担う。
 *
 * <p>マスターデータの妥当性（初期キャラのタイプが実在する・初期所持アイテムのIDがアイテム定義にある・
 * スロットが9種そろう）は起動時のローダ検証で担保済みのため、ここでは再検証しない（§8.2 末尾）。
 */
@Service
public class PlayerInitializationServiceImpl implements PlayerInitializationService {

    /** 塔攻略モードの既定値（tech_db/player.md §1）。 */
    private static final String DEFAULT_TOWER_MODE = "auto_repeat";

    /** 撤退HP閾値の既定値（tech_db/player.md §1）。 */
    private static final double DEFAULT_HP_THRESHOLD = 0.3;

    /** ポーション使用HP閾値の既定値（tech_db/player.md §2）。 */
    private static final double DEFAULT_POTION_THRESHOLD = 0.3;

    /** 表示ログ件数の既定値（tech_db/player.md §2）。 */
    private static final int DEFAULT_BATTLE_LOG_COUNT = 50;

    /** トースト通知の既定値（tech_db/player.md §2）。 */
    private static final boolean DEFAULT_TOAST_ENABLED = true;

    private final PlayerRepository playerRepository;
    private final CharacterRepository characterRepository;
    private final CharacterTypes characterTypes;
    private final EquipmentSlots equipmentSlots;
    private final InitialPlayer initialPlayer;
    private final Clock clock;

    public PlayerInitializationServiceImpl(PlayerRepository playerRepository,
            CharacterRepository characterRepository, CharacterTypes characterTypes,
            EquipmentSlots equipmentSlots, InitialPlayer initialPlayer, Clock clock) {
        this.playerRepository = playerRepository;
        this.characterRepository = characterRepository;
        this.characterTypes = characterTypes;
        this.equipmentSlots = equipmentSlots;
        this.initialPlayer = initialPlayer;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>トランザクション境界は呼び出し側が持ち（§8.2 手順8）、本メソッドでは開始しない。
     * 呼び出し側の付け忘れを防ぐため {@code Propagation.MANDATORY} で既存トランザクションを必須にする。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Player initialize(String userId) {
        Instant now = clock.instant();

        Player player = createPlayer(userId, now);
        createSettings(player.getId());
        Character character = createCharacter(player.getId(), now);
        createEquipSlots(character.getId());
        grantInitialItems(player.getId());

        return player;
    }

    /** 手順2: プレイヤーを既定値で作る。塔外のため塔関連と交戦中の敵は NULL のままにする。 */
    private Player createPlayer(String userId, Instant now) {
        Player player = new Player();
        player.setId(newId());
        player.setUserId(userId);
        player.setGold(0L);
        player.setTowerMode(DEFAULT_TOWER_MODE);
        player.setHpThreshold(DEFAULT_HP_THRESHOLD);
        player.setRunGold(0L);
        player.setHighestFloor(0);
        player.setLastTickAt(now);
        player.setCreatedAt(now);
        playerRepository.save(player);
        return player;
    }

    /** 手順3: プレイヤー設定を既定値で作る。自動売却は既定で無効（NULL）。 */
    private void createSettings(String playerId) {
        PlayerSettings settings = new PlayerSettings();
        settings.setId(newId());
        settings.setPlayerId(playerId);
        settings.setPotionThreshold(DEFAULT_POTION_THRESHOLD);
        settings.setBattleLogCount(DEFAULT_BATTLE_LOG_COUNT);
        settings.setToastEnabled(DEFAULT_TOAST_ENABLED);
        playerRepository.saveSettings(settings);
    }

    /** 手順4: 初期キャラを1体作る。ステータスはタイプ別 LV1 基礎値をそのまま写し、全快で始める。 */
    private Character createCharacter(String playerId, Instant now) {
        InitialCharacterData initial = initialPlayer.character();
        CharacterTypeData type = characterTypes.all().get(initial.type());

        Character character = new Character();
        character.setId(newId());
        character.setPlayerId(playerId);
        character.setName(initial.name());
        character.setType(initial.type());
        character.setLevel(initial.level());
        character.setExp(0L);
        character.setMaxHp(type.hp());
        character.setHp(type.hp());
        character.setBaseAtk(type.atk());
        character.setBaseDef(type.def());
        character.setBaseSpd(type.spd());
        character.setLimitBreak(0);
        character.setSkillPoints(0);
        character.setCreatedAt(now);
        characterRepository.save(character);
        return character;
    }

    /** 手順5: 装備スロット9種を未装備（{@code equipment_id} は NULL）で作る。 */
    private void createEquipSlots(String characterId) {
        for (String slotId : equipmentSlots.all().keySet()) {
            CharacterEquipSlot slot = new CharacterEquipSlot();
            slot.setCharacterId(characterId);
            slot.setSlot(slotId);
            characterRepository.saveEquipSlot(slot);
        }
    }

    /** 手順6: 初期所持アイテムを種類ごとに1行付与する。定義が空なら何も付与しない。 */
    private void grantInitialItems(String playerId) {
        for (InitialItemData initial : initialPlayer.items()) {
            InventoryItem item = new InventoryItem();
            item.setId(newId());
            item.setPlayerId(playerId);
            item.setItemId(initial.id());
            item.setQuantity(initial.quantity());
            playerRepository.saveItem(item);
        }
    }

    /** 主キーの採番（UUID4）。列はいずれも {@code varchar(36)}（tech_db/player.md）。 */
    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
