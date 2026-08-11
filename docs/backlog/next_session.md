# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-11 / main `11c5ade`（**Phase 5 設計整合ゲート**＝ `diagrams-review` を実行し [2026-08-11_133822.md](../reviews/diagrams-review/2026-08-11_133822.md) に9件（高2 / 中6 / 低1）。**うち5件は doc-review の ISSUE-1301・1303・1308 を仕様書にだけ適用し、図へ波及させなかったもの**＝ 701・702（記録時点）・703・705（リタイア即時）・706（転生の導線）。709 は `check_schema_triple.py` が赤で返した1行（`tech_db/auth.md` の「実装予定:」）。`check_docs.py`・`check_doc_size.py` は違反0。**Java 移行とは独立の docs タスク**なので §1 の 3-A-3 反映②はそのまま残っている）。1つ前は 2026-08-11 / main `45261b8`（**Phase 5 仕様確定ゲートの反映**＝ ISSUE-1301〜1310 を適用。1311 は `doc-size` で消化済み）。その前は 2026-08-10 / main `185a87b`（**3-A-3 レビュー指摘の反映①**＝ ISSUE-901・902・907・908 の4件。`mvn verify` **単体306件 + 結合85件 Green・C1 100%（194/194・未達0）**、`check_java_conventions.py` **違反0・WARN 13件**）。**残るは §1 の5件でゲートが閉じる**。レビューレポート [2026-08-10_155729.md](../reviews/backend-review/2026-08-10_155729.md) が正で、**各指摘に修正案と既存テストへの影響まで書いてある**。**`VerificationMailSenderImpl#transmit` には分岐を書かない**（単体テストが override して通るため、`if` を置くと C1 が落ちる）。内訳は [changelog.md](../changelog.md) の 2026-08-10 ブロックが正。

**レビュー指摘の「既存テストへの影響」は鵜呑みにしない**（①で実証）。ISSUE-907 は「テストは戻り値をスタブしていない」としていたが、実際は `AuthServiceImplTest` のパラメータ化テスト2件が `thenReturn(0/1/2)` で使っており、そのまま `void` 化すると分岐一覧6行（`password_reset.md §23 #10〜#12`・`§25 #15〜#17`）が代替なしで落ちる。**着手前に対象メソッドを `grep` して読み手を数える**。①では「`void` 化＋リポジトリ統合テスト新設」をユーザー判断で採り、`RefreshTokenRepositoryTest`・`EmailVerificationTokenRepositoryTest` を新設して件数の振る舞いを実DBへ移した。

**`mvn verify` に Docker は要らない**（結合テストは domain・web とも `EmbeddedPostgresSupport` の埋め込み PostgreSQL を使う）。`report_java_tests.py --run` は既定で `-DskipITs` なので、**結合テストまで見るなら `--run --it`**。

**STEP 2R は完了済みで backend の Phase 機能へ着手してよい**。以後の移行順序は **3-A-2（完了）→ 3-A-3（製造①②・製造完了ゲート・レビュー指摘の反映①まで完了。残るは §1 の反映②）→ 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**。順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)（索引 + `java_migration/` 3分冊）。**3-B は詳細設計まで完了済み**で、残るのはテストリスト作成 → 製造。

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
/dev 3-A-3 レビュー指摘の反映②（ログのマスク・SMTP設定・片付け）: ISSUE-903・904・905・906・909 の5件を適用する
完了条件: ①5件を修正案どおり直す ②`python scripts/report_java_tests.py --run --it` が**単体・結合とも green で C1 100%**（**分岐は増えない**ので新規テストは原則不要。903・909 は規約チェッカー側の語表を触るため `python -m pytest scripts/tests -q` 403件も維持する） ③`python scripts/check_java_conventions.py` が違反0・WARN 13件のまま（**903 で語表を広げると WARN が増え得る**。増えたら理由を添えて件数を更新する） ④main へ統合してコミットする
参照: [2026-08-10_155729.md](../reviews/backend-review/2026-08-10_155729.md) の ISSUE-903・904・905・906・909（**修正案・該当行・既存テストへの影響まで書いてある**ので、まずこの5節だけ読む）。マスクの固定表は [logging/application.md](../process/coding_standards_backend/logging/application.md) §3.1、SMTP の設定項目は [mail.md](../tech/detail/tech_auth/mail.md) §16.2 が正
前提: main `185a87b`。触るのは `afkgame-env`（`MailSettings`・ログ周り）・`afkgame-web`（`AuthApi` の `googleAuthCode`）・`afkgame-domain`（`VerificationMailSenderImpl`・`pom.xml` の `httpclient5`）・`scripts/check_java_conventions.py`（+ `scripts/tests`）・規約ドキュメント。**worktree `3a3-fix-mask` を作って作業する**（`python scripts/worktree.py add 3a3-fix-mask`）。**Docker は不要**（結合テストは埋め込み PostgreSQL）。新規シェルで `mvn -v` 3.9.11・JDK 17.0.20・`docker --version` 29.6.2 は実行確認済み。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **Phase 5 設計図の指摘反映**（ISSUE-701〜709）。**701・702 は同じ記録時点を扱うので必ず同じ修正パスで直す**（片方だけだと図どうしが食い違う）。706・707 は `screen_transition/endgame.md` 67行目を共有するのでマージする | なし（**Java 移行と独立**・並行可） | `p5-diagrams-fix`<br>docs | `fix-specs` |
| 2 | **3-B テストリスト作成①（Phase 1: game / battle）**。tick・戦闘サービスが先に要るため tower は後段 | §1（ゲートを閉じてから） | `3b-testlist-battle`<br>backend | `test-list` |
| 3 | **3-B 製造①（Phase 1: game / battle）** | キュー2 | `3b-dev-battle`<br>backend | `dev` |
| 4 | **3-B テストリスト作成②（Phase 1: tower）**。分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件 | キュー3 | `3b-testlist-tower`<br>backend | `test-list` |
| 5 | **3-B 製造②（Phase 1: tower）** | キュー4 | `3b-dev-tower`<br>backend | `dev` |

- **仕様書の指摘を直したら、それを検証対象に持つ図まで同じパスで直す**（今回の学び。仕様書だけ直した結果、ISSUE-1301・1303・1308 が図の指摘5件に化けた）。対応表は [basic-design.md](../../.claude/project/basic-design.md) §1。還元案（`fix-specs` への組み込み・Mermaid チェッカーの常設化）はレビュー末尾が正
- **3-A-3 の製造完了ゲートは開いたまま**（指摘9件のうち4件を反映済み。残る5件を §1 で閉じる）。前回ゲート（3-A-2）の「プロセスへの還元」①②は**判定12・13 として実装済みで効いている**（違反0・WARN 13件で横ばい）が、**網の外に同型の穴が3件**出た（ISSUE-903 語表の穴 / 905 pom の依存 / 907 Repository の戻り値）。次の還元候補は [2026-08-10_155729.md](../reviews/backend-review/2026-08-10_155729.md)「プロセスへの還元」1〜3 が正
- **更新系 SQL の条件は実DBテストでしか検証されない**（①の学び）。`AND used = FALSE` のような条件を足したら、サービスの単体テスト（Repository をモックする）では素通りするため、対応するリポジトリ統合テストを同じコミットで足す。`RepositoryTestSupport` を継承すれば埋め込み PostgreSQL で回る
- **分岐一覧に行を足すときは末尾へ追加する**（①の判断）。途中へ挿すと後続番号が動き、テストの `分岐:` マーカーを全部書き替えることになる。番号は流れ順ではなく安定した識別子として扱う
- **3-A-3 製造① で `WebIntegrationTestSupport#updateFixture` を新設した**。`dataSource` が `defaultAutoCommit = false` のため、素の `jdbcTemplate.update` は**更新件数が返るのに値が残らない**（DBCP が接続返却時にロールバックする）。統合テストでフィクスチャを直接書き換えるときは必ずこれを通す
- **判定13（`--unused`）の現在値は WARN 13件**（`AuthSettings` 3・`GameSettings` 9・`LogKey.TOKEN`）。ゼロを強制せず**この件数からの増減だけ見る**
- **`mail.md` §17 への差し戻しは不要と確定した**。`VerificationMailSenderImplTest#test_トランザクション外なら即時に送る`（§17 に行を持たない `send` の実装分岐）は製造②で **C1 100%・未達0** を満たしたため、§17 へ9行目を足さずに据え置く
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻すのは**テストリスト作成1件のみ**（詳細設計は 2026-08-11 の限界突破＝[tech_limitbreak.md](../tech/detail/tech_limitbreak.md) で完了。内訳は [carryover_notes.md](carryover_notes.md) §2）。移行の残りは **3-B の後に 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1）
