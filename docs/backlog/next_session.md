# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / main `9c73ab7`（**`.claude/references/coding-standards-backend.md` を索引 + 分冊3件へ分割した**（`/doc-size`。4,944字＝区分D 残量56字のため §2〜§4 を `layering.md`・`writing.md`・`exception-logging.md` へ移送。本文の削除なし）。あわせて**前セッション `09557ec` のステージ漏れ**（`coding_standards_backend/logging.md` と対になる `common.md` §7・`tech_logging.md` の追随）を `3bed24b`〜`eb574c5` でコミット済み。backend のコードは無変更。直前の成果は **Service をインタフェース + `〜ServiceImpl` 構成へ改めた**（ユーザー指示。ガイドライン 3.2.5.4.1 に合わせ規約の逸脱1件を解消）。`Auth`/`Health`/`Jwt`/`PlayerInitialization`Service と `VerificationMailSender` を分割し、`@Service`・`@Transactional` は実装側・公開 Javadoc はインタフェース側へ。呼び出し側は型がインタフェースになるだけで無改造、単体テスト4件は `*ServiceImplTest` へ改名。規約は `domain_service.md` §2〜§5・`domain.md` §5・`layering.md` §2・`test.md` §1 と派生の `.claude/references/**`・`project/dev.md` を追随済み。直前の成果は **移行 STEP 3-A-2 の製造完了・Red 40件が Green**（`41e92aa`）。`AuthService#register` / `#login` / `#logout`、`EmailVerificationToken` + 同 Repository、`VerificationMailSender`、`UserRepository#findByEmail` / `#updateLastLoginAt`、`AuthApi` の3エンドポイントと Resource 4件を追加。`mvn clean verify` exit 0（単体140件・統合46件、JaCoCo branch は3モジュールとも100%・除外0件）、`check_java_conventions.py`・`check_branch_list.py --tests` も exit 0。**テスト2件の誤りを是正した**（分岐一覧は変更なし）— ①`display_name` は列の既定値をアプリ側で付与する規約（`tech_db.md` §4-2）に従い `User` のフィールド初期値へ置き、assert を `isNull` → `冒険者` に正した（従来の期待どおり null で INSERT すると NOT NULL 違反）②`emailOfLength` はローカル部243文字が `@Email` の RFC 5321 上限64文字で弾かれ長さの分岐（§11 #3）へ到達しなかったため、形式を保つ組み立てへ直した。**`PasswordEncoder` は bcrypt 1本に統一**（雛形の `DelegatingPasswordEncoder`＝pbkdf2 既定が名前解決で注入され §9 と食い違うため）。**`VerificationMailSender` は境界と WARN ログのみの仮実装**で、送信手段と「コミット後・トランザクションの外」の仕組みは 3-A-3 の担当）。直前までの成果は [changelog.md](../changelog.md) の 2026-08-09 ブロックが正。

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
/backend-review 移行 STEP 3-A-2: register / login / logout の実装をレビューする
完了条件: ①`python .claude/scripts/review_prep.py --dir docs/reviews/backend-review --paths backend --title バックエンドコードレビュー結果 --categories "コード品質 / セキュリティ / 一貫性"` で差分モードの範囲と ISSUE 番号を確定する（既定は差分。全量なら `--full`）②`python scripts/check_java_conventions.py` を**先に**実行し、出力をそのままレポートへ取り込む（機械判定の11ルールを目視で重ねて探さない）③`docs/reviews/backend-review/` へレポートを1件出力し、指摘の重要度と対応方針まで示す④**修正は適用しない**（レビュー→修正適用は別セッション。`profile.md` §6 規律5）。実装の疑義は [known_issues.md](known_issues.md) へ記録する
参照: 固有の観点・保存先は [review-code.md](../../.claude/project/review-code.md)、手順（差分モード・分担）は [review-procedure.md](../../.claude/references/review-procedure.md) §1、出力形式は [review-format.md](../../.claude/references/review-format.md)。仕様の正は [tech_auth_account.md](../tech/detail/tech_auth_account.md) §9〜§15、規約は [coding_standards_backend.md](../process/coding_standards_backend.md) の分冊（`common.md` → 層別）
前提: main の最新 `9c73ab7`（前回レビューは `docs/reviews/backend-review/2026-08-08_221814.md`。以降の backend の差分は Red の 9647e06・3-A-2 の実装 41e92aa・**Service のインタフェース分割 d6eeca3**・ログ共通部品 09557ec。`64409f3`〜`9c73ab7` はドキュメントのみで backend 無変更）。**読み取りのみなので worktree を作らない**（[worktree_guide.md](../process/worktree_guide.md) §5.1 #1）。重点は**上の最終更新に挙げた3つの判断** — `PasswordEncoder` の1本化、`VerificationMailSender` の仮実装、テスト2件の是正。`docs/backlog/open_specs.md` は**不在＝未確定ゼロ**
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **移行 STEP 3-A-3 の詳細設計**。link-account / verify-email / password-reset の処理フローと分岐一覧を `tech_auth_account.md`（§16以降）へ追記する。**確認メールの送信手段（SMTP設定・本文・再送）はここで確定する** — 3-A-2 は「送信失敗を登録の成否へ反映しない」前提と `VerificationMailSender` の境界（仮実装）だけを置いた（同 §10 手順9）。**上限超過に注意**（親 `tech_auth.md` は残り629字、子は分割済み） | **満たし済み**（3-A-2 の製造が Green ＝ `41e92aa`。§1 のレビュー指摘待ちではない） | `auth-3a3-detail`<br>docs | `detail-design` |
| 2 | **移行 STEP 3-A-2 のレビュー指摘修正**。`docs/reviews/backend-review/` の最新レポートに従って `backend/` を直す。**§1 のレビューで指摘が出た場合のみ**（0件なら本行を消す） | §1 のレビュー完了 | `auth-3a2-fix`<br>backend | `dev` |
| 3 | **移行 STEP 3-A-3 のテストリスト作成**。上の詳細設計の分岐一覧を Red へ展開する | 優先1の詳細設計 | `auth-3a3-testlist`<br>backend | `test-list` |
| 4 | **移行 STEP 3-A-3 の製造**。Red を Green にする | 優先3のテストリスト | `auth-3a3-dev`<br>backend | `dev` |
| 5 | **移行 STEP 3-B: tower の詳細設計**。`tech_tower.md`（新規）へ処理フローと分岐一覧を作る（`tech_tower.md` が無く分岐一覧も未作成のため 3-B は詳細設計から始める） | なし（3-A と領域が重ならないので並行可） | `tower-detail`<br>docs | `detail-design` |

- **キューが空いたら戻す行**: 3-B の残り（Phase 1: game / battle）と、それぞれのテストリスト作成 → 製造（順序の正は [carryover_notes.md](carryover_notes.md) §1）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件 — ①**③限界突破の詳細設計**: `POST /api/character/limit-break` を `tech_limitbreak.md`（新規）へ。素材＝同一 `master_id` のキャラ1体で `limit_break` +1（上限5回）。起点は `master/character.md` §8・§8.1、可否は `tech_state.md` §4、`canLimitBreak` は `tech_scout.md` §6。`characters.master_id` は Phase 4 で追加する未実装列 ②**④ダンジョン3（塔6〜8）のマスターデータ**: `docs/data/towers/` へ3ファイル追加し `TOWERS_OVERVIEW.md`・`master_data.md` の索引を更新（書式は `009_黄昏の塔.md` に倣う） ③**テストリスト作成**: 拠点・施設・鍛冶屋（`tech_base.md` §7・§8 の36件 + `tech_forge_*` の74件）。**詳細設計は拠点・施設・①酒場スカウト・②鍛冶屋まで完了済み**
