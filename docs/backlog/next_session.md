# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-08 / 対応コミット: `d08cccb`（移行 STEP 3-A-1a の Red をマスターデータ検証テストとして追加）。`tech_auth.md` §8.3 の分岐 #3・#4・#6・#10 を、**サービスではなくレジストリ構築時に落とす**設計でテスト化した（「起動時のローダ検証で中止する」＝§8.2 末尾）。プロダクトコードは1行も書いておらず、テストコンパイルが未作成の6型ちょうどで停止する。**次は同じ 1a の Green から**。手順・進捗の正は [java_migration.md](java_migration.md)

**Green 待ちが2件並んでいる**: 1a（§1・Red は `d08cccb`）と 1b（優先3・Red は `37d4ef5`）。互いに独立で、両方そろうと 1c（初期化サービス）に着手できる。

**Phase 4 詳細設計の既完了分**: 拠点・施設（`tech_base.md`・分岐一覧37件）と①酒場スカウト（`tech_scout.md`・分岐一覧29件。`643728a` で統合済み）。副産物として **`characters.master_id`**（[tech_db/player.md](../tech/basic/tech_db/player.md) §4・Phase 4・未実装）と**酒場専用16体**（[master/character.md](../data/master/character.md) §7.3）が確定しているので、残る②〜④はこれを前提にしてよい。

## 0. 並行作業のルール（着手前に読む）

worktree を使う複数セッションが同時に走る前提。**着手状態は git 側に持たせ、本ファイルには書かない**（書いた行はその瞬間から古くなる）。

| # | ルール |
|---|-------|
| 1 | **着手の宣言＝ worktree の作成**。本ファイルへ「着手中」と書き足さない。今の状態の正は `python scripts/worktree.py list`（ブランチ・main との差分・未コミットの有無が出る） |
| 2 | §1・§2 のタスクには **worktree 名を採番してある**（§2 は「wt 名 / 領域」列、§1 は「前提」の `worktree.py add`）。同名の worktree か `wt/` ブランチが既にあれば別セッションが着手中 → **別の行を取る** |
| 3 | 2本目を並行で始めるなら §2 の**領域が重ならない行**を取る（[worktree_guide](../process/worktree_guide.md) §2 ルール2）。重なる行しか残っていなければ着手せずユーザーへ確認する |
| 4 | **本ファイルの更新は main でのみ・統合の直後に1回**（worktree の中では触らない）。§1 を次のタスクへ書き換え、消化した §2 行を消す。統合せず中断する場合だけ、main へ戻って §1 に `wt/<名前>` と再開手順を書く |
| 5 | 鮮度確認は `git log` に加えて `worktree.py list` を見る。**該当作業のコミットが wt 側にあれば「完了・未統合」**（着手せず、統合してよいかユーザーへ確認）。コミット0件でも `dirty` の worktree は別セッションが作業中であり、放棄ではない |

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
| 0 | **Phase 4 詳細設計 ②鍛冶屋・素材** `/api/forge/*`（強化・製作・分解の3操作）。強化上限・製作可能レアリティ・強化コスト倍率の解決は `tech_base.md` §2.1 のしきい値規則を使う。数値の正は `economy.md` §2.9 と `master/equipment.md`。書式は `tech_scout.md` に揃える（新規 `tech_forge.md` を起こし索引3点へ登録） | なし（Java 移行と独立） | `p4forge-detail`<br>docs/tech | `detail-design` |
| 1 | **Phase 4 詳細設計 ③限界突破** `POST /api/character/limit-break`。素材＝同一 `master_id` のキャラ1体を消費し `limit_break` を+1（上限5回。ボーナスの正は `master/character.md` §8.1）。探索中は不可（`tech_state.md` §4）。重複の発生源とレスポンスの `canLimitBreak` は `tech_scout.md` §6 が正 | ①完了済み（`master_id`・重複仕様が確定） | `p4limitbreak-detail`<br>docs/tech | `detail-design` |
| 2 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 3 | 移行 STEP 3-A-1b の **Green**（初期化対象の Entity + Mapper）。Red 済みの Mapper 疎通テスト28件を通す。Entity 5件（Player・PlayerSettings・Character・CharacterEquipSlot・InventoryItem）を `com.afkgame.domain.model` へ、Mapper 5件（インタフェース + 同名 XML）を `com.afkgame.domain.repository` へ。表層は各テストの Javadoc が持つ。**要判断**: ①`model.Character` は `java.lang.Character` と単純名が衝突（`users`→`User` の規則を優先して命名済み。改名するなら Green 前に）②`characters.rarity` は Phase 3 の列で V1 スキーマに無いため Entity にも持たせない | `37d4ef5` で Red 済み | `step3a1b-green`<br>backend | `dev` |
| 4 | 移行 STEP 3-A-1c（プレイヤー初期化サービス + 結線）。`tech_auth.md` §8.2 の8手順を単一トランザクションで実装し `POST /api/auth/guest` へ結線。分岐一覧 #1・#2・#5・#7〜#9・#11・#12 が対象 | 1a + 1b の **Green** 完了 | `step3a1c-init`<br>backend | `test-list` → `dev` |

- 移行 STEP 3-A-2（register / login / logout）は 3-A-1c の完了後。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は java_migration.md §4 の 2-B 表）。初期化は 3-A-1c の手順2以降を再利用する（`tech_auth.md` §8 冒頭）。続けて STEP 3-A-3（link-account / verify-email / password-reset。確認メール送信・トークン検証）
- 移行 STEP 3-B（Phase 1 の game / battle / tower 移植）は 3-A 完了後。game は `tech_state`/`tech_tick`/`tech_polling` §5、battle は `tech_battle`/`tech_rng`/`tech_numeric` §5 に分岐一覧がある。**tower は `tech_tower.md` が存在せず分岐一覧も無い**（auth と同じ欠落。2026-08-08 確認）ため `detail-design` から始める
- **`scripts/check_branch_list.py --tests` は Java のテストを見ていない**（走査先が `backend/tests/unit/*.py` 固定。2026-08-08 確認）。`test-list.md` §7 の完了基準「マーカーで機械照合」は Java 移行後のテストに対しては**素通り**する（`tech_auth.md §8.3` はマーカー未参照＝照合対象外の扱いで exit 0 になる）。1a の Red では分岐マーカーを Javadoc に書いたが照合されていないため、対応は手で確認した。走査先へ `backend/**/src/test/java/**/*.java` を足すのは **3-A-1c の完了後**にする（§8.3 の12行がそろうまでは部分カバレッジで ERROR になるため）
- 移行 STEP 4（Phase 2 スコープの移植: equipment / shop・日替わり含む）は STEP 3-B の完了後。着手前に `tech_shop.md` §7・§8 の分岐一覧が使える粒度かを確認する
- 移行 STEP 5（Phase 3 製造①の実装済み分＝パーティ・スキル操作を移植）。続けて製造②（スキル戦闘処理: skill / environment）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する。製造②では `SkillData` へダメージ倍率・対象・状態異常のフィールドを追加する
- **`docs/backlog/java_migration.md` は 7,988字 / 上限8,000字（残り12字）**。STEP 3 以降の進捗を書き足す前に `doc-size` で分割する（STEP 別に子ファイルへ切り出す等）。次の追記は圧縮では吸収できない
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った（着手は優先2または STEP 5 に合流させる）
- 移行 STEP 6（切替と後始末: Vite プロキシ・`.vscode/launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理）は上記すべての完了後。手順は [java_migration.md](java_migration.md) §4 が正
- `docker-compose.yml` は成果物として作成済みだが **Docker 環境が未検証**のため未起動確認。`local` で実際に `docker compose up -d` → `mvn spring-boot:run` → `GET /health` を通すのは Docker が使えるタイミングで行う
- **環境**（2026-08-08 に新規シェルで実行確認済み。コマンド形は §1 の「前提」が持つ）: `mvn`・`java` は PATH にも `JAVA_HOME` にも無く、**フルパス + `JAVA_HOME` を毎回与える**（PowerShell からは `mvn.cmd`。JDK は `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`）。統合テストDBは zonky 埋め込み PostgreSQL（`@AutoConfigureEmbeddedDatabase(provider = ZONKY)`）。`@ConfigurationProperties` は `afkgame-env` の `com.afkgame.env.config` へ置く。親POMに JaCoCo branch しきい値100%が入っているため、追加した分岐はすべてテストで通す
