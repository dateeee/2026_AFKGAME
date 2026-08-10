# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-10 / main `d963b24`（**`known_issues.md` #22 が解消し、`scripts/tests` は 382件 green**）。原因は `842023e` が `parse_tables()` の戻り値を `(sections, dup_errors)` へ変えたのにテストが追従していなかったことで、**正は本体側**と判断してテスト30件を現行シグネチャへ寄せた（期待値は不変。衝突検出のテスト2件を新設）。`check_branch_list.py` は無変更で、引数なし・`--tests` とも**分岐一覧41件・違反0**。1つ前は 3-A-3 製造①（link-account / verify-email）で、テストリスト①の Red 43件を Green にし、`mvn verify` は**単体259件 + 結合62件 Green・C1 100%（170/170・未達0）**、`check_java_conventions.py`・`check_error_codes.py`・`check_branch_list.py --tests`・`check_docs.py`・`check_doc_size.py` いずれも違反0。**製造で決めた2点**（verify-email の 422 は Resource へ束ねて `@Valid @ModelAttribute` で受ける／Google設定の判定は `StringUtils.hasText` の1条件にする）と**テスト側の修正2件**（期待値は不変。§19 #5 の入力追加／フィクスチャを `updateFixture` 経由へ）の内訳は [changelog.md](../changelog.md) の 2026-08-10 ブロックが正。その前は 3-A-3 テストリスト作成①と、3-A-2 の製造完了ゲート。

**STEP 2R は完了済みで backend の Phase 機能へ着手してよい**。以後の移行順序は **3-A-2（完了）→ 3-A-3 → 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**。順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)（索引 + `java_migration/` 3分冊）。**3-A-3・3-B とも詳細設計は完了済み**で、残るのはテストリスト作成 → 製造。

**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（Python 削除を STEP 3〜5 より先に実施したため）。E2E はハーネスと `GET /health` まで疎通済みだが、テスト本体は STEP 5 完了まで赤が正常。

**複数セッションにまたがる申し送りの正は [carryover_notes.md](carryover_notes.md)**（§1 Java 移行 / §2 仕様・マスターデータ / §3 環境・ツール）。着手前にそちらも見る。**恒久的な知見は同ファイルに残さず規約・コマンド表の正へ移す**方針なので、Java 実装の流儀は [coding_standards_backend.md](../process/coding_standards_backend.md) の分冊、環境・コマンド（`docker exec` を使う理由は §1、Tomcat の所在・`SPRING_PROFILES_ACTIVE` 必須は分冊 [commands/backend.md](../../.claude/project/commands/backend.md) §5・§6）は [commands.md](../../.claude/project/commands.md) が正。

**常設スクリプトの回帰テストは全件 green**（`python -m pytest scripts/tests -q` = 382件）。`scripts/**` を変更するタスクは、このテストを退行検出の網として使う。

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
/dev scripts: check_java_conventions.py へ①機密名の突合 ②未参照の設定・enum 値の検出 を足す
完了条件: ①2判定を追加して `python scripts/check_java_conventions.py` が完走する（116ファイル）②判定①は違反0（現行コードは規約どおり）、判定②は `AuthSettings#guestExpire`・`#passwordResetTokenExpire`・`MailSettings` の各アクセサを WARN として実際に検出する。**ゼロ強制はしない**（先行投入を許容し件数の増減だけ見る運用）③判定ごとに [test_check_java_conventions.py](../../scripts/tests/test_check_java_conventions.py) へ緑パス + 変異の対を足し `python -m pytest scripts/tests -q` が全件 green④コミットする
参照: 判定仕様は [2026-08-10_013313.md](../reviews/backend-review/2026-08-10_013313.md) §プロセスへの還元 1・2（対象範囲と違反条件がコードブロックで確定済み）。①の固定表は [application.md](../diagrams/system_architecture/application.md) §3.1 規約1。同型の既存判定（MDCキーは `LogKey` に定義されたものだけ）が `scripts/check_java_conventions.py` にあるので枠を踏襲する
前提: main `d963b24`（#22 解消済みで `scripts/tests` は 382件 green ＝ 本タスクの退行を検出できる。backend のプロダクトコードは触らない）。`python scripts/check_java_conventions.py` は新規シェルで実行確認済み（`116 files checked: 違反なし`）。②の検出対象が**現に未参照**であることも確認済み（`guestExpire`・`passwordResetTokenExpire` は `AuthSettings` の宣言と `AfkgameSettingsConfig` の生成しか出てこず読み手が無い）。**キュー1（テストリスト②）より先に置く理由**は、`password-reset` の製造で `rawResetToken` 系の名前が増えるため①は製造前に入れるほど効くこと（レビュー §還元1 が明記）。編集を伴うので `python scripts/worktree.py add conv-checker-feedback` で worktree を作る（[worktree_guide.md](../process/worktree_guide.md) §5.2）。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-A-3 のテストリスト作成②（password-reset / メール送信）**。§23（16件）・§25（19件）・§17（8件）を Red へ展開する。**§17 は #1・#2・#6 の振る舞いだけ実装済み**（`VerificationMailSenderImplTest`）なので、残り5件を足したうえで8行すべてにマーカーを行き渡らせる | なし（3-A-3 の詳細設計は完了。**製造①が main へ入ったので「統合を待つ」制約は解けた**） | `auth-3a3-testlist-b`<br>backend | `test-list` |
| 2 | **3-A-3 の製造②（password-reset / メール送信）** | キュー1のテストリスト② | `auth-3a3-dev-b`<br>backend | `dev` |
| 3 | **3-A-3 の製造完了ゲート**（`backend-review` 差分）。3-A-2 と同じく**製造①②をまとめて1回**見る（①だけで先に回すと②で同じ差分を二度読む） | キュー2の製造② | なし（読み取りのみ）<br>backend | `backend-review` |

- **3-A-2 の製造完了ゲートは閉じた**（指摘6件すべて解消。レポートの正は [2026-08-10_013313.md](../reviews/backend-review/2026-08-10_013313.md)）。「プロセスへの還元」4件のうち**③C1 では拾えない観点は見送り**、**④「DB例外の写像を実DBで通す」は ISSUE-806 で前提の固定までが入った**（`DuplicateKeyException` → 409 `AUTH_EMAIL_TAKEN` の写像そのものを API 経由で通すのは未実施）。残る①②が §1
- **3-A-3 製造① で `WebIntegrationTestSupport#updateFixture` を新設した**。`dataSource` が `defaultAutoCommit = false` のため、素の `jdbcTemplate.update` は**更新件数が返るのに値が残らない**（DBCP が接続返却時にロールバックする）。統合テストでフィクスチャを直接書き換えるときは必ずこれを通す
- **キューが空いたら戻す行**: 3-B のテストリスト作成 → 製造（Phase 1: game / battle → tower の順。tick・戦闘サービスが先に要るため tower のテストは後段。tower の分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件。順序の正は [carryover_notes.md](carryover_notes.md) §1）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ
