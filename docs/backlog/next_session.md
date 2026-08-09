# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-10 / main `8ac9dcd`（**3-A-2 レビュー指摘の修正セグメントB（ログと設定値）を完了した**。ISSUE-704 `userIdMDCPutFilter` を除去（`known_issues.md` #20 ⑤を解消済みへ更新）／705 識別子の無かった認証失敗ログ3か所へ `LogKey.EMAIL` でマスク済みメールを付与し、`emailTaken()` の誤った Javadoc を訂正／706 `refresh`・`logout` の無言分岐へ WARN を足し、`LogReason` へ `refresh_not_found`・`refresh_expired`・`refresh_reused`・`refresh_owner_mismatch` の4値を追加（`tech_logging.md` の reason 表へ反映。**4つ目はレビューの列挙に無く、持ち主不一致に当たる値が既存に無いため足した**）／710 ハードコードだった確認トークン24時間を `AuthSettings` へ移し、再設定トークン1時間と `mail.md` §16.2 のメール設定を `MailSettings`（新規）+ `afkgame.properties` へ用意した。**SMTP の利用自体は STEP 3-A-3 の担当で、本セグメントは設定の受け皿まで**（`VerificationMailSenderImpl` はスタブのまま）。`mvn verify` 単体195件 + 結合46件 Green・C1 100%（128/128）。**セグメントA（main `badc375`）・B が済み、残る C（下記）で 3-A-2 の指摘は打ち止め**。直前までの成果は [changelog.md](../changelog.md) の 2026-08-10 ブロックが正。

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
/integration-test 移行 STEP 3-A-2 レビュー指摘の修正・セグメントC（テスト補強）: ISSUE-707・708 と入力長テストの追随
完了条件: ①ISSUE-707 `AuthApiIntegrationTest` へ register 成功・register 重複・login 成功の3本を足す（実DBで未検証の5点＝`password_hash` が bcrypt で永続化される／`email_verification_tokens` に `purpose=verify_email` の行が1件残る／`uq_users_email` 違反が409になりユーザー・プレイヤー行が残らない／ログイン成功で `last_login_at` が更新される／**既存のリフレッシュトークンがログインで失効しない**）②ISSUE-708 `UserRepositoryTest` を新設し（`RepositoryTestSupport` を継承）、`findByEmail` の該当あり／該当なし／ゲスト行（`email` が NULL）と `updateLastLoginAt` を実DBで確認する。分岐 §13 #7 のマーカーをそこへ移す③`AuthApiTest` をメール長 **254** へ追随させる（現在は 255 を正として書いてある）④パスワード上限128の分岐 §11 #14（129文字以上）のテストを足し、`python scripts/check_branch_list.py --tests` を違反0にする⑤`mvn verify` が Green で C1 100% を維持
参照: 指摘の詳細は [2026-08-09_230636.md](../reviews/backend-review/2026-08-09_230636.md) の ISSUE-707・708（起点）、分岐一覧の正は [tech_auth/account.md](../tech/detail/tech_auth/account.md) §11・§13、テスト基盤は `WebIntegrationTestSupport`（結合）と `RepositoryTestSupport`（リポジトリ）
前提: セグメントB は統合済み（main `8ac9dcd`）で `mvn verify` 単体195件 + 結合46件 Green・C1 100%（128/128）。`check_branch_list.py --tests` は**現在 §11 #14 の1件だけが違反**（本ターンで実行確認済み。④がそれを消す）。入力長を仕様側だけ確定して実装が未追随である経緯は [carryover_notes.md](carryover_notes.md) §2 の①②。触るのは `afkgame-web` の `AuthApiTest`・`AuthApiIntegrationTest` と `afkgame-domain` の `UserRepositoryTest`（新規）で、**本番コードは触らない見込み**。JDK 17.0.20（Temurin）・Maven 3.9.11・docker 29.6.2 は新規シェルで実行確認済み（**結合テストは埋め込み Postgres を使うので docker が要る**）。worktree は `python scripts/worktree.py add auth-3a2-test`。`docs/backlog/open_specs.md` は**不在＝未確定ゼロ**
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-A-3 のテストリスト作成①（link-account / verify-email）**。§19（23件）・§21（16件）を Red へ展開する | 3-A-3 の詳細設計（完了。main `122ba2b`） | `auth-3a3-testlist-a`<br>backend | `test-list` |
| 2 | **3-A-3 のテストリスト作成②（password-reset / メール送信）**。§23（16件）・§25（19件）・§17（8件）を Red へ展開する。**§17 は #1・#2・#6 の振る舞いだけ実装済み**（`VerificationMailSenderImplTest`）なので、残り5件を足したうえで8行すべてにマーカーを行き渡らせる | 同上（キュー1とは対象APIが重ならないので並行可） | `auth-3a3-testlist-b`<br>backend | `test-list` |
| 3 | **3-B: tower の詳細設計**。`tech_tower.md`（新規）へ処理フローと分岐一覧を作る（`tech_tower.md` が無く分岐一覧も未作成のため 3-B は詳細設計から始める） | なし（3-A と領域が重ならないので並行可） | `tower-detail`<br>docs | `detail-design` |

- **セグメントA〜C が揃ったら `backend-review`（差分モード）で製造完了ゲートを通す**。指摘元のレビューは [2026-08-09_230636.md](../reviews/backend-review/2026-08-09_230636.md)
- **キューが空いたら戻す行**: 3-A-3 の製造（分岐82件のためセグメント2本を見込む。`auth-3a3-dev-a` / `-b` / backend / `dev`。前提はキュー2・3 のテストリスト）→ 3-B の残り（Phase 1: game / battle）とそれぞれの テストリスト作成 → 製造（順序の正は [carryover_notes.md](carryover_notes.md) §1）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ
