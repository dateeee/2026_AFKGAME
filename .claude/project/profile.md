# AFK GAME — プロジェクトプロファイル

全スキルが最初に読む共通プロファイル。工程固有の値は [INDEX.md](INDEX.md) の対応表から該当プロファイルを開く。

## 1. 基本情報

| 項目 | 値 |
|------|-----|
| 種別 | 放置系ファンタジーRPG（Webブラウザゲーム） |
| 構成 | フロントエンド（Vue 3 SPA）+ バックエンド（FastAPI）の2層 |
| プレイ形態 | **シングルプレイ専用**（マルチプレイは想定しない） |
| 開発段階 | Phase 1 完了、Phase 2 進行中。仕様は Phase 1〜5 まで確定済み |
| 開発工程 | 7工程（[docs/development_process.md](../../docs/development_process.md)） |

## 2. ディレクトリ

| パス | 内容 |
|------|------|
| `backend/app/` | `models/` `schemas/` `services/` `routers/` `master_data/` `db/` + `config.py` `main.py` `dependencies.py` `exceptions.py` `logging_config.py` `middleware.py` |
| `backend/tests/unit/` | 単体テスト（pytest）。`conftest.py` `helpers.py` は `backend/tests/` 直下 |
| `backend/tests/integration/` | API統合テスト（**未整備**。結合テスト着手時に作成） |
| `frontend/src/` | `components/` `views/` `stores/` `api/` `types/` `composables/` `router/` `utils/` `assets/` |
| `frontend/tests/e2e/` | E2Eテスト（**未整備**。Playwright は結合テスト着手時に導入） |
| `docs/design/` | 要件定義の成果物（`game_spec.md` 索引 + `systems/`） |
| `docs/tech/` | 基本設計・詳細設計の成果物（`tech_spec.md` 索引 + `tech_*.md`） |
| `docs/data/` | マスターデータ（`master_data.md` 索引 + `master/` `towers/` `skills/`） |
| `diagrams/` | 設計図6点（各図は索引 + 同名ディレクトリ構成） |
| `docs/reviews/` | レビュー結果の追記型アーカイブ（スキル名ごとのディレクトリ + `archive/`。ドキュメント規約の文字数対象外） |

## 3. 技術スタック

| 層 | 技術 | 規約 |
|----|------|------|
| DBモデル | SQLAlchemy 2.0 | `Mapped[]` 型アノテーション + `mapped_column()`。旧 `Column()` スタイル禁止 |
| スキーマ | Pydantic v2 | `CamelModel` を継承し `schemas/` に配置。`model_config = ConfigDict(from_attributes=True)`。旧 `class Config` 禁止 |
| ロジック | Python | `services/` に集約。ルーターにビジネスロジックを書かない |
| API | FastAPI | `APIRouter`（prefix / tags / response_model を指定）、依存性注入は `Depends` |
| ログ | 標準 logging | `logging_config` 準拠 |
| UI | Vue 3 | `<script setup lang="ts">` + Composition API |
| 状態管理 | Pinia | `defineStore` の **Setup Store 形式** |
| 型 | TypeScript | 厳密な型定義。`any` を避ける |
| テスト | pytest + pytest-cov | C1（分岐）カバレッジ100%。設定は `backend/pytest.ini` / `backend/.coveragerc` |

## 4. 常用コマンド

| 目的 | コマンド |
|------|---------|
| バックエンド構文確認 | `cd backend && python -m py_compile app/main.py` |
| 単体テスト（カバレッジ付き） | `cd backend && python -m pytest -q` |
| C1 100% 判定 | `cd backend && python -m pytest --cov=app --cov-branch --cov-fail-under=100 -q` |
| 未達分岐の特定 | `cd backend && python -m pytest --cov=app --cov-branch --cov-report=term-missing -q` |
| HTMLカバレッジレポート | `backend/htmlcov/index.html`（未実行行=赤、部分分岐=黄） |
| フロント型チェック | `cd frontend && npm run type-check`（`vue-tsc --noEmit`） |
| ドキュメント規約チェック | `python scripts/check_doc_size.py`（`--list` / `--sections`） |

## 5. アーキテクチャ不変条件

破ってはいけない前提。設計・実装・レビューのすべてで守る。

| # | 不変条件 | 理由 |
|---|---------|------|
| 1 | **サーバー権威**: 戦闘計算はバックエンドで実行する。フロントはログ表示のみ | チート対策 |
| 2 | **ハイブリッドtick制**: 60秒固定間隔。オンライン中はポーリング、オフライン中は復帰時に一括計算 | 放置ゲームの中核 |
| 3 | **シングルプレイ専用** | マルチプレイ前提の設計を持ち込まない |
| 4 | **開発時フォールバック**: バックエンド未起動でもフロント単体で動作する（`frontend/src/composables/useBattleLocal.ts`） | デバッグ用途 |
| 5 | **Phase厳守**: 対象Phaseより後のPhaseの機能を実装しない（将来拡張を考慮した設計は可） | 段階的リリース |
| 6 | **データ駆動**: マスターデータの数値をコードにハードコードしない | バランス調整の分離 |

## 6. コスト規律

`.claude/skills/**` の全スキルに共通して適用する。

| # | 規律 |
|---|------|
| 1 | サブエージェントは**並列化の価値がある場合のみ**（同時最大4体）。1体で済む調査・修正はメインコンテキストで行う |
| 2 | 機械的な作業（一括置換・リンク修正・定型データ生成・構文検証）は `model: sonnet` のサブエージェントか使い捨てスクリプトで処理する |
| 3 | サブエージェントには**担当ファイルのみを列挙**し、「列挙外は読まない」「戻り値は結論のみ」を明示する |
| 4 | 仕様書・コードは**必要なセクションだけ読む**（大きなファイルの全文読み込みを避ける） |
| 5 | レビュー系スキルは**差分モードがデフォルト**。全量は `full` 指定時のみ |
| 6 | 工程の区切り（レビュー完了・コミット後）で `/compact` または `/clear` を提案する |

## 7. ドキュメント規約

| # | 規約 |
|---|------|
| 1 | 文字数上限は [docs/documentation_rules.md](../../docs/documentation_rules.md) §3（`.claude/**` は区分D = 5,000字） |
| 2 | **変更履歴セクションを個々のファイルに置かない**。改稿時は [docs/changelog.md](../../docs/changelog.md) の先頭へ1行追記する（§5.1） |
| 3 | ドキュメントの作成・改稿後は `python scripts/check_doc_size.py` を実行する（超過・履歴セクションの復活は exit 1） |

