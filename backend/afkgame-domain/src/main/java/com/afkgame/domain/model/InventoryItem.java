package com.afkgame.domain.model;

/**
 * {@code inventory_items} テーブルの Entity。
 *
 * <p>列・一意制約の正は docs/tech/basic/tech_db/item.md §3。同一アイテムは1行にまとめ、
 * 数量で表す（{@code uq_inventory_items_player_item}）。
 *
 * <p>分類・価格・回復割合は列に持たず、{@code itemId} からマスターデータを引いて得る（同 §3）。
 */
public class InventoryItem {

    private String id;
    private String playerId;
    private String itemId;
    private int quantity;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
