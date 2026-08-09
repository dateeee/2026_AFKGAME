# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / `cf5417c` で **移行 STEP 2R-F（実行・デプロイの切替）を完了し、これで STEP 2R が完了**した。war を Tomcat 11.0.24 へ配備して実機で通っている — `GET /health` が 200（`db:ok`）、`POST /api/auth/guest` が 200 でトークンペアを返す。**コンテキストパスは ROOT に確定**（`webapps/ROOT.war`。`/health`・`/api/**` を仕様どおりの絶対パスで受けるため、Vite の `server.proxy` に `rewrite` は不要）。E2E ハーネス（`serve-backend.mjs`）は専用 `CATALINA_BASE` を組み立てて :8100 で war を起動する方式へ書き直した（`java -jar` は 2R-B で実行可能 jar が消えており動かない状態だった）。DB操作は `docker compose exec` ではなく `docker exec afkgame-postgres` を使う（compose のプロジェクト名が cwd 由来で、worktree からは起動中コンテナを引けないため）。`mvn clean verify` は exit 0（単体89件・統合45件が緑、JaCoCo branch は domain・web とも check 通過）。詳細は [changelog.md](../changelog.md) の 2026-08-09 先頭行。**Tomcat 11.0.24 は `%LOCALAPPDATA%\Programs\apache-tomcat-11.0.24`**（`CATALINA_HOME` はユーザー環境変数へ設定済みだが**既存シェルには未反映**）。**起動には `SPRING_PROFILES_ACTIVE`（`local` / `production`）が必須**（未設定なら落ちる）。

**STEP 2R が完了したので backend の Phase 機能へ着手してよい**（2R 完了までの着手禁止は解除）。以後の移行順序は **3-A-2（register / login / logout）→ 3-A-3 → 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**。順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)（索引 + `java_migration/` 3分冊）。

**auth の分岐一覧は「ゲスト作成」しか無い**（`tech_auth.md` §8.3 の12件は初期化の観点のみ）。register / login / logout そのものの分岐（メール重複・パスワード不一致・失効トークン等）は未作成で、**3-A-2 は `test-list` ではなく `detail-design` から始める**（tower も同じ欠落。`carryover_notes.md` §1）。

**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（Python 削除を STEP 3〜5 より先に実施したため）。E2E はハーネスと `GET /health` まで疎通済みだが、テスト本体は STEP 5 完了まで赤が正常。

**⚠ `efficiency_memo.md` が 8,570字で上限超過**（区分C・570字オーバー）。`/retro` で棚卸しして解消する。

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
/detail-design 移行 STEP 3-A-2 の前工程: register / login / logout の処理フローと分岐一覧を作る
完了条件: ①`docs/tech/detail/tech_auth_account.md`（新規）へ3操作の処理フローと分岐一覧を書く（**`tech_auth.md` へは追記しない** — 残り698字・§8 が既に H2 上限超過。`tech_forge.md` + `tech_forge_*.md` の分割に倣う）②分岐は最低でも「メール形式不正・メール重複・パスワード強度・BCrypt 検証失敗・存在しないメール・ゲストアカウントでのログイン試行・リフレッシュトークンの失効/不在/二重失効」を含める③`python scripts/check_branch_list.py` が exit 0（現状29件・違反なしなので、増分だけ見ればよい）④`tech_auth.md` §8 冒頭・[tech_spec.md](../tech/tech_spec.md)・[README.md](../../README.md) の索引から新ファイルへ到達できる（`check_docs.py` の索引到達性）⑤`check_doc_size.py`・`check_docs.py` とも違反0
参照: 分岐一覧の書式と初期化の正は [tech_auth.md](../tech/detail/tech_auth.md) §8.2・§8.3（**register は手順2以降を再利用**＝§8 冒頭）、API契約とエラーコードは [tech_api.md](../tech/basic/tech_api.md) の auth 節、トークン仕様は同 §1・§4。`BCryptPasswordEncoder` strength 12 と `SecurityConfig` の認証不要パス追加は [carryover_notes.md](carryover_notes.md) §1、`uq_players_user_id` 違反の扱い（業務エラーコードを新設しない）は同 §1
前提: main の最新 `cf5417c`（STEP 2R 完了。war + Tomcat で `/health`・ゲスト認証とも 200）。**`docs/tech/**` を編集するので worktree を作る**: `python scripts/worktree.py add auth-3a2-detail` → `EnterWorktree` に `path` で移動（領域は docs/tech）。**外部ツール・ランタイムは不要**（仕様書のみ。JDK・Maven・Tomcat・Docker は使わない）。`docs/backlog/open_specs.md` は**不在＝未確定ゼロ**
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **移行 STEP 3-A-2 のテストリスト作成**。§1 で作った register / login / logout の分岐一覧を JUnit の Red へ展開する。`afkgame-web` の `AuthApi` と `afkgame-domain` の `AuthService` が対象。**同一モジュールに Red が複数並ぶと片方だけでは Green を検証できない**（[test-list.md](../../.claude/project/test-list.md) §7）ので、Red と Green は同じ単位で積む | **§1 の分岐一覧が完成してから** | `auth-3a2-testlist`<br>backend | `test-list` |
| 2 | **`afkgame-env` を JaCoCo の分岐100%ゲートへ載せる**。`AfkgameSettingsConfig` のプロファイル検査（4分岐）に単体テストが無く 100% を満たせないため、env だけゲート対象外にしてある。テストを足して `afkgame-env/pom.xml` へ jacoco プラグインを宣言する | なし（2R-F 完了で backend が空いた） | `env-jacoco`<br>backend | `unit-test` |
| 3 | **効率メモの棚卸し**。[efficiency_memo.md](efficiency_memo.md) が 8,570字で**上限超過**。消化して原因を反映し、済んだエントリを削除する。**削除は main で**（union は削除を伝播しない） | なし | main で実施<br>docs/backlog | `retro` |
| 4 | **`scripts/check_java_conventions.py` の常設化**。Java 規約10項目を機械判定できず `backend-review` で毎回使い捨てを書いている（項目と背景は [carryover_notes.md](carryover_notes.md) §3）。回帰テストを `scripts/tests/` へ追加し [commands.md](../../.claude/project/commands.md) §1 へ登録する | なし | `java-conventions`<br>scripts | `dev` |

- **キューが空いたら戻す行**: 移行 STEP 3-A-2 の製造（`dev`。上記1の後）、3-A-3（link-account / verify-email / password-reset）。順序の正は [carryover_notes.md](carryover_notes.md) §1
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件 — ①**③限界突破の詳細設計**: `POST /api/character/limit-break` を `tech_limitbreak.md`（新規）へ。素材＝同一 `master_id` のキャラ1体で `limit_break` +1（上限5回）。起点は `master/character.md` §8・§8.1、可否は `tech_state.md` §4、`canLimitBreak` は `tech_scout.md` §6。`characters.master_id` は Phase 4 で追加する未実装列 ②**④ダンジョン3（塔6〜8）のマスターデータ**: `docs/data/towers/` へ3ファイル追加し `TOWERS_OVERVIEW.md`・`master_data.md` の索引を更新（書式は `009_黄昏の塔.md` に倣う） ③**テストリスト作成**: 拠点・施設・鍛冶屋（`tech_base.md` §7・§8 の36件 + `tech_forge_*` の74件）。**詳細設計は拠点・施設・①酒場スカウト・②鍛冶屋まで完了済み**
