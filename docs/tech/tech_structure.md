# AFK GAME — ディレクトリ・フロント／バック構成

> [tech_spec.md](tech_spec.md) §2〜§4。

## 2. ディレクトリ構成

```
2026_AFKGAME/
├── README.md                      # プロジェクト概要・セットアップ・ドキュメント索引
├── CLAUDE.md                      # AIエージェント向け開発ルール
├── docs/                          # 仕様書 ※構成は README.md を正とする
├── diagrams/                      # 設計図（Mermaid）※構成は README.md を正とする
├── scripts/                       # 開発補助スクリプト
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
│   │   │   ├── ui/                # UIプリミティブ（tech_design_system.md が正）
│   │   │   │   ├── AppIcon.vue, icons.ts   # インラインSVG（絵文字は使わない）
│   │   │   │   ├── Base{Button,Card,Modal,Badge,Field,Select,TextInput}.vue
│   │   │   │   └── NumberStepper.vue, StatBar.vue
│   │   │   ├── layout/            # アプリシェル（ヘッダ・ナビ・スクロール境界）
│   │   │   │   └── AppShell, AppHeader, AppNav, navItems.ts, ConnectionBanner
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
│   │           ├── tokens.css     # デザイントークン（色・書体・寸法の唯一の定義元）
│   │           └── main.css       # Tailwind読込・ベース・横断ユーティリティ
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
│   │   │   ├── shop.py            # ShopDailyState, ShopDailySlot（Phase 2〜）
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
│   │   │   ├── party.py           # パーティ編成・スキル・限界突破（Phase 3〜）
│   │   │   ├── base.py            # 施設建設・レベルアップ（Phase 4〜）
│   │   │   ├── forge.py           # 装備強化・製作・分解（Phase 4〜）
│   │   │   ├── boss_rush.py       # ボスラッシュ・ランキング（Phase 5〜）
│   │   │   ├── abyss.py           # 深淵の塔ランキング（Phase 5〜）
│   │   │   └── prestige.py        # 転生・ポイント振り分け（Phase 5〜）
│   │   ├── services/              # ビジネスロジック
│   │   │   ├── battle_service.py  # 戦闘計算・エンカウント処理（オフライン報酬含む）
│   │   │   ├── equipment_service.py # 装備ロジック（Phase 2〜）
│   │   │   ├── shop_daily_service.py # 日替わりショップ（Phase 2〜）
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
│   ├── alembic.ini                # 接続先は env.py が config.DATABASE_URL から設定
│   └── alembic/                   # DBマイグレーション
│       ├── env.py
│       └── versions/              # 1リリース = 1リビジョン
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
| ルーティング | Vue Router | 画面遷移（`router/index.ts` の定義が正） |
| API通信 | Axios or fetch | FastAPI との REST 通信 |
| 言語 | TypeScript | 型安全な開発 |
| スタイル | Tailwind CSS v4 | ユーティリティCSS。色・寸法・部品の定義は [tech_design_system.md](tech_design_system.md) が正（トークンは `assets/styles/tokens.css` の `@theme`） |

### レスポンシブ設計

| 項目 | 仕様 |
|------|------|
| デザイン方針 | モバイルファースト |
| ブレークポイント | 768px（以下: モバイル、以上: PC） |
| 最小対応幅 | 320px |
| レイアウト | PC: 2カラム / モバイル: 1カラム（縦積み） |
| タッチ対応 | ホバー依存のUIは避ける。`:hover` は `@media (hover: hover)` で囲む（タップ後にホバーが残るため） |
| タップ領域・入力 | タップ対象44px以上。入力部品は16px固定（下回ると iOS Safari が自動拡大する） |
| セーフエリア | `viewport-fit=cover` + `env(safe-area-inset-*)`。`AppShell` が引き受ける |
| 数値表示ユーティリティ | 大きな数値を短縮表記する関数を `src/utils/format.ts` に実装。表記ルールは [ui.md](../design/systems/ui.md)「数値表示フォーマット」が正 |

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
FAST_CALC_THRESHOLD = 100       # これを超える（101以上の）未処理tickは簡略計算に切り替え
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
