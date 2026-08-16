# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-16 / main `2044150`（**3-B テストリスト作成②-c: tower の進行制御**。[control.md](../tech/detail/tech_tower/control.md) §12 の**分岐一覧10件を Red へ展開**した（`@ParameterizedTest` が割れて21ケース）。担当は②-a と同じく層で割り（入力検証は web の Resource テスト、状態依存は `TowerServiceImplTest`）、**#9・#10 は新テストを書かず②-b の `FloorProgressionImplTest` #18・#17 へマーカーを足した**。**`tech_state.md` §5（7件）は先送り**（理由は「いまの位置」）。**既存の参照は番号で追わず内容で照合する**（`known_issues.md` は欠番あり、`frontend/**` は ESLint / Prettier の一括整形で行内容の差分が広い）。**履歴の正は [changelog.md](../changelog.md) の 2026-08-16 ブロック**なので、本ファイルへ積み増さない。

**いまの位置**: Java 移行の残りは **3-B（Phase 1: tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)、STEP の定義は [steps.md](java_migration/steps.md)「STEP 3〜5」）。**3-B は詳細設計・テストリスト作成①（game / battle）・製造①（①-i〜①-iv）・キャラ成長の製造・テストリスト作成②-a（一覧・入塔）・②-b（階進行）・②-c（進行制御）まで完了**で、残るのは**②-d（全滅の後始末と不変条件）→ 製造②**。**Phase 1〜3 の機能はどの言語でも未実装の期間**（E2E はハーネスと `GET /health` まで疎通済みで、テスト本体は STEP 5 完了まで赤が正常）。

**②-d が要る理由は [carryover_notes.md](carryover_notes.md) §1 が正**（②-c で判明。`tech_state.md` §5 は Phase 1〜3 にまたがり部分展開できず、`onPartyWiped` に Red が無いまま残っている）。

**製造①が残した前提**（セグメント②が上に積む）。

| # | 事実 |
|---|------|
| 1 | **`@Service` を付けられるのはセグメント②**。`BattleSimulator` は `FloorProgression`・`Enemies`、`LapAnalyzer` は `FloorCatalog` を注入するため、実体が無いままスキャンに載せると**結合テストのコンテキスト起動が壊れる**（`@Transactional` は `BattleServiceImpl` へ付与済み。②-b で `FloorProgressionImpl` の器はできたが `@Service` は未付与） |
| 2 | **敵の `enemies.yml` はセグメント②で載せる**（`critRate` 列を含む。味方側 `character_types.yml` は配線済みで [tech_rng.md](../tech/detail/tech_rng.md) §6 が供給元の正） |
| 3 | **表層の正は各テストクラスの Javadoc「製造工程への申し送り」**（②-a の `TowerServiceImplTest`・②-b の `FloorProgressionImplTest`・②-c の `TowerModeResourceTest`／`TowerRetreatConditionsResourceTest`）。そこに無い名前を新設しない。②-b が足したのは `FloorProgressionImpl`・`TowerClearRecordRepository#save`/`#updateProgress`・`TowerData.modifiers` + `TowerModifier`、②-c が足したのは `TowerService#retire`/`#changeMode`/`#updateRetreatConditions`（いずれも戻り値なし）と `TowerModeResource`・`TowerRetreatConditionsResource`（`hpThreshold` は **`Double`**。`double` だと欠落が `0.0` に化けて撤退無効化と区別できない） |

**現況の実測値**（2026-08-16）。

| 対象 | 値・見方 |
|------|---------|
| Java テスト | 単体**475件**（うち **Red 81件** ＝ ②-a 33 + ②-b 27 + ②-c 21。domain 62・web 19）・結合**88件 green**（domain 61・web 27）・**C1 の未達0**（全モジュール `All coverage checks have been met`） |
| Red が無い回 | `python scripts/report_java_tests.py --run` をそのまま使える（既定は `-DskipITs`。結合まで見るなら `--run --it`） |
| **Red を作る回**（テストリスト工程） | `mvn verify "-Dmaven.test.failure.ignore=true"` で見る。surefire の失敗で止まると failsafe が走らず結合の退行が見えないため。**PowerShell では `-D...=...` を引用符で囲む**（囲まないと引数が割れて `Unknown lifecycle phase` で即死する） |
| ビルド環境 | Maven 3.9.11 / Temurin **17.0.20**。**新規シェルで `mvn` をそのまま叩ける**（`mvn -version` で実測。フルパスは carryover §3） |
| 常設スクリプト | `python -m pytest scripts/tests -q` = 509件 green（`.claude/**` まで含めると602件）。`scripts/**` を変更する回の退行検出に使う |
| Java 規約 | `check_java_conventions.py` は **違反0 / WARN 6 が正常値**。ゼロを強制せず増減だけ見る（内訳は carryover §3。新しい依存を足す回は `DEPENDENCY_IMPORTS` への登録が要る） |
| Mermaid | `python scripts/check_mermaid.py` が常設（使い捨てで書き直さない） |
| 未確定仕様 | [open_specs.md](open_specs.md) に**1件**（#1 Phase 1〜2 の計画メンテナンス告知手段。`f30cdc7` で登録） |

**複数セッションにまたがる申し送りの正は [carryover_notes.md](carryover_notes.md)**（§1 Java 移行 / §2 仕様・マスターデータ / §3 環境・ツール）。着手前にそちらも見る。**恒久的な知見は同ファイルに残さず正へ移す**方針で、Java 実装の流儀は [coding_standards_backend.md](../process/coding_standards_backend.md)、環境・コマンドは [commands.md](../../.claude/project/commands.md) が正。

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
/test-list 3-B テストリスト作成②-d（Phase 1: 全滅の後始末と不変条件）: tech_state.md §5 を Phase 別に分けてから Phase 1 分を Red へ展開する
完了条件: ①[tech_state.md](../tech/detail/tech_state.md) §5 を **Phase 1 分**（全滅ペナルティのゴールド・EXP・HP と不変条件違反＝現 #3・#5・#6・#7）と **Phase 2〜3 分**（現 #4 装備の没収・#1・#2 編成ロック）へ分け、後者を別節へ移す。**同節はマーカー未参照なので今なら番号を振り直せる**（`WARN許容` の注記も移動先へ追随させる） ②Phase 1 分**4件**を Red のテストへ展開する。全滅ペナルティ3件は `FloorProgressionImpl#onPartyWiped`（②-b が担当を明記済み。依存を足すのもこの回）、不変条件違反1件は読み取り時の検証で、**ERRORログは `ListAppender<ILoggingEvent>` で見る**（[test.md](../process/coding_standards_backend/test.md) §5 新規 #1） ③表層は**テストクラスの Javadoc「製造工程への申し送り」へ書き**、本体は `UnsupportedOperationException` に留める（`@Service` は付けない＝前提1）。**別名を新設しない**＝前提3 ④`python scripts/check_branch_list.py --tests` 違反0・WARN 0 ⑤`mvn verify "-Dmaven.test.failure.ignore=true"` で **Red が4分岐ぶん増え、結合88件 green・C1 の未達0** のままを確認する（`@ParameterizedTest` はケース単位で数えるため件数は分岐数より多くなる。②-a 24行→33件、②-b 21→27、②-c 10→21。Red がある回の見方は前文） ⑥`check_java_conventions.py` 違反0・WARN 6 据え置き ⑦main へ統合してコミットする
参照: [tech_state.md](../tech/detail/tech_state.md) §1.1（不変条件）・§3（探索セッションと全滅ペナルティの適用順序6手順）・§5（分岐一覧）。書き方と申し送りの手本は②-b の `FloorProgressionImplTest` の Javadoc、置き場は `domain.service.tower`（[common.md](../process/coding_standards_backend/common.md) §2.1）。**`runItems` / `runEquipmentIds` は `Player` に列が無い**ため、Phase 1 の没収対象は `runGold` と EXP だけになる（②-b の Javadoc が正）
前提: main `2044150`。ビルド環境・テストの実測値・常設チェックは前文の表が正（`JAVA_HOME` の切り替えは不要）。**worktree `3b-testlist-tower-d` を作って作業する**（`python scripts/worktree.py add 3b-testlist-tower-d`）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（選んだ時点で着手可否を判断できるように）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B 製造②（Phase 1: tower）**。`TowerServiceImpl`・`FloorCatalog`・`FloorProgressionImpl` の実装（`service/tower`）と `Enemies`・`Towers` のレジストリ化（`enemies.yml`・`towers.yml` + `@Component`）。**Green 済みクラスへ `@Service` を付けるのもこの回**（前提1）。**塔IDの表記ゆれ**（テストの `tower_goblin` / `tech_data.md` §1.4 の `goblin_tower` / `endgame.md` の `abyss_tower`）は `towers.yml` を書くこの回でそろえ、既存テストの定数も同じ回で追随させる。着手時に規模を見てセグメントへ割る | §1（②-d）。**`onPartyWiped` は②-d が Red を用意するまで実装できない** | `3b-dev-tower`<br>backend | `dev` |
| 2 | **基本設計 spot-review（[2026-08-16_193802](../reviews/spot-review/2026-08-16_193802.md)）の指摘6件を反映**（高1・中1・要検討3・低1）。**別セッションが並行して出したレポート**で反映は未着手。**ISSUE-1505（`run_gold` → `gold` の反映契機が未定義）は②-d の射程**なので、先に確定させると手戻りが減る | なし | `fixspecs-basic`<br>docs | `fix-specs` |

- **分岐一覧の旧形式は残り2件**（`tech_polling.md` §5・`tech_rng.md` §5）。標準形式への移行は**1行が真偽の両方を持つ行の分割＝既存マーカーの番号ずれ**を伴うので、参照元のテストを触る回に同じセッションでまとめて行う（[detail-design.md](../../.claude/project/detail-design.md) §4 が「残件は候補キューで追跡する」と定める分）
- **`tech_polling.md` §5 の10件は tick API（Controller・Resource）を作る回の後**に `integration-test`（E2E）で消化する（判断根拠と照合対象外の扱いは carryover §1）
- **マーカー0件の一覧2件（照合対象外）と Phase 4 の除外は [carryover_notes.md](carryover_notes.md) §2 が正**
- **本ファイルは残量WARN**（台帳 11 → 12件）。前文が伸びる回は書く前に carryover へ逃がす
