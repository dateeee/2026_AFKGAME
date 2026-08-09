# AFK GAME — プロジェクト固有プロファイル 索引

`.claude/` は用途で3つに分かれる。**固有の値は本ディレクトリにのみ置く**。

| ディレクトリ | 性質 | 他プロジェクトへの持ち出し |
|------------|------|------------------------|
| [.claude/skills/](../skills/) | 工程の一般手順（プロジェクト非依存） | **無改造でコピー可** |
| [.claude/references/](../references/) | スキル間で共有する一般リファレンス | **無改造でコピー可** |
| `.claude/project/`（本ディレクトリ） | AFK GAME 固有の値・観点 | [_TEMPLATE.md](_TEMPLATE.md) に沿って**書き直す** |

## 共通プロファイル

| ファイル | 内容 |
|---------|------|
| [profile.md](profile.md) | 技術スタック・ディレクトリ・常用コマンド・アーキテクチャ不変条件。**全スキルが最初に読む** |
| [_TEMPLATE.md](_TEMPLATE.md) | 再利用テンプレート。新プロジェクト立ち上げ時の記述スキーマ |

## 工程 ↔ スキル ↔ プロファイル 対応表

工程の定義は [docs/process/development_process.md](../../docs/process/development_process.md)。

| # | 工程 | スキル（一般手順） | プロファイル（固有値） | ゲートスキル |
|---|------|-----------------|-------------------|------------|
| 1 | 要件定義 | `requirements` | [requirements.md](requirements.md) | `doc-review` → `fix-specs` |
| 2 | 基本設計 | `basic-design` | [basic-design.md](basic-design.md) | `diagrams-review`、`doc-review` |
| 3 | 詳細設計 | `detail-design` | [detail-design.md](detail-design.md) | `doc-review` |
| 4 | テストリスト作成 | `test-list` | [test-list.md](test-list.md) | （Red確認は工程内） |
| 5 | 製造 | `dev` | [dev.md](dev.md) | `backend-review`、`frontend-review` |
| 6 | 単体テスト | `unit-test` | [unit-test.md](unit-test.md) | （C1 100%は工程内） |
| 7 | 結合テスト | `integration-test` | [integration-test.md](integration-test.md) | `full-review` |

## 支援スキルのプロファイル

| スキル | プロファイル | 用途 |
|-------|------------|------|
| `doc-review` | [review-docs.md](review-docs.md) | 仕様書の横断レビュー |
| `diagrams-review` | [review-diagrams.md](review-diagrams.md) | 設計図と仕様書・コードの整合レビュー |
| `fix-specs` | [review-docs.md](review-docs.md) | レビュー結果の仕様書への反映 |
| `backend-review` | [review-code.md](review-code.md) | バックエンドのコードレビュー |
| `frontend-review` | [review-code.md](review-code.md) | フロントエンドのコードレビュー |
| `full-review` | [review-fullstack.md](review-fullstack.md) | 仕様↔コード・フロント↔バックの統合レビュー |
| `resolve-specs` | [requirements.md](requirements.md) | 未確定仕様の確定（要件定義工程の一部） |
| `next` | [next.md](next.md) | 新セッション冒頭で引き継ぎファイルから次タスクを特定し工程スキルへ委譲 |
| `retro` | [retro.md](retro.md) | 効率メモ（Stop フックが自動追記）の振り返りとスキル・成果物への反映 |
| `doc-size` | [doc-size.md](doc-size.md) | ドキュメント文字数の是正（分割か圧縮かの判断と適用・台帳の消化） |

## 補助プロファイル

| ファイル | 参照元 | 内容 |
|---------|-------|------|
| [test-patterns.md](test-patterns.md) | `test-list`、`unit-test` | AFK GAME のモジュール名・エラーコードを使ったテスト実装の実例 |
| [commands.md](commands.md) | 全スキル（[profile.md](profile.md) §4 経由） | 全工程共通の常用コマンド表（索引・§1） |
| [commands/backend.md](commands/backend.md) | `dev`、`unit-test`、`integration-test` | §2 出力の受け取り方 / §3 モジュールを絞ったテスト / §5 外部ツールの所在 / §6 起動と疎通確認 |
| [commands/adhoc.md](commands/adhoc.md) | 使い捨て調査を行うスキル | §4 使い捨て調査の作法 |

## 共有リファレンス（`.claude/references/`）

| ファイル | 参照元 | 内容 |
|---------|-------|------|
| [review-procedure.md](../references/review-procedure.md) | レビュー系5スキル | レビューの共通手順・コスト規律 |
| [review-format.md](../references/review-format.md) | レビュー系5スキル | レビュー結果の出力形式 |
| [coding-standards-backend.md](../references/coding-standards-backend.md) | `dev`、`backend-review`、`unit-test` | Java 実装の規約要約の**索引**（§1 使い方 + 分冊表）。**正は [docs/process/coding_standards_backend.md](../../docs/process/coding_standards_backend.md)**（本書はその派生） |
| [coding-standards-backend/layering.md](../references/coding-standards-backend/layering.md) | 同上 | §2 層の責務・呼び出し可否・横断処理 |
| [coding-standards-backend/writing.md](../references/coding-standards-backend/writing.md) | 同上 | §3 記述（書式・型・定数・可視性） |
| [coding-standards-backend/exception-logging.md](../references/coding-standards-backend/exception-logging.md) | 同上 | §4 例外・トランザクション・ログ・Javadoc |
| [coding-standards-backend/review-checklist.md](../references/coding-standards-backend/review-checklist.md) | `backend-review` | §5 レビュー観点チェックリスト |

改稿時は [docs/changelog.md](../../docs/changelog.md) の先頭へ追記する（各ファイルに変更履歴セクションを置かない）。
