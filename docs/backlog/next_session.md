# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-16 / main `882d619`（**キャラ成長①: 到達側の分岐追加と Red 展開**。`tech_numeric.md` §5 へ #15〜#25 を末尾追加し、成長率の列を `character_types.yml`・`CharacterTypeData` へ搭載、`CharacterGrowth#requiredExpToNextLevel` を新設して **Red 11件**を置いた）。直前の `0bdc678` は **known_issues の棚卸しとフロント規約整備の是正**（**番号の欠番が増えたので既存の参照は番号で追わず内容で照合する**。`frontend/**` は ESLint / Prettier の一括整形が入っており**行内容の差分が広い**）。同日は他セッションの成果も main に入っている（Phase 5 ボスラッシュ詳細設計 / `spot-review` スキル新設・worktree 前提化 / 要件定義の spot-review レポート / backend-review 還元案3件の常設化 / `worktree_guide.md` §5 の分冊化 / 申し送りメモの棚卸し）。**履歴の正は [changelog.md](../changelog.md) の 2026-08-16 ブロック**なので、本ファイルへ積み増さない。

**いまの位置**: Java 移行の残りは **3-B（Phase 1: tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)、STEP の定義は [steps.md](java_migration/steps.md)「STEP 3〜5」）。**3-B は詳細設計・テストリスト作成①（game / battle）・製造①（①-i〜①-iv）まで完了**で、残るのは**テストリスト作成②（tower）→ 製造②**。**Phase 1〜3 の機能はどの言語でも未実装の期間**（E2E はハーネスと `GET /health` まで疎通済みで、テスト本体は STEP 5 完了まで赤が正常）。

**製造①が残した前提**（セグメント②が上に積む）。

| # | 事実 |
|---|------|
| 1 | **`CharacterGrowth` は Red 済みで未実装**（`applyLevelUp`・`requiredExpToNextLevel` が `UnsupportedOperationException`、`addExp` は上限判定と加算のみ）。分岐一覧（`tech_numeric.md` §5 #15〜#25・`tech_party.md` §6）と成長率の列は搭載済みなので、**残るのは製造だけ**＝候補キューの行 |
| 2 | **`@Service` を付けられるのはセグメント②**。`BattleSimulator` は `FloorProgression`・`Enemies`、`LapAnalyzer` は `FloorCatalog` を注入するため、実体が無いままスキャンに載せると**結合テストのコンテキスト起動が壊れる**（`@Transactional` は `BattleServiceImpl` へ付与済み） |
| 3 | **敵の `enemies.yml` はセグメント②で載せる**（`critRate` 列を含む。味方側 `character_types.yml` は配線済みで [tech_rng.md](../tech/detail/tech_rng.md) §6 が供給元の正） |
| 4 | **表層の正は各テストクラスの Javadoc「製造工程への申し送り」**。そこに無い名前を新設しない |

**現況の実測値**（2026-08-16）。

| 対象 | 値・見方 |
|------|---------|
| Java テスト | 単体**391件**（うち **Red 11件はキャラ成長の未実装＝正常**。内訳は `UnsupportedOperationException` の ERROR 7件・`AssertionFailedError` の FAILURE 4件で、いずれも `CharacterGrowthImplTest`）・結合88件 green・**C1 は 100%（282/282・未達0）** |
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
/test-list 3-B テストリスト作成②-a（Phase 1: tower の一覧・入塔）: 分岐一覧を失敗するテストへ展開する
完了条件: ①[tech_tower/list.md](../tech/detail/tech_tower/list.md) 9件・[select.md](../tech/detail/tech_tower/select.md) 15件の**計24件**を Red のテストへ展開し、両ファイルの分岐一覧へマーカーを付ける ②表層（インタフェース・record・Resource）は**テストクラスの Javadoc「製造工程への申し送り」へ書き**、本体は `UnsupportedOperationException` に留める（`@Service` は付けない＝前提2） ③`python scripts/check_branch_list.py --tests` 違反0・WARN 0 ④`mvn verify "-Dmaven.test.failure.ignore=true"` で **Red が24件だけ増え、結合88件 green・C1 の未達0** のままを確認する（Red がある回の見方は前文） ⑤`check_java_conventions.py` 違反0 ⑥main へ統合してコミットする
参照: 上記2ファイルの分岐一覧（正）。書き方の手本は `BattleSimulatorImplTest`・`LapAnalyzerImplTest` の Javadoc、置き場は `domain.service.tower`（[common.md](../process/coding_standards_backend/common.md) §2.1）
前提: main `d52daea`。ビルド環境・テストの実測値・常設チェックは前文の表が正（`JAVA_HOME` の切り替えは不要）。**worktree `3b-testlist-tower-a` を作って作業する**（`python scripts/worktree.py add 3b-testlist-tower-a`）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（選んだ時点で着手可否を判断できるように）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B テストリスト作成②-b（階進行）**。[tech_tower/progress.md](../tech/detail/tech_tower/progress.md) の21件（tick内・階クリア後） | §1（②-a が tower の表層を定義する） | `3b-testlist-tower-b`<br>backend | `test-list` |
| 2 | **3-B テストリスト作成②-c（進行制御・状態機械）**。[tech_tower/control.md](../tech/detail/tech_tower/control.md) 10件 + [tech_state.md](../tech/detail/tech_state.md) §5 の7件 ＝ 17件 | キュー1 | `3b-testlist-tower-c`<br>backend | `test-list` |
| 3 | **3-B 製造②（Phase 1: tower）**。`FloorCatalog`・`FloorProgression` の実装（`service/tower`）と `Enemies` のレジストリ化（`enemies.yml` + `@Component`）。**Green 済みクラスへ `@Service` を付けるのもこの回**（前提2）。着手時に規模を見てセグメントへ割る | キュー2 | `3b-dev-tower`<br>backend | `dev` |
| 4 | **キャラ成長の製造**（Red 11件を Green にする）。`CharacterGrowthImpl` の到達判定・ステータス再計算・SP付与を実装し、**同じ回で `LapAnalyzerImpl#lapsToLevelUp` を配線する**（`carryover_notes.md` §1）。仕様は `tech_numeric.md` §5 #15〜#25・`tech_party.md` §6 | なし（Red は main にある） | `3b-dev-growth`<br>backend | `dev` |

- **分岐一覧の旧形式は残り2件**（`tech_polling.md` §5・`tech_rng.md` §5）。標準形式への移行は**1行が真偽の両方を持つ行の分割＝既存マーカーの番号ずれ**を伴うので、参照元のテストを触る回に同じセッションでまとめて行う（[detail-design.md](../../.claude/project/detail-design.md) §4 が「残件は候補キューで追跡する」と定める分）
- **`tech_polling.md` §5 の10件は tick API（Controller・Resource）を作る回の後**に `integration-test`（E2E）で消化する（判断根拠と照合対象外の扱いは carryover §1）
- **マーカー0件の一覧2件（照合対象外）と Phase 4 の除外は [carryover_notes.md](carryover_notes.md) §2 が正**
