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

## 4. 使い捨て調査の作法

その場限りの検証スクリプト・外部問い合わせで空振りを繰り返さないための作法。

| # | 作法 |
|---|------|
| 1 | **1件で検算してから全量へ回す**。中間件数が期待の桁と合うか必ず確かめる。**`0件`・極端に少ない件数は「異常なし」ではなく解析失敗を疑う**（`unzip` の glob が入れ子に当たらない・`awk` が想定外の行を飲む・引数が多すぎてコマンドラインが溢れる等は、もっともらしい件数を返して黙って失敗する） |
| 2 | **worktree 作業中は context-mode 系ツールへ `cwd` を明示する**。既定はプロジェクトルートで worktree を指さないため、1バッチ丸ごと `No such file or directory` になる |
| 3 | **生成と読み取りは同じ `language` で完結させる**（`ctx_execute` の shell が `/tmp` へ書いたファイルは python の実行環境から見えない） |
| 4 | **`Grep` の `glob` に否定（`!...`）は使えない**。除外を伴う横断調査は `ctx_execute` 内でフィルタする |
| 5 | **API の実在確認（クラス名・コンストラクタ・既定値の有無）は着手前に項目を列挙し、`javap` / `unzip` を1バッチで出す**。前の答えが次の問いを生む形で投げると往復が芋づる式に増える |
| 6 | **Maven Central の版は `https://repo1.maven.org/maven2/<groupId のスラッシュ表記>/<artifactId>/maven-metadata.xml` の `<release>`** を見る（`search.maven.org` の solrsearch API は遅く落ちやすい）。`mvn dependency:tree` に **`-q` を付けない**（ツリーは INFO 出力なので消える） |
| 7 | **使い捨て Java を Maven のクラスパスで動かす**: `mvn -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=cp.txt` → `-cp` / `-d` に渡すパスは `cygpath -w` で Windows 形式へ直す（Git Bash の `/c/...` を `javac` / `java` は解釈できない）。クラスパスの区切りは `;` |
