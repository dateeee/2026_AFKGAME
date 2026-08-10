# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-10 / main `883529c`（**3-A-3 製造②（password-reset / メール送信）**。テストリスト②の Red 43行分を Green にし、**テストは1件も変更していない**。`mvn verify` は**単体307件 + 結合64件 Green・C1 100%（188/188・未達0）**、`check_java_conventions.py` は**違反0 / WARN 20件 → 13件**）。**仮実装だったメール送信を解消**し、SMTP は Jakarta Mail（`org.eclipse.angus:angus-mail`）を直接使う（`spring-context-support` は親が管理する版が Spring 7 系で本体6系と食い違うため採らない）。**`VerificationMailSenderImpl#transmit` には分岐を書かない**（単体テストが override して通るため、`if` を置くと C1 が落ちる）。内訳は [changelog.md](../changelog.md) の 2026-08-10 ブロックが正。1つ前は 3-A-3 テストリスト作成②（Red 43行）、その前は `check_java_conventions.py` への判定12・13 追加（`scripts/tests` **403件 green**）と 3-A-3 製造①（link-account / verify-email）。

**STEP 2R は完了済みで backend の Phase 機能へ着手してよい**。以後の移行順序は **3-A-2（完了）→ 3-A-3（製造①②とも完了。残るは §1 のレビューゲート）→ 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**。順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)（索引 + `java_migration/` 3分冊）。**3-B は詳細設計まで完了済み**で、残るのはテストリスト作成 → 製造。

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
/backend-review 3-A-3 製造完了ゲート: 製造①②の差分をまとめて1回見る
完了条件: ①レポートを `docs/reviews/backend-review/` へ出力し、指摘の重要度（高/中/低）を判定する ②**修正はこのセッションで適用しない**（レビュー→修正は別セッション。`profile.md` §6 規律5）。高・中の件数と対象ファイルを報告して終える ③コミットする
参照: 手順は [review-procedure.md](../../.claude/references/review-procedure.md) §1〜§3、観点・重要度基準は [review-code.md](../../.claude/project/review-code.md)。**差分の基点は前回レポート [2026-08-10_013313.md](../reviews/backend-review/2026-08-10_013313.md) の `HEAD: ffce8ab` から自動で決まる**ので、手で範囲を指定しない
前提: main `883529c`。差分は `ffce8ab..883529c` で、**製造①（link-account / verify-email）・判定12・13 の追加・製造②（password-reset / メール送信）が1回の差分に入る**（3-A-2 と同じく製造をまとめて1回見る形）。読み取りのみなので **worktree は作らない**。前回レビューの「プロセスへの還元」のうち**未消化の①②**を今回の観点に含める（③は見送り済み・④は ISSUE-806 で前提の固定まで完了）。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B テストリスト作成①（Phase 1: game / battle）**。tick・戦闘サービスが先に要るため tower は後段 | §1 のゲート | `3b-testlist-battle`<br>backend | `test-list` |
| 2 | **3-B 製造①（Phase 1: game / battle）** | キュー1 | `3b-dev-battle`<br>backend | `dev` |
| 3 | **3-B テストリスト作成②（Phase 1: tower）**。分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件 | キュー2 | `3b-testlist-tower`<br>backend | `test-list` |
| 4 | **3-B 製造②（Phase 1: tower）** | キュー3 | `3b-dev-tower`<br>backend | `dev` |

- **3-A-2 の製造完了ゲートは閉じた**（指摘6件すべて解消。レポートの正は [2026-08-10_013313.md](../reviews/backend-review/2026-08-10_013313.md)）。「プロセスへの還元」4件のうち**③C1 では拾えない観点は見送り**、**④「DB例外の写像を実DBで通す」は ISSUE-806 で前提の固定までが入った**（`DuplicateKeyException` → 409 `AUTH_EMAIL_TAKEN` の写像そのものを API 経由で通すのは未実施）。残る①②は §1 のゲートで見る
- **3-A-3 製造① で `WebIntegrationTestSupport#updateFixture` を新設した**。`dataSource` が `defaultAutoCommit = false` のため、素の `jdbcTemplate.update` は**更新件数が返るのに値が残らない**（DBCP が接続返却時にロールバックする）。統合テストでフィクスチャを直接書き換えるときは必ずこれを通す
- **判定13（`--unused`）の現在値は WARN 13件**（`AuthSettings` 3・`GameSettings` 9・`LogKey.TOKEN`）。ゼロを強制せず**この件数からの増減だけ見る**
- **`mail.md` §17 への差し戻しは不要と確定した**。`VerificationMailSenderImplTest#test_トランザクション外なら即時に送る`（§17 に行を持たない `send` の実装分岐）は製造②で **C1 100%・未達0** を満たしたため、§17 へ9行目を足さずに据え置く
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ。移行の残りは **3-B の後に 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1）
