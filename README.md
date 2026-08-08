# AFK GAME

放置系ファンタジーRPGのWebブラウザゲーム。

プレイヤーは冒険者ギルドのマスターとして冒険者を育成・編成し、ダンジョン（塔）へ派遣する。
アプリを閉じている間も探索・戦闘は自動で進み、復帰時に報酬をまとめて受け取れる。

## 技術スタック

| レイヤー | 技術 |
|---------|------|
| フロントエンド | Vue.js 3 (SPA / Composition API / TypeScript) + Vite + Pinia + Tailwind CSS |
| バックエンド | Java 17 / Terasoluna (Spring Boot 3) + MyBatis3 + Flyway |
| DB | PostgreSQL |
| 描画方式 | テキストベース（Canvas不使用）。UIアイコンはSVG、アイテムは画像 |

## セットアップ

### バックエンド

```bash
docker compose up -d db   # PostgreSQL :5432
cd backend
mvn clean install
java -jar afkgame-web/target/afkgame-web.jar     # http://localhost:8000
```

API ドキュメント: http://localhost:8000/docs

### フロントエンド

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173（/api は :8000 へプロキシ）
```

VS Code は実行構成 **Full Stack** で同時起動できる（[.vscode/launch.json](.vscode/launch.json)）。

### 環境変数（既定値は `application.yml`）

| 変数 | 既定値 | 用途 |
|------|-------|------|
| `DATABASE_URL` / `_USER` / `_PASSWORD` | `jdbc:postgresql://localhost:5432/afkgame` / `afkgame` / `afkgame` | DB接続情報（本番では変更必須） |
| `JWT_SECRET` | `dev-secret-change-in-production` | JWT署名鍵（本番で変更必須） |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 空 | Google OAuth（空の場合は無効） |
| `LOG_LEVEL` / `LOG_FORMAT` | `INFO` / `text` | ログ出力設定 |

その他は `afkgame-env` の `application.yml` を参照。

### 主なコマンド

| コマンド | 内容 |
|---------|------|
| `npm run dev` / `npm run build` | フロント開発サーバー / 本番ビルド |
| `npm run type-check` | 型チェック（vue-tsc + E2E） |
| `npm run test:e2e` | E2Eテスト（Playwright。専用ポート/DBで自動起動） |
| `mvn verify` | バックエンドテスト（JUnit 5 + JaCoCo。branch 100%・`target/site/jacoco/`） |
| `python scripts/check_doc_size.py` | ドキュメント文字数チェック |
| `python scripts/check_docs.py` | ドキュメント機械検証（リンク・索引・曖昧語・正の逸脱ほか） |
| `python scripts/check_branch_list.py` | 分岐一覧の構造検証（`--tests` でテスト対応照合） |
| `python scripts/rotate_reviews.py --apply` | レビュー結果の退避（直下は最新10件、超過分は `archive/` へ） |

## ディレクトリ構成

```
2026_AFKGAME/
├── README.md                    # 本ファイル（概要・セットアップ）
├── CLAUDE.md                    # AIエージェント向け開発ルール
├── .claude/                     # エージェント定義
│   ├── skills/                  # 工程7件 + 支援10件（プロジェクト非依存）
│   ├── references/              # スキル共通リファレンス（同上）
│   └── project/                 # プロジェクト固有プロファイル（索引: INDEX.md）
├── docs/                        # ドキュメント（分類軸は documentation_rules.md §10）
│   ├── design/ tech/ data/      # 成果物: ゲーム仕様 / 技術仕様 / マスターデータ
│   ├── diagrams/                # 成果物: 設計図（Mermaid）。索引 + 同名ディレクトリ
│   ├── process/                 # 進め方: 工程定義・ドキュメント規約
│   ├── backlog/                 # 状態: 未処理項目の台帳（工程で増減する）
│   └── reviews/                 # 記録: レビュー結果（自動生成。スキル名/日時.md）
├── scripts/                     # 開発補助スクリプト
├── frontend/                    # Vue.js SPA
│   ├── src/views/ components/   # ページ / UIコンポーネント
│   ├── src/stores/ composables/ # Pinia ストア / Composition API ロジック
│   ├── src/api/ types/          # API通信レイヤー / 型定義
│   └── tests/e2e/               # E2Eテスト（Playwright）
└── backend/                     # Terasoluna (Spring Boot) サーバー
    ├── afkgame-domain/ web/     # Entity・Mapper・Service / API・DTO・Security
    └── afkgame-env/ initdb/     # 環境設定（application.yml）/ Flyway
```

## アーキテクチャ方針

- **ハイブリッドtick制**: 60秒間隔のtickで戦闘処理。オンライン中はポーリング、オフライン中は復帰時に一括計算
- **サーバー権威**: 戦闘計算はバックエンドで実行（チート対策）。フロントはログ表示のみ
- **シングルプレイ専用**: マルチプレイは想定しない

## 開発フェーズ

| Phase | 内容 |
|-------|------|
| Phase 1 (MVP) | キャラ1体の自動戦闘、レベルアップ、オフライン報酬、常設ショップ |
| Phase 2 | 装備システム、複数の塔、ショップ拡張（日替わり装備）、認証 |
| Phase 3 | パーティ編成、タイプ（素質）・スキルシステム |
| Phase 4 | 拠点建設（酒場・鍛冶屋・訓練場・倉庫・市場）、素材・生産システム |
| Phase 5 | エンドコンテンツ（ボスラッシュ、転生等） |

仕様は全Phase(1-5)を先に確定し、実装をPhase 1から順に進める方針。進捗の正は [開発工程](docs/process/development_process.md) §5。

## ドキュメント索引

### 開発プロセス・台帳

分類軸は [documentation_rules.md](docs/process/documentation_rules.md) §10。

| 分類 | ファイル |
|------|---------|
| 進め方 `docs/process/` | [development_process](docs/process/development_process.md) 7工程・ゲート・進捗 / [phases](docs/process/phases.md) 工程別の定義 / [documentation_rules](docs/process/documentation_rules.md) 文書規約 / [spec_ownership](docs/process/spec_ownership.md) 正の所在マップ |
| 状態 `docs/backlog/` | [open_specs](docs/backlog/open_specs.md) 未確定仕様 / [balance_backlog](docs/backlog/balance_backlog.md) 見直す数値 / [known_issues](docs/backlog/known_issues.md) 実装の疑義 / [next_session](docs/backlog/next_session.md) 引き継ぎ / [efficiency_memo](docs/backlog/efficiency_memo.md) 効率メモ / [java_migration](docs/backlog/java_migration.md) Java移行計画 |
| 横断 | [glossary](docs/glossary.md) 用語集 / [changelog](docs/changelog.md) 変更履歴 / [INDEX](.claude/project/INDEX.md) 工程↔スキル対応表 |

### 仕様書
- [docs/design/game_spec.md](docs/design/game_spec.md) — ゲーム仕様の索引
  - [requirements/](docs/design/requirements/) 要件 — product プロダクト / non_functional 非機能 / operation 運用
  - [systems/](docs/design/systems/) — character / battle / equipment / economy / dungeon / endgame / ui / ui_onboarding
- [docs/tech/tech_spec.md](docs/tech/tech_spec.md) — 技術仕様の索引
  - [basic/](docs/tech/basic/) 基本設計 — [db](docs/tech/basic/tech_db.md)（テーブル定義書の索引 + [tech_db/](docs/tech/basic/tech_db/)） / data / structure / api / api_common / architecture / logging
  - [nonfunctional/](docs/tech/nonfunctional/) 非機能 — performance / security / operations
  - [detail/](docs/tech/detail/) 詳細設計 — battle / offline / skill / party / tick / polling / state / rng / numeric / shop / design_system / auth
- [docs/data/master_data.md](docs/data/master_data.md) — マスターデータの索引 + 塔データ一覧
  - [master/](docs/data/master/) — character / item / equipment / base / endgame
  - [TOWERS_OVERVIEW.md](docs/data/towers/TOWERS_OVERVIEW.md) 全塔概要一覧 / [SKILLS_OVERVIEW.md](docs/data/skills/SKILLS_OVERVIEW.md) スキルシステム概要

### 設計図
[docs/diagrams/](docs/diagrams/) — 全6図とも索引 + 同名ディレクトリ構成。
[er_diagram](docs/diagrams/er_diagram.md) / [class_diagram](docs/diagrams/class_diagram.md) / [battle_flow](docs/diagrams/battle_flow.md) / [api_sequence](docs/diagrams/api_sequence.md) / [system_architecture](docs/diagrams/system_architecture.md)（構成 / tick / 権威 / 本番）/ [screen_transition](docs/diagrams/screen_transition.md)（認証 / ナビ / Phase 5 / モーダル）
