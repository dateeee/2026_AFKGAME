# AFK GAME — 技術仕様書

> プロジェクト概要は [CLAUDE.md](../CLAUDE.md)、ゲーム仕様は [game_spec.md](game_spec.md)、マスターデータは [master_data.md](master_data.md) を参照。
>
> 詳細仕様: [戦闘ログ・オフライン計算](tech_battle_offline.md) / [認証システム](tech_auth.md)

---

## 1. データ設計

### 1.1 ゲーム状態（API レスポンス: `GET /api/game/state`）

> **注意**: 以下は `GameStateResponse` の実際のJSON構造。フロント・バック間で camelCase を使用。Phase 3以降のフィールドはコメントで記載。

```jsonc
{
  "player": {
    "id": "uuid-string",
    "gold": 1500,
    "currentTowerId": "goblin_tower",  // null = 塔外待機中
    "currentFloor": 3,                 // null = 塔外待機中（currentTowerIdと連動）
    "targetFloor": 10,
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
  "towersCleared": {
    "goblin_tower": { "cleared": true, "highestFloor": 20 },
    "forest_tower": { "cleared": false, "highestFloor": 15 }
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

### 1.2 敵データ定義例
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

### 1.3 戦闘ログデータ構造
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

### 1.4 塔データ定義例
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

### 1.5 環境効果（modifier）定義例
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

### 1.6 施設データ構造（Phase 4〜）

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

### 1.7 装備強化データ構造（Phase 4〜）

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

---

## 2. ディレクトリ構成

```
2026_AFKGAME/
├── docs/
│   ├── design/                    # ゲームデザイン仕様
│   │   └── game_spec.md           # ゲーム仕様書（システム設計・バランス・UI）
│   ├── tech/                      # 技術仕様
│   │   ├── tech_spec.md           # 本仕様書（技術設計）
│   │   ├── tech_battle_offline.md # 戦闘ログ・オフライン計算仕様
│   │   └── tech_auth.md           # 認証システム仕様（Phase 2〜）
│   ├── data/                      # マスターデータ
│   │   ├── master_data.md         # マスターデータ定義（共通数値定義）
│   │   ├── towers/                # 塔別マスターデータ
│   │   │   ├── TOWERS_OVERVIEW.md # 全塔概要一覧（推奨LV・フロア数・ダンジョン構成）
│   │   │   ├── 000_テンプレート.md # 新規塔作成用テンプレート
│   │   │   ├── 001_ゴブリンの塔.md # ゴブリンの塔（敵・構成・ドロップ）
│   │   │   ├── 002_森の塔.md      # 森の塔（敵・構成・ドロップ）
│   │   │   ├── 003_獣の塔.md      # 獣の塔（敵・構成・ドロップ）
│   │   │   ├── 004_毒沼の塔.md    # 毒沼の塔（敵・構成・ドロップ）
│   │   │   ├── 005_業火の塔.md    # 業火の塔（敵・構成・ドロップ）
│   │   │   ├── 006_氷雪の塔.md    # 氷雪の塔（敵・構成・ドロップ）
│   │   │   ├── 007_砂漠の塔.md    # 砂漠の塔（敵・構成・ドロップ）
│   │   │   ├── 008_深海の塔.md    # 深海の塔（敵・構成・ドロップ）
│   │   │   ├── 009_黄昏の塔.md    # 黄昏の塔（敵・構成・ドロップ）
│   │   │   └── 010_天空の塔.md    # 天空の塔（敵・構成・ドロップ）
│   │   └── skills/                # スキル系統別マスターデータ
│   │       ├── SKILLS_OVERVIEW.md # スキルシステム概要・共通ルール
│   │       ├── 000_テンプレート.md # 新規系統作成用テンプレート
│   │       ├── 001_剣術系統.md     # 剣術系統（物理単体攻撃）
│   │       ├── 002_魔法系統.md     # 魔法系統（魔法攻撃）
│   │       ├── 003_回復系統.md     # 回復系統（HP回復・蘇生）
│   │       ├── 004_強化系統.md     # 強化系統（バフ）
│   │       ├── 005_弱体系統.md     # 弱体系統（デバフ・状態異常）
│   │       └── 006_生存術系統.md   # 生存術系統（耐久・防御）
│   ├── glossary.md                # 用語集（ゲーム・技術用語）
│   ├── open_specs.md              # 未確定仕様一覧
│   └── reviews/                   # 仕様レビュー結果（/doc-review コマンドで自動生成）
│
├── diagrams/                      # 設計図（Mermaid）
│   ├── er_diagram.md              # ER図（データベース設計）
│   ├── class_diagram.md           # クラス図（ドメインモデル）
│   ├── screen_transition.md       # 画面遷移図
│   ├── battle_flow.md             # 戦闘ターン処理フロー図
│   ├── system_architecture.md     # システム構成図
│   └── api_sequence.md            # APIシーケンス図
│
├── frontend/                      # Vue.js SPA
│   ├── src/
│   │   ├── App.vue                # ルートコンポーネント
│   │   ├── main.ts                # エントリーポイント
│   │   ├── router/
│   │   │   └── index.ts           # Vue Router 設定
│   │   ├── stores/                # Pinia ストア
│   │   │   ├── gameStore.ts       # ゲーム状態管理
│   │   │   ├── battleStore.ts     # 戦闘状態管理
│   │   │   ├── playerStore.ts     # プレイヤー情報
│   │   │   ├── equipmentStore.ts  # 装備管理（Phase 2〜）
│   │   │   └── authStore.ts       # 認証状態管理（Phase 2〜）
│   │   ├── composables/           # Composition API ロジック
│   │   │   ├── usePolling.ts      # ポーリング制御（tick API呼び出し）
│   │   │   ├── useBattleLocal.ts  # MVP用: フロント側tick計算（API未接続時）
│   │   │   └── useGameLoop.ts     # ゲーム起動・状態管理
│   │   ├── components/            # UIコンポーネント
│   │   │   └── equipment/         # 装備関連コンポーネント（Phase 2〜）
│   │   │       ├── EquipmentCard.vue      # 装備カード表示
│   │   │       ├── EquipmentCompare.vue   # 装備比較
│   │   │       ├── EquipmentInventory.vue # 装備インベントリ
│   │   │       └── EquipmentSlotGrid.vue  # 装備スロット一覧
│   │   ├── views/                 # ページコンポーネント
│   │   │   ├── GameView.vue       # メインゲーム画面
│   │   │   ├── LoginView.vue      # ログイン・登録統合画面（Phase 2〜。モード切替でログイン/登録を切り替え）
│   │   │   ├── RegisterView.vue   # /register → LoginView?mode=register へリダイレクト（Phase 2〜）
│   │   │   ├── SettingsView.vue   # 設定画面
│   │   │   ├── ShopView.vue       # ショップ画面（Phase 1〜）
│   │   │   ├── EquipmentView.vue  # 装備画面（Phase 2〜）
│   │   │   ├── PartyView.vue      # パーティ編成画面（Phase 3〜）
│   │   │   └── BaseView.vue       # 拠点画面（Phase 4〜）
│   │   ├── api/                   # API通信
│   │   │   ├── client.ts          # FastAPI との通信レイヤー
│   │   │   └── auth.ts            # 認証API（Phase 2〜）
│   │   ├── types/                 # TypeScript 型定義
│   │   │   └── game.ts            # ゲーム関連の型
│   │   ├── utils/                 # ユーティリティ
│   │   │   └── format.ts          # フォーマット関数
│   │   └── assets/
│   │       ├── icons/             # アイテム・装備アイコン
│   │       └── styles/
│   ├── index.html
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── package.json
│
├── backend/                       # FastAPI サーバー
│   ├── app/
│   │   ├── main.py                # FastAPI エントリーポイント
│   │   ├── config.py              # 設定・定数
│   │   ├── dependencies.py        # 依存性注入（認証・プレイヤー取得）
│   │   ├── middleware.py          # ミドルウェア（リクエストログ等）
│   │   ├── exceptions.py         # カスタム例外
│   │   ├── logging_config.py     # ログ設定
│   │   ├── models/                # SQLAlchemy モデル（DB定義）
│   │   │   ├── player.py
│   │   │   ├── character.py
│   │   │   ├── item.py
│   │   │   ├── equipment.py       # 装備モデル（Phase 2〜）
│   │   │   └── user.py            # User, RefreshToken, EmailVerificationToken（Phase 2〜）
│   │   ├── schemas/               # Pydantic スキーマ（API I/O）
│   │   │   ├── __init__.py        # CamelModel ベースクラス
│   │   │   ├── player.py
│   │   │   ├── battle.py
│   │   │   ├── equipment.py       # 装備スキーマ（Phase 2〜）
│   │   │   ├── shop.py            # ショップスキーマ
│   │   │   ├── tower.py           # 塔関連スキーマ
│   │   │   └── auth.py            # 認証関連スキーマ（Phase 2〜）
│   │   ├── routers/               # APIルーター
│   │   │   ├── auth.py            # 認証
│   │   │   ├── game.py            # ゲーム状態取得・設定更新
│   │   │   ├── battle.py          # 戦闘tick処理（オフライン計算含む）
│   │   │   ├── tower.py           # 塔選択・退却・モード変更
│   │   │   ├── shop.py            # ショップ商品一覧・購入
│   │   │   ├── equipment.py       # 装備一覧・装着・売却・ロック（Phase 2〜）
│   │   │   ├── base.py            # 施設建設・レベルアップ（Phase 4〜）
│   │   │   └── forge.py           # 装備強化・製作・分解（Phase 4〜）
│   │   ├── services/              # ビジネスロジック
│   │   │   ├── battle_service.py  # 戦闘計算・エンカウント処理（オフライン報酬含む）
│   │   │   ├── equipment_service.py # 装備ロジック（Phase 2〜）
│   │   │   ├── auth_service.py    # 認証ロジック（Phase 2〜）
│   │   │   ├── game_state_builder.py # ゲーム状態レスポンス構築
│   │   │   ├── base_service.py    # 施設建設・レベルアップ（Phase 4〜）
│   │   │   └── forge_service.py   # 装備強化・製作・分解（Phase 4〜）
│   │   ├── master_data/           # マスターデータ（Python定数）
│   │   │   ├── enemies.py         # 敵データ
│   │   │   ├── towers.py          # 塔データ
│   │   │   ├── items.py           # アイテムデータ
│   │   │   ├── equipment.py       # 装備ベースデータ
│   │   │   └── characters.py      # キャラクター成長データ
│   │   └── db/
│   │       └── database.py        # DB接続設定
│   ├── requirements.txt
│   └── alembic/                   # DBマイグレーション
│       └── ...
│
└── README.md
```

---

## 3. フロントエンド構成

| レイヤー | 技術 | 役割 |
|---------|------|------|
| ビルドツール | Vite | 高速ビルド・HMR |
| UIフレームワーク | Vue 3 (Composition API) | SPA コンポーネント管理 |
| 状態管理 | Pinia | ゲーム状態のリアクティブ管理 |
| ルーティング | Vue Router | 画面遷移（ゲーム / 装備 / パーティ / 拠点） |
| API通信 | Axios or fetch | FastAPI との REST 通信 |
| 言語 | TypeScript | 型安全な開発 |

### レスポンシブ設計

| 項目 | 仕様 |
|------|------|
| デザイン方針 | モバイルファースト |
| ブレークポイント | 768px（以下: モバイル、以上: PC） |
| 最小対応幅 | 320px |
| レイアウト | PC: 2カラム / モバイル: 1カラム（縦積み） |
| タッチ対応 | ホバー依存のUI（`:hover` のみ）は避ける |
| 数値表示ユーティリティ | 大きな数値（ゴールド等）を短縮表記する関数を `src/utils/format.ts` に実装 |

---

## 4. バックエンド構成

| レイヤー | 技術 | 役割 |
|---------|------|------|
| フレームワーク | FastAPI | REST API + 自動ドキュメント生成（Swagger UI） |
| ORM | SQLAlchemy 2.0 | DB操作 |
| バリデーション | Pydantic v2 | リクエスト/レスポンスの型定義 |
| DB | SQLite（MVP）→ PostgreSQL | データ永続化。ゴールドは `BIGINT`（64bit）カラムで管理 |
| マイグレーション | Alembic | DBスキーマ管理 |
| 認証 | JWT（Phase 2〜） | ユーザー認証・セッション管理 |
| OAuth | Google OAuth 2.0（Phase 2〜） | Googleアカウント連携 |
| パスワードハッシュ | bcrypt（cost factor = 12） | パスワード保存 |

### 設定値
```python
# backend/app/config.py
TICK_INTERVAL_SECONDS = 60      # 1 tick の間隔（秒）
TURNS_PER_TICK = 3              # 1 tick あたりのターン数（20秒/ターン × 3 = 60秒）
OFFLINE_EFFICIENCY = 1.0        # オフライン時の報酬効率（オンラインと同一）
MAX_OFFLINE_HOURS = 24          # オフライン報酬の最大蓄積時間
FAST_CALC_THRESHOLD = 100       # これ以上の未処理tickは簡略計算に切り替え
MAX_BATTLE_LOG_RECORDS = 100    # DB保持ログ件数上限
MAX_LOG_PER_RESPONSE = 50       # 1レスポンスあたりのログ件数上限
MAX_PLAYER_LEVEL = 9999         # プレイヤーLV上限
MAX_GOLD = 9_223_372_036_854_775_807  # ゴールド上限（64bit符号付き整数最大値）

# 認証設定（Phase 2〜）
ACCESS_TOKEN_EXPIRE_MINUTES = 30
REFRESH_TOKEN_EXPIRE_DAYS = 30
BCRYPT_COST_FACTOR = 12
EMAIL_VERIFY_TOKEN_EXPIRE_HOURS = 24
GUEST_ACCOUNT_EXPIRE_DAYS = 90
PASSWORD_MIN_LENGTH = 8
```

---

## 5. API設計

### 認証（Phase 2〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/auth/guest` | ゲストアカウント作成・JWT発行 |
| POST | `/api/auth/register` | メール+パスワードでユーザー登録。確認メール送信 |
| POST | `/api/auth/login` | メール+パスワードでログイン。JWT発行 |
| POST | `/api/auth/refresh` | リフレッシュトークンで新アクセストークン取得（ローテーションあり） |
| POST | `/api/auth/logout` | リフレッシュトークン無効化（ログアウト） |
| GET | `/api/auth/verify-email?token=xxx` | メール確認トークンの検証・アカウント有効化 |
| POST | `/api/auth/google` | Google認可コードでログイン/登録 |
| POST | `/api/auth/link-account` | ゲストアカウントをメール/Googleに紐づけ（ゲスト→本登録） |
| POST | `/api/auth/password-reset/request` | パスワードリセットメール送信 |
| POST | `/api/auth/password-reset/confirm` | パスワードリセット実行 |

### ゲーム状態
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/game/state` | ゲーム状態の取得（起動時・復帰時に呼ぶ） |
| PUT | `/api/game/settings` | プレイヤー設定の更新（ポーション閾値・戦闘ログ表示数・通知設定・自動売却レアリティ） |

### 戦闘（tick）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/battle/tick` | 現在時刻までの未処理tickをまとめて計算しDB反映。戦闘ログ・更新後ステータスを返却。オンライン中のポーリングでもオフライン復帰時でも同じエンドポイントを使用 |

### 操作系（プレイヤーのアクション）
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/tower/list` | 全塔の一覧を取得（名前・階数・解放条件・解放/クリア状態・最高到達階）（Phase 2〜） |
| POST | `/api/tower/select` | 塔・目標階の選択（`towerId`, `targetFloor`, `towerMode`: `auto_repeat` \| `stop_on_clear`）。未解放の塔は403 |
| POST | `/api/tower/retire` | 現戦闘終了後にリタイア（進行中の階の戦闘完了後に撤退） |
| PUT | `/api/tower/mode` | 進行モードの切り替え（進行中でも変更可） |
| PUT | `/api/tower/retreat-conditions` | 撤退条件の更新（`hpThreshold`: 0〜1） |
| GET | `/api/shop/lineup` | ショップの現在の品揃えを取得。Phase 1: 常設のみ。Phase 2〜: 常設＋日替わり |
| POST | `/api/shop/buy` | ショップでアイテム購入。常設商品: `itemId` + `quantity`（ポーションID等は常設扱い、在庫無制限）。Phase 2〜: 日替わり商品は `dailySlotIndex`（枠番号指定、各1個限り）を追加 |
| GET | `/api/equipment/list` | プレイヤーの全装備一覧を取得（Phase 2〜） |
| POST | `/api/equipment/equip` | 装備の変更（Phase 2〜） |
| POST | `/api/equipment/sell` | 装備売却（`equipmentIds`）。装備を消費してゴールドを獲得（売却価格 = 5 × レアリティ倍率 × 装備レベル）（Phase 2〜） |
| POST | `/api/equipment/lock` | 装備のロック/アンロック切替（`equipmentId`）（Phase 2〜） |
| POST | `/api/item/sell` | アイテム売却（`itemId`, `quantity`）。換金アイテム・素材を売却してゴールドを獲得（Phase 4〜） |

### パーティ・スキル（Phase 3〜）
| メソッド | パス | 説明 |
|---------|------|------|
| PUT | `/api/party/edit` | パーティ編成の変更（`memberIds`: キャラID配列、最大4人） |
| POST | `/api/skill/learn` | スキル習得（`characterId`, `skillId`）。SP消費。前提スキル未習得時はエラー |
| PUT | `/api/skill/set-active` | アクティブスキルのセット変更（`characterId`, `activeSlots`: スキルID配列、最大2） |
| POST | `/api/skill/reset` | スキル全リセット（`characterId`）。ゴールド消費（LV×50G）。全SP返却 |
| POST | `/api/character/limit-break` | 限界突破（`characterId`, `materialCharacterId`）。素材キャラを消費 |

### 施設・拠点（Phase 4〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/base/build` | 施設を建設（`facilityId`）。ゴールド+素材を消費してLV0→LV1 |
| POST | `/api/base/upgrade` | 施設をレベルアップ（`facilityId`）。ゴールド+素材を消費 |
| POST | `/api/base/scout` | 酒場でスカウト実行。ゴールドを消費してキャラ1体をランダム獲得 |

### 鍛冶屋（Phase 4〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/forge/enhance` | 装備強化（`equipmentId`）。強化石+ゴールドを消費して+1 |
| POST | `/api/forge/craft` | 装備製作（`rank`: 1-5）。素材+ゴールドを消費してランダム装備を生成 |
| POST | `/api/forge/disassemble` | 装備分解（`equipmentId`）。装備を消費して素材を獲得 |

### ボスラッシュ（Phase 5〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/boss-rush/start` | ボスラッシュ開始。通常塔探索を停止してボスラッシュモードに移行 |
| POST | `/api/boss-rush/retire` | ボスラッシュリタイア。現在の戦闘完了後に終了し、累積報酬を確定取得 |
| GET | `/api/boss-rush/ranking` | サーバーランキング取得（上位100件）。認証必須 |

### 転生（Phase 5〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/prestige` | 転生実行（`characterId`）。LV9999チェック後、LV/EXP/SPリセット・転生ポイント10pt付与 |
| PUT | `/api/prestige/invest` | 転生ポイント投資（`characterId`, `stat`, `points`）。指定のボーナスにポイントを割り振る |
| POST | `/api/prestige/reset` | 転生ボーナス全リセット（`characterId`）。ゴールド消費で全ポイント返還 |

> **設計方針**: `/api/battle/tick` がゲーム進行の中心。オンライン中のポーリングでもオフライン復帰時でも同じAPIを叩く。tickの中で戦闘計算・報酬付与・DB保存をすべて行うため、別途 save や offline/claim のエンドポイントは不要。塔の階層進行・撤退判定もtick処理内で行う。

---

## 6. アーキテクチャ方針

```
[Vue.js SPA]  ←── REST API (polling) ──→  [FastAPI]  ←── ORM ──→  [DB]
   │                                          │
   ├─ 60秒ごとにポーリング                  ├─ tick処理（戦闘計算の権威）
   ├─ 戦闘ログのテキスト表示                    ├─ オフライン復帰時のまとめ計算
   ├─ UI状態管理（Pinia）                      ├─ データ永続化
   └─ オフラインキャッシュ（一時的）             └─ 不正防止（サーバー権威）
```

- **本番ではすべての戦闘計算はサーバー側（FastAPI）で実行**。チート対策のためフロントでは計算しない
- フロントは **ポーリングで結果を取得** → テキストログとして表示するだけ
- オフライン中はサーバーで何もせず、**復帰時に経過tick数分をまとめてシミュレーション** する

### Phase 1 データ永続化方針

認証システムはPhase 2からのため、Phase 1ではゲストアカウント方式でデータを保存する。

| 項目 | 仕様 |
|------|------|
| 方式 | 初回アクセス時にサーバーがUUIDベースのゲストアカウントを自動作成 |
| 識別トークン | UUID v4（サーバーで生成） |
| トークン保存先 | クライアント側の LocalStorage（キー: `guest_token`） |
| APIリクエスト | `Authorization: Bearer <guest_token>` ヘッダーで識別 |
| サーバー側 | トークンに紐づくプレイヤーデータをSQLiteに保存 |
| Phase 2移行 | ゲスト→本登録フロー（[tech_auth.md](tech_auth.md) 参照）で既存データを引き継ぎ |
| データロスト | LocalStorage消去時はデータ復旧不可（Phase 1では許容） |

```
■ 初回アクセスフロー
  1. フロント: LocalStorageに guest_token が存在するか確認
  2. なければ POST /api/auth/guest → サーバーがUUID生成・DB保存・トークン返却
  3. フロント: guest_token を LocalStorage に保存
  4. 以降のAPIリクエストに Authorization ヘッダーを付与

■ 再訪問フロー
  1. フロント: LocalStorageから guest_token を取得
  2. GET /api/game/state（Authorization ヘッダー付き）→ 既存データをロード
```

### エラーハンドリング・通信切断時の挙動

| 項目 | 仕様 |
|------|------|
| リトライ回数 | 最大3回 |
| リトライ間隔 | 指数バックオフ（1秒 → 2秒 → 4秒） |
| 3回失敗時 | 画面上部に「接続エラー」バナーを表示。次のtickタイミング（60秒後）で自動リトライ再開 |
| 切断中の表示 | 最後に取得したデータをそのまま表示（更新停止） |
| 復帰時 | サーバーから最新状態を取得して画面を更新（通常のtick処理と同じ） |
| ユーザー操作 | 切断中のAPI操作（装備変更等）は即座にエラー表示。復帰後に再操作が必要 |

```
■ 通信エラー時のフロー
  1. API呼び出し失敗
  2. 1秒後にリトライ（1回目）
  3. 2秒後にリトライ（2回目）
  4. 4秒後にリトライ（3回目）
  5. 3回失敗 → 「接続エラー」バナー表示、ポーリング継続（次tick=60秒後に再試行）
  6. 成功時 → バナー消去、最新状態を反映
```

### ログ設計

#### ログライブラリ
Python標準 `logging` モジュールを使用。Uvicornのアクセスログと連携する。

#### ログレベル方針

| レベル | 用途 | 例 |
|--------|------|-----|
| DEBUG | 開発用の詳細情報 | SQLクエリ、リクエストボディ、レスポンスボディ |
| INFO | 正常系イベント | リクエスト受信、tick処理完了（処理tick数・結果）、ゲストアカウント作成 |
| WARNING | 想定内のエラー | 認証失敗（401）、バリデーションエラー（422）、リソース不足（ゴールド不足等） |
| ERROR | 想定外のエラー | 未捕捉例外、DB接続失敗、データ整合性エラー |

#### ログフォーマット

**開発時（テキスト形式）:**
```
[2026-03-15 14:38:30] WARNING  auth: 認証失敗 reason=player_not_found token=abc1****wxyz request_id=550e8400-e29b
```

**本番（構造化JSON）:**
```json
{
  "timestamp": "2026-03-15T14:38:30.123Z",
  "level": "WARNING",
  "logger": "auth",
  "message": "認証失敗",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "client_ip": "127.0.0.1",
  "method": "GET",
  "path": "/api/game/state",
  "reason": "player_not_found"
}
```

ログフォーマットの切り替えは環境変数 `LOG_FORMAT`（`text` / `json`、デフォルト: `text`）で制御。

#### ロガー名体系

| ロガー名 | 対象 |
|----------|------|
| `afkgame.auth` | 認証処理（ゲスト作成、トークン検証） |
| `afkgame.battle` | 戦闘tick処理、オフライン計算 |
| `afkgame.game` | ゲーム状態取得・更新 |
| `afkgame.shop` | ショップ購入 |
| `afkgame.tower` | 塔選択・リタイア |
| `afkgame.middleware` | リクエストログミドルウェア |

#### 認証エラーの詳細ログ

401レスポンス時に、失敗理由をWARNINGレベルで出力する。

| reason | 説明 | 出力例 |
|--------|------|--------|
| `header_missing` | Authorizationヘッダーなし | `WARNING auth: 認証失敗 reason=header_missing` |
| `invalid_format` | Bearer形式でない | `WARNING auth: 認証失敗 reason=invalid_format` |
| `player_not_found` | トークンに該当するプレイヤーなし | `WARNING auth: 認証失敗 reason=player_not_found token=abc1****wxyz` |
| `token_expired` | JWT期限切れ（Phase 2〜） | `WARNING auth: 認証失敗 reason=token_expired` |

#### リクエストログミドルウェア

全APIリクエストに対して以下を実行する:

1. **リクエストID付与**: 各リクエストにUUID v4を生成し、レスポンスヘッダー `X-Request-ID` に含める
2. **処理時間計測**: リクエスト開始〜レスポンス完了の時間をミリ秒単位で計測
3. **INFOログ出力**: `method`, `path`, `status_code`, `duration_ms`, `player_id`（認証済みの場合）

```
[2026-03-15 14:38:30] INFO  middleware: POST /api/battle/tick 200 45ms player_id=550e8400 request_id=xxx
```

#### 機密情報のマスク規則

| 対象 | マスク方法 |
|------|-----------|
| トークン値 | 先頭4文字 + `****` + 末尾4文字（例: `abc1****wxyz`） |
| パスワード | 出力禁止（ログに含めない） |
| メールアドレス | ローカル部の先頭2文字 + `***@` + ドメイン（例: `ab***@example.com`） |

#### バックエンドエラーハンドリング

##### 統一エラーレスポンス形式

全APIエラーレスポンスを以下の形式に統一する:

```json
{
  "error": {
    "code": "AUTH_PLAYER_NOT_FOUND",
    "message": "指定されたプレイヤーが見つかりません",
    "request_id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

##### エラーコード体系

| プレフィックス | 対象 | 例 |
|---------------|------|-----|
| `AUTH_` | 認証関連 | `AUTH_HEADER_MISSING`, `AUTH_INVALID_FORMAT`, `AUTH_PLAYER_NOT_FOUND`, `AUTH_TOKEN_EXPIRED` |
| `BATTLE_` | 戦闘関連 | `BATTLE_NOT_IN_TOWER`, `BATTLE_ALREADY_WIPED` |
| `GAME_` | ゲーム状態関連 | `GAME_STATE_NOT_FOUND` |
| `SHOP_` | ショップ関連 | `SHOP_INSUFFICIENT_GOLD`, `SHOP_ITEM_SOLD_OUT` |
| `TOWER_` | 塔関連 | `TOWER_NOT_UNLOCKED`, `TOWER_INVALID_FLOOR` |
| `EQUIP_` | 装備関連 | `EQUIP_NOT_FOUND`, `EQUIP_SLOT_MISMATCH` |
| `SKILL_` | スキル関連 | `SKILL_INSUFFICIENT_SP`, `SKILL_PREREQUISITE_NOT_MET` |
| `BASE_` | 施設関連 | `BASE_INSUFFICIENT_MATERIALS`, `BASE_MAX_LEVEL` |
| `FORGE_` | 鍛冶屋関連 | `FORGE_INSUFFICIENT_MATERIALS`, `FORGE_LEVEL_TOO_LOW` |
| `INTERNAL_` | サーバー内部エラー | `INTERNAL_UNEXPECTED_ERROR` |

##### グローバル例外ハンドラ

FastAPIの例外ハンドラで未捕捉例外を捕捉し、以下を実行する:

1. ERRORレベルでスタックトレースをログ出力
2. クライアントには `500` + `INTERNAL_UNEXPECTED_ERROR` を返却（スタックトレースは含めない）
3. リクエストIDをレスポンスに含め、ログとの突合を可能にする

#### 設定値

```python
# backend/app/config.py に追加
LOG_LEVEL = "INFO"                  # ログレベル（環境変数 LOG_LEVEL で上書き可）
LOG_FORMAT = "text"                 # ログフォーマット（text / json、環境変数 LOG_FORMAT で上書き可）
```

### アクセシビリティ対応方針

WCAG準拠レベルは明示的に定めず、ベストエフォートで以下を実装する。

| 項目 | 方針 |
|------|------|
| HTML | セマンティックHTML要素を使用（`<button>`, `<nav>`, `<main>`, `<h1>`〜`<h6>` 等） |
| キーボード操作 | Tab移動・Enter実行で全機能にアクセス可能にする |
| 色非依存 | 色だけに依存しない情報表示（テキストラベル・アイコンを併用） |
| フォーカス | フォーカスインジケータを視認可能に保つ（ブラウザデフォルトを削除しない） |

- テキストベースUIのため、スクリーンリーダーとの親和性は自然に高い
- 正式なWCAG準拠テスト・認証は行わない

### MVP開発方針
Phase 1 から **フロントエンド（Vue + Vite）とバックエンド（FastAPI + SQLite）を同時開発** する。

| 機能 | Phase 1（MVP） | 備考 |
|------|---------------|------|
| tick計算 | FastAPI `/api/battle/tick` | サーバー権威 |
| データ保存 | SQLite | サーバーDB |
| オフライン報酬 | サーバー側で計算 | 復帰時にまとめて処理 |
| フロント | Vue 3 SPA | ポーリングで結果取得・表示 |

### 開発時フォールバック構成
バックエンド未起動時のフロント単体テスト用として、ローカル計算モードも用意する。

- フロントの `api/client.ts` にフラグ（`USE_API: boolean`）を設け、`false` 時はローカル計算に切り替え
- ローカル計算のロジックは `composables/useBattleLocal.ts` に配置
- あくまで **開発・デバッグ用のフォールバック** であり、本番ではAPI連携を使用する

---

## 7. ゲームループ（ハイブリッドtick制）

```
■ 起動時
  1. API: GET /api/game/state → ゲーム状態ロード
  2. API: POST /api/battle/tick → 未処理tick（＝オフライン分）をまとめて計算
  3. tickレスポンスにオフライン分が含まれていれば、報酬サマリーモーダルを表示
  4. Piniaに最新状態を反映 → Vue描画

■ オンライン中（ポーリングループ）
  5. setInterval（60秒間隔）で繰り返し:
     a. API: POST /api/battle/tick → 前回からの未処理tickを計算
     b. レスポンスの戦闘ログ・ステータスをPiniaに反映
     c. Vue が自動再描画 → テキストログ表示更新
     d. 階クリア・レベルアップ等のイベント表示

■ 離脱時
  6. visibilitychange で検知（最終アクセス時刻はサーバー側の lastTickAt で管理）
```

---

## 8. 今後の検討事項

- [ ] デプロイ先の選定（Vercel + Render / Railway / VPS など）→ 実装完了後に決定
- [x] ブラウザ対応範囲 → §3 レスポンシブ設計に反映済み
- [x] アクセシビリティ対応 → §6 アクセシビリティ対応方針に反映済み
- [ ] パフォーマンス目標（ログ保持件数の上限など）

---

## 9. 変更履歴

| 日付 | 内容 |
|------|------|
| 2026-03-08 | 初版作成 |
| 2026-03-08 | ショップAPI（`GET /api/shop/lineup`）追加。ゲーム状態JSONに `shop` フィールド追加 |
| 2026-03-08 | 装備スロットを8枠に変更（weapon/shield/head/body/arms/waist/legs/ears） |
| 2026-03-08 | レビュー指摘対応: ディレクトリ構成更新、ShopView追加、装備9スロット化（ring追加）、塔外状態JSON対応、ショップAPI詳細化、敵データ管理方針追記 |
| 2026-03-08 | 認証システム仕様を追加（JWT、ゲストプレイ、メール登録、Google OAuth、API 10エンドポイント、DBモデル3テーブル） |
| 2026-03-08 | 塔データ定義（1.4）・環境効果定義（1.5）を追加。ゲーム状態JSONに `towersCleared` フィールド追加 |
| 2026-03-08 | 戦闘ログ・オフライン計算仕様を [tech_battle_offline.md](tech_battle_offline.md) に分離。認証システム仕様を [tech_auth.md](tech_auth.md) に分離 |
| 2026-03-08 | Phase 4仕様: 施設データ構造（§1.6）・装備強化データ構造（§1.7）追加。ゲーム状態JSONに `base`・`materials` フィールド追加。施設API 3エンドポイント・鍛冶屋API 3エンドポイント追加。ディレクトリ構成にbase/forge関連ファイル追加 |
| 2026-03-08 | Phase 3仕様: ゲーム状態JSONに `party`・`skills`・`limitBreak`・`type` フィールド追加（`class`→`type`に変更）。戦闘ログにスキル/回復/バフエントリー追加。パーティ・スキルAPI 5エンドポイント追加 |
| 2026-03-08 | §3 レスポンシブ設計を追加（モバイルファースト、768px、最小320px、format.ts）。§4 設定値に `MAX_PLAYER_LEVEL=9999`・`MAX_GOLD=64bit整数最大値` 追加。DB型注記（BIGINT）追加 |
| 2026-03-08 | Phase 5仕様: ゲーム状態JSONに `bossRush` フィールド・キャラクターの `prestige` フィールド追加。ボスラッシュAPI 3エンドポイント・転生API 3エンドポイント追加 |
| 2026-03-08 | レビュー指摘対応: 売却API 2エンドポイント追加（`POST /api/equipment/sell`, `POST /api/item/sell`）。§2 ディレクトリ構成に glossary.md 追加 |
| 2026-03-08 | レビュー指摘対応: §2 ディレクトリ構成の towers/ 一覧に TOWERS_OVERVIEW.md・003〜010_各塔.md を追加 |
| 2026-03-09 | §6 に Phase 1 データ永続化方針（ゲストアカウント自動生成）、エラーハンドリング（指数バックオフ3回リトライ）、アクセシビリティ対応方針（ベストエフォート）を追加 |
| 2026-03-09 | ゲーム状態JSONの settings フィールドを拡張（potionThreshold, battleLogCount, toastEnabled, autoSellRarity） |
| 2026-03-15 | §6 にログ設計セクションを新設（ログレベル方針、フォーマット、認証エラー詳細ログ、リクエストログミドルウェア、機密情報マスク規則、統一エラーレスポンス形式、エラーコード体系、グローバル例外ハンドラ） |
| 2026-03-15 | §6 ログ設計・エラーハンドリングを仮版から正式版に確定 |
| 2026-03-15 | レビュー指摘対応: §2 ディレクトリ構成を新構造（design/tech/data/diagrams/skills）に更新。§1.1 potionAutoUseThreshold重複フィールドを削除、potionThresholdを0.1〜0.5/0.1刻みに統一。§5 ポーション閾値APIを0.1〜0.5に更新 |
| 2026-03-15 | tech_battle_offline.md §3.2 エンカウント抽選ロジック追記（重み付きプール抽選・均等確率体数決定・Phase共通ロジック）、敵スキル処理フロー追記（Phase 5ボスラッシュWave 11+、CD管理は味方と同一） |
| 2026-08-01 | 複数塔対応: `GET /api/tower/list` エンドポイント追加（解放/クリア状態含む）。`/api/tower/select` に未解放塔403の記載を追加 |
