# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-11 / main `87e50f3`（**3-B 製造①-i：戦闘・オフラインの表層**。`domain.service.battle` へ27ファイル（インタフェース12・record 4・enum 1・Impl 10）、`masterdata` へ `EnemyData`・`Enemies` を新設し、**`mvn test-compile` は全モジュール exit 0**（未実装シンボル66 → 0）。詳細は [changelog.md](../changelog.md) の 2026-08-11 ブロック）。1つ前は main `2d43725`・`192dd50`（`/retro` の反映6件と効率メモ10件の削除）、その前は `53ba169`（Service・Resource を業務領域のサブパッケージへ再編。**AOP 境界ログのポイントカットは `..*.`＝配下**で、`.*.` へ戻すと境界ログがテストに検出されないまま消える）。

**製造①-i が残した前提**（①-ii・①-iii が上に積む）。

| # | 事実 |
|---|------|
| 1 | **Impl のメソッド本体は `UnsupportedOperationException`**。既定値返しにすると null 期待・ゼロ期待のテストが偶然 Green になるため。Green の回は「そのクラスの例外を消す」作業になる |
| 2 | **`@Service`・`@Component` をまだ付けていない**。`FloorCatalog`・`FloorProgression`・`Enemies` の実体がセグメント②まで無く、`@ComponentScan("com.afkgame.domain")` に載せると**結合テスト85件のコンテキスト起動が壊れる**。DI 配線は協調先が揃う Green の回で入れる |
| 3 | **`FloorCatalog`・`FloorProgression` はインタフェースだけを `service/battle` に置いた**（読み手が `LapAnalyzer`・`BattleSimulator` だけで、テストが同パッケージから無修飾参照しているため）。**実装はセグメント②で `service/tower` へ置く** |
| 4 | **`check_java_conventions.py` は 違反0 / WARN 14 が現在の正常値**（13 → +1 は `LogReason.CLOCK_SKEW` の先行投入。実際に出力するのは①-iii）。着手判定で「WARN 13」と食い違っても異常ではない |
| 5 | 表層の正は各テストクラスの Javadoc「製造工程への申し送り」。**そこに無い名前を新設しない** |

**Java 移行の残りは 3-B（Phase 1: game / battle / tower）→ 4（Phase 2）→ 5（Phase 3）→ 6（切替と後始末）**（順序の正は [carryover_notes.md](carryover_notes.md) §1、手順・進捗の正は [java_migration.md](java_migration.md)、STEP の定義は [steps.md](java_migration/steps.md)「STEP 3〜5」）。**3-B は詳細設計・テストリスト作成①（game / battle）・製造①-i まで完了**で、残るのは製造①-ii → ①-iii → テストリスト作成②（tower）→ 製造②。**Phase 1〜3 の機能はどの言語でも未実装の期間**（E2E はハーネスと `GET /health` まで疎通済みで、テスト本体は STEP 5 完了まで赤が正常）。

**Red の現況**: 単体378件のうち **Red 72件はすべて `service/battle` の新規10クラス**（`mvn verify` 実測。既存の単体テストと**結合テスト85件は全件 green**）。`-Dtest=<クラス>` で領域ごとに Green を取っていく。

**`mvn verify` に Docker は要らない**（結合テストは domain・web とも `EmbeddedPostgresSupport` の埋め込み PostgreSQL）。`report_java_tests.py --run` は既定で `-DskipITs` なので、**結合テストまで見るなら `--run --it`**。**Red がある状態で結合テストまで通したいときは `mvn verify -Dmaven.test.failure.ignore=true`**（surefire の失敗で止まると failsafe が走らず「結合テストの退行が見えない」）。**常設スクリプトの回帰テストは全件 green**（`python -m pytest scripts/tests -q` = 406件）で、`scripts/**` を変更するタスクはこれを退行検出の網に使う。

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
/dev 3-B 製造①-ii（戦闘計算の Green）: 依存の無い6クラスのロジックを実装して Green にする
完了条件: ①`DamageCalculatorImplTest`・`TargetSelectorImplTest`・`StatCalculatorImplTest`・`HealingCalculatorImplTest`・`EncounterSelectorImplTest`・`CharacterGrowthImplTest` が全件 Green（`-Dtest=<クラス> -Dsurefire.failIfNoSpecifiedTests=false`） ②この6つの Impl に `@Service` を付ける（**協調先が揃っているのはこの6つだけ**。`CharacterGrowthImpl` の `GameSettings` は既存 Bean）。付けたら `mvn verify -Dmaven.test.failure.ignore=true` で**結合テスト85件が green のまま**を確認する ③**クリティカル率の供給元を決める**（Phase 1 の基礎5%。`tech_rng.md` §6 が「プレイヤー・敵で共通の定数にしない」を求めるため、マスターデータか設定値のどちらかへ置いて詳細設計へ反映する） ④`EncounterSelectorImpl` が投げるシステム例外のコード `INTERNAL_MASTER_DATA_INVALID` を `tech_error_handling.md` と `afkgame-web` の `ErrorCatalog` へ登録し `check_error_codes.py` を green にする ⑤`check_java_conventions.py` 違反0・WARN 14以下（`GameSettings` の accessor を実装が参照すると減る） ⑥残る4クラス（`BattleService`・`BattleSimulator`・`OfflineCalculator`・`LapAnalyzer`）は Red のままが正常 ⑦main へ統合してコミットする
参照: 上記6テストクラスの Javadoc「製造工程への申し送り」（表層の正）。計算式・丸めは [tech_numeric.md](../tech/detail/tech_numeric.md) §2〜§4、乱数の消費順序と境界規約は [tech_rng.md](../tech/detail/tech_rng.md) §1・§3・§6、ダメージ計算とターゲット選択は [tech_battle.md](../tech/detail/tech_battle.md) §3.1・§3.3、経験値テーブルは [character.md](../data/master/character.md) §1.4
前提: main `87e50f3`。**Maven 3.9.11 + JDK 17.0.20（Temurin）を新規シェルで `mvn -version` により実行確認済み**。`mvn verify` に Docker は不要（同セッションで実測）。**表層は実装済みで、各 Impl のメソッドが `UnsupportedOperationException` を投げている状態**＝その本体を置き換える作業になる。**worktree `3b-dev-battlecalc` を作って作業する**（`python scripts/worktree.py add 3b-dev-battlecalc`）。`docs/backlog/open_specs.md` は不在＝未確定ゼロ
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（選んだ時点で着手可否を判断できるように）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **3-B 製造①-iii（tick・オフラインの Green）**。`BattleService`・`BattleSimulator`・`OfflineCalculator`・`LapAnalyzer`。`PlayerRepository#findByIdForUpdate`/`#updateTickState` の**マッピング XML もこの回**（インタフェースだけ先行済み） | §1（戦闘計算） | `3b-dev-tick`<br>backend | `dev` |
| 2 | **3-B テストリスト作成②（Phase 1: tower）**。分岐一覧は [tech_tower.md](../tech/detail/tech_tower.md) §0 の55件 + `tech_state.md` §5 の7件 | キュー1 | `3b-testlist-tower`<br>backend | `test-list` |
| 3 | **3-B 製造②（Phase 1: tower）**。`FloorCatalog`・`FloorProgression` の実装（`service/tower`）と `Enemies` のレジストリ化（`enemies.yml` + `@Component`）を含む | キュー2 | `3b-dev-tower`<br>backend | `dev` |
| 4 | **diagrams-review 還元案3件の適用**（①`fix-specs` へ図への波及チェック ②`scripts/check_mermaid.py` の常設化 ③列の「備考」文言を三者一致の照合対象へ）。正は [2026-08-11_133822.md](../reviews/diagrams-review/2026-08-11_133822.md) 末尾 | なし（`docs`・`scripts` 領域。backend と重ならず並行可） | `dr-feedback`<br>docs/scripts | `retro` |

- **仕様書の指摘を直したら、それを検証対象に持つ図まで同じパスで直す**（キュー4 の還元案①が常設化。仕様書だけ直し ISSUE-1301・1303・1308 が図の指摘5件に化けた）
- **backend-review の還元候補3件の正は [2026-08-10_155729.md](../reviews/backend-review/2026-08-10_155729.md)「プロセスへの還元」**
- **`tech_polling.md` §5 の10件は `integration-test`（E2E）担当**（①-a で JUnit 対象外と判断）。tick API が要るため製造①-iii（キュー1）の後
- **マーカー0件＝照合対象外の一覧2件**: `tech_offline.md` §7（12行・Phase 3 の製造で展開）と `tech_numeric.md` §6（2件・`PUT /api/game/settings` の Resource を作る回）
- **分岐一覧の旧形式は残り2件**（`tech_polling.md` §5・`tech_rng.md` §5）。標準形式への移行は**1行が真偽の両方を持つ行の分割＝既存マーカーの番号ずれ**を伴うので、参照元のテストを触る回に同じセッションでまとめて行う
- **Phase 4 は Java 移行完了まで本キューから外している**（2026-08-09・ユーザー判断。戻す1件の正は `carryover_notes.md` §2）
