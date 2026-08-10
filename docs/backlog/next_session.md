# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-10 / main `9fbe365`（**3-A-2 の製造完了ゲートを閉じた**。セグメントE = ISSUE-802・806・803 を消化し、`backend-review` の指摘6件がすべて解消）。パスワード長 8/128 の正を [account.md](../tech/detail/tech_auth/account.md) §9「入力長」と確定したうえで、`RegisterResource` の `@Size` と `AuthSettings`（`afkgame.properties`）が一致することを統合テストで固定し、`isEmailConstraintViolation` が立つ「実DB例外に `uq_users_email` が含まれる」前提を `UserRepositoryTest` で固定した（内訳は [changelog.md](../changelog.md) の 2026-08-10 ブロックが正）。`mvn verify` は **単体211件 + 結合59件 Green・C1 100%（144/144・未達0）**、`check_java_conventions.py` は114ファイル違反なし。以下は1つ前の状態（**3-B: tower の詳細設計を完了**）。`/api/tower/*` 5API と tick 内の階進行を [tech_tower.md](../tech/detail/tech_tower.md)（索引）+ `tech_tower/` 4分冊へ確定し、分岐一覧55件を作成した。

**STEP 2R は完了済みで backend の Phase 機能へ着手してよい**。以後の移行順序は **3-A-2（完了）→ 3-A-3 → 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**。順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)（索引 + `java_migration/` 3分冊）。**3-A-3・3-B とも詳細設計は完了済み**で、残るのはテストリスト作成 → 製造。

**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（Python 削除を STEP 3〜5 より先に実施したため）。E2E はハーネスと `GET /health` まで疎通済みだが、テスト本体は STEP 5 完了まで赤が正常。

**複数セッションにまたがる申し送りの正は [carryover_notes.md](carryover_notes.md)**（§1 Java 移行 / §2 仕様・マスターデータ / §3 環境・ツール）。着手前にそちらも見る。**恒久的な知見は同ファイルに残さず規約・コマンド表の正へ移す**方針なので、Java 実装の流儀は [coding_standards_backend.md](../process/coding_standards_backend.md) の分冊、環境・コマンド（`docker exec` を使う理由は §1、Tomcat の所在・`SPRING_PROFILES_ACTIVE` 必須は分冊 [commands/backend.md](../../.claude/project/commands/backend.md) §5・§6）は [commands.md](../../.claude/project/commands.md) が正。

**`known_issues.md` #22 は未解消**（`python -m pytest scripts/tests -q` が30件 failed）。本セッションで原因まで特定した — `check_branch_list.py:156` の `for fname, sec in sections` に `parse_tables()` の戻り値が合わず `ValueError: not enough values to unpack`。**マーカー照合のロジックではなくテストと関数シグネチャの不整合**であり、`--tests` 自体はリポジトリ本体に対して正常に動く（分岐一覧41件・違反なし）ため、テストリスト工程のゲートにはそのまま使ってよい。

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
/test-list 3-A-3 テストリスト作成①（link-account / verify-email）: §19（23件）・§21（16件）を失敗するテストへ展開する
完了条件: ①link.md §19 の23件と verify.md §21 の16件を、各テストの Javadoc へ「分岐: tech_auth/link.md §19 #<行番号>」形式のマーカー付きで展開する②`python scripts/check_branch_list.py --tests` が違反0（全行にテストが対応する）③**実装が無いので Red で止まるのが正**。赤の理由が「未実装」であることを確認し、期待値を実装側へ寄せない④コミットする
参照: 分岐一覧の正は [link.md](../tech/detail/tech_auth/link.md) §19（行27〜・23件）・[verify.md](../tech/detail/tech_auth/verify.md) §21（行24〜・16件）が起点、テストの書き方の正は [test.md](../process/coding_standards_backend/test.md) §1「配置と分離」・§2「記述規約」
前提: main `9ab1a1a`（3-A-2 のゲートは `9fbe365` で閉じた。以後の2件は本引き継ぎの更新 `006c639` と、無関係な全体アーキテクチャ図の是正 `9ab1a1a` で、いずれも auth 領域に触れていない）。3-A-3 の詳細設計は main `122ba2b` で完了済みで、以後この領域へ変更は入っていない。編集を伴うので `python scripts/worktree.py add auth-3a3-testlist-a` で worktree を作る（[worktree_guide.md](../process/worktree_guide.md) §5.2）。JDK 17.0.20（Temurin）・Maven 3.9.11・docker 29.6.2 は新規シェルで実行確認済み。**Docker Desktop は OS 起動時に自動起動しない** — `docker info` が npipe エラーを返したら `$LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe` を起動する（`afkgame-postgres` は追随して healthy になる。起動待ちは約5秒）。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-A-3 のテストリスト作成②（password-reset / メール送信）**。§23（16件）・§25（19件）・§17（8件）を Red へ展開する。**§17 は #1・#2・#6 の振る舞いだけ実装済み**（`VerificationMailSenderImplTest`）なので、残り5件を足したうえで8行すべてにマーカーを行き渡らせる | 3-A-3 の詳細設計（完了。main `122ba2b`）。§1 とは対象APIが重ならないので並行可 | `auth-3a3-testlist-b`<br>backend | `test-list` |
| 2 | **`backend-review` の「プロセスへの還元」①②**。`check_java_conventions.py` へ①機密名の突合と②未参照の設定・enum 値の検出を足す（②は ISSUE-802・803 と同型の再発防止で、`AuthSettings` の `guestExpire`・`passwordResetTokenExpire` が現に未参照） | なし（`scripts/` 単独で完結。§1・キュー1と領域が重ならないので並行可） | `conv-checker-feedback`<br>scripts | `dev` |
| 3 | **3-A-3 の製造①（link-account / verify-email）** | §1 のテストリスト① | `auth-3a3-dev-a`<br>backend | `dev` |
| 4 | **3-A-3 の製造②（password-reset / メール送信）** | キュー1のテストリスト② | `auth-3a3-dev-b`<br>backend | `dev` |

- **3-A-2 の製造完了ゲートは閉じた**（指摘6件すべて解消。レポートの正は [2026-08-10_013313.md](../reviews/backend-review/2026-08-10_013313.md)）。「プロセスへの還元」4件のうち**③C1 では拾えない観点は見送り**、**④「DB例外の写像を実DBで通す」は ISSUE-806 で前提の固定までが入った**（`DuplicateKeyException` → 409 `AUTH_EMAIL_TAKEN` の写像そのものを API 経由で通すのは未実施）。残る①②がキュー2
- **`known_issues.md` #22 は診断済み・未修正**（原因は前文）。`check_branch_list.py` へ手を入れるタスクの前に片付ける。回帰テスト30件が赤のまま本体を変更すると、退行を検出できない
- **キューが空いたら戻す行**: 3-B のテストリスト作成 → 製造（Phase 1: game / battle → tower の順。tick・戦闘サービスが先に要るため tower のテストは後段。tower の分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件。順序の正は [carryover_notes.md](carryover_notes.md) §1）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ
