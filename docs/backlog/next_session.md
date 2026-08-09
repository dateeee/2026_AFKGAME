# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / main `9647e06`（**移行 STEP 3-A-2 のテストリスト（Red）を積んだ**。[tech_auth_account.md](../tech/detail/tech_auth_account.md) §11・§13・§15 の**分岐一覧38件をテスト40件へ展開**し、サービス層25件 = `AuthServiceTest` / Web層14件 = `AuthApiTest` / 認証必須の拒否（§15 #2）1件 = `AuthApiIntegrationTest` へ振り分けた。`check_branch_list.py --tests`・`check_java_conventions.py` はともに exit 0。**Red は `mvn test` の testCompile で確定**しており、落ち方は「シンボルを見つけられません」のみ ＝ 未作成の型3件と `AuthService` の未実装メソッド3件を作れば Green に向かう。**製造が実装する表層は `AuthServiceTest` と `AuthApiTest` のクラス Javadoc に列挙済み**）。直前までの成果は [changelog.md](../changelog.md) の 2026-08-09 ブロックが正。

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
/dev 移行 STEP 3-A-2: register / login / logout の Red 40件を Green にする
完了条件: ①`afkgame-domain` へ `AuthService#register(email, rawPassword)` / `#login(email, rawPassword)` / `#logout(userId, rawRefreshToken)` と、未作成の3件（`EmailVerificationToken`・`EmailVerificationTokenRepository`（+ 同名マッピングXML）・`VerificationMailSender`）、`UserRepository#findByEmail` / `#updateLastLoginAt` を追加する②`afkgame-web` へ `AuthApi` の3エンドポイントと Resource 4件（`RegisterResource`・`LoginResource`・`LogoutResource`・`StatusResource`。制約は `AuthApiTest` のクラス Javadoc が正）、`BCryptPasswordEncoder`（strength 12）の Bean 定義、`SpringSecurityConfig.PUBLIC_ENDPOINTS` へ register・login を追加する（**logout は認証必須なので足さない**）③`cd backend && mvn clean verify` が exit 0（単体・統合とも緑、JaCoCo branch は env・domain・web の3モジュールとも100%。**追加した分岐で未達が出たら分岐一覧に無いテストを足さず**、防御的分岐を削るか `unit-test.md` §4 の除外規則で判断する）④`python scripts/check_branch_list.py --tests` と `python scripts/check_java_conventions.py` が exit 0（出力の受け取り方は [commands.md](../../.claude/project/commands.md) §2 — mvn はファイルへ落として cp932 で読む）
参照: **実装の起点は `AuthServiceTest` と `AuthApiTest` のクラス Javadoc**（製造が作る表層を型・シグネチャまで列挙してある）。処理フローは [tech_auth_account.md](../tech/detail/tech_auth_account.md) §10（登録）・§12（ログイン）・§14（ログアウト）、3操作に共通する規約（bcrypt strength 12・トークン生成・エラーコード）は同 §9。例外は**既存の `AppException`（code + status）に合わせる**（gfw 例外への置換は [known_issues.md](known_issues.md) §2 #19 の別タスク）。`email_verification_tokens` は `V1__initial_schema.sql` に作成済みでマイグレーション追加は不要
前提: main の最新 `9647e06`（Red は testCompile で確定済み。`check_branch_list.py --tests` は exit 0）。**`backend/` を編集するので worktree を作る**: `python scripts/worktree.py add auth-3a2-dev` → `EnterWorktree` に `path` で移動（領域は backend）。**外部ツールは新規シェルで実測済み（2026-08-09）**: Maven 3.9.11・JDK 17.0.20（Adoptium）・Docker 29.6.2 はいずれも PATH 反映済み（統合テストは埋め込み PostgreSQL を使うので Docker は不要）。`docs/backlog/open_specs.md` は**不在＝未確定ゼロ**。**確認メールの送信手段（SMTP設定・本文・再送）は 3-A-3 で確定する**ため、`VerificationMailSender` は境界の定義と WARN ログに留める
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **移行 STEP 3-A-3 の詳細設計**。link-account / verify-email / password-reset の処理フローと分岐一覧を `tech_auth_account.md`（§16以降）へ追記する。**確認メールの送信手段（SMTP設定・本文・再送）はここで確定する** — 3-A-2 は「送信失敗を登録の成否へ反映しない」前提と `VerificationMailSender` の境界だけを置いた（同 §10 手順9）。**上限超過に注意**（親 `tech_auth.md` は残り629字、子は分割済み） | **§1 の製造が Green になってから**（3-A-2 の実装が確定しないと共通規約 §9 が動く） | `auth-3a3-detail`<br>docs | `detail-design` |

- **キューが空いたら戻す行**: 移行 STEP 3-A-3 のテストリスト作成 → 製造（順序の正は [carryover_notes.md](carryover_notes.md) §1）。その後は 3-B（Phase 1: game / battle / tower。**tower は `detail-design` から**）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件 — ①**③限界突破の詳細設計**: `POST /api/character/limit-break` を `tech_limitbreak.md`（新規）へ。素材＝同一 `master_id` のキャラ1体で `limit_break` +1（上限5回）。起点は `master/character.md` §8・§8.1、可否は `tech_state.md` §4、`canLimitBreak` は `tech_scout.md` §6。`characters.master_id` は Phase 4 で追加する未実装列 ②**④ダンジョン3（塔6〜8）のマスターデータ**: `docs/data/towers/` へ3ファイル追加し `TOWERS_OVERVIEW.md`・`master_data.md` の索引を更新（書式は `009_黄昏の塔.md` に倣う） ③**テストリスト作成**: 拠点・施設・鍛冶屋（`tech_base.md` §7・§8 の36件 + `tech_forge_*` の74件）。**詳細設計は拠点・施設・①酒場スカウト・②鍛冶屋まで完了済み**
