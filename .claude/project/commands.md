# AFK GAME — 常用コマンド

[profile.md](profile.md) §4 から移管した全工程共通のコマンド表（本ファイルが正）。
工程固有の判定・絞り込みは各工程プロファイルを参照。コミット作法のみ profile.md §4 に残している。

## 1. コマンド一覧

| 目的 | コマンド |
|------|---------|
| バックエンド構文確認 | `cd backend && mvn -q compile` |
| 単体テスト（C1計測つき） | `cd backend && mvn test`（JaCoCoで計測。判定・絞り込み・レポートは [unit-test.md](unit-test.md) §2） |
| フロント型チェック | `cd frontend && npm run type-check`（`vue-tsc --noEmit`） |
| ドキュメント規約チェック | `python scripts/check_doc_size.py`（`--list`。上限90%超は残量WARN） |
| 特定ファイルの残量・H2内訳 | `python scripts/check_doc_size.py --sections <path>`（**`--sections` を付けずに path だけ渡すと無視されて全件チェックになる**） |
| ドキュメント機械検証 | `python scripts/check_docs.py`（リンク・索引到達性・曖昧語・正の逸脱・決定先送り・台帳存否。`--links` 等で個別実行） |
| 分岐一覧の検証 | `python scripts/check_branch_list.py`（構造検証。`--tests` でテストとの対応照合） |
| DBスキーマ三者一致 | `python scripts/check_schema_triple.py`（定義書↔ER図↔Entity/Mapper。`--columns` `--tags` `--unique` `--nofk` `--nullable` `--naming` `--index` で個別実行） |
| 常設スクリプトの回帰テスト | `python -m pytest scripts/tests .claude/scripts/tests .claude/hooks/tests -q`（規約は [_TEMPLATE.md](_TEMPLATE.md)） |
| トークン使用量ログ | `logs/token_usage.csv`（Stop フックが自動更新。過去分は `python scripts/log_token_usage.py --all`） |

## 2. 出力の受け取り方

| 対象 | 方法 |
|------|------|
| `mvn` の出力解析（Red確認・失敗原因の特定） | `cd backend && mvn test > <スクラッチパッド>/mvn.log 2>&1` でファイルへ落とし、`ctx_execute` の python で **`cp932` デコード**して読む。**Bash のパイプ + `grep` は使わない**（日本語が文字化けし、`[ERROR]` に続く字下げ行＝`シンボル: クラス X` が grep から落ちる。読み直しで重い `mvn` を2回走らせることになる） |
| 使い捨てスクリプト | **Write ツールでスクラッチパッドへ作成** → `python <path> <リポジトリルート>` で実行する（Bash のヒアドキュメント + リダイレクトでの作成は worktree セッションで拒否される） |
