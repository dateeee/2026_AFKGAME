# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-10 / main `daca632`（**3-A-2 の製造完了ゲート `backend-review` を実施**。レポートは [2026-08-10_013313.md](../reviews/backend-review/2026-08-10_013313.md)）。前回指摘 ISSUE-701〜711 は **9件が完全に解消**、706（refresh の失敗ログ統一）と 709（制約名による重複判定）は残りを新規指摘へ引き継いだ。新規は6件（高1 / 中2 / 低3）で、最重要は **ISSUE-801: AOP境界ログが `rawRefreshToken`・`rawToken` を平文で `application.log` へ書き出す**（`LayerLoggingInterceptor` の固定表は規約 §3.1 どおりだが、`AuthService#refresh`・`#logout`・`VerificationMailSender#send` のパラメータ名がどれにも一致せず素通りする。`account.md` §9「トークン生値は出力しない」違反。**パラメータ名を `refreshToken`／`token` へ揃えるだけで閉じ、規約の改訂は不要**）。ほかは 802 パスワード長 8/128 の二重定義（設定側は参照0件）・803 `MailSettings` 先行投入と `known_issues.md` #6 のずれ・804 refresh のユーザー不在分岐だけログ無し・805 `@MaskReturnValue` がJDKプロキシ依存・806 制約名判定が実DBで未検証。**レビューは読み取りのみで、成果物はレポート1件と本ファイルの更新だけ**（`mvn verify` は実行していない。直前の Green 実績は main `13e709f` の 単体196件 + 結合58件・C1 100%）。**レビューとは別に、外部API呼び出しの RESTクライアント選定（`RestClient` + `HttpComponentsClientHttpRequestFactory`）を確定して統合済み**（`d25239b`。`tech_selection.md` §2 へ採用行、`tech_backend.md` §4.3 を新設、`afkgame-domain` へ `httpclient5`。**指摘とは無関係の独立変更**で、セグメントD・E の対象ファイルとは重ならない）。**さらに独立変更として [spec_ownership.md](../process/spec_ownership.md) を再構成した**（残量114字で新規行を追加できない状態だったため。備考列を廃止・一般原則から導ける8行を削除して 7,886字 → 4,929字 / 25行 → 17行、登録基準と書式を §2 へ新設、デッドだった経験値式のパターンを修正。`tech_auth/account.md` §9 は「`citext` は採らない」の1句だけ追記でログ行は無変更）。直前までの成果は [changelog.md](../changelog.md) の 2026-08-10 ブロックが正。

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
/dev 3-A-2 レビュー指摘の修正セグメントD（境界ログのマスク漏れ）: ISSUE-801・804・805 を修正する
完了条件: ①ISSUE-801 — `AuthService`・`AuthServiceImpl` の `refresh`／`logout` の引数を `rawRefreshToken` → `refreshToken`、`VerificationMailSender`・`VerificationMailSenderImpl#send` を `rawToken` → `token` へ改名し（Javadoc の `@param` も追随）、`LayerLoggingInterceptorTest` へ固定表8語を網羅するマスクテストを足す②ISSUE-804 — `AuthServiceImpl` 行140〜143 へ `LogReason.USER_NOT_FOUND` の WARN を1行足す③ISSUE-805 — `LayerLoggingInterceptor#formatResult` を、実装メソッドに `@MaskReturnValue` が無ければ宣言インタフェース側も探す形にし、テストを1件足す④`mvn verify` が Green・C1 100%、`python scripts/check_java_conventions.py` が違反0⑤コミットする
参照: 指摘の本文と修正案は [2026-08-10_013313.md](../reviews/backend-review/2026-08-10_013313.md) の ISSUE-801・804・805（起点。修正案はそのまま適用できる粒度で書いてある）、規約の正は [logging/application.md](../process/coding_standards_backend/logging/application.md) §3.1、仕様の正は [tech_auth/account.md](../tech/detail/tech_auth/account.md) §9「ログ」
前提: main `daca632`（レビューのコミット `2b4d088` の後に RESTクライアント選定 `d25239b` が入っている。どちらもセグメントD の対象ファイルとは重ならない）。**ISSUE-801 は高（セキュリティ）で、規約 §3.1 の固定表は無変更のままパラメータ名側を寄せれば閉じる**（`refreshToken`・`token` は既に固定表にある）。`@Size` や設定値には触らない（ISSUE-802・806 はセグメントE の担当で、対象ファイルが重ならない）。編集を伴うので `python scripts/worktree.py add auth-3a2-fix-d` で worktree を作る（[worktree_guide.md](../process/worktree_guide.md) §5.2）。JDK 17.0.20（Temurin）・Maven 3.9.11・docker 29.6.2 は新規シェルで実行確認済み（`mvn verify` の結合テストは docker の PostgreSQL が要る）。`docs/backlog/open_specs.md` は**不在＝未確定ゼロ**
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-A-2 レビュー指摘の修正セグメントE（設定値の二重定義とテスト補強）**。ISSUE-802（パスワード長 8/128 が `RegisterResource` のリテラルと設定値に二重定義され、設定側は参照0件 → 一致を固定するテストを新設）・ISSUE-806（`isEmailConstraintViolation` の制約名判定を `UserRepositoryTest` の実DB例外で固定）・ISSUE-803（`known_issues.md` #6 を「設定は用意済み・読み手が未実装」へ）を修正する | 本レビュー（`2026-08-10_013313.md`）。セグメントD とは対象ファイルが重ならないので並行可 | `auth-3a2-fix-e`<br>backend + docs | `dev` |
| 2 | **3-A-3 のテストリスト作成①（link-account / verify-email）**。§19（23件）・§21（16件）を Red へ展開する | 3-A-3 の詳細設計（完了。main `122ba2b`） | `auth-3a3-testlist-a`<br>backend | `test-list` |
| 3 | **3-A-3 のテストリスト作成②（password-reset / メール送信）**。§23（16件）・§25（19件）・§17（8件）を Red へ展開する。**§17 は #1・#2・#6 の振る舞いだけ実装済み**（`VerificationMailSenderImplTest`）なので、残り5件を足したうえで8行すべてにマーカーを行き渡らせる | 同上（キュー2とは対象APIが重ならないので並行可） | `auth-3a3-testlist-b`<br>backend | `test-list` |
| 4 | **3-B: tower の詳細設計**。`tech_tower.md`（新規）へ処理フローと分岐一覧を作る（`tech_tower.md` が無く分岐一覧も未作成のため 3-B は詳細設計から始める） | なし（3-A と領域が重ならないので並行可） | `tower-detail`<br>docs | `detail-design` |

- **3-A-2 の製造完了ゲート（`backend-review`）は実施済み**。指摘6件の修正はセグメントD（§1）とE（キュー1）に割ってある。**D を先に通す**（ISSUE-801 が高・セキュリティのため）
- レビューの「プロセスへの還元」4件（`check_java_conventions.py` へ機密名の突合／未参照の設定・enum 値の検出／C1 では拾えない観点／結合テストへ「DB例外の写像を実DBで通す」）は、**D・E の修正とは別枠**。3-A-3 の製造より前に還元1（機密名の突合）を入れると効果が大きい
- **キューが空いたら戻す行**: 3-A-3 の製造（分岐82件のためセグメント2本を見込む。`auth-3a3-dev-a` / `-b` / backend / `dev`。前提はキュー2・3 のテストリスト）→ 3-B の残り（Phase 1: game / battle）とそれぞれの テストリスト作成 → 製造（順序の正は [carryover_notes.md](carryover_notes.md) §1）
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻す3件の内訳は [carryover_notes.md](carryover_notes.md) §2 が持つ
