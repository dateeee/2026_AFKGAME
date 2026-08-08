# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-08 / 対応コミット: `d08cccb`（移行 STEP 3-A-1a の Red をマスターデータ検証テストとして追加）。`tech_auth.md` §8.3 の分岐 #3・#4・#6・#10 を、**サービスではなくレジストリ構築時に落とす**設計でテスト化した（「起動時のローダ検証で中止する」＝§8.2 末尾）。プロダクトコードは1行も書いておらず、テストコンパイルが未作成の6型ちょうどで停止する。**次は同じ 1a の Green から**。手順・進捗の正は [java_migration.md](java_migration.md)

**Green 待ちが2件並んでいる**: 1a（§1・Red は `d08cccb`）と 1b（優先3・Red は `37d4ef5`）。互いに独立で、両方そろうと 1c（初期化サービス）に着手できる。

**Phase 4 詳細設計**: 拠点・施設（`tech_base.md`）と①酒場スカウト（`tech_scout.md`・`643728a`）は完了。副産物の **`characters.master_id`**（[tech_db/player.md](../tech/basic/tech_db/player.md) §4・Phase 4・未実装）と**酒場専用16体**（[master/character.md](../data/master/character.md) §7.3）は確定済み。

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
/dev 移行 STEP 3-A-1a（初期化に使うマスターデータ）: Red 済みのテスト19ケース（メソッド15件）を Green にする。com.afkgame.domain.masterdata へ record 5件（CharacterTypeData・EquipmentSlotData・InitialPlayerData・InitialCharacterData・InitialItemData）とレジストリ3件（CharacterTypes・EquipmentSlots・InitialPlayer）を追加し、MasterDataLoader へ `<T> T loadSingle(String resourcePath, Class<T> type)` を足す。YAML 3件（character_types.yml・equipment_slots.yml・initial_player.yml）を afkgame-domain の src/main/resources/masterdata/ へ置く。要求される表層（record の構成と制約・レジストリの公開 API・テスト用のパッケージプライベートなコンストラクタ）は各テストクラスの Javadoc に「製造工程への申し送り」として書いてある
完了条件: mvn verify が成功・追加した19ケースが全PASS・JaCoCo branch 100%（親POMのしきい値）・コミット
参照: backend/afkgame-domain/src/test/java/com/afkgame/domain/masterdata/（Red テスト3件＋MasterDataLoaderTest が起点）、数値の正は docs/data/master/character.md §1.1・§1.2 と docs/data/master/item.md §3.5、スロットIDの正は docs/tech/basic/tech_db/item.md §1、既存書式は同パッケージの Items.java・ItemData.java と src/main/resources/masterdata/items.yml
前提: `d08cccb` で Red 済み（テスト実行前のコンパイルで停止し、未作成の6型 CharacterTypes・CharacterTypeData・EquipmentSlots・InitialPlayer・InitialCharacterData・InitialPlayerData に「シンボルを見つけられません」が出る状態。PASS したテストは無い）。**着手時の要判断1件**: `character_types.yml` は LV1 基礎値のみを持ち成長率を入れない（`tech_auth.md` §8.1）。未知キーは起動時に弾かれるので、先走って growthRate を足すと落ちる。**編集は worktree で行う**（[worktree_guide.md](../process/worktree_guide.md) §5）: `python scripts/worktree.py add step3a1a-green` → `EnterWorktree`(path)、完了後に §5.3 の `worktree.py merge`。触る領域は backend。**環境（2026-08-08 に実行確認済み）**: `mvn`・`java` は PATH にも JAVA_HOME にも無く、Bash から動くのは `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot" "/c/Users/tubas/AppData/Local/Programs/apache-maven-3.9.11/bin/mvn" -f backend/pom.xml -pl afkgame-domain -am test` の形（JAVA_HOME をインラインで与えないと "JAVA_HOME is not defined correctly" で落ちる）。統合テストDBは zonky 埋め込み PostgreSQL（Docker 未検証）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 0 | **Phase 4 詳細設計 ②鍛冶屋・素材** `/api/forge/*`（強化・製作・分解の3操作）。強化上限・製作可能レアリティ・強化コスト倍率の解決は `tech_base.md` §2.1（**2026-08-08 にしきい値方式を廃止し全10LV定義へ変更済み**。施設LVと一致する行を引くだけ）。数値の正は `economy.md` §2.9 と `master/equipment.md`。書式は `tech_scout.md` に揃える（新規 `tech_forge.md` を起こし索引3点へ登録） | なし（Java 移行と独立） | `p4forge-detail`<br>docs/tech | `detail-design` |
| 1 | **Phase 4 詳細設計 ③限界突破** `POST /api/character/limit-break`。素材＝同一 `master_id` のキャラ1体を消費し `limit_break` を+1（上限5回。ボーナスの正は `master/character.md` §8.1）。探索中は不可（`tech_state.md` §4）。重複の発生源とレスポンスの `canLimitBreak` は `tech_scout.md` §6 が正 | ①完了済み（`master_id`・重複仕様が確定） | `p4limitbreak-detail`<br>docs/tech | `detail-design` |
| 2 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 3 | 移行 STEP 3-A-1b の **Green**（初期化対象の Entity + Mapper）。Red 済みの Mapper 疎通テスト28件を通す。Entity 5件（Player・PlayerSettings・Character・CharacterEquipSlot・InventoryItem）を `com.afkgame.domain.model` へ、Mapper 5件（インタフェース + 同名 XML）を `com.afkgame.domain.repository` へ。表層は各テストの Javadoc が持つ。**要判断**: ①`model.Character` は `java.lang.Character` と単純名が衝突（`users`→`User` の規則を優先して命名済み。改名するなら Green 前に）②`characters.rarity` は Phase 3 の列で V1 スキーマに無いため Entity にも持たせない | `37d4ef5` で Red 済み | `step3a1b-green`<br>backend | `dev` |
| 4 | 移行 STEP 3-A-1c（プレイヤー初期化サービス + 結線）。`tech_auth.md` §8.2 の8手順を単一トランザクションで実装し `POST /api/auth/guest` へ結線。分岐一覧 #1・#2・#5・#7〜#9・#11・#12 が対象 | 1a + 1b の **Green** 完了 | `step3a1c-init`<br>backend | `test-list` → `dev` |

- 上記に載らない**複数セッションにまたがる申し送り**（移行 STEP の順序・環境・確定済み仕様の波及）は [carryover_notes.md](carryover_notes.md) が持つ。着手前にそちらも見る
