# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-10 / main `13e709f`（**3-A-2 レビュー指摘の修正セグメントC（テスト補強）を完了し、A〜C で指摘の修正は打ち止め**。ISSUE-707 `AuthApiIntegrationTest` へ register 成功・register 重複・login 成功の3本を足し、bcrypt ハッシュの永続化（`$2a$12$`・生パスワードを保存しない）／確認トークン `purpose=verify_email` が未使用で1件／409 でユーザーもプレイヤーも増えない／`last_login_at` が進む／**既存のリフレッシュトークンがログインで失効しない**を実DBの行で検証した／708 `UserRepositoryTest` を新設して `findByEmail` の該当あり・該当なし・ゲスト行（`email` が NULL）と `updateLastLoginAt` を実DBで確認し、分岐 §13 #7 のマーカーをそこへ移した（`AuthServiceImplTest` の同名テストは #6 と同一経路のため削除し、#6 の Javadoc へ受け皿を明記）／入力長は**仕様どおりに本番コードも直した**（`RegisterResource` を email 254・password 8〜128 へ）。**前提では「本番コードは触らない見込み」としていたが、`@Size(max = 255)`・password 上限なしのままでは ③④ のテストが通らないため踏み込んだ**。あわせて `.claude/project/integration-test.md` §3 へ再発防止の1行（移行 STEP で追加したエンドポイントは実DBの行を検証する L1 テストを最低1本持つ）を追記した。`mvn verify` 単体196件 + 結合58件 Green・C1 100%（128/128）で、**2回実行して結果は同一**。セグメントA は main `badc375`、B は `8ac9dcd`。直前までの成果は [changelog.md](../changelog.md) の 2026-08-10 ブロックが正。

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
/backend-review 移行 STEP 3-A-2（register / login / logout）: 製造完了ゲートの差分レビュー
完了条件: ①前回レビュー（`2026-08-09_230636.md`）以降の差分を対象に、`docs/reviews/backend-review/` へレポートを1本出す②セグメントA〜C の修正（ISSUE-701〜711）が指摘どおりに閉じているかを判定する③修正で新たに入ったコード（`AuthSettings`・`MailSettings`・`LogReason` の4値・`LayerLoggingInterceptor` 周辺・`RegisterResource` の入力長・`UserRepositoryTest`・`AuthApiIntegrationTest` の3本）に新規の指摘が無いかを見る④残す指摘には ISSUE 番号を採番し、重要度と検出可能工程を付ける
参照: 前回レビューは [2026-08-09_230636.md](../reviews/backend-review/2026-08-09_230636.md)（起点。ISSUE 一覧と対応表）、観点・重要度基準は [.claude/project/review-code.md](../../.claude/project/review-code.md)、仕様の正は [tech_auth/account.md](../tech/detail/tech_auth/account.md)
前提: セグメントC まで統合済み（main `13e709f`）。`mvn verify` は単体196件 + 結合58件 Green・C1 100%（128/128）で、**2回実行して結果は同一**（本ターンで実行確認済み）。`check_branch_list.py --tests`・`check_doc_size.py`・`check_docs.py` はいずれも違反0（同上）。**読み取りのみなので worktree は作らない**（[worktree_guide.md](../process/worktree_guide.md) §5.1 #1）。**レビュー→修正適用は別セッションに分ける**（`profile.md` §6 規律5）ので、指摘の修正は次回キューへ行として戻す。JDK 17.0.20（Temurin）・Maven 3.9.11・docker 29.6.2 は新規シェルで実行確認済み（レビュー自体はビルド不要）。`docs/backlog/open_specs.md` は**不在＝未確定ゼロ**
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-A-3 のテストリスト作成①（link-account / verify-email）**。§19（23件）・§21（16件）を Red へ展開する | 3-A-3 の詳細設計（完了。main `122ba2b`） | `auth-3a3-testlist-a`<br>backend | `test-list` |
| 2 | **3-A-3 のテストリスト作成②（password-reset / メール送信）**。§23（16件）・§25（19件）・§17（8件）を Red へ展開する。**§17 は #1・#2・#6 の振る舞いだけ実装済み**（`VerificationMailSenderImplTest`）なので、残り5件を足したうえで8行すべてにマーカーを行き渡らせる | 同上（キュー1とは対象APIが重ならないので並行可） | `auth-3a3-testlist-b`<br>backend | `test-list` |
| 3 | **3-B: tower の詳細設計**。`tech_tower.md`（新規）へ処理フローと分岐一覧を作る（`tech_tower.md` が無く分岐一覧も未作成のため 3-B は詳細設計から始める） | なし（3-A と領域が重ならないので並行可） | `tower-detail`<br>docs | `detail-design` |

- **3-A-2 の指摘修正（セグメントA〜C）は完了済み**。製造完了ゲートの `backend-review` が §1 の「次回」。その指摘の修正適用は、レビュー完了時にキューへ行として戻す
- **キューが空いたら戻す行**: 3-A-3 の製造（分岐82件のためセグメント2本を見込む。`auth-3a3-dev-a` / `-b` / backend / `dev`。前提はキュー2・3 のテストリスト）→ 3-B の残り（Phase 1: game / battle）とそれぞれの テストリスト作成 → 製造（順序の正は [carryover_notes.md](carryover_notes.md) §1）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ
