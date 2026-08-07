# AFK GAME — プロジェクトプロファイル

全スキルが最初に読む共通プロファイル。工程固有の値は [INDEX.md](INDEX.md) の対応表から該当プロファイルを開く。

## 1. 基本情報

| 項目 | 値 |
|------|-----|
| 種別 | 放置系ファンタジーRPG（Webブラウザゲーム） |
| 構成 | フロントエンド（Vue 3 SPA）+ バックエンド（FastAPI）の2層 |
| 開発工程 | 7工程。工程の定義と**Phase進捗の正**は [development_process.md](../../docs/development_process.md)（進捗は §5） |

## 2. ディレクトリ

| パス | 内容 |
|------|------|
| `backend/app/` | `models/` `schemas/` `services/` `routers/` `master_data/` `db/` + 直下に `main.py` `config.py` `dependencies.py` `exceptions.py` `logging_config.py` `middleware.py` |
| `backend/tests/` | `unit/`（単体・pytest）、`integration/`（API統合）。`conftest.py` `helpers.py` は直下 |
| `frontend/src/` | `components/` `views/` `stores/` `api/` `types/` `composables/` `router/` `utils/` `assets/` |
| `frontend/tests/e2e/` | E2Eテスト（Playwright） |
| `docs/design/` | 要件定義の成果物（`game_spec.md` 索引 + `systems/`） |
| `docs/tech/` | 基本設計・詳細設計の成果物（`tech_spec.md` 索引 + `tech_*.md`） |
| `docs/data/` | マスターデータ（`master_data.md` 索引 + `master/` `towers/` `skills/`） |
| `diagrams/` | 設計図6点（各図は索引 + 同名ディレクトリ構成） |
| `docs/reviews/` | レビュー結果の追記型アーカイブ（スキル名ごと + `archive/`。文字数上限の対象外） |

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
| テスト | pytest + pytest-cov | C1（分岐）カバレッジ100%（設定は [unit-test.md](unit-test.md) §1） |

## 4. 常用コマンド

| 目的 | コマンド |
|------|---------|
| バックエンド構文確認 | `cd backend && python -m py_compile app/main.py` |
| 単体テスト（C1計測つき） | `cd backend && python -m pytest -q`（判定・絞り込み・レポートは [unit-test.md](unit-test.md) §2） |
| フロント型チェック | `cd frontend && npm run type-check`（`vue-tsc --noEmit`） |
| ドキュメント規約チェック | `python scripts/check_doc_size.py`（`--list` / `--sections [path]`。上限90%超は残量WARN） |
| ドキュメント機械検証 | `python scripts/check_docs.py`（リンク・索引到達性・曖昧語・正の逸脱・決定先送り・台帳存否。`--links` 等で個別実行） |
| 分岐一覧の検証 | `python scripts/check_branch_list.py`（構造検証。`--tests` でテストとの対応照合） |
| DBスキーマ三者一致 | `python scripts/check_schema_triple.py`（定義書↔ER図↔models。`--columns` `--tags` `--unique` `--nofk` `--nullable` `--naming` `--index` で個別実行） |
| 常設スクリプトの回帰テスト | `python -m pytest scripts/tests .claude/scripts/tests .claude/hooks/tests -q`（規約は [_TEMPLATE.md](_TEMPLATE.md)） |
| トークン使用量ログ | `logs/token_usage.csv`（Stop フックが自動更新。過去分は `python scripts/log_token_usage.py --all`） |
| 複数行コミットメッセージ | Bash ツール: `git commit -F - <<'MSG'`／PowerShell ツール: `git commit -m @'...'@`。**取り違えると `@` が本文へ混入し amend が必要になる** |

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

`.claude/skills/**` の全スキルに共通して適用する。**本表が正**（CLAUDE.md「コスト規律」は要約 + リンク。[docs/spec_ownership.md](../../docs/spec_ownership.md)）。

| # | 規律 |
|---|------|
| 1 | サブエージェントは**並列化の価値がある場合のみ**（同時最大4体）。1体で済む調査・修正はメインコンテキストで行う |
| 2 | 機械的な作業（一括置換・リンク修正・定型データ生成・構文検証）は `model: sonnet` のサブエージェントか使い捨てスクリプトで処理する |
| 3 | サブエージェントには**担当ファイルのみを列挙**し、「列挙外は読まない」「戻り値は結論のみ」を明示する |
| 4 | 仕様書・コードは**必要なセクションだけ読む**（大きなファイルの全文読み込みを避ける） |
| 5 | 工程の区切り（レビュー完了・コミット後）で `/clear` を既定として提案する（同一タスクを続ける場合のみ `/compact`）。レビュー→修正適用は別セッションに分ける |
| 6 | 大きな出力（ログ・テスト結果・git履歴・集計・検索）の処理は context-mode（`ctx_batch_execute` / `ctx_execute`）で行い、生出力を会話に持ち込まない。ファイルの分析・要約は `ctx_execute_file`。`Read` の全文読みは Edit 前提のときのみ |

レビュー系スキル固有の規律（差分モード既定・分担・照合範囲）は [review-procedure.md](../references/review-procedure.md) §1 が正。

規律4の運用手順:

| 場面 | 手順 |
|------|------|
| 仕様書参照 | 索引（`tech_spec.md` 等）で担当ファイルを特定 → 該当ファイルのみ読む。大きいファイルは `Read` の offset/limit で節単位 |
| 再読の禁止 | 同一セッション内で同じファイルを再 Read しない（Edit 失敗時の再確認を除く） |

## 7. ドキュメント規約

| # | 規約 |
|---|------|
| 1 | 文字数上限は [docs/documentation_rules.md](../../docs/documentation_rules.md) §3（`.claude/**` は区分D = 5,000字） |
| 2 | **変更履歴セクションを個々のファイルに置かない**。改稿時は [docs/changelog.md](../../docs/changelog.md) の先頭へ1行追記する（§5.1） |
| 3 | 作成・改稿後は §4 の規約チェックと機械検証を実行する。超過は [documentation_rules.md](../../docs/documentation_rules.md) §7 の台帳運用（B・Cは登録して一括是正へ、A・Dはセッション内是正） |
| 4 | 同じ数値・仕様の正は1ファイル。トピックごとの正は [docs/spec_ownership.md](../../docs/spec_ownership.md) で宣言する |
| 5 | 機械検証は §4 の常設スクリプトを優先し、使い捨ては常設で賄えない検証のみ（繰り返すなら常設化を提案する） |
| 6 | CLAUDE.md と `.claude/project/**` で重複するルールを改稿する際は、もう一方を必ず突合して同時に更新する |

