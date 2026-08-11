# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-11 / main `2d62982`（**Phase 5 設計図の指摘反映**＝ `diagrams-review` の ISSUE-701〜709 の9件（高2 / 中6 / 低1）を図と仕様書へ適用し、**Phase 5 の設計整合ゲートが閉じた**。`check_schema_triple.py` は**1件の不一致 → 差分なし（exit 0）**、`check_docs.py`・`check_doc_size.py` とも違反0（残量WARN 13で横ばい）、Mermaid は実パーサ（mermaid v11）で全6ブロック parse 成功。検証サブエージェントが見つけた食い違い1件＝深淵ランキングの表示項目に対しレスポンス例へ `reachedAt` が無かった点も同時に直した）。1つ前は 2026-08-11 / main `0776fd8`（**3-A-3 レビュー指摘の反映②**＝ 製造完了ゲートが閉じ、Java 移行は 3-B へ進める。`report_java_tests.py --run --it` は**単体306件 + 結合85件 Green・C1 100%**）。内訳は [changelog.md](../changelog.md) の 2026-08-11 ブロックが正。

**Java 移行の残りは 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)、STEP の定義は [steps.md](java_migration/steps.md)「STEP 3〜5」）。**3-B は詳細設計まで完了済み**で、残るのはテストリスト作成 → 製造。**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（backend に実在するのは `RandomFactory` のみ＝実測。E2E はハーネスと `GET /health` まで疎通済みで、テスト本体は STEP 5 完了まで赤が正常）。

**3-B（game / battle）の分岐一覧は6文書60件**（`tech_tick.md` §5 12 / `tech_polling.md` §5 10 / `tech_offline.md` §5 15 / `tech_battle.md` §5 5 / `tech_rng.md` §5 8 / `tech_numeric.md` §5 10。tower の55件 + `tech_state.md` §5 の7件は別セグメント）。1セッションに収まらないため **Green を取れる単位で ①-a（tick 系37件）と ①-b（戦闘計算系23件）へ割った**。根拠は [test-list.md](../../.claude/project/test-list.md) §7「同一モジュールに Red を複数並べるなら Green も同じ単位でまとめて取る」（Maven はテストソースを一括コンパイルするため、未実装の型を参照する別の Red があると `mvn test` はテスト実行前に止まる）。

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
/test-list 3-B テストリスト作成①-a（Phase 1: game — tick / polling / offline）: 分岐一覧37件を失敗するJUnitテストへ展開する
完了条件: ①`tech_tick.md` §5 の12件・`tech_polling.md` §5 の10件・`tech_offline.md` §5 の15件をすべてテスト化し `分岐: <ファイル> §5 #N` マーカーで対応付ける ②`python scripts/check_branch_list.py --tests` が exit 0（**現在は3文書ともマーカー0件＝照合対象外**なので、追加した分がそのまま判定に出る） ③対象クラスを限定した `mvn test -Dtest=...` で**期待どおり失敗する**ことを確認する（Red。全体 Green の確認は製造工程） ④main へ統合してコミットする
参照: [tech_tick.md](../tech/detail/tech_tick.md) §5・§6、[tech_polling.md](../tech/detail/tech_polling.md) §5、[tech_offline.md](../tech/detail/tech_offline.md) §5。記述規約の正は [coding_standards_backend/test.md](../process/coding_standards_backend/test.md)、実例は [test-patterns.md](../../.claude/project/test-patterns.md)
前提: main `2d62982`。**Maven 3.9.11 + JDK 17.0.20（Temurin）を新規シェルで `mvn -version` により実行確認済み**（PATH 反映済み）。`mvn verify` に Docker は不要。**`tech_tick.md` §6・`tech_rng.md` §6「Java 実装時に満たすこと」は移植時に満たして節ごと削除する**（`steps.md`「STEP 3〜5」）。tick/polling/offline のサービスは backend に未実装のため、表層が分岐一覧に無ければ [tech_backend.md](../tech/basic/tech_backend.md) §4.1 の service 一覧を見て、そこにも無ければ docstring で表層を定義して製造へ申し送る（`test-list.md` §3。コード側を読み回して推測しない）。**worktree `3b-testlist-game` を作って作業する**（`python scripts/worktree.py add 3b-testlist-game`）。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（選んだ時点で着手可否を判断できるように）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B テストリスト作成①-b（Phase 1: battle — battle / rng / numeric）**。分岐一覧23件（`tech_battle.md` §5 5 + `tech_rng.md` §5 8 + `tech_numeric.md` §5 10） | §1（①-a）。同一モジュールへ Red を積むため §1 とは並行しない | `3b-testlist-battle`<br>backend | `test-list` |
| 2 | **3-B 製造①（Phase 1: game / battle）**。①-a・①-b の Red をまとめて Green にする | キュー1 | `3b-dev-battle`<br>backend | `dev` |
| 3 | **3-B テストリスト作成②（Phase 1: tower）**。分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件 | キュー2 | `3b-testlist-tower`<br>backend | `test-list` |
| 4 | **3-B 製造②（Phase 1: tower）** | キュー3 | `3b-dev-tower`<br>backend | `dev` |
| 5 | **diagrams-review 還元案3件の適用**（①`fix-specs` へ図への波及チェック ②`scripts/check_mermaid.py` の常設化 ③列の「備考」文言を三者一致の照合対象へ）。正は [2026-08-11_133822.md](../reviews/diagrams-review/2026-08-11_133822.md) 末尾 | なし（`docs/`・`scripts/` 領域。backend と重ならず並行可） | `dr-feedback`<br>docs/scripts | `retro` |

- **仕様書の指摘を直したら、それを検証対象に持つ図まで同じパスで直す**（キュー5 の還元案①がこれの常設化。仕様書だけ直した結果、doc-review の ISSUE-1301・1303・1308 が図の指摘5件に化けた）
- **backend-review 側の還元候補は [2026-08-10_155729.md](../reviews/backend-review/2026-08-10_155729.md)「プロセスへの還元」1〜3**（①判定13 を pom と Repository の戻り値へ広げる ②分岐一覧へ「同時実行」の行 ③`--suppressed` で規約例外を一覧化）
- **更新系 SQL の条件は実DBテストでしか検証されない**。`AND used = FALSE` のような条件を足したら、Repository をモックするサービス単体テストでは素通りするため、`RepositoryTestSupport` を継承したリポジトリ統合テストを同じコミットで足す
- **分岐一覧に行を足すときは末尾へ追加する**。途中へ挿すと後続番号が動き、テストの `分岐:` マーカーを全部書き替えることになる
- **統合テストでフィクスチャを直接書き換えるときは `WebIntegrationTestSupport#updateFixture` を通す**（`dataSource` が `defaultAutoCommit = false` のため、素の `jdbcTemplate.update` は更新件数が返るのに値が残らない）
- **判定13（`--unused`）の現在値は WARN 13件**（`AuthSettings` 3・`GameSettings` 9・`LogKey.TOKEN`）。ゼロを強制せず**増減だけ見る**
- **Phase 4 は Java 移行が終わるまで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻すのは**テストリスト作成1件のみ**（詳細設計は完了済み。内訳は `carryover_notes.md` §2）
