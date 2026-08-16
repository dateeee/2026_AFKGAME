# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-16 / main `8b53900`（**3-B テストリスト作成②-b: tower の階進行**。[progress.md](../tech/detail/tech_tower/progress.md) §10 の**分岐一覧21件を Red へ展開**した（`@ParameterizedTest` 5件が割れて27ケース）。対象は継ぎ目 `FloorProgression` のうち **`onEnemyDefeated` だけ**で、`ensureEncounter`（`EncounterSelector` へ委譲。一覧は `tech_skill.md` §8・`tech_rng.md` §5）と `onPartyWiped`（全滅ペナルティ＝`tech_state.md` §3）は担当が別）。**既存の参照は番号で追わず内容で照合する**（`known_issues.md` は欠番あり、`frontend/**` は ESLint / Prettier の一括整形で行内容の差分が広い）。**履歴の正は [changelog.md](../changelog.md) の 2026-08-16 ブロック**なので、本ファイルへ積み増さない。

**いまの位置**: Java 移行の残りは **3-B（Phase 1: tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)、STEP の定義は [steps.md](java_migration/steps.md)「STEP 3〜5」）。**3-B は詳細設計・テストリスト作成①（game / battle）・製造①（①-i〜①-iv）・キャラ成長の製造・テストリスト作成②-a（一覧・入塔）・②-b（階進行）まで完了**で、残るのは**②-c（進行制御）→ 製造②**。**Phase 1〜3 の機能はどの言語でも未実装の期間**（E2E はハーネスと `GET /health` まで疎通済みで、テスト本体は STEP 5 完了まで赤が正常）。

**製造①が残した前提**（セグメント②が上に積む）。

| # | 事実 |
|---|------|
| 1 | **`@Service` を付けられるのはセグメント②**。`BattleSimulator` は `FloorProgression`・`Enemies`、`LapAnalyzer` は `FloorCatalog` を注入するため、実体が無いままスキャンに載せると**結合テストのコンテキスト起動が壊れる**（`@Transactional` は `BattleServiceImpl` へ付与済み。②-b で `FloorProgressionImpl` の器はできたが `@Service` は未付与） |
| 2 | **敵の `enemies.yml` はセグメント②で載せる**（`critRate` 列を含む。味方側 `character_types.yml` は配線済みで [tech_rng.md](../tech/detail/tech_rng.md) §6 が供給元の正） |
| 3 | **表層の正は各テストクラスの Javadoc「製造工程への申し送り」**（②-a の `TowerServiceImplTest`・②-b の `FloorProgressionImplTest`）。そこに無い名前を新設しない。②-b が足したのは `FloorProgressionImpl`・`TowerClearRecordRepository#save`/`#updateProgress`・`TowerData.modifiers` + `TowerModifier` |

**現況の実測値**（2026-08-16）。

| 対象 | 値・見方 |
|------|---------|
| Java テスト | 単体**454件**（うち **Red 60件** ＝ ②-a 33 + ②-b 27。domain 54・web 6）・結合**88件 green**・**C1 の未達0**（全モジュール `All coverage checks have been met`） |
| Red が無い回 | `python scripts/report_java_tests.py --run` をそのまま使える（既定は `-DskipITs`。結合まで見るなら `--run --it`） |
| **Red を作る回**（テストリスト工程） | `mvn verify "-Dmaven.test.failure.ignore=true"` で見る。surefire の失敗で止まると failsafe が走らず結合の退行が見えないため。**PowerShell では `-D...=...` を引用符で囲む**（囲まないと引数が割れて `Unknown lifecycle phase` で即死する） |
| ビルド環境 | Maven 3.9.11 / `JAVA_HOME` は Temurin **17.0.20**（`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`）。Adoptium 配下はこの1本のみで、**新規シェルで `mvn` をそのまま叩ける**（PowerShell の `mvn -version` で実測） |
| 常設スクリプト | `python -m pytest scripts/tests -q` = 509件が全件 green（`.claude/scripts/tests` `.claude/hooks/tests` まで含めると602件）。`scripts/**` を変更する回はこれを退行検出の網に使う |
| Java 規約 | `check_java_conventions.py` は **違反0 / WARN 6 が正常値**。ゼロを強制せず増減だけ見る（内訳は carryover §3。新しい依存を足す回は `DEPENDENCY_IMPORTS` への登録が要る） |
| Mermaid | `python scripts/check_mermaid.py` が常設（使い捨てで書き直さない） |
| 未確定仕様 | `docs/backlog/open_specs.md` は不在＝ゼロ |

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
/test-list 3-B テストリスト作成②-c（Phase 1: tower の進行制御と状態機械）: 分岐一覧を失敗するテストへ展開する
完了条件: ①[tech_tower/control.md](../tech/detail/tech_tower/control.md) §12 の**10件**（リタイア・進行モード切替・撤退条件更新）と [tech_state.md](../tech/detail/tech_state.md) §5 の**7件**（探索セッション・不変条件・操作ガード）＝**17件**を Red のテストへ展開する ②表層は**テストクラスの Javadoc「製造工程への申し送り」へ書き**、本体は `UnsupportedOperationException` に留める（`@Service` は付けない＝前提1）。`retire` / `mode` / `retreat-conditions` は②-a の Javadoc が「②-c が `TowerService` へ足す」と予告済みで、**別名を新設しない**＝前提3 ③`python scripts/check_branch_list.py --tests` 違反0・WARN 0 ④`mvn verify "-Dmaven.test.failure.ignore=true"` で **Red が17分岐ぶん増え、結合88件 green・C1 の未達0** のままを確認する（`@ParameterizedTest` はケース単位で数えるため件数は分岐数より多くなる。②-a は24行→33件、②-b は21行→27件。Red がある回の見方は前文） ⑤`check_java_conventions.py` 違反0・WARN 6 据え置き ⑥main へ統合してコミットする
参照: 上記2ファイルの分岐一覧（正）と control.md §11 の処理フロー・tech_state.md §1〜§4。書き方と申し送りの手本は②-a の `TowerServiceImplTest`・②-b の `FloorProgressionImplTest` の Javadoc、置き場は `domain.service.tower`（[common.md](../process/coding_standards_backend/common.md) §2.1）。**全滅の後始末（`FloorProgression#onPartyWiped`）を tech_state.md §5 が持つなら同じ回で展開する**
前提: main `8b53900`。ビルド環境・テストの実測値・常設チェックは前文の表が正（`JAVA_HOME` の切り替えは不要）。**worktree `3b-testlist-tower-c` を作って作業する**（`python scripts/worktree.py add 3b-testlist-tower-c`）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（選んだ時点で着手可否を判断できるように）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B 製造②（Phase 1: tower）**。`TowerServiceImpl`・`FloorCatalog`・`FloorProgressionImpl` の実装（`service/tower`）と `Enemies`・`Towers` のレジストリ化（`enemies.yml`・`towers.yml` + `@Component`）。**Green 済みクラスへ `@Service` を付けるのもこの回**（前提1）。**塔IDの表記ゆれ**（テストの `tower_goblin` / `tech_data.md` §1.4 の `goblin_tower` / `endgame.md` の `abyss_tower`）は `towers.yml` を書くこの回でそろえ、既存テストの定数も同じ回で追随させる。着手時に規模を見てセグメントへ割る | §1（②-c） | `3b-dev-tower`<br>backend | `dev` |
| 2 | **要件定義 spot-review（[2026-08-16_191841](../reviews/spot-review/2026-08-16_191841.md)）の指摘1件を反映**。ISSUE-1401（中）: Phase 1〜2 の計画メンテナンスの告知手段が未定義。**別セッションが本セッションと並行して出したレポート**で、反映は未着手 | なし | `fixspecs-maint`<br>docs | `fix-specs` |

- **分岐一覧の旧形式は残り2件**（`tech_polling.md` §5・`tech_rng.md` §5）。標準形式への移行は**1行が真偽の両方を持つ行の分割＝既存マーカーの番号ずれ**を伴うので、参照元のテストを触る回に同じセッションでまとめて行う（[detail-design.md](../../.claude/project/detail-design.md) §4 が「残件は候補キューで追跡する」と定める分）
- **`tech_polling.md` §5 の10件は tick API（Controller・Resource）を作る回の後**に `integration-test`（E2E）で消化する（判断根拠と照合対象外の扱いは carryover §1）
- **マーカー0件の一覧2件（照合対象外）と Phase 4 の除外は [carryover_notes.md](carryover_notes.md) §2 が正**
