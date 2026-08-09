package com.afkgame.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.afkgame.domain.model.InventoryItem;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.model.PlayerSettings;

/**
 * {@link PlayerRepository} のテスト。
 *
 * <p>列・NULL 可否・一意制約の正は docs/tech/basic/tech_db/player.md §1（{@code players}）・
 * §2（{@code player_settings}）と docs/tech/basic/tech_db/item.md §3（{@code inventory_items}）。
 *
 * <p>観点: 主体 Entity（players）と従 Entity（player_settings・inventory_items）それぞれの
 * 全列往復・NULL 許容列の両側・一意制約・未登録の経路。従 Entity の操作を主体側の Repository が持つ
 * 構成は docs/process/coding_standards_backend/domain.md §3 #3。
 */
class PlayerRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private PlayerRepository playerRepository;

    /** 主体 Entity: {@code players}。 */
    @Nested
    class Testプレイヤー {

        /** 塔に潜っている状態の Player（NULL 許容列にすべて値が入る）。 */
        private Player 塔内のプレイヤー(String userId) {
            Player player = new Player();
            player.setId(uuid("player"));
            player.setUserId(userId);
            player.setGold(1000L);
            player.setCurrentTowerId("tower_001");
            player.setCurrentFloor(12);
            player.setTargetFloor(20);
            player.setTowerMode("stop_on_clear");
            player.setHpThreshold(0.4);
            player.setCurrentEnemyId("slime_001");
            player.setCurrentEnemyHp(37);
            player.setRunGold(250L);
            player.setHighestFloor(15);
            player.setLastTickAt(FIXED_NOW);
            player.setCreatedAt(FIXED_NOW);
            return player;
        }

        /** 塔の外にいる状態の Player（NULL 許容列がすべて NULL）。 */
        private Player 塔外のプレイヤー(String userId) {
            Player player = new Player();
            player.setId(uuid("player"));
            player.setUserId(userId);
            player.setGold(0L);
            player.setTowerMode("auto_repeat");
            player.setHpThreshold(0.3);
            player.setRunGold(0L);
            player.setHighestFloor(0);
            player.setLastTickAt(FIXED_NOW);
            player.setCreatedAt(FIXED_NOW);
            return player;
        }

        @Test
        void 塔内のプレイヤーを登録するとすべての列が往復する() {
            Player expected = 塔内のプレイヤー(givenUser());

            playerRepository.save(expected);

            assertThat(playerRepository.findById(expected.getId()))
                    .usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void 塔外のプレイヤーは進行状況の列がNULLのまま往復する() {
            Player expected = 塔外のプレイヤー(givenUser());

            playerRepository.save(expected);

            Player actual = playerRepository.findById(expected.getId());
            assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
            assertThat(actual.getCurrentTowerId()).isNull();
            assertThat(actual.getCurrentFloor()).isNull();
            assertThat(actual.getTargetFloor()).isNull();
            assertThat(actual.getCurrentEnemyId()).isNull();
            assertThat(actual.getCurrentEnemyHp()).isNull();
        }

        @Test
        void 同じユーザーに2人目のプレイヤーは作れない() {
            String userId = givenUser();
            playerRepository.save(塔外のプレイヤー(userId));

            assertThatThrownBy(() -> playerRepository.save(塔外のプレイヤー(userId)))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void ユーザーIDでプレイヤーを引ける() {
            String userId = givenUser();
            Player expected = 塔外のプレイヤー(userId);
            playerRepository.save(expected);

            assertThat(playerRepository.findByUserId(userId))
                    .usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void 未登録のIDで引くとnullを返す() {
            assertThat(playerRepository.findById("player_not_exists")).isNull();
        }

        @Test
        void プレイヤーを持たないユーザーIDで引くとnullを返す() {
            assertThat(playerRepository.findByUserId(givenUser())).isNull();
        }
    }

    /** 従 Entity: {@code player_settings}（players と 1:1）。 */
    @Nested
    class Test設定 {

        private PlayerSettings 設定(String playerId, String autoSellRarity) {
            PlayerSettings settings = new PlayerSettings();
            settings.setId(uuid("settings"));
            settings.setPlayerId(playerId);
            settings.setPotionThreshold(0.3);
            settings.setBattleLogCount(50);
            settings.setToastEnabled(true);
            settings.setAutoSellRarity(autoSellRarity);
            return settings;
        }

        @Test
        void 自動売却を設定した状態がすべての列で往復する() {
            PlayerSettings expected = 設定(givenPlayer(), "uncommon");

            playerRepository.saveSettings(expected);

            assertThat(playerRepository.findSettingsByPlayerId(expected.getPlayerId()))
                    .usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void 自動売却なしの設定はレアリティがNULLのまま往復する() {
            PlayerSettings expected = 設定(givenPlayer(), null);

            playerRepository.saveSettings(expected);

            PlayerSettings actual = playerRepository.findSettingsByPlayerId(expected.getPlayerId());
            assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
            assertThat(actual.getAutoSellRarity()).isNull();
        }

        @Test
        void 同じプレイヤーに2件目の設定は作れない() {
            String playerId = givenPlayer();
            playerRepository.saveSettings(設定(playerId, null));

            assertThatThrownBy(() -> playerRepository.saveSettings(設定(playerId, null)))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void 設定を持たないプレイヤーIDで引くとnullを返す() {
            assertThat(playerRepository.findSettingsByPlayerId(givenPlayer())).isNull();
        }
    }

    /** 従 Entity: {@code inventory_items}。 */
    @Nested
    class Test所持品 {

        private InventoryItem 所持品(String playerId, String itemId, int quantity) {
            InventoryItem item = new InventoryItem();
            item.setId(uuid("inventory"));
            item.setPlayerId(playerId);
            item.setItemId(itemId);
            item.setQuantity(quantity);
            return item;
        }

        @Test
        void 所持品を登録するとすべての列が往復する() {
            String playerId = givenPlayer();
            InventoryItem expected = 所持品(playerId, "hp_potion", 5);

            playerRepository.saveItem(expected);

            assertThat(playerRepository.findAllItemsByPlayerId(playerId))
                    .singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        /**
         * 0件（所持品なし）／1件（ゲスト作成直後の hp_potion×5）／2件（複数種類を所持）を分ける。
         */
        @ParameterizedTest(name = "登録{0}件なら{0}件返す")
        @ValueSource(ints = {0, 1, 2})
        void プレイヤーIDで引くと登録件数分を返す(int count) {
            String playerId = givenPlayer();
            for (int i = 0; i < count; i++) {
                playerRepository.saveItem(所持品(playerId, "item_" + i, 1));
            }

            assertThat(playerRepository.findAllItemsByPlayerId(playerId)).hasSize(count);
        }

        @Test
        void プレイヤーIDで引くと他プレイヤーの所持品は含まない() {
            String playerId = givenPlayer();
            InventoryItem mine = 所持品(playerId, "hp_potion", 5);
            playerRepository.saveItem(mine);
            playerRepository.saveItem(所持品(givenPlayer(), "hp_potion", 3));

            assertThat(playerRepository.findAllItemsByPlayerId(playerId))
                    .extracting(InventoryItem::getId).containsExactly(mine.getId());
        }

        @Test
        void 同じプレイヤーと同じアイテムでは2行目を作れない() {
            String playerId = givenPlayer();
            playerRepository.saveItem(所持品(playerId, "hp_potion", 5));

            assertThatThrownBy(() -> playerRepository.saveItem(所持品(playerId, "hp_potion", 1)))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }
}
