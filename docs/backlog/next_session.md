# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-11 / main `53ba169`（**Service・Resource を業務領域のサブパッケージへ再編**。`domain.service.{auth,health,player,battle}` / `web.resource.{auth,common,health}` へ移した（**Red 10クラスは `service/battle/` にある**＝製造①の実装もそこへ置く）。分ける／分けないの判断表は [common.md](../process/coding_standards_backend/common.md) §2.1 が正で、`model`・`repository`・`masterdata`・`api` は平置きのまま。**AOP 境界ログのポイントカットを `.*.` → `..*.`（配下）へ広げてある** — `.*.` はサブパッケージに一致せず、戻すと境界ログがテストに検出されないまま消える。`mvn compile` 全モジュール成功・`afkgame-web` 単体85/結合27件 green・`test-compile` の Red は移動前後とも同一の66件）。1つ前は main `925f437`（**`/doc-size` 残量WARN 4件の是正**。**仕様書のパスが変わった** — `tech_api.md` → 索引 + `tech_api/{core,auth,gameplay,character,base,endgame}.md`、`tech_db/player.md` から `tech_db/progression.md` を分離、`tech_auth.md` §8 → `tech_auth/init.md`（§6 は `tech_db/auth.md` と重複のため削除）、`tech_shop.md` → 索引 + `tech_shop/{lineup,buy}.md`。**各索引から辿ること**。Javadoc・マスターYAML・分岐マーカー計70箇所を追随済みで、常設スクリプト5本が green）。1つ前は main `33abd77`（**3-B 分岐一覧の差し戻し4件**。`--tests` exit 0・WARN 0＝分岐一覧45件、`mvn test-compile` は未実装シンボル66件で失敗＝**期待どおりの Red**。詳細は [changelog.md](../changelog.md) の 2026-08-11 ブロック）。

**Java 移行の残りは 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)、STEP の定義は [steps.md](java_migration/steps.md)「STEP 3〜5」）。**3-B は詳細設計とテストリスト作成①（game / battle）まで完了済み**で、残るのは製造① → テストリスト作成②（tower）→ 製造②。**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（backend に実在するのは `RandomFactory` のみ＝実測。E2E はハーネスと `GET /health` まで疎通済みで、テスト本体は STEP 5 完了まで赤が正常）。

**3-B（game / battle）のテストリストは完了**（`tech_tick.md` §5 12 + `tech_offline.md` §5 15 + **§6 10** + `tech_battle.md` §5 5 + `tech_rng.md` §5 9 + `tech_numeric.md` §5 14 = **65行が全行 Red 対応済み**＝ `--tests` exit 0 で実測）。**製造①は3セッションに割ってある**（§1・キュー1・2）。1セッションに詰めない理由は [test-list.md](../../.claude/project/test-list.md) §7 —— Maven はテストソースを一括コンパイルするため、表層が1つでも欠けると `mvn test` はテスト実行前に止まる。**先に表層だけを入れて `test-compile` を通し**（§1）、以降は `-Dtest=<クラス>` で領域ごとに Green を取る。

**分岐一覧へ行を足すセッションは、同じセッションで対応する Red まで足す**（①-b で判明）。マーカーが付いている一覧に行だけ足すと `check_branch_list.py --tests` が「行 #N に対応するテストがない」で ERROR になり、次セッションの着手判定が止まる。**行は必ず末尾へ追加する**（途中挿入は既存マーカーの番号を全部ずらす）。

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
/dev 3-B 製造①-i（表層の実装）: テストが要求するインタフェース・record・enum を作り `mvn test-compile` を通す
完了条件: ①`afkgame-domain` の `mvn test-compile` が exit 0（ロジックは未実装でよく、`mvn test` はアサーション失敗の Red が正常） ②表層は**各テストクラスの Javadoc「製造工程への申し送り」が正**で、そこに無い名前を新設しない ③対象は `BattleService`・`BattleSimulator`・`OfflineCalculator`・`LapAnalyzer`・`FloorCatalog`・`DamageCalculator`(+`DamageDirection`)・`TargetSelector`・`EncounterSelector`(+`EncounterEntry`)・`StatCalculator`・`HealingCalculator`・`CharacterGrowth`・`FloorProgression`・`Enemies`(+`EnemyData`)・`TickResult`・`BattleOutcome`・`OfflineSummary`・`LapAnalysis` と `LoggerName.BATTLE`・`LogReason.CLOCK_SKEW`・`PlayerRepository#findByIdForUpdate`/`#updateTickState`（実測: 未実装シンボル66件） ④`check_java_conventions.py` 違反0・WARN 13で横ばい ⑤main へ統合してコミットする
参照: `BattleServiceImplTest`・`OfflineCalculatorImplTest`・`LapAnalyzerImplTest`・`BattleSimulatorImplTest`・`DamageCalculatorImplTest`・`EncounterSelectorImplTest` ほかの Javadoc「製造工程への申し送り」（表層の正）。配置規約は [coding_standards_backend.md](../process/coding_standards_backend.md) の分冊。**置き場は `com.afkgame.domain.service.battle`**（Red がそこにあるので同じパッケージへ作れば import 不要。規約は [common.md](../process/coding_standards_backend/common.md) §2.1）。`FloorCatalog`・`FloorProgression` だけ `battle/` と `tower/` のどちらへ置くかを製造②の分割に合わせて決める
前提: main `53ba169`。**Maven 3.9.11 + JDK 17.0.20（Temurin）を新規シェルで `mvn -version` により実行確認済み**。`mvn verify` に Docker は不要。**クリティカル率の供給元（Phase 1 の基礎5%）は未定**で、決めるのは Green を取る回（キュー1）でよい（`tech_rng.md` §6 が「プレイヤー・敵で共通の定数にしない」を求める）。**worktree `3b-dev-surface` を作って作業する**（`python scripts/worktree.py add 3b-dev-surface`）。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（選んだ時点で着手可否を判断できるように）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B 製造①-ii（戦闘計算の Green）**。`DamageCalculator`・`TargetSelector`・`StatCalculator`・`HealingCalculator`・`EncounterSelector`・`CharacterGrowth` | §1（表層） | `3b-dev-battlecalc`<br>backend | `dev` |
| 2 | **3-B 製造①-iii（tick・オフラインの Green）**。`BattleService`・`BattleSimulator`・`OfflineCalculator`・`LapAnalyzer`・`FloorCatalog` | キュー1 | `3b-dev-tick`<br>backend | `dev` |
| 3 | **3-B テストリスト作成②（Phase 1: tower）**。分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件 | キュー2 | `3b-testlist-tower`<br>backend | `test-list` |
| 4 | **3-B 製造②（Phase 1: tower）** | キュー3 | `3b-dev-tower`<br>backend | `dev` |
| 5 | **diagrams-review 還元案3件の適用**（①`fix-specs` へ図への波及チェック ②`scripts/check_mermaid.py` の常設化 ③列の「備考」文言を三者一致の照合対象へ）。正は [2026-08-11_133822.md](../reviews/diagrams-review/2026-08-11_133822.md) 末尾 | なし（`docs`・`scripts` 領域。backend と重ならず並行可） | `dr-feedback`<br>docs/scripts | `retro` |

- **仕様書の指摘を直したら、それを検証対象に持つ図まで同じパスで直す**（キュー5 の還元案①が常設化。仕様書だけ直し ISSUE-1301・1303・1308 が図の指摘5件に化けた）
- **backend-review の還元候補3件の正は [2026-08-10_155729.md](../reviews/backend-review/2026-08-10_155729.md)「プロセスへの還元」**
- **`tech_polling.md` §5 の10件は `integration-test`（E2E）担当**（①-a で JUnit 対象外と判断）。tick API が要るため製造①（§1・キュー1・2）の後
- **マーカー0件＝照合対象外の一覧2件**: `tech_offline.md` §7（12行・Phase 3 の製造で展開）と `tech_numeric.md` §6（2件・`PUT /api/game/settings` の Resource を作る回）
- **分岐一覧の旧形式は残り2件**（`tech_polling.md` §5・`tech_rng.md` §5）。標準形式への移行は**1行が真偽の両方を持つ行の分割＝既存マーカーの番号ずれ**を伴うので、参照元のテストを触る回に同じセッションでまとめて行う
- **Phase 4 は Java 移行完了まで本キューから外している**（2026-08-09・ユーザー判断。戻す1件の正は `carryover_notes.md` §2）
