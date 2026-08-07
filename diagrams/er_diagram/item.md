# ER図 — 装備・アイテム・ショップ・施設

> 親: [er_diagram.md](../er_diagram.md)。**DBスキーマの正は** [tech_db/item.md](../../docs/tech/basic/tech_db/item.md) であり、本図は視覚化として属性を再掲する（食い違いは定義書側へ揃える）。データ構造は [tech_data.md](../../docs/tech/basic/tech_data.md)、日替わりショップの生成・購入は [tech_shop.md](../../docs/tech/detail/tech_shop.md)。

## 装備・アイテム系

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
erDiagram
    Player ||--o{ Equipment : "owns"
    Player ||--o{ InventoryItem : "has"
    CharacterEquipSlot }o--o| Equipment : "equips"
    InventoryItem }o--|| ItemMaster : "references"

    Equipment {
        uuid id PK
        uuid player_id FK "references Player.id"
        string base_id "装備マスターID"
        enum slot "weapon/shield/head/body/arms/waist/legs/ears/ring"
        enum rarity "common/uncommon/rare/epic/legendary"
        int level "装備レベル (ドロップ=敵LV / ショップ=最高到達階層)"
        int enhance_level "強化段階 0-10"
        int stat_atk "nullable, ATK値"
        int stat_def "nullable, DEF値"
        int stat_hp "nullable, HP値"
        int stat_spd "nullable, SPD値"
        float lifesteal "nullable, HP吸収率 0.03-0.08"
        boolean is_two_handed "両手武器フラグ"
        boolean locked "売却ロック"
        datetime acquired_at
    }

    InventoryItem {
        uuid id PK
        uuid player_id FK, UK "references Player.id（item_id と複合一意）"
        string item_id UK "マスター参照 ItemMaster.id（DB外部キーなし・player_id と複合一意）"
        int quantity "所持数"
    }

    ItemMaster {
        string id PK "例: hp_potion, enhance_stone"
        string name "アイテム名"
        enum category "potion / material / currency"
        int stack_limit "所持上限 (potion:99, material:9999)"
        int price "nullable, ショップ購入価格"
        int sell_price "nullable, 売却価格"
        float heal_ratio "nullable, 回復割合 (potion用)"
        string description "アイテム説明"
    }
```

> **注**: `ItemMaster` はDBテーブルではなく、コード内定義のマスターデータ（`backend/app/master_data/items.py`）。`InventoryItem.item_id` は他のマスター参照列と同じく `FK` タグを付けず、`InventoryItem }o--|| ItemMaster` のリレーション線が論理参照を示す（DBレベルのFK制約はない。親 [tech_db.md](../../docs/tech/basic/tech_db.md) §4-6）。`InventoryItem` 側は `category` を列として持たず、`item_id` から `ItemMaster` を引いて得る。

## ショップ・施設系

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
erDiagram
    Player ||--o| ShopDailyState : "has"
    Player ||--o{ Facility : "has max 5"

    ShopDailyState ||--o{ ShopDailySlot : "has 5 slots"

    ShopDailyState {
        uuid id PK
        uuid player_id FK, UK "references Player.id（プレイヤーごとに1件）"
        datetime reset_at "次回リセット時刻 (00:00 UTC)"
    }

    ShopDailySlot {
        uuid id PK
        uuid shop_daily_state_id FK, UK "references ShopDailyState.id（slot_index と複合一意）"
        int slot_index UK "0-4 枠番号"
        enum category "weapon / armor / accessory"
        string base_id "装備マスターID"
        enum rarity "common / uncommon / rare"
        int level "装備レベル (= 最高到達階層, 下限1)"
        int stat_atk "nullable, ATK値"
        int stat_def "nullable, DEF値"
        int stat_hp "nullable, HP値"
        int stat_spd "nullable, SPD値"
        int price "購入価格"
        boolean sold "購入済みフラグ"
    }

    Facility {
        uuid id PK "Phase 4〜 (未実装)"
        uuid player_id FK, UK "references Player.id（facility_type と複合一意）"
        enum facility_type UK "tavern/forge/training_ground/warehouse/market"
        int level "0-10 (0=未建設)"
    }
```
