# AFK GAME — データ設計

> [tech_spec.md](../tech_spec.md) §1。ゲーム仕様は [game_spec.md](../../design/game_spec.md)、DB構造は [er_diagram.md](../../diagrams/er_diagram.md)。

## 1.1 ゲーム状態（API レスポンス: `GET /api/game/state`）

`GameStateResponse` の JSON 例は [tech_data/game_state.md](tech_data/game_state.md) が持つ。本節は**トップレベルキーの一覧**と **`towersCleared` のキー体系**を担当する。フロント・バック間は camelCase で、エンドポイント定義の正は [tech_api.md](tech_api.md)「ゲーム状態」。

| キー | 内容 | Phase | 掲載節 |
|------|------|-------|--------|
| `player` | 所持金・現在の塔／階・目標階・周回モード・撤退HP閾値・最高到達階・戦闘中の敵ID／HP | 1〜 | §1.1.1 |
| `settings` | ポーション自動使用閾値（0.1〜0.5・0.1刻み）・戦闘ログ表示件数・トースト通知・自動売却レアリティ。選択肢の正は [systems/ui.md](../../design/systems/ui.md) | 1〜 | §1.1.1 |
| `potions` | ポーション所持数 | 1〜 | §1.1.1 |
| `towersCleared` | 塔ごとの到達記録（キー体系は下記） | 1〜 | §1.1.1 |
| `currentEnemy` | 戦闘中の敵（`null` = 非戦闘） | 1〜 | §1.1.1 |
| `characters` | 所持キャラクターの配列（Phase 3〜 はレアリティ・限界突破・実効ステータス・スキルを含む） | 1〜 | §1.1.2 |
| `equipment` | 所持装備の全件 | 2〜 | §1.1.3 |
| `equipped` | スロット → 装備ID のマッピング | 2〜 | §1.1.3 |
| `party` | パーティ（最大4人） | 3〜 | §1.1.1 |
| `inventory` | 素材インベントリ | 4〜 | §1.1.3 |
| `base` | 施設レベル（構造は §1.6） | 4〜 | §1.1.3 |
| `materials` | 素材所持数 | 4〜 | §1.1.3 |
| `bossRush` | ボスラッシュ状態 | 5〜 | §1.1.3 |

- Phase 3以降のキーは JSON 例では**コメントで予約**してあり、実体化は該当Phaseの基本設計で行う
- 日替わりショップの状態は本JSONに含めない（Phase 2〜: `GET /api/shop/lineup` で取得。[tech_shop.md](../detail/tech_shop.md)）
- 転生データ（Phase 5〜）はトップレベルではなく `characters` の各要素が `prestige` として持つ

> **`towersCleared` のキー体系**: キーは塔ID。イベントダンジョン（Phase 5〜）のみ `{towerId}_{difficulty}` の形で難易度を畳み込み、難易度別に到達記録を持つ（`difficulty` = `beginner` / `intermediate` / `advanced`）。通常塔・深淵の塔はサフィックスを付けないため、Phase 1〜4 のセーブデータは移行処理なしでそのまま有効。値の型は全エントリ共通で `{ cleared, highestFloor }`。キーの組み立てはサーバーが行う（[tech_api.md](tech_api.md)「イベントダンジョン」）。

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
  "id": "equip_uuid",
  "baseId": "sword",
  "slot": "weapon",
  "rarity": "common",
  "statAtk": 5,
  "enhanceLevel": 3,
  "level": 5
}
```

- `enhanceLevel`: 現在の強化段階（0〜鍛冶屋LVの上限値）
- 実効ステータス: `表示値 = 元のステータス + (enhanceLevel × 基礎値の10%)`
