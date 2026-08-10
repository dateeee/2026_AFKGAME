# AFK GAME — 常用コマンド

[profile.md](profile.md) §4 から移管した全工程共通のコマンド表（本ファイルが索引かつ §1 の正）。
工程固有の判定・絞り込みは各工程プロファイルを参照。コミット作法のみ profile.md §4 に残している。

| 分冊 | 担当節 |
|------|-------|
| [commands/backend.md](commands/backend.md) | §2 出力の受け取り方 / §3 モジュールを絞ったテスト / §5 外部ツールの所在 / §6 起動と疎通確認 |
| [commands/adhoc.md](commands/adhoc.md) | §4 使い捨て調査の作法 |

## 1. コマンド一覧

1行で完結するもの。手順を伴う起動・疎通確認は [commands/backend.md](commands/backend.md) §6。

| 目的 | コマンド |
|------|---------|
| バックエンド構文確認 | `cd backend && mvn -q compile` |
| 単体テスト（C1計測つき） | `cd backend && mvn test`（JaCoCoで計測。判定・絞り込み・レポートは [unit-test.md](unit-test.md) §2） |
| Javaテスト結果の要約 | `python scripts/report_java_tests.py`（集計と判定。使い方は [commands/backend.md](commands/backend.md) §2 と `--help`） |
| フロント型チェック | `cd frontend && npm run type-check`（`vue-tsc --noEmit`） |
| ドキュメント規約チェック | `python scripts/check_doc_size.py`（`--list`。上限90%超は残量WARN） |
| 特定ファイルの残量・H2内訳 | `python scripts/check_doc_size.py --sections <path>`（**`--sections` を付けずに path だけ渡すと無視されて全件チェックになる**） |
| ドキュメント機械検証 | `python scripts/check_docs.py`（リンク・索引到達性・曖昧語・正の逸脱・決定先送り・台帳存否。`--links` 等で個別実行） |
| 分岐一覧の検証 | `python scripts/check_branch_list.py`（構造検証。`--tests` でテストとの対応照合） |
| Java 規約チェック | `python scripts/check_java_conventions.py`（タブ・行長・import・ログ・DI・SQL・日時・乱数・マスク・未参照の13判定。`--format` 等で個別実行。避けられない箇所は `// 規約例外: <理由>` で抑止。未参照は WARN で exit code に算入しない） |
| エラーコード一致 | `python scripts/check_error_codes.py`（`tech_error_handling.md` ↔ Web層の `ErrorCatalog`。欠落・余剰・ステータス不一致。`--summary` で件数のみ） |
| DBスキーマ一致 | `python scripts/check_schema_triple.py`（定義書↔ER図↔models↔Flyway DDL。`--columns` `--tags` `--unique` `--nofk` `--nullable` `--naming` `--index` で個別実行） |
| 常設スクリプトの回帰テスト | `python -m pytest scripts/tests .claude/scripts/tests .claude/hooks/tests -q`（規約は [_TEMPLATE.md](_TEMPLATE.md)） |
| トークン使用量ログ | `logs/token_usage.csv`（Stop フックが自動更新。過去分は `python scripts/log_token_usage.py --all`） |
| DB操作（起動中コンテナ） | `docker exec afkgame-postgres <cmd>`。**`docker compose exec` は使わない** — compose のプロジェクト名が cwd 由来で、worktree からは起動中コンテナを引けない（`container_name` は固定なので `docker exec` なら引ける） |
