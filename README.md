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
| `JWT_SECRET` | `dev-secret-change-in-production` | JWT署名鍵（本番では必ず変更） |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 空 | Google OAuth（空の場合は無効） |
| `LOG_LEVEL` / `LOG_FORMAT` | `INFO` / `text` | ログ出力設定 |

その他の設定値は [backend/app/config.py](backend/app/config.py) を参照。

### 主なコマンド

| コマンド | 内容 |
|---------|------|
| `npm run dev` / `npm run build` | フロント開発サーバー / 本番ビルド |
| `npm run type-check` | 型チェック（vue-tsc） |
| `pytest` | バックエンドテスト（C1 100%・`htmlcov/` にHTMLレポート） |
| `python scripts/check_doc_size.py` | ドキュメント文字数チェック |

## ディレクトリ構成

```
2026_AFKGAME/
├── README.md                    # 本ファイル（概要・セットアップ）
├── CLAUDE.md                    # AIエージェント向け開発ルール
├── docs/                        # 仕様書
│   ├── development_process.md   # 開発工程定義書（7工程・TDD・テスト標準）
│   ├── documentation_rules.md   # ドキュメント規約（文字数上限・分割）
│   ├── glossary.md              # 用語集
│   ├── open_specs.md            # 未確定仕様一覧（全確定後に削除）
│   ├── balance_backlog.md       # バランス調整（数値のみ調整待ち）
│   ├── known_issues.md          # 実装の疑義
│   ├── design/                  # ゲーム仕様
│   │   ├── game_spec.md         # 索引（開発フェーズ・設計方針・変更履歴）
│   │   └── systems/             # システム別仕様（character / battle / equipment 他）
│   ├── tech/                    # 技術仕様
│   │   ├── tech_spec.md         # 索引（章構成・変更履歴）
│   │   └── tech_*.md            # レイヤー別 + 詳細設計（一覧は下記索引）
│   ├── data/                    # マスターデータ
│   │   ├── master_data.md       # 索引 + 塔データ一覧
│   │   ├── master/              # カテゴリ別数値（character / item / equipment 他）
│   │   ├── towers/              # 塔別データ（OVERVIEW + 001〜010）
│   │   └── skills/              # スキル系統別データ（OVERVIEW + 001〜006）
│   └── reviews/                 # レビュー結果（自動生成）
├── diagrams/                    # 設計図（Mermaid）。索引 + 同名ディレクトリに分割
├── scripts/                     # 開発補助スクリプト
├── frontend/src/                # Vue.js SPA
│   ├── views/ components/       # ページ / UIコンポーネント
│   ├── stores/ composables/     # Pinia ストア / Composition API ロジック
│   └── api/ types/              # API通信レイヤー / 型定義
└── backend/app/                 # FastAPI サーバー
    ├── routers/ services/       # APIルーター / ビジネスロジック
    ├── models/ schemas/         # SQLAlchemy モデル / Pydantic スキーマ
    └── master_data/             # マスターデータ定義
```

## アーキテクチャ方針

- **ハイブリッドtick制**: 戦闘はバックエンドで60秒間隔のtickごとに処理。オンライン中はポーリング、オフライン中は復帰時にまとめて計算
- **サーバー権威**: 戦闘計算はサーバー側で実行（チート対策）。フロントはログ表示のみ
- **シングルプレイ専用**: マルチプレイは想定しない
- **MVP同時開発**: フロント（Vue）＋バックエンド（FastAPI + SQLite）を同時開発

## 開発フェーズ

| Phase | 内容 | 状況 |
|-------|------|------|
| Phase 1 (MVP) | キャラ1体の自動戦闘、レベルアップ、オフライン報酬、常設ショップ | 実装・単体テスト完了 |
| Phase 2 | 装備システム、複数の塔、ショップ拡張（日替わり装備）、認証 | 進行中 |
| Phase 3 | パーティ編成、タイプ（素質）・スキルシステム | 未着手 |
| Phase 4 | 拠点建設（酒場・鍛冶屋・訓練場・倉庫・市場）、素材・生産システム | 未着手 |
| Phase 5 | エンドコンテンツ（ボスラッシュ、転生等） | 未着手 |

仕様は全Phase(1-5)を先に確定し、実装をPhase 1から順に進める方針。

## ドキュメント索引

### 開発プロセス
- [docs/development_process.md](docs/development_process.md) — 開発工程（7工程・TDD・テスト標準）
- [docs/documentation_rules.md](docs/documentation_rules.md) — ドキュメント規約（文字数上限・分割）
- [docs/glossary.md](docs/glossary.md) — 用語集
- [docs/open_specs.md](docs/open_specs.md) — 未確定仕様（実装をブロックする事項）
- [docs/balance_backlog.md](docs/balance_backlog.md) — バランス調整（見直す数値）
- [docs/known_issues.md](docs/known_issues.md) — 実装の疑義

大きな仕様書は **索引 + 個別ファイル** に分割している（[documentation_rules.md](docs/documentation_rules.md) §6）。索引から辿ること。

### 仕様書
- [docs/design/game_spec.md](docs/design/game_spec.md) — ゲーム仕様の索引
  - [systems/](docs/design/systems/) — character / battle / equipment / economy / dungeon / endgame / ui
  - 要件: [product](docs/design/product_requirements.md) プロダクト / [nfr](docs/design/non_functional_requirements.md) 非機能 / [operation](docs/design/operation_requirements.md) 運用
- [docs/tech/tech_spec.md](docs/tech/tech_spec.md) — 技術仕様の索引
  - 基本設計: [data](docs/tech/tech_data.md) データ / [structure](docs/tech/tech_structure.md) 構成 / [api](docs/tech/tech_api.md) API / [architecture](docs/tech/tech_architecture.md) 方針 / [logging](docs/tech/tech_logging.md) ログ / [auth](docs/tech/tech_auth.md) 認証
  - 非機能（設計）: [performance](docs/tech/tech_performance.md) 性能・容量 / [security](docs/tech/tech_security.md) セキュリティ / [operations](docs/tech/tech_operations.md) 運用
  - 詳細設計: [battle](docs/tech/tech_battle.md) 戦闘 / [offline](docs/tech/tech_offline.md) オフライン / [tick](docs/tech/tech_tick.md) tick / [polling](docs/tech/tech_polling.md) フロント / [state](docs/tech/tech_state.md) 状態 / [rng](docs/tech/tech_rng.md) 乱数 / [numeric](docs/tech/tech_numeric.md) 数値
- [docs/data/master_data.md](docs/data/master_data.md) — マスターデータの索引 + 塔データ一覧
  - [master/](docs/data/master/) — character / item / equipment / base / endgame
  - [towers/TOWERS_OVERVIEW.md](docs/data/towers/TOWERS_OVERVIEW.md) 全塔概要一覧 / [skills/SKILLS_OVERVIEW.md](docs/data/skills/SKILLS_OVERVIEW.md) スキルシステム概要

### 設計図
- 索引形式: [er_diagram.md](diagrams/er_diagram.md) ER図 / [class_diagram.md](diagrams/class_diagram.md) クラス図 / [battle_flow.md](diagrams/battle_flow.md) 戦闘フロー図 / [api_sequence.md](diagrams/api_sequence.md) APIシーケンス図
- 単一: [screen_transition.md](diagrams/screen_transition.md) 画面遷移図 / [system_architecture.md](diagrams/system_architecture.md) システム構成図
