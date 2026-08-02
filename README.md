# AFK GAME

放置系ファンタジーRPGのWebブラウザゲーム。

プレイヤーは冒険者ギルドのマスターとして冒険者を育成・編成し、ダンジョン（塔）へ派遣する。
アプリを閉じている間も探索・戦闘は自動で進み、復帰時に報酬をまとめて受け取れる。

## 技術スタック

| レイヤー | 技術 |
|---------|------|
| フロントエンド | Vue.js 3 (SPA / Composition API / TypeScript) + Vite + Pinia + Tailwind CSS |
| バックエンド | Python / FastAPI + SQLAlchemy 2.0 + Pydantic v2 |
| DB | SQLite（MVP）→ PostgreSQL（本番） |
| 描画方式 | テキストベース（Canvas不使用）。アイテム等にはアイコン画像を使用 |

## セットアップ

### バックエンド

```bash
cd backend
python -m venv .venv && .venv\Scripts\activate   # Windows
pip install -r requirements.txt -r requirements-dev.txt
uvicorn app.main:app --reload --port 8000
```

API ドキュメント: http://localhost:8000/docs

### フロントエンド

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173（/api は :8000 へプロキシ）
```

VS Code の場合は実行構成 **Full Stack** で両方を同時起動できる（[.vscode/launch.json](.vscode/launch.json)）。

### 環境変数（`backend/.env`）

| 変数 | 既定値 | 用途 |
|------|-------|------|
| `DATABASE_URL` | `sqlite:///./afkgame.db` | DB接続文字列 |
| `JWT_SECRET` | `dev-secret-change-in-production` | JWT署名鍵（本番では必ず変更） |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 空 | Google OAuth（空の場合は無効） |
| `LOG_LEVEL` / `LOG_FORMAT` | `INFO` / `text` | ログ出力設定 |

その他の設定値は [backend/app/config.py](backend/app/config.py) を参照。

### 主なコマンド

| コマンド | 内容 |
|---------|------|
| `npm run dev` / `npm run build` | フロント開発サーバー / 本番ビルド |
| `npm run type-check` | 型チェック（vue-tsc + E2E） |
| `npm run test:e2e` | E2Eテスト（Playwright。フロント・バックを専用ポート/DBで自動起動） |
| `pytest` | バックエンドテスト（C1 100%・`htmlcov/` にHTMLレポート） |
| `python scripts/check_doc_size.py` | ドキュメント文字数チェック |
| `python scripts/rotate_reviews.py --apply` | レビュー結果のローテーション（直下は最新10件、超過分は `archive/` へ） |

## ディレクトリ構成

```
2026_AFKGAME/
├── README.md                    # 本ファイル（概要・セットアップ）
├── CLAUDE.md                    # AIエージェント向け開発ルール
├── .claude/                     # エージェント定義
│   ├── skills/                  # 工程スキル7件 + 支援スキル7件（プロジェクト非依存）
│   ├── references/              # スキル共通リファレンス（同上）
│   └── project/                 # プロジェクト固有プロファイル（索引: INDEX.md）
├── docs/                        # 仕様書
│   ├── *.md                     # プロセス・規約・バックログ（→「ドキュメント索引」）
│   ├── design/                  # ゲーム仕様（索引 game_spec.md + systems/）
│   ├── tech/                    # 技術仕様（索引 tech_spec.md + tech_*.md）
│   ├── data/                    # マスターデータ（索引 master_data.md + master/ towers/ skills/）
│   └── reviews/                 # レビュー結果（自動生成。スキル名/日時.md + archive/）
├── diagrams/                    # 設計図（Mermaid）。索引 + 同名ディレクトリに分割
├── scripts/                     # 開発補助スクリプト
├── frontend/                    # Vue.js SPA
│   ├── src/views/ components/   # ページ / UIコンポーネント
│   ├── src/stores/ composables/ # Pinia ストア / Composition API ロジック
│   ├── src/api/ types/          # API通信レイヤー / 型定義
│   └── tests/e2e/               # E2Eテスト（Playwright）
└── backend/                     # FastAPI サーバー
    ├── app/routers/ services/   # APIルーター / ビジネスロジック
    ├── app/models/ schemas/     # SQLAlchemy モデル / Pydantic スキーマ
    ├── app/master_data/         # マスターデータ定義
    └── tests/unit/ integration/ # 単体テスト / API統合テスト
```

## アーキテクチャ方針

- **ハイブリッドtick制**: 60秒間隔のtickで戦闘処理。オンライン中はポーリング、オフライン中は復帰時に一括計算
- **サーバー権威**: 戦闘計算はバックエンドで実行（チート対策）。フロントはログ表示のみ
- **シングルプレイ専用**: マルチプレイは想定しない

## 開発フェーズ

| Phase | 内容 | 状況 |
|-------|------|------|
| Phase 1 (MVP) | キャラ1体の自動戦闘、レベルアップ、オフライン報酬、常設ショップ | 実装・テスト完了 |
| Phase 2 | 装備システム、複数の塔、ショップ拡張（日替わり装備）、認証 | 進行中 |
| Phase 3 | パーティ編成、タイプ（素質）・スキルシステム | 未着手 |
| Phase 4 | 拠点建設（酒場・鍛冶屋・訓練場・倉庫・市場）、素材・生産システム | 未着手 |
| Phase 5 | エンドコンテンツ（ボスラッシュ、転生等） | 未着手 |

仕様は全Phase(1-5)を先に確定し、実装をPhase 1から順に進める方針。

## ドキュメント索引

### 開発プロセス
- [docs/development_process.md](docs/development_process.md) — 開発工程（7工程・TDD・テスト標準）
- [.claude/project/INDEX.md](.claude/project/INDEX.md) — 工程↔スキル↔プロファイル対応表
- [docs/documentation_rules.md](docs/documentation_rules.md) — ドキュメント規約（文字数上限・分割）
- [docs/glossary.md](docs/glossary.md) — 用語集
- [docs/balance_backlog.md](docs/balance_backlog.md) — バランス調整（見直す数値）
- [docs/known_issues.md](docs/known_issues.md) — 実装の疑義

大きな仕様書は **索引 + 個別ファイル** 構成。索引から辿ること。

### 仕様書
- [docs/design/game_spec.md](docs/design/game_spec.md) — ゲーム仕様の索引
  - [systems/](docs/design/systems/) — character / battle / equipment / economy / dungeon / endgame / ui
  - 要件: [product](docs/design/product_requirements.md) プロダクト / [nfr](docs/design/non_functional_requirements.md) 非機能 / [operation](docs/design/operation_requirements.md) 運用
- [docs/tech/tech_spec.md](docs/tech/tech_spec.md) — 技術仕様の索引
  - 基本設計: [data](docs/tech/tech_data.md) データ / [structure](docs/tech/tech_structure.md) 構成 / [api](docs/tech/tech_api.md) API / [architecture](docs/tech/tech_architecture.md) 方針 / [logging](docs/tech/tech_logging.md) ログ / [auth](docs/tech/tech_auth.md) 認証
  - 非機能（設計）: [performance](docs/tech/tech_performance.md) 性能・容量 / [security](docs/tech/tech_security.md) セキュリティ / [operations](docs/tech/tech_operations.md) 運用
  - 詳細設計: [battle](docs/tech/tech_battle.md) 戦闘 / [offline](docs/tech/tech_offline.md) オフライン / [tick](docs/tech/tech_tick.md) tick / [polling](docs/tech/tech_polling.md) フロント / [state](docs/tech/tech_state.md) 状態 / [rng](docs/tech/tech_rng.md) 乱数 / [numeric](docs/tech/tech_numeric.md) 数値 / [shop](docs/tech/tech_shop.md) ショップ / [design-system](docs/tech/tech_design_system.md) デザインシステム
- [docs/data/master_data.md](docs/data/master_data.md) — マスターデータの索引 + 塔データ一覧
  - [master/](docs/data/master/) — character / item / equipment / base / endgame
  - [towers/TOWERS_OVERVIEW.md](docs/data/towers/TOWERS_OVERVIEW.md) 全塔概要一覧 / [skills/SKILLS_OVERVIEW.md](docs/data/skills/SKILLS_OVERVIEW.md) スキルシステム概要

### 設計図
- 全6図とも索引 + 同名ディレクトリ構成: [er_diagram.md](diagrams/er_diagram.md) ER図 / [class_diagram.md](diagrams/class_diagram.md) クラス図 / [battle_flow.md](diagrams/battle_flow.md) 戦闘フロー図 / [api_sequence.md](diagrams/api_sequence.md) APIシーケンス図
- [system_architecture.md](diagrams/system_architecture.md) システム構成図（全体構成 / tick / サーバー権威 / 本番構成）/ [screen_transition.md](diagrams/screen_transition.md) 画面遷移図（認証 / ナビ / Phase 5 / モーダル）
