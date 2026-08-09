# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / main `74db746`（**`afkgame-env` を JaCoCo の分岐100%ゲートへ載せた**。`AfkgameSettingsConfig` のプロファイル検査4分岐を単体テスト5件で網羅し、**env・domain・web の3モジュールとも branch 100%・除外指定0件**。`mvn clean verify` は exit 0 ＝ 単体94件・統合45件が緑）。`ca3402f` で `scripts/check_java_conventions.py` を常設化した（Java 規約11ルールを機械判定し、実リポジトリ85ファイルは違反0。使い方は [commands.md](../../.claude/project/commands.md) §1、抑止は `// 規約例外: <理由>`）。`cf5417c` で **移行 STEP 2R-F（実行・デプロイの切替）を完了し、これで STEP 2R が完了**した。war を Tomcat 11.0.24 へ配備して実機で通っている — `GET /health` が 200（`db:ok`）、`POST /api/auth/guest` が 200 でトークンペアを返す。**コンテキストパスは ROOT に確定**（`webapps/ROOT.war`。`/health`・`/api/**` を仕様どおりの絶対パスで受けるため、Vite の `server.proxy` に `rewrite` は不要）。E2E ハーネス（`serve-backend.mjs`）は専用 `CATALINA_BASE` を組み立てて :8100 で war を起動する方式へ書き直した（`java -jar` は 2R-B で実行可能 jar が消えており動かない状態だった）。DB操作は `docker compose exec` ではなく `docker exec afkgame-postgres` を使う（compose のプロジェクト名が cwd 由来で、worktree からは起動中コンテナを引けないため）。`mvn clean verify` は exit 0（単体89件・統合45件が緑、JaCoCo branch は domain・web とも check 通過）。詳細は [changelog.md](../changelog.md) の 2026-08-09 先頭行。**Tomcat 11.0.24 は `%LOCALAPPDATA%\Programs\apache-tomcat-11.0.24`**（`CATALINA_HOME` はユーザー環境変数へ設定済みだが**既存シェルには未反映**）。**起動には `SPRING_PROFILES_ACTIVE`（`local` / `production`）が必須**（未設定なら落ちる）。

**STEP 2R が完了したので backend の Phase 機能へ着手してよい**（2R 完了までの着手禁止は解除）。以後の移行順序は **3-A-2（register / login / logout）→ 3-A-3 → 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**。順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)（索引 + `java_migration/` 3分冊）。

**register / login / logout の分岐一覧は揃った**（`0c3e1a8`。[tech_auth_account.md](../tech/detail/tech_auth_account.md) §11・§13・§15 に38件）。3-A-2 は `test-list` から始められる。**tower は `tech_tower.md` が無く分岐一覧も未作成**なので、3-B は引き続き `detail-design` から始める（`carryover_notes.md` §1）。

**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（Python 削除を STEP 3〜5 より先に実施したため）。E2E はハーネスと `GET /health` まで疎通済みだが、テスト本体は STEP 5 完了まで赤が正常。

**複数セッションにまたがる申し送りの正は [carryover_notes.md](carryover_notes.md)**（§1 Java 移行 / §2 仕様・マスターデータ / §3 環境・ツール）。着手前にそちらも見る。**恒久的な知見は同ファイルに残さず規約・コマンド表の正へ移す**方針にしたので、Java 実装の流儀は [coding_standards_backend.md](../process/coding_standards_backend.md) の分冊、環境・コマンドは [commands.md](../../.claude/project/commands.md) を見る。

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
/test-list 移行 STEP 3-A-2: register / login / logout の分岐一覧38件を失敗するテスト（Red）へ展開する
完了条件: ①`tech_auth_account.md` §11（登録13件）・§13（ログイン13件）・§15（ログアウト12件）の全38件にテストを対応づけ、`afkgame-domain` の `AuthServiceTest`（サービス層の分岐）と `afkgame-web` の `AuthApiTest`（HTTPステータス・エラーコード・バリデーション）へ振り分ける②各テストの Javadoc へ `分岐: tech_auth_account.md §11 #1` 形式のマーカーを書き、`python scripts/check_branch_list.py` と `python scripts/check_java_conventions.py` が exit 0（前者はマーカーと分岐一覧の対応照合、後者は Java 規約11ルール。テストコードも規約の対象）③`cd backend && mvn test` が **Red で落ちること**を確認する（未実装メソッドの呼び出しのため。出力の受け取り方は [commands.md](../../.claude/project/commands.md) §2 — ファイルへ落として cp932 で読む）④**Red と Green は同じ単位で積む**（[test-list.md](../../.claude/project/test-list.md) §7）ため、本セッションでは製造へ進まない
参照: 分岐一覧の正は [tech_auth_account.md](../tech/detail/tech_auth_account.md) §11・§13・§15、処理フローは同 §10・§12・§14、3操作に共通する規約（bcrypt strength 12・トークン生成・エラーコード）は同 §9。テストの配置・分離・記述規約は [coding_standards_backend/test.md](../process/coding_standards_backend/test.md) §1・§2。既存の書き方は `AuthServiceTest`（ゲスト作成・refresh）と `AuthApiTest` に倣う
前提: main の最新 `74db746`（3-A-2 の詳細設計は `0c3e1a8` で完了。`check_branch_list.py` は32セクション・WARN 0）。**`backend/` を編集するので worktree を作る**: `python scripts/worktree.py add auth-3a2-testlist` → `EnterWorktree` に `path` で移動（領域は backend）。**外部ツールは新規シェルで実測済み（2026-08-09）**: Maven 3.9.11・JDK 17.0.20（Adoptium）・Docker 29.6.2 はいずれも PATH 反映済みでそのまま呼べる（`CATALINA_HOME` は新規シェルへ未反映だが、本タスクに Tomcat は不要）。`docs/backlog/open_specs.md` は**不在＝未確定ゼロ**
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **移行 STEP 3-A-2 の製造**。§1 で積んだ Red を Green にする。`afkgame-domain` の `AuthService` へ register / login / logout を実装し、`afkgame-web` の `AuthApi` へ3エンドポイントと Resource（Bean Validation）を足す。`BCryptPasswordEncoder`（strength 12）の Bean 定義と `SecurityConfig` の認証不要パス追加（register・login。**logout は認証必須**）も本セグメント | **§1 のテストリスト（Red）が積まれてから** | `auth-3a2-dev`<br>backend | `dev` |

- **キューが空いたら戻す行**: 移行 STEP 3-A-3（link-account / verify-email / password-reset。**確認メールの送信手段（SMTP設定・本文・再送）はここで確定する** — 3-A-2 では送信失敗を登録の成否へ反映しない前提だけを置いた。`tech_auth_account.md` §10 手順9）。順序の正は [carryover_notes.md](carryover_notes.md) §1
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件 — ①**③限界突破の詳細設計**: `POST /api/character/limit-break` を `tech_limitbreak.md`（新規）へ。素材＝同一 `master_id` のキャラ1体で `limit_break` +1（上限5回）。起点は `master/character.md` §8・§8.1、可否は `tech_state.md` §4、`canLimitBreak` は `tech_scout.md` §6。`characters.master_id` は Phase 4 で追加する未実装列 ②**④ダンジョン3（塔6〜8）のマスターデータ**: `docs/data/towers/` へ3ファイル追加し `TOWERS_OVERVIEW.md`・`master_data.md` の索引を更新（書式は `009_黄昏の塔.md` に倣う） ③**テストリスト作成**: 拠点・施設・鍛冶屋（`tech_base.md` §7・§8 の36件 + `tech_forge_*` の74件）。**詳細設計は拠点・施設・①酒場スカウト・②鍛冶屋まで完了済み**
