# AFK GAME — 常用コマンド

[profile.md](profile.md) §4 から移管した全工程共通のコマンド表（本ファイルが正）。
工程固有の判定・絞り込みは各工程プロファイルを参照。コミット作法のみ profile.md §4 に残している。

## 1. コマンド一覧

| 目的 | コマンド |
|------|---------|
| バックエンド構文確認 | `cd backend && mvn -q compile` |
| 単体テスト（C1計測つき） | `cd backend && mvn test`（JaCoCoで計測。判定・絞り込み・レポートは [unit-test.md](unit-test.md) §2） |
| フロント型チェック | `cd frontend && npm run type-check`（`vue-tsc --noEmit`） |
| ドキュメント規約チェック | `python scripts/check_doc_size.py`（`--list` / `--sections [path]`。上限90%超は残量WARN） |
| ドキュメント機械検証 | `python scripts/check_docs.py`（リンク・索引到達性・曖昧語・正の逸脱・決定先送り・台帳存否。`--links` 等で個別実行） |
| 分岐一覧の検証 | `python scripts/check_branch_list.py`（構造検証。`--tests` でテストとの対応照合） |
| DBスキーマ三者一致 | `python scripts/check_schema_triple.py`（定義書↔ER図↔Entity/Mapper。`--columns` `--tags` `--unique` `--nofk` `--nullable` `--naming` `--index` で個別実行） |
| 常設スクリプトの回帰テスト | `python -m pytest scripts/tests .claude/scripts/tests .claude/hooks/tests -q`（規約は [_TEMPLATE.md](_TEMPLATE.md)） |
| トークン使用量ログ | `logs/token_usage.csv`（Stop フックが自動更新。過去分は `python scripts/log_token_usage.py --all`） |
