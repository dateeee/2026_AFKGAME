# AFK GAME — ゲーム状態JSON

> 親: [tech_data.md](../tech_data.md) §1.1。**トップレベルキーの一覧と `towersCleared` のキー体系は親が正**であり、本書は `GET /api/game/state` のレスポンス（`GameStateResponse`）の JSON 例を持つ。
> エンドポイント定義の正は [tech_api/core.md](../tech_api/core.md)「ゲーム状態」、永続化スキーマは [tech_db.md](../tech_db.md)。

> **注意**: 以下はトップレベルキーごとに分けて示した実際のJSON構造で、**3節を連結すると1つのレスポンスになる**。フロント・バック間で camelCase を使用。Phase 3以降のフィールドはコメントで記載。

## 1.1.1 プレイヤー状態

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
  "settings": {
    "potionThreshold": 0.3,            // ポーション自動使用閾値（既定0.3。範囲・刻みは親 §1.1）
    "battleLogCount": 50,              // 戦闘ログ表示件数（20/50/100）
    "toastEnabled": true,              // トースト通知ON/OFF
    "autoSellRarity": null             // Phase 2〜: 自動売却レアリティ（null/common/uncommon）
  },
  "potions": {
    "hp_potion": 10
  },
  "towersCleared": {                   // 到達記録。キーは塔ID。目標階の上限は tech_api/gameplay.md「操作系」が正
    "goblin_tower": { "cleared": true, "highestFloor": 20 },
    "forest_tower": { "cleared": false, "highestFloor": 15 },
    "abyss_tower": { "cleared": false, "highestFloor": 87 },  // 深淵の塔（無限塔）。totalFloors を持たず上限は highestFloor + 1 のみ。cleared は常に false
    "trial_maze_beginner": { "cleared": true, "highestFloor": 10 }   // Phase 5〜: イベントダンジョンのみ難易度を畳み込む
  },
  "currentEnemy": {                    // null = 現在戦闘中でない
    "id": "goblin",
    "name": "ゴブリン",
    "hp": 8,
    "maxHp": 35,
    "level": 2
  }
}
```

## 1.1.2 キャラクター

```jsonc
{
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
      "effectiveMaxHp": 150            // 装備込み最大HP（装備未装着時はmaxHpと同値）。Phase 3〜はレアリティ・限界突破も込み
      // "masterId": "hero_002",       // Phase 4〜: マスターキャラのID（重複・限界突破の同一性判定）
      // "rarity": "common",           // Phase 3〜: キャラのレアリティ（common〜legendary）
      // "limitBreak": 0,              // Phase 3〜: 限界突破回数（0-5）
      // "effectiveAtk": 25,           // Phase 3〜: 実効ATK（基礎 × レアリティ倍率 × 限界突破ボーナス + 装備）
      // "effectiveDef": 12,           // Phase 3〜: 実効DEF（同上）
      // "effectiveSpd": 10,           // Phase 3〜: 実効SPD（同上）
      // "skills": { ... },            // Phase 3〜: スキル情報
      // "prestige": { ... }           // Phase 5〜: 転生データ
    }
  ]
}
```

## 1.1.3 装備と予約キー

```jsonc
{
  "equipment": [                       // Phase 2〜: プレイヤーの全装備
    {
      "id": "equip_uuid",
      "baseId": "sword",
      "slot": "weapon",
      "rarity": "uncommon",
      "level": 5,
      "enhanceLevel": 0,
      "statAtk": 11,
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
  // "base": { ... },                  // Phase 4〜: 施設レベル（構造は tech_data.md §1.6）
  // "materials": { ... },             // Phase 4〜: 素材所持数
  // "bossRush": { ... }               // Phase 5〜: ボスラッシュ状態
}
```
