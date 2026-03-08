# AFK GAME — 技術仕様書

> プロジェクト概要は [CLAUDE.md](../CLAUDE.md)、ゲーム仕様は [game_spec.md](game_spec.md)、マスターデータは [master_data.md](master_data.md) を参照。
>
> 詳細仕様: [戦闘ログ・オフライン計算](tech_battle_offline.md) / [認証システム](tech_auth.md)

---

## 1. データ設計

### 1.1 ゲーム状態（API レスポンス / LocalStorage キャッシュ）
```json
{
  "version": "1.0.0",
  "lastTickAt": 1709856000000,
  "player": {
    "gold": 1500,
    "currentDungeon": "dungeon_001",
    "currentTower": "goblin_tower",   // null = 塔外待機中
    "currentFloor": 3,                // null = 塔外待機中（currentTowerと連動）
    "targetFloor": 10,
    "highestFloor": 12,
    "towerMode": "auto_repeat",
    "retreatConditions": {
      "hpThreshold": 0.3
    },
    "towersCleared": {
      "goblin_tower": { "cleared": true, "highestFloor": 20 },
      "forest_tower": { "cleared": false, "highestFloor": 15 }
    }
  },
  "characters": [
    {
      "id": "hero_001",
      "name": "勇者",
      "class": "warrior",
      "level": 5,
      "exp": 120,
      "stats": {
        "hp": 150,
        "maxHp": 150,
        "atk": 25,
        "def": 12,
        "spd": 10
      },
      "equipment": {
        "weapon": null,
        "shield": null,
        "head": null,
        "body": null,
        "arms": null,
        "waist": null,
        "legs": null,
        "ears": null,
        "ring": null
      }
    }
  ],
  "battle": {
    "enemies": [
      { "id": "goblin", "hp": 8, "maxHp": 35 }
    ],
    "turn": 4,
    "towerGold": 45,
    "towerLoot": [
      { "itemId": "goblin_dagger", "quantity": 1 }
    ]
  },
  "potions": {
    "hp_potion": 10
  },
  "potionAutoUseThreshold": 0.5,
  "inventory": [],
  "shop": {
    "dailyResetAt": 1709856000000,
    "dailyItems": [
      { "slotIndex": 0, "itemId": "iron_sword", "category": "weapon", "rarity": "common", "sold": false }
    ]
  },
  "settings": {
    "soundEnabled": true
  }
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

---

## 2. ディレクトリ構成

```
2026_AFKGAME/
├── docs/
│   ├── game_spec.md              # ゲーム仕様書
│   ├── tech_spec.md              # 本仕様書（技術設計）
│   ├── tech_battle_offline.md    # 戦闘ログ・オフライン計算仕様
│   ├── tech_auth.md              # 認証システム仕様（Phase 2〜）
│   ├── master_data.md            # マスターデータ定義
│   ├── open_specs.md             # 未確定仕様一覧
│   └── towers/                   # 塔別マスターデータ
│       ├── 000_テンプレート.md    # 新規塔作成用テンプレート
│       ├── 001_ゴブリンの塔.md    # ゴブリンの塔（敵・構成・ドロップ）
│       └── 002_森の塔.md         # 森の塔（敵・構成・ドロップ）
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
│   │   │   └── authStore.ts       # 認証状態管理（Phase 2〜）
│   │   ├── composables/           # Composition API ロジック
│   │   │   ├── usePolling.ts      # ポーリング制御（tick API呼び出し）
│   │   │   ├── useBattleLocal.ts  # MVP用: フロント側tick計算（API未接続時）
│   │   │   ├── useGameLoop.ts     # ゲーム起動・状態管理
│   │   │   └── useAuth.ts         # 認証ロジック（Phase 2〜）
│   │   ├── components/            # UIコンポーネント
│   │   │   ├── BattleLog.vue      # 戦闘ログ表示
│   │   │   ├── CharacterStatus.vue # キャラステータス
│   │   │   ├── TowerInfo.vue      # 塔・階層情報
│   │   │   ├── HpBar.vue          # HPバー
│   │   │   └── OfflineRewardModal.vue # オフライン報酬モーダル
│   │   ├── views/                 # ページコンポーネント
│   │   │   ├── GameView.vue       # メインゲーム画面
│   │   │   ├── LoginView.vue      # ログイン画面（Phase 2〜）
│   │   │   ├── RegisterView.vue   # 登録画面（Phase 2〜）
│   │   │   ├── ShopView.vue       # ショップ画面（Phase 1〜）
│   │   │   ├── EquipmentView.vue  # 装備画面（Phase 2〜）
│   │   │   ├── PartyView.vue      # パーティ編成画面（Phase 3〜）
│   │   │   └── BaseView.vue       # 拠点画面（Phase 4〜）
│   │   ├── api/                   # API通信
│   │   │   └── client.ts          # FastAPI との通信レイヤー
│   │   ├── types/                 # TypeScript 型定義
│   │   │   └── game.ts            # ゲーム関連の型
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
│   │   ├── models/                # SQLAlchemy モデル（DB定義）
│   │   │   ├── player.py
│   │   │   ├── character.py
│   │   │   ├── item.py
│   │   │   └── user.py            # User, RefreshToken, EmailVerificationToken（Phase 2〜）
│   │   ├── schemas/               # Pydantic スキーマ（API I/O）
│   │   │   ├── player.py
│   │   │   ├── character.py
│   │   │   ├── battle.py
│   │   │   └── auth.py            # 認証関連スキーマ（Phase 2〜）
│   │   ├── routers/               # APIルーター
│   │   │   ├── auth.py            # 認証
│   │   │   ├── game.py            # ゲーム状態取得・保存
│   │   │   ├── battle.py          # 戦闘結果計算
│   │   │   └── offline.py         # オフライン報酬
│   │   ├── services/              # ビジネスロジック
│   │   │   ├── battle_service.py  # 戦闘計算
│   │   │   ├── offline_service.py # オフライン報酬計算
│   │   │   ├── tower_service.py   # 塔・階層・敵データ
│   │   │   └── auth_service.py    # 認証ロジック（Phase 2〜）
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

---

## 4. バックエンド構成

| レイヤー | 技術 | 役割 |
|---------|------|------|
| フレームワーク | FastAPI | REST API + 自動ドキュメント生成（Swagger UI） |
| ORM | SQLAlchemy 2.0 | DB操作 |
| バリデーション | Pydantic v2 | リクエスト/レスポンスの型定義 |
| DB | SQLite（MVP）→ PostgreSQL | データ永続化 |
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
| PUT | `/api/game/settings` | プレイヤー設定の更新（音量等） |

### 戦闘（tick）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/battle/tick` | 現在時刻までの未処理tickをまとめて計算しDB反映。戦闘ログ・更新後ステータスを返却。オンライン中のポーリングでもオフライン復帰時でも同じエンドポイントを使用 |

### 操作系（プレイヤーのアクション）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/tower/select` | 塔・目標階の選択（`towerId`, `targetFloor`, `towerMode`: `auto_repeat` \| `stop_on_clear`） |
| POST | `/api/tower/retire` | 現戦闘終了後にリタイア（進行中の階の戦闘完了後に撤退） |
| PUT | `/api/tower/mode` | 進行モードの切り替え（進行中でも変更可） |
| PUT | `/api/tower/retreat-conditions` | 撤退条件の更新（`hpThreshold`: 0〜1） |
| GET | `/api/shop/lineup` | ショップの現在の品揃えを取得（常設＋日替わり） |
| POST | `/api/shop/buy` | ショップでアイテム購入。常設商品: `itemId` + `quantity`（ポーションID等は常設扱い、在庫無制限）。日替わり商品: `dailySlotIndex`（枠番号指定、各1個限り） |
| PUT | `/api/potion/config` | ポーション自動使用の閾値設定（`threshold`: 0.3/0.5/0.7） |
| POST | `/api/equipment/equip` | 装備の変更（Phase 2〜） |

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

- [ ] デプロイ先の選定（Vercel + Render / Railway / VPS など）
- [ ] ブラウザ対応範囲（モバイル対応の詳細）
- [ ] アクセシビリティ対応
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
