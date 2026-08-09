# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / main `122ba2b`（**移行 STEP 3-A-3 の詳細設計を確定した**。対象APIが4本で `account.md` の残量に収まらないため操作別の子ファイルへ分割し、節番号を §16〜§25 として通した — [mail.md](../tech/detail/tech_auth/mail.md)（送信規約）・[link.md](../tech/detail/tech_auth/link.md)・[verify.md](../tech/detail/tech_auth/verify.md)・[password_reset.md](../tech/detail/tech_auth/password_reset.md)。分岐一覧は新規5本・計84件。**ユーザー判断で3点を確定** — ①メール正規化はアプリ層で小文字化（案A。`known_issues.md` #21 を削除）②メール長は **254**（255 との食い違いを解消）③**確認メールの再送APIは Phase 2 では設けない**。あわせてパスワード上限128の分岐を §11 へ追加し、`spec_ownership.md` へ3トピックを登録した。**backend は無変更**でコードは `d6eeca3` 以降 実質無変更、`mvn test` は未実行。仕様は確定したが**実装が未追随**の3点は [carryover_notes.md](carryover_notes.md) §2 が持つ。直前までの成果は [changelog.md](../changelog.md) の 2026-08-09 ブロックが正。

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
/dev 移行 STEP 3-A-2 レビュー指摘の修正・セグメントA（ドメイン中核）: ISSUE-701・702・709・711 とメール正規化を実装する
完了条件: ①ISSUE-701 リフレッシュの同時実行で再利用検知をすり抜ける経路を塞ぐ②ISSUE-702 確認メールの送信を**コミット後・トランザクションの外**へ移す（方式の正は `tech_auth/mail.md` §16.1。タイムアウト5秒・失敗は WARN のみ・SMTP 未設定なら送信せず INFO も §16 が持つ）③ISSUE-709 `DuplicateKeyException` を制約名で判別する④ISSUE-711 生トークン長のコメント誤りを直す⑤**メールアドレスの正規化（前後空白除去 + 小文字化）を `AuthServiceImpl` へ実装**し、register の重複確認・login の検索・保存を正規化後の値へ寄せる（規約の正は `account.md` §9「メールの正規化」、分岐は §11 #15/#16・§13 #14/#15）⑥`mvn test` が Green で C1 100% を維持
参照: 指摘の詳細は [2026-08-09_230636.md](../reviews/backend-review/2026-08-09_230636.md) の ISSUE-701・702・709・711（起点）、仕様の正は [tech_auth/account.md](../tech/detail/tech_auth/account.md) §9〜§13 と [tech_auth/mail.md](../tech/detail/tech_auth/mail.md) §16
前提: 3-A-3 の詳細設計は完了（main `122ba2b`）＝ ISSUE-702 の送信方式は確定済み。3-A-2 の製造は Green（`41e92aa`）。触るのは `AuthServiceImpl`・`RefreshTokenRepository`(.java/.xml)・`VerificationMailSenderImpl` とテスト。**メール長254・パスワード上限128 のテスト修正は本セグメントに含めない**（`AuthApiTest` は候補キュー2）。JDK 17.0.20（Temurin）・Maven 3.9.11 は新規シェルで実行確認済み。worktree は `python scripts/worktree.py add auth-3a2-fix-a`。`docs/backlog/open_specs.md` は**不在＝未確定ゼロ**
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-A-2 レビュー指摘の修正・セグメントB（ログと設定値）**。ISSUE-704（`userIdMDCPutFilter` 除去＝`known_issues.md` #20 ⑤）・705（`LogKey.EMAIL` が本番未使用）・706（refresh の失敗分岐にログが無い）・710（確認トークン期限を `AuthSettings` へ。**再設定トークン1時間・SMTP・`mail.from` も `tech_auth/mail.md` §16.2 の設定表どおりに足す**）。**`tech_logging.md`「失敗理由（reason）の値」と `tech_backend.md` §4.2 への追記を伴う** | なし（§1 のセグメントAと触るファイルが重ならないので並行可） | `auth-3a2-fix-b`<br>backend/web+env+docs | `dev` |
| 2 | **同・セグメントC（テスト補強）**。ISSUE-707（統合テストへ register 成功・register 重複・login 成功の3本を追加）・708（`UserRepositoryTest` を新設し分岐 §13 #7 を移設）。あわせて**メール長254・パスワード上限128 へ `AuthApiTest` を追随**させる（`carryover_notes.md` §2） | §1 のセグメントA・キュー1のセグメントB（是正後の挙動を検証するため） | `auth-3a2-test`<br>backend/test | `integration-test` |
| 3 | **3-A-3 のテストリスト作成①（link-account / verify-email）**。§19（23件）・§21（16件）を Red へ展開する | 3-A-3 の詳細設計（完了。main `122ba2b`） | `auth-3a3-testlist-a`<br>backend | `test-list` |
| 4 | **3-A-3 のテストリスト作成②（password-reset / メール送信）**。§23（16件）・§25（19件）・§17（8件）を Red へ展開する | 同上（キュー3とは対象APIが重ならないので並行可） | `auth-3a3-testlist-b`<br>backend | `test-list` |
| 5 | **3-B: tower の詳細設計**。`tech_tower.md`（新規）へ処理フローと分岐一覧を作る（`tech_tower.md` が無く分岐一覧も未作成のため 3-B は詳細設計から始める） | なし（3-A と領域が重ならないので並行可） | `tower-detail`<br>docs | `detail-design` |

- **キューが空いたら戻す行**: 3-A-3 の製造（分岐82件のためセグメント2本を見込む。`auth-3a3-dev-a` / `-b` / backend / `dev`）→ 3-B の残り（Phase 1: game / battle）とそれぞれの テストリスト作成 → 製造（順序の正は [carryover_notes.md](carryover_notes.md) §1）／**ログ3種別の実装**（規約は `coding_standards_backend/logging.md` の索引 + 分冊、実装との差分5点は同 §3。`java_migration.md` の STEP と独立に着手できる。`logging-3types` / backend / `dev`）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ
