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
| [review.md](review.md) | レビュー系5スキル + `fix-specs` の共通パラメータ・重要度の共通軸・分冊表。**レビュー時に最初に読む** |
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

レビュー系は **[review.md](review.md)（共通）+ 分冊1件**を読む（分冊どうしは読み合わない）。

| スキル | プロファイル | 用途 |
|-------|------------|------|
| `doc-review` | [review.md](review.md) + [review/docs.md](review/docs.md) | 仕様書の横断レビュー |
| `diagrams-review` | [review.md](review.md) + [review/diagrams.md](review/diagrams.md) | 設計図と仕様書・コードの整合レビュー |
| `fix-specs` | [review.md](review.md) + [review/docs.md](review/docs.md) §5 | レビュー結果の仕様書への反映 |
| `backend-review` | [review.md](review.md) + [review/backend.md](review/backend.md) | バックエンドのコードレビュー |
| `frontend-review` | [review.md](review.md) + [review/frontend.md](review/frontend.md) | フロントエンドのコードレビュー |
| `full-review` | [review.md](review.md) + [review/fullstack.md](review/fullstack.md) | 仕様↔コード・フロント↔バックの統合レビュー |
| `spot-review` | [review.md](review.md) + [review/spot.md](review/spot.md) | 対象（ファイル・フォルダ・工程）を指定するレビュー。工程別の一般観点は `skills/spot-review/references/**` |
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
| [dev/verification.md](dev/verification.md) | `dev` | §5.1 動作確認時の Gotchas（外部依存の版調査・DIコンテナ起動確認・`Edit` の `old_string` 特定）。**該当状況でのみ読む** |
| [integration-test/conventions.md](integration-test/conventions.md) | `integration-test` | §1.1 L1（MockMvc）/ §1.2 L2（Playwright）の記述規約。**テストを書くときのみ読む** |

## 共有リファレンス（`.claude/references/`）

| ファイル | 参照元 | 内容 |
|---------|-------|------|
| [review-procedure.md](../references/review-procedure.md) | レビュー系5スキル | レビューの共通手順・コスト規律 |
| [review-format.md](../references/review-format.md) | レビュー系5スキル | レビュー結果の出力形式 |
| [script-conventions.md](../references/script-conventions.md) | スクリプトを新設・改修する全スキル | `.claude/` 配下のスクリプトの置き場所・回帰テスト（緑パス + 変異テスト）・固有値の引数化 |
| [coding-standards-backend.md](../references/coding-standards-backend.md) | `dev`、`backend-review`、`unit-test` | Java 実装の規約要約の**索引**。§2〜§5 の分冊（`coding-standards-backend/`）は同書の分冊表から開く（§5 チェックリストは `backend-review` のみ）。**正は [docs/process/coding_standards_backend.md](../../docs/process/coding_standards_backend.md)**（本書はその派生） |

改稿時は [docs/changelog.md](../../docs/changelog.md) の先頭へ追記する（各ファイルに変更履歴セクションを置かない）。
