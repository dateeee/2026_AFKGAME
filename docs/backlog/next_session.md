# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-11 / main `fab4eef`（**3-B テストリスト作成①-b**＝ `tech_battle.md` §5 の5件・`tech_rng.md` §5 の8件・**再構成した** `tech_numeric.md` §5 の12件（計25行）を JUnit の Red へ展開した（7クラス13メソッド / 19マーカー）。`check_branch_list.py --tests` は exit 0 で**3文書とも全行にテストが対応**（5/5・8/8・12/12 を実測）、`mvn test` は未実装型の「シンボルを見つけられません」56件で失敗＝**期待どおりの Red**、`check_java_conventions.py` 違反0・WARN 13 で横ばい、`check_docs.py`／`check_doc_size.py` 違反0、プロダクトコードの変更なし。**`tech_numeric.md` §5 はユーザー判断で本工程内に再構成した**（旧10件 → §5「丸め・クランプ・飽和」12件 + §6「入力値の検証」2件。所持枠上限＝Phase 2〜（`tech_base.md` §8）と撤退HP閾値の入力検証＝セグメント②（`tech_tower/control.md` §12）は持ち主の一覧への参照注記に替えた）。詳細は [carryover_notes.md](carryover_notes.md) §1・§2）。1つ前は 2026-08-11 / main `4355f46`（**①-a**＝ `tech_tick.md` §5 の12件と `tech_offline.md` §5 の15件を24テストへ）。内訳は [changelog.md](../changelog.md) の 2026-08-11 ブロックが正。

**Java 移行の残りは 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)、STEP の定義は [steps.md](java_migration/steps.md)「STEP 3〜5」）。**3-B は詳細設計まで完了済み**で、残るのはテストリスト作成 → 製造。**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（backend に実在するのは `RandomFactory` のみ＝実測。E2E はハーネスと `GET /health` まで疎通済みで、テスト本体は STEP 5 完了まで赤が正常）。

**3-B（game / battle）のテストリストは①-a・①-b で完了**（`tech_tick.md` §5 12 + `tech_offline.md` §5 15 + `tech_battle.md` §5 5 + `tech_rng.md` §5 8 + `tech_numeric.md` §5 12 = **52行 / 37テストメソッド**。`tech_polling.md` §5 の10件は E2E へ回し、tower の55件 + `tech_state.md` §5 の7件は別セグメント）。次は**詳細設計の差し戻し → 製造①**。根拠は [test-list.md](../../.claude/project/test-list.md) §7「同一モジュールに Red を複数並べるなら Green も同じ単位でまとめて取る」（Maven はテストソースを一括コンパイルするため、未実装の型を参照する別の Red があると `mvn test` はテスト実行前に止まる）。

**分岐一覧へ行を足すセッションは、同じセッションで対応する Red まで足す**（①-b で判明）。5文書とも全行にマーカーが付いているため、行だけ足すと `check_branch_list.py --tests` が「行 #N に対応するテストがない」で ERROR になり、次セッションの着手判定が止まる。**行は必ず末尾へ追加する**（途中挿入は既存44マーカーの番号を全部ずらす）。

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
/detail-design 3-B 分岐一覧の差し戻し4件（tick / offline / numeric / rng）: テストリスト工程で見つかった穴を分岐一覧へ足し、同じセッションで対応する Red まで書く
完了条件: ①`tech_offline.md` §5 の末尾へ §4.1 の期待値計算式の行を追加（`base_hit`・`crit_factor`・`skill_factor`・`E_taken`・撃破ターン数・消費数/周回。範囲攻撃 `×0.7×敵数`・攻撃スキル2枠は高い倍率のみ・被ダメ軽減の上限80%・挑発の按分が未カバー） ②`tech_tick.md` §5 #10（パーティが空）の `last_tick_at` を進めるか確定して行へ明記し、`BattleServiceImplTest` の該当テストへアサートを足す ③`tech_numeric.md` §5 の末尾へ「回復量の下限1」の行を追加（§2 の丸め規則に対応） ④`tech_rng.md` §5 の末尾へターゲット抽選の正常系（生存者から1体選ぶ）の行を追加 ⑤①③④で足した行の Red を同じセッションで書く（`LapAnalyzerImplTest` を新規・`HealingCalculatorImplTest`・`TargetSelectorImplTest` へ追記） ⑥`python scripts/check_branch_list.py --tests` が exit 0・WARN 0 ⑦main へ統合してコミットする
参照: [tech_offline.md](../tech/detail/tech_offline.md) §4.1・§5、[tech_tick.md](../tech/detail/tech_tick.md) §5、[tech_numeric.md](../tech/detail/tech_numeric.md) §2・§5、[tech_rng.md](../tech/detail/tech_rng.md) §5。表層の正は `OfflineCalculatorImplTest`（`LapAnalyzer`）・`HealingCalculatorImplTest`・`TargetSelectorImplTest` の Javadoc「製造工程への申し送り」
前提: main `fab4eef`。**Maven 3.9.11 + JDK 17.0.20（Temurin）を新規シェルで `mvn -version` により実行確認済み**（PATH 反映済み）。`mvn verify` に Docker は不要。**行は必ず末尾へ追加する**（途中挿入で既存44マーカーの番号がずれる）。**`afkgame-domain` のテストは製造①まで test-compile が通らない**ので Red の確認は「未実装型のシンボル未検出で止まる」ところまで（`test-list.md` §7）。**クリティカル率の供給元（Phase 1 の基礎5%）の決定は製造①へ回してよい**（`carryover_notes.md` §1）。**worktree `3b-branchrows` を作って作業する**（`python scripts/worktree.py add 3b-branchrows`）。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（選んだ時点で着手可否を判断できるように）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B 製造①（Phase 1: game / battle）**。①-a・①-b の Red（37メソッド）をまとめて Green にする | §1（差し戻し4件） | `3b-dev-battle`<br>backend | `dev` |
| 2 | **3-B テストリスト作成②（Phase 1: tower）**。分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件 | キュー1 | `3b-testlist-tower`<br>backend | `test-list` |
| 3 | **3-B 製造②（Phase 1: tower）** | キュー2 | `3b-dev-tower`<br>backend | `dev` |
| 4 | **diagrams-review 還元案3件の適用**（①`fix-specs` へ図への波及チェック ②`scripts/check_mermaid.py` の常設化 ③列の「備考」文言を三者一致の照合対象へ）。正は [2026-08-11_133822.md](../reviews/diagrams-review/2026-08-11_133822.md) 末尾 | なし（`docs/`・`scripts/` 領域。backend と重ならず並行可） | `dr-feedback`<br>docs/scripts | `retro` |
| 5 | **`carryover_notes.md` の圧縮**（7,964/8,000・**残り36字**。消化済み行の削除と、恒久的な知見の正への移設） | なし。**worktree を作らず main で行う**（`merge=union` は削除を伝播しないため） | —<br>docs | `doc-size` |

- **仕様書の指摘を直したら、それを検証対象に持つ図まで同じパスで直す**（キュー4 の還元案①が常設化。仕様書だけ直し ISSUE-1301・1303・1308 が図の指摘5件に化けた）
- **backend-review の還元候補3件の正は [2026-08-10_155729.md](../reviews/backend-review/2026-08-10_155729.md)「プロセスへの還元」**
- **更新系 SQL の条件は実DBテストでしか検証されない**（サービス単体テストはモックで素通りする）。`AND used = FALSE` のような条件を足したら `RepositoryTestSupport` 継承の統合テストを同じコミットで足す
- **`tech_polling.md` §5 の10件は `integration-test`（E2E）担当**（①-a で JUnit 対象外と判断）。tick API が要るためキュー1（製造①）の後。空いたら行へ戻す（`carryover_notes.md` §1）
- **`tech_numeric.md` §6（入力値の検証）2件はマーカー0件＝照合対象外**。`PUT /api/game/settings` の Resource を作る回（キュー2〜3の周辺）で消化する
- **統合テストでフィクスチャを直接書き換えるときは `WebIntegrationTestSupport#updateFixture` を通す**（`dataSource` が `defaultAutoCommit = false` のため、素の `jdbcTemplate.update` は更新件数が返るのに値が残らない）
- **判定13（`--unused`）の現在値は WARN 13件**（`AuthSettings` 3・`GameSettings` 9・`LogKey.TOKEN`）。ゼロを強制せず**増減だけ見る**
- **Phase 4 は Java 移行完了まで本キューから外している**（2026-08-09・ユーザー判断）。再開時に戻すのはテストリスト作成1件のみ（`carryover_notes.md` §2）
