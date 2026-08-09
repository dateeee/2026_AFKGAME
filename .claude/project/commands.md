# AFK GAME — 常用コマンド

[profile.md](profile.md) §4 から移管した全工程共通のコマンド表（本ファイルが正）。
工程固有の判定・絞り込みは各工程プロファイルを参照。コミット作法のみ profile.md §4 に残している。

## 1. コマンド一覧

| 目的 | コマンド |
|------|---------|
| バックエンド構文確認 | `cd backend && mvn -q compile` |
| バックエンド起動（war + Tomcat） | `cd backend && mvn clean install` → war を `$CATALINA_HOME/webapps/ROOT.war` へコピー → `SPRING_PROFILES_ACTIVE=local` を与えて `catalina.bat run`（手順の正は [tech_operations.md](../../docs/tech/nonfunctional/tech_operations.md) §12.1。**要 `CATALINA_HOME`**。実行可能 jar は無い） |
| 単体テスト（C1計測つき） | `cd backend && mvn test`（JaCoCoで計測。判定・絞り込み・レポートは [unit-test.md](unit-test.md) §2） |
| フロント型チェック | `cd frontend && npm run type-check`（`vue-tsc --noEmit`） |
| ドキュメント規約チェック | `python scripts/check_doc_size.py`（`--list`。上限90%超は残量WARN） |
| 特定ファイルの残量・H2内訳 | `python scripts/check_doc_size.py --sections <path>`（**`--sections` を付けずに path だけ渡すと無視されて全件チェックになる**） |
| ドキュメント機械検証 | `python scripts/check_docs.py`（リンク・索引到達性・曖昧語・正の逸脱・決定先送り・台帳存否。`--links` 等で個別実行） |
| 分岐一覧の検証 | `python scripts/check_branch_list.py`（構造検証。`--tests` でテストとの対応照合） |
| DBスキーマ一致 | `python scripts/check_schema_triple.py`（定義書↔ER図↔models↔Flyway DDL。`--columns` `--tags` `--unique` `--nofk` `--nullable` `--naming` `--index` で個別実行） |
| 常設スクリプトの回帰テスト | `python -m pytest scripts/tests .claude/scripts/tests .claude/hooks/tests -q`（規約は [_TEMPLATE.md](_TEMPLATE.md)） |
| トークン使用量ログ | `logs/token_usage.csv`（Stop フックが自動更新。過去分は `python scripts/log_token_usage.py --all`） |
| DB操作（起動中コンテナ） | `docker exec afkgame-postgres <cmd>`。**`docker compose exec` は使わない** — compose のプロジェクト名が cwd 由来で、worktree からは起動中コンテナを引けない（`container_name` は固定なので `docker exec` なら引ける） |
| E2E ハーネスの疎通確認 | `docker compose up -d` → `cd backend && mvn -DskipTests package` → `node frontend/tests/e2e/support/serve-backend.mjs`（専用DB `afkgame_e2e` を作り直し :8100 で war を起動。`SPRING_PROFILES_ACTIVE` はハーネスが付与）。`GET /health` が `db:ok` を返せば疎通 |

**外部ツールの所在**（新規シェルでの `mvn -version` / `CATALINA_HOME` 実測。2026-08-09）

| ツール | 版・所在 | シェルへの反映 |
|-------|---------|--------------|
| JDK | Adoptium 17.0.20（`JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`） | **反映済み**。そのまま呼べる |
| Maven | 3.9.11 | **反映済み**。`mvn` をそのまま呼べる |
| Tomcat | 11.0.24（`%LOCALAPPDATA%\Programs\apache-tomcat-11.0.24`） | `CATALINA_HOME` はユーザー環境変数へ設定済みだが**既存シェルには未反映**。コマンド側で明示するかフルパスで呼ぶ |

## 2. 出力の受け取り方

| 対象 | 方法 |
|------|------|
| `mvn` の出力解析（Red確認・失敗原因の特定） | `cd backend && mvn test > <スクラッチパッド>/mvn.log 2>&1` でファイルへ落とし、`ctx_execute` の python で **`cp932` デコード**して読む。**Bash のパイプ + `grep` は使わない**（日本語が文字化けし、`[ERROR]` に続く字下げ行＝`シンボル: クラス X` が grep から落ちる。読み直しで重い `mvn` を2回走らせることになる） |
| 使い捨てスクリプト | **Write ツールでスクラッチパッドへ作成** → `python <path> <リポジトリルート>` で実行する（Bash のヒアドキュメント + リダイレクトでの作成は worktree セッションで拒否される。`python -c` へ日本語を直接書くと CP932 で壊れて SyntaxError になる） |

## 3. バックエンド: モジュールを絞ってテストする

全体 `mvn test` が重いときの絞り込み。**下の4点はセットで使う**（1つ欠けると空振りするか、誤った実測値を読む）。

| # | 指定 | 欠けたときに起きること |
|---|------|--------------------|
| 1 | `-pl <module>` には**必ず `-am` を付ける** | `~/.m2` の**変更前の成果物**を解決し、「変更が効いていない」という誤った結論を実測値として読む |
| 2 | 単一モジュールを回す前に親 POM を `mvn -N install` | 依存解決に失敗する |
| 3 | `-Dtest=<クラス>` には `-Dsurefire.failIfNoSpecifiedTests=false` | 対象クラスを持たないモジュールで surefire が落ちる |
| 4 | pom へプラグインを新規追加した**直後の初回だけ** `-o`（オフライン）を外す | 未取得のプラグインを解決できず失敗する |

例: `cd backend && mvn -N install -q` の後に
`mvn -pl afkgame-domain -am test -Dtest=BattleServiceTest -Dsurefire.failIfNoSpecifiedTests=false`。
出力の読み方（CP932・ファイル経由）は §2。
