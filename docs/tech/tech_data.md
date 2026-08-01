# AFK GAME — データ設計

> [tech_spec.md](tech_spec.md) §1。ゲーム仕様は [game_spec.md](../design/game_spec.md)、DB構造は [er_diagram.md](../../diagrams/er_diagram.md)、変更履歴は親に集約（[§9](tech_spec.md#9-変更履歴)）。

## 1.1 ゲーム状態（API レスポンス: `GET /api/game/state`）

> **注意**: 以下は `GameStateResponse` の実際のJSON構造。フロント・バック間で camelCase を使用。Phase 3以降のフィールドはコメントで記載。

```jsonc
{
  "player": {
    "id": "uuid-string",
    "gold": 1500,
    "currentTowerId": "goblin_tower",  // null = 塔外待機中
    "currentFloor": 3,                 // null = 塔外待機中（currentTowerIdと連動）
    "targetFloor": 10,                 // null = 塔外待機中
    "towerMode": "auto_repeat",        // "auto_repeat" | "stop_on_clear"
    "hpThreshold": 0.3,                // 撤退条件HP閾値（0.0〜1.0）
    "highestFloor": 12,
    "currentEnemyId": "goblin",        // null = 戦闘中でない
    "currentEnemyHp": 8                // null = 戦闘中でない
  },
  // "party": ["hero_001", ...],       // Phase 3〜: パーティ（最大4人）
  "characters": [
    {
      "id": "hero_001",
      "name": "勇者",
      "type": "melee",                 // Phase 3〜: タイプ（melee/magic/holy/agile）
      "level": 5,
      "exp": 120,
      "hp": 150,                       // 現在HP
      "maxHp": 150,                    // 基礎最大HP
      "baseAtk": 25,                   // 基礎ATK
      "baseDef": 12,                   // 基礎DEF
      "baseSpd": 10,                   // 基礎SPD
      "effectiveMaxHp": 150            // 装備込み最大HP（装備未装着時はmaxHpと同値）
      // "limitBreak": 0,              // Phase 3〜: 限界突破回数（0-5）
      // "skills": { ... },            // Phase 3〜: スキル情報
      // "prestige": { ... }           // Phase 5〜: 転生データ
    }
  ],
  "settings": {
    "potionThreshold": 0.3,            // ポーション自動使用閾値（0.1〜0.5、0.1刻み。デフォルト0.3）
    "battleLogCount": 50,              // 戦闘ログ表示件数（20/50/100/200）
    "toastEnabled": true,              // トースト通知ON/OFF
    "autoSellRarity": null             // Phase 2〜: 自動売却レアリティ（null/common/uncommon）
  },
  "potions": {
    "hp_potion": 10
  },
  "towersCleared": {                   // 塔別の到達記録。目標階の上限 = min(highestFloor + 1, totalFloors)
    "goblin_tower": { "cleared": true, "highestFloor": 20 },
    "forest_tower": { "cleared": false, "highestFloor": 15 },
    "abyss_tower": { "cleared": false, "highestFloor": 87 }   // 深淵の塔（無限塔）。cleared は常に false
  },
  "currentEnemy": {                    // null = 現在戦闘中でない
    "id": "goblin",
    "name": "ゴブリン",
    "hp": 8,
    "maxHp": 35,
    "level": 2
  },
  "equipment": [                       // Phase 2〜: プレイヤーの全装備
    {
      "id": "equip_uuid",
      "baseId": "sword",
      "slot": "weapon",
      "rarity": "uncommon",
      "level": 5,
      "enhanceLevel": 0,
      "statAtk": 8,
      "statDef": null,
      "statHp": null,
      "statSpd": null,
      "lifesteal": null,
      "isTwoHanded": false,
      "locked": false,
      "acquiredAt": "2026-03-15T12:00:00Z"
    }
  ],
  "equipped": {                        // Phase 2〜: スロット→装備IDのマッピング
    "weapon": "equip_uuid",
    "shield": null,
    "head": null,
    "body": null,
    "arms": null,
    "waist": null,
    "legs": null,
    "ears": null,
    "ring": null
  }
  // "inventory": [],                  // Phase 4〜: 素材インベントリ
  // "shop": { ... },                  // Phase 2〜: 日替わりショップ状態
  // "base": { ... },                  // Phase 4〜: 施設レベル
  // "materials": { ... },             // Phase 4〜: 素材所持数
  // "bossRush": { ... }               // Phase 5〜: ボスラッシュ状態
}
```

## 1.2 敵データ定義例
```json
{
  "enemies": [
    {
      "id": "slime",
      "name": "スライム",
      "level": 1,
      "stats": { "hp": 20, "atk": 5, "def": 2, "spd": 3 },
      "rewards": { "gold": 5, "exp": 10 },
      "dropTable": [
        { "itemId": "potion", "rate": 0.1 }
      ]
    }
  ]
}
```

> 敵データはグローバル定義。各塔の `floorEncounters` で `enemyId` を参照する設計（塔ごとの所属情報は持たない）。

## 1.3 戦闘ログデータ構造
```json
{
  "tickNumber": 142,
  "timestamp": 1709856030000,
  "entries": [
    { "type": "attack", "actor": "勇者", "target": "スライム", "damage": 12 },
    { "type": "skill", "actor": "勇者", "skillId": "sword_1", "skillName": "強撃", "target": "スライム", "damage": 55 },
    { "type": "heal", "actor": "僧侶", "skillId": "heal_1", "skillName": "ヒール", "target": "勇者", "amount": 40 },
    { "type": "buff", "actor": "魔法使い", "skillId": "buff_1", "skillName": "力の祝福", "target": "全体", "effect": "ATK+20%", "duration": 3 },
    { "type": "attack", "actor": "スライム", "target": "勇者", "damage": 3 },
    { "type": "defeat", "target": "スライム", "rewards": { "gold": 5, "exp": 10 } }
  ]
}
```

## 1.4 塔データ定義例
```json
{
  "id": "forest_tower",
  "dungeonId": "dungeon_001",
  "name": "森の塔",
  "floors": 30,
  "unlockCondition": { "type": "tower_clear", "towerId": "goblin_tower" },
  "modifiers": [],
  "floorEncounters": {
    "1": [
      { "enemyId": "wild_boar", "weight": 70 },
      { "enemyId": "giant_snake", "weight": 30 }
    ],
    "2": [
      { "enemyId": "wild_boar", "weight": 50 },
      { "enemyId": "giant_snake", "weight": 50 }
    ],
    "30": [
      { "enemyId": "behemoth", "weight": 100 }
    ]
  }
}
```

- `unlockCondition`: 解放条件。`type: "tower_clear"` は指定塔のボス討伐が条件
- `modifiers`: 環境効果の配列（ダンジョン1の塔は空配列）
- `floorEncounters`: 各階のエンカウントプール。`weight` は相対的な出現確率

## 1.5 環境効果（modifier）定義例
ダンジョン2以降の塔で使用。

```json
{
  "modifiers": [
    {
      "id": "spd_debuff_15",
      "type": "stat_modifier",
      "target": "player",
      "stat": "spd",
      "value": -0.15,
      "description": "足元の泥が動きを鈍らせる"
    },
    {
      "id": "regen_per_floor",
      "type": "recovery",
      "trigger": "floor_clear",
      "value": 0.03,
      "description": "清浄な水場: 階クリア後にHP 3%回復"
    },
    {
      "id": "poison_fog",
      "type": "dot",
      "trigger": "turn_start",
      "value": 0.02,
      "description": "毒霧が充満している"
    }
  ]
}
```

| type | 処理タイミング | 計算方法 |
|------|-------------|---------|
| `stat_modifier` | 入塔時 + LVアップ時 | `effective_stat = base_stat × (1 + value)` |
| `dot` | 各ターン行動前 | `damage = floor(maxHP × value)`、最低1 |
| `recovery` | 階クリア後 | `heal = floor(maxHP × value)` |
| `restriction` | ポーション判定時 | `no_potion`: 使用不可、`potion_half`: 回復量×0.5 |
| `bonus` | 報酬計算時 | `reward = base_reward × (1 + value)` |

## 1.6 施設データ構造（Phase 4〜）

施設レベルは `base` オブジェクトでプレイヤーごとに管理。`level: 0` は未建設を表す。

```json
{
  "base": {
    "tavern": { "level": 3 },
    "forge": { "level": 2 },
    "training_ground": { "level": 1 },
    "warehouse": { "level": 1 },
    "market": { "level": 0 }
  }
}
```

| 施設ID | 施設名 | 効果参照先 |
|--------|--------|----------|
| `tavern` | 酒場 | キャラスカウト（レアリティ上限） |
| `forge` | 鍛冶屋 | 装備強化上限・製作レアリティ・コスト倍率 |
| `training_ground` | 訓練場 | 控えキャラEXP獲得率 |
| `warehouse` | 倉庫 | アイテム所持上限 |
| `market` | 市場 | ゴールドボーナス倍率 |

## 1.7 装備強化データ構造（Phase 4〜）

強化済み装備はステータスに `enhanceLevel` フィールドを持つ。

```json
{
  "id": "iron_sword_001",
  "baseId": "iron_sword",
  "slot": "weapon",
  "rarity": "common",
  "stats": { "atk": 5 },
  "enhanceLevel": 3,
  "level": 5
}
```

- `enhanceLevel`: 現在の強化段階（0〜鍛冶屋LVの上限値）
- 実効ステータス: `表示値 = 元のステータス + (enhanceLevel × 基礎値の10%)`
