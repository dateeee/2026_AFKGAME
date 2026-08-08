# クラス図 — 装備・アイテム・ショップ・施設

> 親: [class_diagram.md](../class_diagram.md)。仕様は [systems/equipment.md](../../design/systems/equipment.md) / [systems/economy.md](../../design/systems/economy.md)。

## 装備・アイテム

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
classDiagram
    direction TB

    class Equipment {
        +uuid id
        +string baseId
        +EquipSlot slot
        +Rarity rarity
        +int level  ドロップ=敵LV / ショップ=最高到達階層
        +int enhanceLevel  0-10
        +int statAtk  nullable
        +int statDef  nullable
        +int statHp  nullable
        +int statSpd  nullable
        +float lifesteal  nullable 0.03-0.08
        +bool isTwoHanded
        +bool locked
        +datetime acquiredAt
        +calcEffectiveStats() Stats
        +calcEnhancedStats() Stats
        +calcSellPrice() int
        +canEnhance(forgeLevel) bool
        +enhance()
        +calcBaseValue() int
        +getRarityMultiplier() float
    }

    class EquipSlot {
        <<enumeration>>
        weapon  武器
        shield  盾 片手武器時のみ
        head  頭
        body  胴体
        arms  腕
        waist  腰
        legs  足
        ears  耳
        ring  指輪
    }

    class Rarity {
        <<enumeration>>
        common  白 x1.0 付与1-2
        uncommon  緑 x1.3 付与2
        rare  青 x1.6 付与2-3
        epic  紫 x2.0 付与3
        legendary  橙 x2.5 付与4全種
    }

    class InventoryItem {
        +string itemId
        +int quantity
        +add(amount)
        +remove(amount) bool
        +isFull() bool
    }

    class ItemCategory {
        <<enumeration>>
        potion  ポーション 上限99
        material  素材
        currency  換金アイテム
    }

    class DropEntry {
        +string itemId
        +float dropRate  0.0-1.0
        +int quantity
        +roll() bool
    }

    Equipment --> EquipSlot
    Equipment --> Rarity
    InventoryItem ..> ItemCategory : itemId から ItemMaster 経由で参照
```

- `InventoryItem` は `category` を**自身の列として持たない**。カテゴリは `itemId` から `ItemMaster`（コード内マスターデータ）を引いて得る派生値（[er_diagram/item.md](../er_diagram/item.md)）
- `Equipment` のステータス列名は ER図・[tech_data.md](../../tech/basic/tech_data.md) と揃えて `statAtk` / `statDef` / `statHp` / `statSpd`（`Stats.atk` 等のキャラクター側ステータスと区別する）

## ショップ

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
classDiagram
    direction TB

    class Shop {
        +PotionStock[] permanentStock  常設商品
        +DailyItem[5] dailyItems  日替わり5枠
        +datetime resetAt  次回リセット時刻
        +buy(itemId, qty) bool
        +buyDaily(slotIndex) bool
        +canBuy(itemId, qty, playerGold) bool
        +getDailyLineup() DailyItem[]
        +checkReset()
        +refreshDaily(highestFloor, rng)  rngは Random インスタンス
        +getAvailableRarities(highestFloor) Rarity[]
    }

    class DailyItem {
        +int slotIndex  0-4
        +string baseId  装備マスターID
        +EquipCategory category  weapon/armor/accessory
        +Rarity rarity  common-rare
        +int level  装備レベル (= 最高到達階層, 下限1)
        +int statAtk  nullable
        +int statDef  nullable
        +int statHp  nullable
        +int statSpd  nullable
        +int price  固定価格テーブル
        +bool sold  購入済みか
    }

    class EquipCategory {
        <<enumeration>>
        weapon  武器
        armor  防具: 盾/頭/胴体/腕/腰/足
        accessory  アクセサリー: 耳/指輪
    }

    class PotionStock {
        +string itemId
        +int quantity
        +int price
    }

    Shop "1" --> "5" DailyItem
    Shop "1" --> "*" PotionStock
    DailyItem --> EquipCategory
```

- 抽選結果（レアリティ・レベル・ステータス）は生成時に確定して保存する（[tech_shop.md §5](../../tech/detail/tech_shop.md)）
- `EquipCategory`（3値）は `EquipSlot`（9値）とは別概念。カテゴリ→スロットの対応は [systems/equipment.md §2.4](../../design/systems/equipment.md)

## 施設・ボスラッシュ

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
classDiagram
    direction TB

    class Facility {
        +FacilityType type
        +int level  0-10
        +getEffect() float
        +getEffectDescription() string
        +canUpgrade() bool
        +getUpgradeCost() FacilityCost
        +upgrade()
        +build()
    }

    class FacilityCost {
        +int gold
        +int enhanceStone
        +int magicCrystal
        +int rareOre
        +int ancientFragment
    }

    class FacilityType {
        <<enumeration>>
        tavern  酒場: スカウト LV→レアリティ上限
        forge  鍛冶屋: 強化上限/製作レアリティ/コスト倍率
        training_ground  訓練場: 控えEXP獲得率 5-50%
        warehouse  倉庫: 所持上限 70-300枠 (未建設50)
        market  市場: ゴールドボーナス +5-50%
    }

    class BossRushState {
        +bool isActive
        +int currentWave
        +int bestWave
        +int bestWaveHp  タイブレーク用
        +bigint accumulatedGold
        +bigint accumulatedExp
        +start()
        +retire() Rewards
        +getWaveEnemies(wave) Enemy[]
        +getWaveScaling(wave) float
        +checkMilestone(wave) MilestoneReward
        +calcWaveRewards(wave) Rewards
        +applyHpRecovery(party, wave)
        +resetCooldownsAndBuffs(party)
        +isHpRecoveryWave(wave) bool
    }

    class MilestoneReward {
        +int wave  5/10/15/20/25/30...
        +int enhanceStone
        +int magicCrystal
        +int rareOre
        +int ancientFragment
        +int legendaryEquipCount
        +bool claimed
    }

    Facility --> FacilityType
    Facility ..> FacilityCost
    BossRushState ..> MilestoneReward
```
