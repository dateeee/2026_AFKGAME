# AFK GAME — バックエンドの実行コマンド

> [commands.md](../commands.md) の子ファイル（索引側が正）。担当: §2・§3・§5・§6。

## 2. 出力の受け取り方

| 対象 | 方法 |
|------|------|
| `mvn` の出力解析（Red確認・失敗原因の特定） | **まず `python scripts/report_java_tests.py --run`**（要約のみ出力。生ログは `backend/target/mvn.log` に残り、javac エラーは字下げ行ごと抽出済み）。生ログを読むときは `ctx_execute` の python で **`cp932` デコード**する。**Bash のパイプ + `grep` は使わない**（日本語が文字化けし、`[ERROR]` に続く字下げ行＝`シンボル: クラス X` が grep から落ちる。読み直しで重い `mvn` を2回走らせることになる） |
| テスト件数・失敗件数 | 同スクリプトの集計値を使う。**自前で数えるなら `<testcase` の出現数**で数え、`*.txt` の「Tests run」も XML ルートの `tests=` 属性も**使わない**（JUnit 5 の `@Nested` 配下を 0 と報告する。実測: `MasterDataLoaderTest` は `tests="0"` だが `<testcase` は11件）。失敗は `<failure` / `<error` |
| 使い捨てスクリプト | **Write ツールでスクラッチパッドへ作成** → `python <path> <リポジトリルート>` で実行する（Bash のヒアドキュメント + リダイレクトでの作成は worktree セッションで拒否される。`python -c` へ日本語を直接書くと CP932 で壊れて SyntaxError になる） |

## 3. バックエンド: モジュールを絞ってテストする

全体 `mvn test` が重いときの絞り込み。**下の5点はセットで使う**（1つ欠けると空振りするか、誤った実測値を読む）。

| # | 指定 | 欠けたときに起きること |
|---|------|--------------------|
| 1 | `-pl <module>` には**必ず `-am` を付ける** | `~/.m2` の**変更前の成果物**を解決し、「変更が効いていない」という誤った結論を実測値として読む |
| 2 | 単一モジュールを回す前に親 POM を `mvn -N install` | 依存解決に失敗する |
| 3 | `-Dtest=<クラス>` には `-Dsurefire.failIfNoSpecifiedTests=false` | 対象クラスを持たないモジュールで surefire が落ちる |
| 4 | pom へプラグインを新規追加した**直後の初回だけ** `-o`（オフライン）を外す | 未取得のプラグインを解決できず失敗する |
| 5 | ゴールの先頭に `clean` | surefire はレポートディレクトリを掃除しないため、**移動・改名したテストの旧レポートが残って二重計上**される。JaCoCo も前回の実行のまま残り、現行コードを測っていない 100% を実測値として読む |

例: `cd backend && mvn -N install -q` の後に
`mvn -pl afkgame-domain -am clean test -Dtest=BattleServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`。
出力の読み方（CP932・ファイル経由）は §2。
`python scripts/report_java_tests.py --run --module <名前> [--test <クラス>]` は上の5点を自動で付ける
（clean を省いて速くしたいときだけ `--no-clean`。集計側は残骸を検出して除外し、判定へ
`STALE_REPORTS` / `COVERAGE_STALE` を出す）。

## 5. 外部ツールの所在

新規シェルでの `mvn -version` / `CATALINA_HOME` 実測（2026-08-09）。

| ツール | 版・所在 | シェルへの反映 |
|-------|---------|--------------|
| JDK | Adoptium 17.0.20（所在は `%JAVA_HOME%`） | **反映済み**。そのまま呼べる |
| Maven | 3.9.11 | **反映済み**。`mvn` をそのまま呼べる |
| Tomcat | 11.0.24（`%LOCALAPPDATA%\Programs\apache-tomcat-11.0.24`） | `CATALINA_HOME` はユーザー環境変数へ設定済みだが**既存シェルには未反映**。コマンド側で明示するかフルパスで呼ぶ |

## 6. 起動と疎通確認

1行で終わらない手順（[commands.md](../commands.md) §1 から移管）。

| 目的 | 手順 |
|------|------|
| バックエンド起動（war + Tomcat） | `cd backend && mvn clean install` → war を `$CATALINA_HOME/webapps/ROOT.war` へコピー → `SPRING_PROFILES_ACTIVE=local` を与えて `catalina.bat run`（手順の正は [tech_operations.md](../../../docs/tech/nonfunctional/tech_operations.md) §12.1。**要 `CATALINA_HOME`**。実行可能 jar は無い） |
| E2E ハーネスの疎通確認 | `docker compose up -d` → `cd backend && mvn -DskipTests package` → `node frontend/tests/e2e/support/serve-backend.mjs`（専用DB `afkgame_e2e` を作り直し :8100 で war を起動。`SPRING_PROFILES_ACTIVE` はハーネスが付与）。`GET /health` が `db:ok` を返せば疎通 |
