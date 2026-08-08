package com.afkgame.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.afkgame.domain.model.InventoryItem;

/**
 * {@code inventory_items} テーブルの Mapper のテスト。
 *
 * <p>列・一意制約の正は docs/tech/basic/tech_db/item.md §3。同一アイテムは1行にまとめ、数量で表す
 * （{@code uq_inventory_items_player_item}）。
 *
 * <p>観点: 全列の往復・プレイヤー単位の取得件数（0件 / 1件 / 2件）・他プレイヤーの混入・
 * (player_id, item_id) の一意制約。
 */
class InventoryItemMapperTest extends MapperTestSupport {

    @Autowired
    private InventoryItemMapper inventoryItemMapper;

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

        inventoryItemMapper.insert(expected);

        assertThat(inventoryItemMapper.selectByPlayerId(playerId))
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
            inventoryItemMapper.insert(所持品(playerId, "item_" + i, 1));
        }

        assertThat(inventoryItemMapper.selectByPlayerId(playerId)).hasSize(count);
    }

    @Test
    void プレイヤーIDで引くと他プレイヤーの所持品は含まない() {
        String playerId = givenPlayer();
        InventoryItem mine = 所持品(playerId, "hp_potion", 5);
        inventoryItemMapper.insert(mine);
        inventoryItemMapper.insert(所持品(givenPlayer(), "hp_potion", 3));

        assertThat(inventoryItemMapper.selectByPlayerId(playerId))
                .extracting(InventoryItem::getId).containsExactly(mine.getId());
    }

    @Test
    void 同じプレイヤーと同じアイテムでは2行目を作れない() {
        String playerId = givenPlayer();
        inventoryItemMapper.insert(所持品(playerId, "hp_potion", 5));

        assertThatThrownBy(() -> inventoryItemMapper.insert(所持品(playerId, "hp_potion", 1)))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
