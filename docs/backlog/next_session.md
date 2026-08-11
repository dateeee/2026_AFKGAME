# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-12 / main `5d77dce`（**3-B 製造①-iii：tick・ターン処理の Green**。`BattleSimulatorImpl`・`BattleServiceImpl` を実装して**対象21件を Green** にし、`PlayerRepository` のロック読み・tick状態更新と左右のクリティカル率列を配線した。詳細は [changelog.md](../changelog.md) の 2026-08-12 ブロック）。1つ前は main `f04537f`（製造①-ii：戦闘計算6クラスの Green）、その前は `87e50f3`（製造①-i：表層27ファイル）。

**製造①-iii が残した前提**（①-iv・セグメント②が上に積む）。

| # | 事実 |
|---|------|
| 1 | **Green 済みは戦闘計算6クラス + `BattleSimulator`・`BattleService`**。本体が `UnsupportedOperationException` のまま残るのは `LapAnalyzer`・`OfflineCalculator` の2クラスだけ |
| 2 | **`@Service` を付けられるのはセグメント②**。`BattleSimulator` は `FloorProgression`・`Enemies`、`LapAnalyzer` は `FloorCatalog` を注入するため、実体が無いままスキャンに載せると**結合テストのコンテキスト起動が壊れる**。①-iv でも付けない（`@Transactional` は `BattleServiceImpl` へ付与済み） |
| 3 | **`CharacterGrowth#applyLevelUp` は未実装**で、`addExp` も上限判定と加算だけを持つ。到達側の分岐が一覧に無く、ステータス再計算に要る成長率も `character_types.yml` が未搭載のため。分岐の正は `tech_party.md` §6（SP獲得3件・マーカー無し）＝**テストリスト工程が要る** |
| 4 | **クリティカル率は配線済み**（味方 `character_types.yml`・敵 `EnemyData` の `critRate` 列。値は基礎5%）。[tech_rng.md](../tech/detail/tech_rng.md) §6 は「満たすこと」から**供給元を宣言する恒久の節へ書き換え済み**。敵の `enemies.yml` はセグメント②で載せる |
| 5 | **`check_java_conventions.py` は 違反0 / WARN 7 が現在の正常値**（13 → −6 は `CLOCK_SKEW` と `GameSettings` の5アクセサに読み手が付いたため）。ゼロを強制せず増減だけ見る |
| 6 | 表層の正は各テストクラスの Javadoc「製造工程への申し送り」。**そこに無い名前を新設しない** |
| 7 | **main に別セッションの未コミット変更が1件ある**: `scripts/report_java_tests.py`（+142/−38・本タスクとは無関係）。①-iii の統合時に既に在り、`worktree.py merge` がこれで止まったため**手動で `git merge --ff-only` して統合した**。**触らず、正体をユーザーへ確認してから扱う** |

**Java 移行の残りは 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)、STEP の定義は [steps.md](java_migration/steps.md)「STEP 3〜5」）。**3-B は詳細設計・テストリスト作成①（game / battle）・製造①-i〜①-iii まで完了**で、残るのは製造①-iv → テストリスト作成②（tower）→ 製造②。**Phase 1〜3 の機能はどの言語でも未実装の期間**（E2E はハーネスと `GET /health` まで疎通済みで、テスト本体は STEP 5 完了まで赤が正常）。

**Red の現況**: 単体378件のうち **Red 22件は `LapAnalyzer`（10）・`OfflineCalculator`（12）の2クラスだけ**（43 → 22。surefire XML の実測）。既存の単体テストと**結合テスト88件は全件 green**（85 + ①-iii が足した `PlayerRepositoryTest` 3件）で、**C1 は 100%（250/250・未達0）**。`-Dtest=<クラス>` で領域ごとに Green を取っていく。

**`report_java_tests.py --run` は既定で `-DskipITs`**（結合テストまで見るなら `--run --it`）。**Red がある状態で結合テストまで通すなら `mvn verify -Dmaven.test.failure.ignore=true`**（surefire の失敗で止まると failsafe が走らず「結合テストの退行が見えない」。**PowerShell では `-D...=...` を引用符で囲む** — 囲まないと引数が割れて `Unknown lifecycle phase` で即死する）。**常設スクリプトの回帰テストは全件 green**（`python -m pytest scripts/tests -q` = 406件）で、`scripts/**` を変更するタスクはこれを退行検出の網に使う。

**分岐一覧へ行を足すセッションは、同じセッションで対応する Red まで足す**（①-b で判明）。マーカーが付いている一覧に行だけ足すと `check_branch_list.py --tests` が「行 #N に対応するテストがない」で ERROR になり、次セッションの着手判定が止まる。**行は必ず末尾へ追加する**（途中挿入は既存マーカーの番号を全部ずらす）。

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
/dev 3-B 製造①-iv（簡略計算の Green）: `LapAnalyzer` と `OfflineCalculator` を実装して Green にする
完了条件: ①`LapAnalyzerImplTest`（10件）・`OfflineCalculatorImplTest`（12件）が全件 Green（`-Dtest=<クラス> -Dsurefire.failIfNoSpecifiedTests=false`）＝**単体378件の Red が0になる** ②`tech_tick.md` §6 に残る「簡略計算」1行を満たしたら**節ごと削除する**（[steps.md](java_migration/steps.md) の「移植時にあわせて処理するもの」） ③**`@Service` は付けない**（前提2） ④`mvn verify -Dmaven.test.failure.ignore=true` で**結合88件 green・C1 100%（未達0）** のままを確認する ⑤`check_java_conventions.py` 違反0・`check_branch_list.py --tests` WARN 0 ⑥main へ統合してコミットする
参照: 上記2テストクラスの Javadoc「製造工程への申し送り」（表層の正）。期待値計算式は [tech_offline.md](../tech/detail/tech_offline.md) §4・§4.1、分岐一覧は同 §5・§6（§7 は Phase 3 の製造で展開＝対象外）
前提: main `5d77dce`。**Maven 3.9.11 + JDK 17.0.20（Temurin）を新規シェルで `mvn -version` により実行確認済み**（`mvn verify` に Docker 不要。同セッションで実測）。**協調先はテストが全てモックする**ため実体は要らない（`FloorCatalog` はセグメント②）。**worktree `3b-dev-offline` を作って作業する**（`python scripts/worktree.py add 3b-dev-offline`）。`docs/backlog/open_specs.md` は不在＝未確定ゼロ。**main に残る未コミットの `scripts/report_java_tests.py` は別セッションのもの（前提7）＝触らない**
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（選んだ時点で着手可否を判断できるように）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B テストリスト作成②（Phase 1: tower）**。分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件 | §1（①-iv） | `3b-testlist-tower`<br>backend | `test-list` |
| 2 | **3-B 製造②（Phase 1: tower）**。`FloorCatalog`・`FloorProgression` の実装（`service/tower`）と `Enemies` のレジストリ化（`enemies.yml` + `@Component`。`critRate` 列を含む）。**Green 済みクラスへ `@Service` を付けるのもこの回**（前提2） | キュー1 | `3b-dev-tower`<br>backend | `dev` |
| 3 | **キャラ成長のテストリスト作成**（`applyLevelUp`・`addExp` のしきい値到達）。分岐は `tech_party.md` §6 の3件 + `tech_numeric.md` §5 へ足す到達側。成長率の列追加（`character_types.yml`・`CharacterTypeData`・`character.md` §1.2）を含む | 前提3 | `3b-testlist-growth`<br>backend | `test-list` |
| 4 | **diagrams-review 還元案3件の適用**（①`fix-specs` へ図への波及チェック ②`scripts/check_mermaid.py` の常設化 ③列の「備考」文言を三者一致の照合対象へ）。正は [2026-08-11_133822.md](../reviews/diagrams-review/2026-08-11_133822.md) 末尾 | なし（`docs`・`scripts` 領域。backend と重ならず並行可） | `dr-feedback`<br>docs/scripts | `retro` |

- **仕様書の指摘を直したら、それを検証対象に持つ図まで同じパスで直す**（キュー4 の還元案①が常設化。仕様書だけ直し ISSUE-1301・1303・1308 が図の指摘5件に化けた）
- **backend-review の還元候補3件の正は [2026-08-10_155729.md](../reviews/backend-review/2026-08-10_155729.md)「プロセスへの還元」**
- **`tech_polling.md` §5 の10件は `integration-test`（E2E）担当**（①-a で JUnit 対象外と判断）。**`BattleService` までは実装済みだが tick API（Controller・Resource）は未作成**なので、それを作る回の後
- **マーカー0件＝照合対象外の一覧2件**: `tech_offline.md` §7（12行・Phase 3 の製造で展開）と `tech_numeric.md` §6（2件・`PUT /api/game/settings` の Resource を作る回）
- **分岐一覧の旧形式は残り2件**（`tech_polling.md` §5・`tech_rng.md` §5）。標準形式への移行は**1行が真偽の両方を持つ行の分割＝既存マーカーの番号ずれ**を伴うので、参照元のテストを触る回に同じセッションでまとめて行う
- **Phase 4 は Java 移行完了まで本キューから外している**（2026-08-09・ユーザー判断。戻す1件の正は `carryover_notes.md` §2）
