# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-10 / main `701e67a`（**ログ3種別（通信・アプリケーション・エラー）を実装した**（旧キュー5・`java_migration.md` の STEP と独立）。`carryover_notes.md` §3 の差分5点を解消: ①`logback.xml` へ `COMMUNICATION`/`APPLICATION`/`ERROR_ALERT` の3appender・`LOG_DIR`・日次ローテ14日・gz圧縮を追加（`logback-encoder-*.xml` を `logback-appenders-*.xml` へ改名）②`RequestLogFilter` を `afkgame.comm` ロガー・START/END対（`direction=in`）へ書き換え③`LayerLoggingInterceptor`（新設）+ `AspectJExpressionPointcutAdvisor` 2本（Service/Repository境界。ポイントカット式は `afkgame.properties`）④`LoggerName.COMM`/`LAYER`・`LogKey.DIRECTION`/`TARGET`/`SIGNATURE`/`ARGS`/`RESULT` を追加⑤`VerificationMailSenderImpl` の送信を通信ログ（`direction=out target=smtp`）で挟んだ。付随して `LogReason.EXCEPTION`（AOP境界ログの例外時reason。`tech_logging.md` へも追記）と `MaskReturnValue` 注釈（`JwtService#createAccessToken` の生トークンを伏せる）を新設し、`AuthResult#toString()` をオーバーライドしてトークンを伏せた。**`mvn verify` 195件（単体）+ 46件（結合）Green・C1 100%**。**次回（下記）は本セッションでは未着手**（キュー5とは別のタスク。ISSUE-701・702・709・711 とメール正規化は main `badc375` で完了済み）。直前までの成果は [changelog.md](../changelog.md) の 2026-08-09 ブロックが正。

**STEP 2R は完了済みで backend の Phase 機能へ着手してよい**。以後の移行順序は **3-A-2 → 3-A-3 → 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**。順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)（索引 + `java_migration/` 3分冊）。**tower は `tech_tower.md` が無く分岐一覧も未作成**なので、3-B は `detail-design` から始める。

**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（Python 削除を STEP 3〜5 より先に実施したため）。E2E はハーネスと `GET /health` まで疎通済みだが、テスト本体は STEP 5 完了まで赤が正常。

**複数セッションにまたがる申し送りの正は [carryover_notes.md](carryover_notes.md)**（§1 Java 移行 / §2 仕様・マスターデータ / §3 環境・ツール）。着手前にそちらも見る。**恒久的な知見は同ファイルに残さず規約・コマンド表の正へ移す**方針なので、Java 実装の流儀は [coding_standards_backend.md](../process/coding_standards_backend.md) の分冊、環境・コマンド（Tomcat の所在・`SPRING_PROFILES_ACTIVE` 必須・`docker exec` を使う理由）は [commands.md](../../.claude/project/commands.md) が正。

## 0. 並行作業のルール（着手前に読む）

worktree を使う複数セッションが同時に走る前提。**着手状態は git 側に持たせ、本ファイルには書かない**（書いた行はその瞬間から古くなる）。

| # | ルール |
|---|-------|
| 1 | **着手の宣言＝ worktree の作成**。本ファイルへ「着手中」と書き足さない。今の状態の正は `python scripts/worktree.py list`（ブランチ・main との差分・未コミットの有無が出る） |
| 2 | §1・§2 のタスクには **worktree 名を採番してある**（§2 は「wt 名 / 領域」列、§1 は「前提」の `worktree.py add`）。同名の worktree か `wt/` ブランチが既にあれば別セッションが着手中 → **別の行を取る** |
| 3 | 2本目を並行で始めるなら §2 の**領域が重ならない行**を取る（[worktree_guide](../process/worktree_guide.md) §2 ルール2）。重なる行しか残っていなければ着手せずユーザーへ確認する |
| 4 | **本ファイルの更新は main でのみ・統合の直後に1回**（worktree の中では触らない）。§1 を次のタスクへ書き換え、消化した §2 行を消す。統合せず中断する場合だけ、main へ戻って §1 に `wt/<名前>` と再開手順を書く |
| 5 | 鮮度確認は `git log` に加えて `worktree.py list` を見る。**該当作業のコミットが wt 側にあれば「完了・未統合」**（着手せず、統合してよいかユーザーへ確認）。コミット0件でも `dirty` の worktree は別セッションが作業中であり、放棄ではない |
| 6 | 後工程への申し送りは本ファイルへ足さず [carryover_notes.md](carryover_notes.md) へ書く（`merge=union` で自動統合されるので **worktree からでもよい**）。本ファイルは §1・§2 のポインタだけに保つ |

## 1. 次回（コピペ用）

```
/dev 移行 STEP 3-A-2 レビュー指摘の修正・セグメントB（ログと設定値）: ISSUE-704・705・706・710 を実装する
完了条件: ①ISSUE-704 `UserIdMDCPutFilter` を除去する（体系外の MDC キー `USER` への出力。`known_issues.md` #20 ⑤）②ISSUE-705 `LogKey.EMAIL` が本番コードで未使用の状態を解消する（認証失敗ログに識別子が無い）③ISSUE-706 refresh の失敗分岐へログを足し、他の認証失敗と体裁をそろえる。**再利用検知は `AuthServiceImpl#detectReuse` 1か所に寄せてある**ので `.reason(...)` の追加はそこだけで済む④ISSUE-710 確認トークンの有効期間を `AuthSettings` へ移し、あわせて**再設定トークン1時間・SMTP・`mail.from` も `tech_auth/mail.md` §16.2 の設定表どおりに足す**⑤`tech_logging.md`「失敗理由（reason）の値」と `tech_backend.md` §4.2 へ追記する（②③で足す reason と④で足す設定値）⑥`mvn test` が Green で C1 100% を維持
参照: 指摘の詳細は [2026-08-09_230636.md](../reviews/backend-review/2026-08-09_230636.md) の ISSUE-704・705・706・710（起点）、ログ仕様の正は [tech_logging.md](../tech/basic/tech_logging.md)「失敗理由（reason）の値」、設定値の正は [tech_backend.md](../tech/basic/tech_backend.md) §4.2 と [tech_auth/mail.md](../tech/detail/tech_auth/mail.md) §16.2
前提: セグメントA は統合済み（main `badc375`）で `mvn test` 176件 Green・C1 100%。**`LogReason` へ再利用検知の値がまだ無い**のは A が意図的に見送ったため（`tech_logging.md` への追記を伴うので本セグメントの担当）。触るのは `afkgame-web` のフィルタ・`afkgame-env` の `AuthSettings`/`LogReason`・`AuthServiceImpl` のログ行とテスト、および上記2ファイルのドキュメント。**メール長254・パスワード上限128 のテスト修正は含めない**（`AuthApiTest` は候補キュー1）。JDK 17.0.20（Temurin）・Maven 3.9.11 は新規シェルで実行確認済み。worktree は `python scripts/worktree.py add auth-3a2-fix-b`。`docs/backlog/open_specs.md` は**不在＝未確定ゼロ**
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-A-2 レビュー指摘の修正・セグメントC（テスト補強）**。ISSUE-707（統合テストへ register 成功・register 重複・login 成功の3本を追加）・708（`UserRepositoryTest` を新設し分岐 §13 #7 を移設）。あわせて**メール長254・パスワード上限128 へ `AuthApiTest` を追随**させる（`carryover_notes.md` §2 の①②。これで `check_branch_list.py --tests` の §11 #14 も消える） | セグメントA（統合済み `badc375`）・§1 のセグメントB（是正後の挙動を検証するため） | `auth-3a2-test`<br>backend/test | `integration-test` |
| 2 | **3-A-3 のテストリスト作成①（link-account / verify-email）**。§19（23件）・§21（16件）を Red へ展開する | 3-A-3 の詳細設計（完了。main `122ba2b`） | `auth-3a3-testlist-a`<br>backend | `test-list` |
| 3 | **3-A-3 のテストリスト作成②（password-reset / メール送信）**。§23（16件）・§25（19件）・§17（8件）を Red へ展開する。**§17 は #1・#2・#6 の振る舞いだけ実装済み**（`VerificationMailSenderImplTest`）なので、残り5件を足したうえで8行すべてにマーカーを行き渡らせる | 同上（キュー2とは対象APIが重ならないので並行可） | `auth-3a3-testlist-b`<br>backend | `test-list` |
| 4 | **3-B: tower の詳細設計**。`tech_tower.md`（新規）へ処理フローと分岐一覧を作る（`tech_tower.md` が無く分岐一覧も未作成のため 3-B は詳細設計から始める） | なし（3-A と領域が重ならないので並行可） | `tower-detail`<br>docs | `detail-design` |

- **セグメントA〜C が揃ったら `backend-review`（差分モード）で製造完了ゲートを通す**。指摘元のレビューは [2026-08-09_230636.md](../reviews/backend-review/2026-08-09_230636.md)
- **キューが空いたら戻す行**: 3-A-3 の製造（分岐82件のためセグメント2本を見込む。`auth-3a3-dev-a` / `-b` / backend / `dev`。前提はキュー2・3 のテストリスト）→ 3-B の残り（Phase 1: game / battle）とそれぞれの テストリスト作成 → 製造（順序の正は [carryover_notes.md](carryover_notes.md) §1）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ
