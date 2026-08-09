# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / main `ca84315` の次のコミット（**移行 STEP 3-A-2（register / login / logout）のバックエンドコードレビューを実施した**。差分モード・基点 `da91521`。`check_java_conventions.py` は**違反ゼロ**。指摘11件（高0 / 中7 / 低4）を [2026-08-09_230636.md](../reviews/backend-review/2026-08-09_230636.md) へ出力し、**修正は未適用**（`profile.md` §6 規律5 でレビューと修正適用を分けた ＝ 候補キュー1〜3）。`known_issues.md` へ2件反映 — **#21 を新設**（メールアドレスの大小が未正規化。同一アドレスで重複登録できる。**方針決定が先**）、**#20 へ ⑤`userIdMDCPutFilter`** を追記（雛形の gfw ロギング部品の列挙漏れ）。前回 ISSUE-601・602・604・605・606 は解消を確認。**backend は無変更**でコードは `d6eeca3` 以降 実質無変更、`mvn test` は未実行。直前までの成果は [changelog.md](../changelog.md) の 2026-08-09 ブロックが正。

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
/detail-design 移行 STEP 3-A-3: link-account / verify-email / password-reset の処理フローと分岐一覧を確定する
完了条件: ①`tech_auth/account.md` へ3操作の入口・出口条件・手順・分岐一覧を §16 以降として追記する（既存 §10〜§15 の書式に倣い、節番号は §15 の続き）②**確認メールの送信手段（SMTP設定・本文・再送）と「コミット後・トランザクションの外」で送る方式**をここで確定する（3-A-2 は `VerificationMailSender` の境界だけを置いた仮実装で、実際にはトランザクション内で呼んでいる。レビュー ISSUE-702）③**メールアドレスの正規化規約**を §9 の共通規約へ1行で確定する（`known_issues.md` #21。案A アプリ層で小文字化 / 案B `citext`・`lower(email)` の一意インデックス。確定したら #21 の行を削除する）④`carryover_notes.md` §2 の入力長のぶれ2点（メール長 254/255 の食い違い・パスワード上限の分岐行が §11 に無い）を同じ回で片付ける⑤`python scripts/check_branch_list.py`・`check_doc_size.py`・`check_docs.py` が exit 0
参照: 仕様の正は [tech_auth/account.md](../tech/detail/tech_auth/account.md) §9〜§15（起点。3操作の書式をそのまま踏襲する）、[tech_auth.md](../tech/detail/tech_auth.md) §1・§6。エラーコードは [tech_error_handling.md](../tech/basic/tech_error_handling.md)「AUTH_ コード一覧」から選び**本工程で新設しない**（§9）
前提: 3-A-2 の製造は Green（`41e92aa`）でレビュー済み。**指摘11件の修正は未適用**＝候補キュー1〜3（本タスクと領域が重ならないので並行可。ただしキュー1の ISSUE-702 は本タスクの完了条件②が前提）。**上限超過に注意** — 親 `tech_auth.md` は残り629字、子 `account.md` の残量は `check_doc_size.py --sections` で**書く前に**測る（`profile.md` §7 #7）。worktree は `python scripts/worktree.py add auth-3a3-detail`。`docs/backlog/open_specs.md` は**不在＝未確定ゼロ**
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-A-2 レビュー指摘の修正・セグメントA（ドメイン中核）**。ISSUE-701（リフレッシュの同時実行で再利用検知をすり抜ける）・702（確認メールをコミット後へ）・709（`DuplicateKeyException` を制約名で判別）・711（生トークン長のコメント誤り）。触るのは `AuthServiceImpl`・`RefreshTokenRepository`(.java/.xml)・`VerificationMailSenderImpl` とテスト。**仕様追記が要らない4件**をまとめた | §1 の詳細設計（ISSUE-702 の送信方式が決まっていること） | `auth-3a2-fix-a`<br>backend/domain | `dev` |
| 2 | **同・セグメントB（ログと設定値）**。ISSUE-704（`userIdMDCPutFilter` 除去＝`known_issues.md` #20 ⑤）・705（`LogKey.EMAIL` が本番未使用）・706（refresh の失敗分岐にログが無い）・710（確認トークン期限を `AuthSettings` へ）。**`tech_logging.md`「失敗理由（reason）の値」と `tech_backend.md` §4.2 への追記を伴う** | なし（セグメントAと触るファイルが重ならない） | `auth-3a2-fix-b`<br>backend/web+env+docs | `dev` |
| 3 | **同・セグメントC（テスト補強）**。ISSUE-707（統合テストへ register 成功・register 重複・login 成功の3本を追加）・708（`UserRepositoryTest` を新設し分岐 §13 #7 を移設） | セグメントA・B（是正後の挙動を検証するため） | `auth-3a2-test`<br>backend/test | `integration-test` |
| 4 | **3-A-3 のテストリスト作成**。§1 の詳細設計の分岐一覧を Red へ展開する | §1 の詳細設計 | `auth-3a3-testlist`<br>backend | `test-list` |
| 5 | **3-B: tower の詳細設計**。`tech_tower.md`（新規）へ処理フローと分岐一覧を作る（`tech_tower.md` が無く分岐一覧も未作成のため 3-B は詳細設計から始める） | なし（3-A と領域が重ならないので並行可） | `tower-detail`<br>docs | `detail-design` |

- **キューが空いたら戻す行**: 3-A-3 の製造（`auth-3a3-dev` / backend / `dev`）→ 3-B の残り（Phase 1: game / battle）とそれぞれの テストリスト作成 → 製造（順序の正は [carryover_notes.md](carryover_notes.md) §1）／**ログ3種別の実装**（規約は `coding_standards_backend/logging.md` の索引 + 分冊、実装との差分5点は同 §3。`java_migration.md` の STEP と独立に着手できる。`logging-3types` / backend / `dev`）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ
