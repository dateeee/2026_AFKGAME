# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-11 / main `0776fd8`（**3-A-3 レビュー指摘の反映②**＝ ISSUE-903・904・905・906・909 の5件を適用し、**3-A-3 の製造完了ゲートが閉じた**＝ Java 移行は 3-B へ進める。`report_java_tests.py --run --it` は**単体306件 + 結合85件 Green・C1 100%（194/194・未達0）**、`check_java_conventions.py` 違反0・**WARN 13件で横ばい**、`pytest scripts/tests` **406件**（判定12 に語並び判定を足して +3）、`check_docs.py`・`check_doc_size.py` 違反0。あわせて消化済みの申し送り2件＝ `known_issues.md` #6 と `carryover_notes.md` の MailSettings 行を削除した）。1つ前は 2026-08-11 / main `11c5ade`（**Phase 5 設計整合ゲート**＝ `diagrams-review` で9件検出。**§1 がその反映**）。内訳は [changelog.md](../changelog.md) の 2026-08-11 ブロックが正。

**Java 移行の残りは 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)）。**3-B は詳細設計まで完了済み**で、残るのはテストリスト作成 → 製造。**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（E2E はハーネスと `GET /health` まで疎通済みで、テスト本体は STEP 5 完了まで赤が正常）。

**`mvn verify` に Docker は要らない**（結合テストは domain・web とも `EmbeddedPostgresSupport` の埋め込み PostgreSQL）。`report_java_tests.py --run` は既定で `-DskipITs` なので、**結合テストまで見るなら `--run --it`**。**常設スクリプトの回帰テストは全件 green**（`python -m pytest scripts/tests -q` = 406件）で、`scripts/**` を変更するタスクはこれを退行検出の網に使う。

**レビュー指摘の「既存テストへの影響」は鵜呑みにしない**（反映①②の学び）。**着手前に対象を `grep` して読み手を数える**（指摘どおり直すと既存の分岐一覧が代替なしで落ちることがある）。

**複数セッションにまたがる申し送りの正は [carryover_notes.md](carryover_notes.md)**（§1 Java 移行 / §2 仕様・マスターデータ / §3 環境・ツール）。着手前にそちらも見る。**恒久的な知見は同ファイルに残さず規約・コマンド表の正へ移す**方針なので、Java 実装の流儀は [coding_standards_backend.md](../process/coding_standards_backend.md) の分冊、環境・コマンドは [commands.md](../../.claude/project/commands.md) が正。

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
/fix-specs Phase 5 設計図の指摘反映（ISSUE-701〜709）: diagrams-review の9件（高2 / 中6 / 低1）を図と仕様書へ反映する
完了条件: ①9件を修正案どおり直す（**701・702 は同じ「記録時点」を扱うので必ず同じ修正パスで直す**。**706・707 は `screen_transition/endgame.md` 67行目を共有する**のでマージして1回で直す） ②`python scripts/check_docs.py`・`check_doc_size.py` が違反0のまま ③`python scripts/check_schema_triple.py` が**「1 件の不一致」から違反0へ変わる**（709 の解消がそのまま判定に出る） ④main へ統合してコミットする
参照: [2026-08-11_133822.md](../reviews/diagrams-review/2026-08-11_133822.md)（**各節に修正案・該当行・関連ファイルまで書いてある**ので、まず9節を読む）。図 ↔ 仕様書の対応表は [basic-design.md](../../.claude/project/basic-design.md) §1
前提: main `0776fd8`。触るのは `docs/diagrams/`（`battle_flow/bossrush.md`・`api_sequence/endgame.md`・`screen_transition/{endgame,main_nav}.md`・`er_diagram/battle.md`）と `docs/design/systems/{endgame,ui}.md`・`docs/tech/basic/{tech_api.md,tech_db/{battle,auth}.md}`・`docs/tech/detail/tech_state.md`。**worktree `p5-diagrams-fix` を作って作業する**（`python scripts/worktree.py add p5-diagrams-fix`）。**Java 移行と独立**で backend のコードには触らない（709 は `tech_db/auth.md` の「実装予定:」行を実装済みクラスへ合わせるだけ）。`check_schema_triple.py` が現在「1 件の不一致」で返ることは実行確認済み。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B テストリスト作成①（Phase 1: game / battle）**。tick・戦闘サービスが先に要るため tower は後段 | なし（3-A-3 完了・§1 とは領域が重ならないので並行可） | `3b-testlist-battle`<br>backend | `test-list` |
| 2 | **3-B 製造①（Phase 1: game / battle）** | キュー1 | `3b-dev-battle`<br>backend | `dev` |
| 3 | **3-B テストリスト作成②（Phase 1: tower）**。分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件 | キュー2 | `3b-testlist-tower`<br>backend | `test-list` |
| 4 | **3-B 製造②（Phase 1: tower）** | キュー3 | `3b-dev-tower`<br>backend | `dev` |

- **仕様書の指摘を直したら、それを検証対象に持つ図まで同じパスで直す**（§1 の発生原因。仕様書だけ直した結果、doc-review の ISSUE-1301・1303・1308 が図の指摘5件に化けた）。還元案（`fix-specs` への組み込み・Mermaid チェッカーの常設化）は diagrams-review レポート末尾が正
- **次のプロセス還元候補は [2026-08-10_155729.md](../reviews/backend-review/2026-08-10_155729.md)「プロセスへの還元」1〜3**（①判定13 を pom の依存と Repository の戻り値まで広げる ②分岐一覧へ「同時実行」の行を立てる ③`--suppressed` で規約例外を一覧化）。前回ゲートの還元①②は判定12・13 として実装済みで効いている
- **更新系 SQL の条件は実DBテストでしか検証されない**。`AND used = FALSE` のような条件を足したら、Repository をモックするサービス単体テストでは素通りするため、`RepositoryTestSupport` を継承したリポジトリ統合テストを同じコミットで足す
- **分岐一覧に行を足すときは末尾へ追加する**。途中へ挿すと後続番号が動き、テストの `分岐:` マーカーを全部書き替えることになる
- **統合テストでフィクスチャを直接書き換えるときは `WebIntegrationTestSupport#updateFixture` を通す**（`dataSource` が `defaultAutoCommit = false` のため、素の `jdbcTemplate.update` は更新件数が返るのに値が残らない）
- **判定13（`--unused`）の現在値は WARN 13件**（`AuthSettings` 3・`GameSettings` 9・`LogKey.TOKEN`）。ゼロを強制せず**この件数からの増減だけ見る**
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻すのは**テストリスト作成1件のみ**（詳細設計は完了済み。内訳は [carryover_notes.md](carryover_notes.md) §2）
