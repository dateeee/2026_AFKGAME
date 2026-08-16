# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-16 / main `fbcbe8d`（**3-B 製造②-i: tower のマスターデータ**。`towers.yml`（`goblin_tower` 1件）・`enemies.yml`（敵9種・`critRate` 0.05）を載せて `Towers`・`Enemies` を `@Component` 化した。**塔IDの表記は「テーマ名 + `_tower`」に確定**（仕様書・設計図・フロントの10箇所が既に一貫していたので、割れていたテスト側8ファイル16箇所を追随させた）。**`floorEncounters` は載せていない** — `TowerData` に列が無く `FAIL_ON_UNKNOWN_PROPERTIES` で起動が落ちるため、`FloorCatalog` を作る②-iii が列と一緒に足す。**履歴の正は [changelog.md](../changelog.md) の 2026-08-16 ブロック**なので、本ファイルへ積み増さない。

**いまの位置**: Java 移行の残りは **3-B（Phase 1: tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)、STEP の定義は [steps.md](java_migration/steps.md)「STEP 3〜5」）。**3-B は製造②-i（マスターデータ）まで完了**し、残るのは**製造②-ii・②-iii**（§1・§2）。**Phase 1〜3 の機能はどの言語でも未実装の期間**（E2E はハーネスと `GET /health` まで疎通済みで、テスト本体は STEP 5 完了まで赤が正常）。

**製造①が残した前提**（セグメント②が上に積む）。

| # | 事実 |
|---|------|
| 1 | **`@Service` を付けられるのは Green にする回**。`BattleSimulator` は `FloorProgression`、`LapAnalyzer` は `FloorCatalog` を注入するため、実体が無いままスキャンに載せると**結合テストのコンテキスト起動が壊れる**（`@Transactional` は `BattleServiceImpl` へ付与済み。②-b で `FloorProgressionImpl` の器はできたが `@Service` は未付与。マスターデータの `Towers`・`Enemies` は②-i で `@Component` 化済み） |
| 2 | **表層の正は各テストクラスの Javadoc「製造工程への申し送り」**（②-a の `TowerServiceImplTest`・②-b の `FloorProgressionImplTest`・②-c の `TowerModeResourceTest`／`TowerRetreatConditionsResourceTest`）。そこに無い名前を新設しない。②-c が足したのは `TowerService#retire`/`#changeMode`/`#updateRetreatConditions`（戻り値なし）と `TowerModeResource`・`TowerRetreatConditionsResource`（`hpThreshold` は **`Double`**。`double` だと欠落が `0.0` に化けて撤退無効化と区別できない）、②-d が足したのは `TowerStateValidator`（依存なし）と**新規コード `INTERNAL_STATE_INVARIANT_VIOLATED`**（製造②で `tech_error_handling.md` へ登録する） |

**現況の実測値**（2026-08-16）。

| 対象 | 値・見方 |
|------|---------|
| Java テスト | 単体**505件**（うち **Red 96件** ＝ ②-a 33 + ②-b 27 + ②-c 21 + ②-d 15。domain 77・web 19）・結合**88件 green**（domain 61・web 27）・**C1 の未達0**（全モジュール `All coverage checks have been met`） |
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
/dev 3-B 製造②-ii（Phase 1: 塔操作）: `TowerServiceImpl` と tower の Resource を Green にする（一覧・入塔・リタイア・モード・撤退条件）
完了条件: ①`TowerServiceImpl` を実装して②-a 33件と②-c の `TowerServiceImplTest` 分を Green にし、`@Service`・`@Transactional` を付ける（前提1） ②web の `TowerSelectResource`・`TowerModeResource`・`TowerRetreatConditionsResource` へ Bean Validation を付けて Resource テストを Green にする（`@RestController` の付与もこの回） ③`PlayerRepository#updateTowerState` と `TowerClearRecordRepository` のマッピング XML を書く。**現行の UPDATE 文が `tower_mode`・`hp_threshold` を含むか確かめ、含まないなら列を足す**（②-c の申し送り） ④`mvn verify "-Dmaven.test.failure.ignore=true"` で **Red 96 → 42件**（残るのは②-b 27 + ②-d 15 ＝ ②-iii の担当）・結合88件 green・C1 の未達0 ⑤`check_java_conventions.py` 違反0・WARN 6 据え置き ⑥main へ統合してコミットする
参照: [tech_tower/list.md](../tech/detail/tech_tower/list.md) §6・[select.md](../tech/detail/tech_tower/select.md) §8・[control.md](../tech/detail/tech_tower/control.md) §12（分岐一覧の正）。**表層の正は各テストクラスの Javadoc「製造工程への申し送り」**（前提2）
前提: main `fbcbe8d`。`towers.yml`・`enemies.yml` は搭載済みで**塔IDは `goblin_tower`**（テスト定数も追随済み）。**worktree `3b-dev-tower-ii` を作って作業する**（`python scripts/worktree.py add 3b-dev-tower-ii`）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（選んだ時点で着手可否を判断できるように）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B 製造②-iii（Phase 1: 階進行と全滅）**。`FloorProgressionImpl`（②-b 27 + ②-d のペナルティ3分岐）・`FloorCatalog`・`TowerStateValidatorImpl`（②-d の不変条件）を Green にする。**`TowerData` へ `floorEncounters` 列を足して `towers.yml` へ各階のエンカウントプールを書く**（②-i は読み手が無いため未搭載。値の正は `001_ゴブリンの塔.md` §5）。**新規コード `INTERNAL_STATE_INVARIANT_VIOLATED` を [tech_error_handling.md](../tech/basic/tech_error_handling.md) へ登録**し、**EXP 減算の端数の丸めを決めて [tech_state.md](../tech/detail/tech_state.md) §3 手順4 へ書き戻す**のもこの回 | なし（②-i 完了済み）。§1（②-ii）と領域が重なるので並行させない | `3b-dev-tower-iii`<br>backend | `dev` |

- **分岐一覧の旧形式は残り2件**（`tech_polling.md` §5・`tech_rng.md` §5）。標準形式への移行は**1行が真偽の両方を持つ行の分割＝既存マーカーの番号ずれ**を伴うので、参照元のテストを触る回に同じセッションでまとめて行う（[detail-design.md](../../.claude/project/detail-design.md) §4 が「残件は候補キューで追跡する」と定める分）
- **`tech_polling.md` §5 の10件は tick API（Controller・Resource）を作る回の後**に `integration-test`（E2E）で消化する（判断根拠と照合対象外の扱いは carryover §1）
- **マーカー0件の一覧2件（照合対象外）と Phase 4 の除外は [carryover_notes.md](carryover_notes.md) §2 が正**
- **前文が伸びる回は書く前に carryover へ逃がす**（本ファイルは残量WARN の境目を出入りしている。②-i 時点の残量は §4 の手順どおり書く前に測ること）
