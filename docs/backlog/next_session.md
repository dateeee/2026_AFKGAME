# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-10 / main `0193e28`（**3-A-3 テストリスト作成①（link-account / verify-email）が完了**）。[link.md](../tech/detail/tech_auth/link.md) §19 の23件と [verify.md](../tech/detail/tech_auth/verify.md) §21 の16件を**失敗するテスト43件**へ展開し、`check_branch_list.py --tests` は違反0。**Red は `mvn test` のテストコンパイルで停止**し、144件すべてが未実装シンボル6件と `AuthSettings` の引数不足＝「未実装」であることを確認済み（想定外の型エラー0件・プロダクトコードは未変更）。**製造で埋める表層は `AuthServiceImplTest`・`AuthApiTest` のクラス Javadoc「製造工程への申し送り」が正**（内訳は [changelog.md](../changelog.md) の 2026-08-10 ブロックが正）。1つ前は 3-A-2 の製造完了ゲート（`backend-review` 指摘6件を解消。`mvn verify` は単体211件 + 結合59件 Green・C1 100%）と 3-B tower の詳細設計。

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
/dev 3-A-3 製造①（link-account / verify-email）: main に積んである Red 43件を Red → Green → Refactor で満たす
完了条件: ①`AuthService#linkAccount`・`#verifyEmail` と申し送りの表層（`UserRepository#updateLinkedAccount`／`#updateEmailVerified`、`EmailVerificationTokenRepository#findByTokenHash`／`#updateUsedById`、`AuthSettings.googleClientId`、`LinkAccountResource`、`AuthApi` の2エンドポイント、`SpringSecurityConfig` の `PUBLIC_ENDPOINTS` へ `/api/auth/verify-email`）を実装する②`mvn verify` が単体・結合とも Green で **C1 100%（未達0）**③`check_java_conventions.py`・`check_error_codes.py` 違反0④コミットする
参照: 表層と層の分担の正は `AuthServiceImplTest`・`AuthApiTest` のクラス Javadoc「製造工程への申し送り」、処理フローの正は [link.md](../tech/detail/tech_auth/link.md) §18・[verify.md](../tech/detail/tech_auth/verify.md) §20、実装の流儀は [coding_standards_backend.md](../process/coding_standards_backend.md) の分冊
前提: main `0193e28`（テストリスト①の Red のみ。プロダクトコードは未変更で、**`mvn test` がテストコンパイルで止まるのが現状の正**）。`AuthSettings` にコンポーネントを1つ足すため `JwtServiceImplTest`・`AfkgameSettingsConfig`・`afkgame.properties` も同時に直す。**`@RequestParam token` を素直に受けると未指定が 400 `HTTP_400` になり verify.md §21 #2 の 422 `VALIDATION_ERROR` を満たさない**（Resource へ束ねるか `ApiExceptionHandler` へハンドラを足すかは製造で決める）。編集を伴うので `python scripts/worktree.py add auth-3a3-dev-a` で worktree を作る（[worktree_guide.md](../process/worktree_guide.md) §5.2）。JDK 17.0.20（Temurin）・Maven 3.9.11・docker 29.6.2 は新規シェルで実行確認済み（**結合テストは埋め込み PostgreSQL のため docker は不要**）。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **`backend-review` の「プロセスへの還元」①②**。`check_java_conventions.py` へ①機密名の突合と②未参照の設定・enum 値の検出を足す（②は ISSUE-802・803 と同型の再発防止で、`AuthSettings` の `guestExpire`・`passwordResetTokenExpire` が現に未参照） | なし（`scripts/` 単独で完結。§1 と領域が重ならないので並行可） | `conv-checker-feedback`<br>scripts | `dev` |
| 2 | **3-A-3 のテストリスト作成②（password-reset / メール送信）**。§23（16件）・§25（19件）・§17（8件）を Red へ展開する。**§17 は #1・#2・#6 の振る舞いだけ実装済み**（`VerificationMailSenderImplTest`）なので、残り5件を足したうえで8行すべてにマーカーを行き渡らせる | 3-A-3 の詳細設計（完了。main `122ba2b`）。worktree での並行着手は可だが、**main への統合は §1 の製造①が Green を取った後にする** — 同じモジュールへ Red を積むと製造①が `mvn test` を通せない（[test-list.md](../../.claude/project/test-list.md) §7） | `auth-3a3-testlist-b`<br>backend | `test-list` |
| 3 | **3-A-3 の製造②（password-reset / メール送信）** | キュー2のテストリスト② | `auth-3a3-dev-b`<br>backend | `dev` |

- **3-A-2 の製造完了ゲートは閉じた**（指摘6件すべて解消。レポートの正は [2026-08-10_013313.md](../reviews/backend-review/2026-08-10_013313.md)）。「プロセスへの還元」4件のうち**③C1 では拾えない観点は見送り**、**④「DB例外の写像を実DBで通す」は ISSUE-806 で前提の固定までが入った**（`DuplicateKeyException` → 409 `AUTH_EMAIL_TAKEN` の写像そのものを API 経由で通すのは未実施）。残る①②がキュー1
- **`known_issues.md` #22 は診断済み・未修正**（原因は前文）。`check_branch_list.py` へ手を入れるタスクの前に片付ける。回帰テスト30件が赤のまま本体を変更すると、退行を検出できない
- **キューが空いたら戻す行**: 3-B のテストリスト作成 → 製造（Phase 1: game / battle → tower の順。tick・戦闘サービスが先に要るため tower のテストは後段。tower の分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件。順序の正は [carryover_notes.md](carryover_notes.md) §1）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ
