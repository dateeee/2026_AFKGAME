# AFK GAME — ディレクトリ・フロントエンド構成

> [tech_spec.md](../tech_spec.md) §2〜§3。バックエンド構成（§4）は [tech_structure_backend.md](tech_structure_backend.md) が正。

## 2. ディレクトリ構成

```
2026_AFKGAME/
├── README.md                      # プロジェクト概要・セットアップ
├── CLAUDE.md                      # AIエージェント向け開発ルール
├── docs/                          # 仕様書・設計図 ※一覧は docs/INDEX.md を正とする
│   └── diagrams/                  # 設計図（Mermaid）
├── scripts/                       # 開発補助スクリプト
├── docker-compose.yml             # `local` 用 PostgreSQL（:5432）
├── frontend/                      # Vue.js SPA（構成は §3.1）
└── backend/                       # Terasoluna サーバー（war を Tomcat へ配備。構成は tech_structure_backend.md §4.1）
```

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

## 3.1 frontend/ のディレクトリ構成

```
frontend/                          # Vue.js SPA
├── src/
│   ├── App.vue                    # ルートコンポーネント
│   ├── main.ts                    # エントリーポイント
│   ├── router/index.ts            # Vue Router 設定
│   ├── stores/                    # Pinia ストア
│   │   └── {game,battle,player}Store.ts, {equipment,auth}Store.ts（Phase 2〜）
│   ├── composables/               # Composition API ロジック
│   │   ├── usePolling.ts          # ポーリング制御（tick API呼び出し）
│   │   ├── useBattleLocal.ts      # MVP用: フロント側tick計算（API未接続時）
│   │   └── useGameLoop.ts         # ゲーム起動・状態管理
│   ├── components/                # UIコンポーネント
│   │   ├── ui/                    # UIプリミティブ（tech_design_system.md が正）
│   │   │   └── AppIcon.vue+icons.ts（インラインSVG。絵文字は使わない）, Base{Button,Card,Modal,Badge,Field,Select,TextInput}.vue, NumberStepper.vue, StatBar.vue
│   │   ├── layout/                # アプリシェル（ヘッダ・ナビ・スクロール境界）
│   │   │   └── AppShell, AppHeader, AppNav, navItems.ts, ConnectionBanner
│   │   └── equipment/             # 装備関連（Phase 2〜）
│   │       └── Equipment{Card,Compare,Inventory,SlotGrid}.vue
│   ├── views/                     # ページコンポーネント
│   │   ├── GameView（メイン）, SettingsView, ShopView（Phase 1〜）
│   │   ├── LoginView（ログイン・登録統合）, RegisterView（→ LoginView?mode=register へリダイレクト）, EquipmentView（Phase 2〜）
│   │   └── PartyView（パーティ編成。Phase 3〜）, BaseView（拠点。Phase 4〜）
│   ├── api/                       # API通信: client.ts（通信レイヤー）, auth.ts（認証API。Phase 2〜）
│   ├── types/                     # TypeScript 型定義: game.ts
│   ├── utils/                     # ユーティリティ: format.ts
│   └── assets/
│       ├── icons/                 # アイテム・装備アイコン
│       └── styles/tokens.css（デザイントークンの唯一の定義元）, main.css（Tailwind読込・ベース）
└── index.html, vite.config.ts, tsconfig.json, package.json
```

## 3.2 レスポンシブ設計

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
