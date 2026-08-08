# AFK GAME — ディレクトリ・フロント／バック構成

> [tech_spec.md](../tech_spec.md) §2〜§4。

## 2. ディレクトリ構成

```
2026_AFKGAME/
├── README.md                      # プロジェクト概要・セットアップ・ドキュメント索引
├── CLAUDE.md                      # AIエージェント向け開発ルール
├── docs/                          # 仕様書・設計図 ※構成は README.md を正とする
│   └── diagrams/                  # 設計図（Mermaid）
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
│   │   │   ├── LoginView.vue      # ログイン・登録統合画面（Phase 2〜）
│   │   │   ├── RegisterView.vue   # /register → LoginView?mode=register へリダイレクト（Phase 2〜）
│   │   │   ├── SettingsView.vue   # 設定画面
│   │   │   ├── ShopView.vue       # ショップ画面（Phase 1〜）
│   │   │   ├── EquipmentView.vue  # 装備画面（Phase 2〜）
│   │   │   ├── PartyView.vue      # パーティ編成画面（Phase 3〜）
│   │   │   └── BaseView.vue       # 拠点画面（Phase 4〜）
│   │   ├── api/                   # API通信
│   │   │   ├── client.ts          # バックエンドとの通信レイヤー
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
├── backend/                       # Terasoluna (Spring Boot) サーバー
│   ├── pom.xml                    # 親POM（Maven マルチモジュール）
│   ├── afkgame-domain/            # ドメイン層 (com.afkgame.domain)
│   │   ├── model/                 # Entity（テーブル定義の正は tech_db.md）
│   │   │   ├── Player, Character, Item
│   │   │   ├── Equipment / ShopDailyState / ShopDailySlot（Phase 2〜）
│   │   │   ├── User / RefreshToken / EmailVerificationToken（Phase 2〜）
│   │   │   └── Party / PartyMember / CharacterSkill（Phase 3〜）
│   │   ├── repository/            # MyBatis3 Mapper インタフェース + 同名の Mapper XML
│   │   ├── service/               # ビジネスロジック
│   │   │   ├── BattleService      # 戦闘計算・エンカウント（オフライン報酬含む）
│   │   │   ├── GameStateBuilder   # ゲーム状態レスポンス構築
│   │   │   ├── EquipmentService / ShopDailyService / AuthService（Phase 2〜）
│   │   │   ├── PartyService / SkillService（Phase 3〜）
│   │   │   └── BaseService / ForgeService（Phase 4〜）
│   │   └── masterdata/            # マスターデータ（record + 静的Map）
│   │       └── Enemies, Towers, Items, Equipments, Characters, Notices（Phase 3〜）
│   ├── afkgame-web/               # アプリケーション層 (com.afkgame.web)
│   │   ├── AfkgameApplication     # エントリーポイント
│   │   ├── api/                   # @RestController
│   │   │   ├── AuthApi, GameApi, BattleApi, TowerApi, ShopApi
│   │   │   ├── EquipmentApi（Phase 2〜）
│   │   │   ├── PartyApi, NoticeApi（Phase 3〜）
│   │   │   ├── BaseApi, ForgeApi（Phase 4〜）
│   │   │   └── BossRushApi, AbyssApi, PrestigeApi（Phase 5〜）
│   │   ├── resource/              # Resource(DTO) + Bean Validation（API I/O）
│   │   ├── config/                # Security・Jackson・@ConfigurationProperties
│   │   ├── filter/                # リクエストIDログ・共通例外ハンドラ
│   │   └── logback-spring.xml     # ログ設定
│   ├── afkgame-env/               # 環境依存設定（application.yml・DataSource）
│   └── afkgame-initdb/            # Flyway マイグレーション（1リリース = 1バージョン）
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
| API通信 | Axios or fetch | バックエンドとの REST 通信 |
| 言語 | TypeScript | 型安全な開発 |
| スタイル | Tailwind CSS v4 | ユーティリティCSS。色・寸法・部品の定義は [tech_design_system.md](../detail/tech_design_system.md) が正（トークンは `assets/styles/tokens.css` の `@theme`） |

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
| 数値表示ユーティリティ | 大きな数値を短縮表記する関数を `src/utils/format.ts` に実装。表記ルールは [ui.md](../../design/systems/ui.md)「数値表示フォーマット」が正 |

---

## 4. バックエンド構成

| レイヤー | 技術 | 役割 |
|---------|------|------|
| フレームワーク | Terasoluna Server Framework for Spring 5.x（Spring Boot 3 / Java 17） | REST API。実行可能 jar |
| ビルド | Maven（マルチモジュール） | 依存管理・テスト実行 |
| データアクセス | MyBatis3 | DB操作（Mapper インタフェース + XML） |
| バリデーション | Bean Validation（Jakarta） | リクエストの制約定義 |
| JSON | Jackson | camelCase でのシリアライズ |
| APIドキュメント | springdoc-openapi | Swagger UI の自動生成（`/docs`） |
| DB | SQLite（MVP）→ PostgreSQL | データ永続化。ゴールドは `BIGINT`（64bit）カラムで管理 |
| マイグレーション | Flyway | DBスキーマ管理 |
| 認証 | Spring Security + JWT（Phase 2〜） | ユーザー認証・セッション管理 |
| OAuth | Google OAuth 2.0（Phase 2〜） | Googleアカウント連携 |
| パスワードハッシュ | `BCryptPasswordEncoder`（strength = 12） | パスワード保存 |

### 設定値
```yaml
# afkgame-env/src/main/resources/application.yml（afkgame.* を @ConfigurationProperties で受ける）
afkgame:
  tick-interval-seconds: 60      # 1 tick の間隔（秒）
  turns-per-tick: 3              # 1 tick あたりのターン数
  offline-efficiency: 1.0        # オフライン時の報酬効率（オンラインと同一）
  max-offline-hours: 24          # オフライン報酬の最大蓄積時間
  fast-calc-threshold: 100       # これを超える（101以上の）未処理tickは簡略計算に切り替え
  max-battle-log-records: 100    # DB保持ログ件数上限
  max-log-per-response: 50       # 1レスポンスあたりのログ件数上限
  max-player-level: 9999         # プレイヤーLV上限
  max-gold: 9223372036854775807  # ゴールド上限（64bit符号付き整数最大値）
```

- 認証系の定数（トークン期限・bcrypt strength・パスワード要件・ゲスト期限）も `application.yml` に置く（値の正は [tech_auth.md](../detail/tech_auth.md)。本書では列挙しない）
