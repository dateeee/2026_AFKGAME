# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-10 / main `b1b95e9`（**3-A-3 テストリスト作成②**。`password_reset.md` §23（16件）・§25（19件）と `mail.md` §17（8件）の**全43行を Red のテストへ展開**した。新規テストメソッド40件、`check_branch_list.py --tests` 違反0）。**いま `mvn test` はテストコンパイルで停止する＝それが正常な Red の状態**で、ユニークな javac エラー45件のうち42件が未実装シンボル、残り3件も申し送りどおりの表層変更で消える（想定外の型エラー0件）。**製造②が実装すべき表層は各テストのクラス Javadoc「製造工程への申し送り」が正**（`AuthServiceImplTest`・`AuthApiTest`・`VerificationMailSenderImplTest`）で、仕様書に無い判断もそこにある。内訳は [changelog.md](../changelog.md) の 2026-08-10 ブロックが正。1つ前は `check_java_conventions.py` への判定12・13 追加（116ファイル・**違反0 / WARN 20件**、`scripts/tests` **403件 green**）、その前は 3-A-3 製造①（link-account / verify-email）で `mvn verify` は**単体259件 + 結合62件 Green・C1 100%（170/170・未達0）**。

**STEP 2R は完了済みで backend の Phase 機能へ着手してよい**。以後の移行順序は **3-A-2（完了）→ 3-A-3 → 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**。順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)（索引 + `java_migration/` 3分冊）。**3-A-3・3-B とも詳細設計は完了済み**で、残るのはテストリスト作成 → 製造。

**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（Python 削除を STEP 3〜5 より先に実施したため）。E2E はハーネスと `GET /health` まで疎通済みだが、テスト本体は STEP 5 完了まで赤が正常。

**複数セッションにまたがる申し送りの正は [carryover_notes.md](carryover_notes.md)**（§1 Java 移行 / §2 仕様・マスターデータ / §3 環境・ツール）。着手前にそちらも見る。**恒久的な知見は同ファイルに残さず規約・コマンド表の正へ移す**方針なので、Java 実装の流儀は [coding_standards_backend.md](../process/coding_standards_backend.md) の分冊、環境・コマンド（`docker exec` を使う理由は §1、Tomcat の所在・`SPRING_PROFILES_ACTIVE` 必須は分冊 [commands/backend.md](../../.claude/project/commands/backend.md) §5・§6）は [commands.md](../../.claude/project/commands.md) が正。

**常設スクリプトの回帰テストは全件 green**（`python -m pytest scripts/tests -q` = 403件）。`scripts/**` を変更するタスクは、このテストを退行検出の網として使う。

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
/dev 3-A-3 製造②（password-reset / メール送信）: テストリスト②の Red 43行分を Green にする
完了条件: ①`mvn verify` が**単体・結合とも Green で C1 100%（未達0）** ②`python scripts/check_java_conventions.py` が**違反0**で、WARN は 20件から**減る**（製造②で `MailSettings` 6件と `passwordResetTokenExpire` に読み手ができるはずなので、**減っていなければ設定を読み落としている**）③`check_branch_list.py --tests`・`check_error_codes.py` が違反0 ④コミットする
参照: 手順の正は [password_reset.md](../tech/detail/tech_auth/password_reset.md) §22・§24 と [mail.md](../tech/detail/tech_auth/mail.md) §16。**実装すべき表層（シグネチャ・Resource・設定キー・規約例外の置き場所）は Red のテストのクラス Javadoc「製造工程への申し送り」が正**で、仕様書に無い判断もそこに書いてある
前提: main `b1b95e9`（**テストコンパイルが通らないのが正常＝ Red**）。実行環境は前セッションで実行確認済み — Maven 3.9.11 / JDK 17.0.20（Temurin）/ docker コンテナ `afkgame-postgres`。**メール送信の依存（Jakarta Mail・`spring-context-support` 等）はどの pom にも無い**ので追加から入る。テストは実送信を `transmit(to, subject, body)`（パッケージ内可視）で差し替える前提なので、**ライブラリ呼び出しはこのメソッドの中だけに閉じる**。`RefreshTokenRepository#updateRevokedByUserId` は戻り値を `void` → `int` へ変える（既存の呼び出し元・`verify` はそのまま通る）。`UserRepository#updatePasswordHash` は引数 `passwordHash` が判定12 の固定表に無いため `// 規約例外:` が要る。編集を伴うので `python scripts/worktree.py add auth-3a3-dev-b` で worktree を作る（[worktree_guide.md](../process/worktree_guide.md) §5.2）。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-A-3 の製造完了ゲート**（`backend-review` 差分）。3-A-2 と同じく**製造①②をまとめて1回**見る（①だけで先に回すと②で同じ差分を二度読む） | §1 の製造② | なし（読み取りのみ）<br>backend | `backend-review` |
| 2 | **3-B テストリスト作成①（Phase 1: game / battle）**。tick・戦闘サービスが先に要るため tower は後段 | キュー1のゲート | `3b-testlist-battle`<br>backend | `test-list` |

- **3-A-2 の製造完了ゲートは閉じた**（指摘6件すべて解消。レポートの正は [2026-08-10_013313.md](../reviews/backend-review/2026-08-10_013313.md)）。「プロセスへの還元」4件のうち**③C1 では拾えない観点は見送り**、**④「DB例外の写像を実DBで通す」は ISSUE-806 で前提の固定までが入った**（`DuplicateKeyException` → 409 `AUTH_EMAIL_TAKEN` の写像そのものを API 経由で通すのは未実施）。残る①②はキュー1で見る
- **3-A-3 製造① で `WebIntegrationTestSupport#updateFixture` を新設した**。`dataSource` が `defaultAutoCommit = false` のため、素の `jdbcTemplate.update` は**更新件数が返るのに値が残らない**（DBCP が接続返却時にロールバックする）。統合テストでフィクスチャを直接書き換えるときは必ずこれを通す
- **判定13（`--unused`）の現在値は WARN 20件**（`AuthSettings` 4・`GameSettings` 9・`MailSettings` 6・`LogKey.TOKEN`）。ゼロを強制せず**この件数からの増減だけ見る**
- **`mail.md` §17 への差し戻し候補が1件ある**（未反映）。`VerificationMailSenderImplTest#test_トランザクション外なら即時に送る` は `send` の実装分岐（`isSynchronizationActive()` の偽側）だが §17 に対応する行が無い。呼び出し元のコミット / ロールバックのどちらでもなく既存テストでもあるため今回は行を足さず据え置いた。**C1 の分母には入る**ので、製造②で未達になるようなら `detail-design` で §17 へ9行目として足す
- **キューが空いたら戻す行**: 3-B の製造（game / battle）→ tower のテストリスト作成 → 製造（tower の分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件。順序の正は [carryover_notes.md](carryover_notes.md) §1）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ
