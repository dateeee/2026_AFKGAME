# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-08 / 対応コミット: `9805023`（移行 STEP 3-A-1a・1b を Green にした）。マスターデータのレジストリ3件＋record 5件、Entity 5件＋Mapper 5件（XML 同梱）を追加し、`mvn verify` 全モジュール成功・afkgame-domain の単体75件 PASS・JaCoCo branch 100%。**Green 待ちは解消**し、1c（初期化サービス）の前提がそろった。手順・進捗の正は [java_migration.md](java_migration.md)

**1a と 1b は同時に Green にした**（独立ではなかった）。同一モジュールではテストソースが一括コンパイルされるため、片方だけでは `mvn test` がコンパイルで止まり検証できない。以後も同一モジュール内に Red が複数並ぶ場合は同じ制約がかかる。

**Phase 4 詳細設計**: 拠点・施設（`tech_base.md`）・①酒場スカウト（`tech_scout.md`・`643728a`）・②鍛冶屋（`tech_forge.md` + 操作別3件・`40d37c4`）は完了。残りは③限界突破と④塔6〜8。副産物の **`characters.master_id`**（[tech_db/player.md](../tech/basic/tech_db/player.md) §4・Phase 4・未実装）と**酒場専用16体**（[master/character.md](../data/master/character.md) §7.3）は確定済み。

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
/test-list 移行 STEP 3-A-1c（プレイヤー初期化サービス + 結線）: `tech_auth.md` §8.2 の8手順を単一トランザクションで組み立てるサービスの分岐を、失敗するテストへ展開する（Red）。対象は §8.3 の #1・#2・#5・#7〜#9・#11・#12（#3・#4・#6・#10 は 1a のレジストリ構築時テストで消化済み）。続けて Green まで進め、`POST /api/auth/guest` を現状の User + トークンのみから初期化込みへ差し替える
完了条件: Red を確認してコミット → Green（mvn verify 成功・JaCoCo branch 100%・AuthApiIntegrationTest でゲスト作成が Player / PlayerSettings / キャラ1体 / 9スロット / 初期アイテムまで作ることを確認）
参照: docs/tech/detail/tech_auth.md §8.2・§8.3 が起点。部品は 1a のレジストリ（CharacterTypes・EquipmentSlots・InitialPlayer）と 1b の Mapper 5件。サービスの書式とトランザクション境界は backend/afkgame-domain/src/main/java/com/afkgame/domain/service/AuthService.java、結線先は afkgame-web の AuthApi.java
前提: `9805023` で 1a・1b とも Green（afkgame-domain 単体75件 PASS・branch 100%）。**着手時の要判断1件**: #2（1ユーザーに2人目のプレイヤー）は `uq_players_user_id` 違反が `DuplicateKeyException` で上がる。これを業務例外のどのコードへ写すかは `tech_api_common.md` §5 を見てから決める（新設せず既存コードで足りるかを先に確認する）。**編集は worktree で行う**（[worktree_guide.md](../process/worktree_guide.md) §5）: `python scripts/worktree.py add step3a1c-init` → `EnterWorktree`(path)、完了後に §5.3 の `worktree.py merge`。触る領域は backend。**環境（2026-08-08 に実行確認済み）**: `mvn`・`java` は PATH にも JAVA_HOME にも無く、Bash から動くのは `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot" "/c/Users/tubas/AppData/Local/Programs/apache-maven-3.9.11/bin/mvn" -f backend/pom.xml verify` の形（JAVA_HOME をインラインで与えないと "JAVA_HOME is not defined correctly" で落ちる）。統合テストDBは zonky 埋め込み PostgreSQL（Docker 未検証）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 0 | **`efficiency_memo.md` の字数是正**（9,276字 / 上限8,000字・**1,276字超過**）。`d10574d` の追記で超過し、`check_doc_size.py` が exit 1 になっている＝**ドキュメント工程の完了ゲートが全セッションで通らない**。`retro` で反映済みエントリを消化して縮めるのが本筋（縮まりきらなければ `doc-size` で分割） | なし（最優先。docs を触る行はこれを通してから） | `memo-shrink`<br>docs/backlog | `retro` |
| 1 | **Phase 4 詳細設計 ③限界突破** `POST /api/character/limit-break`。素材＝同一 `master_id` のキャラ1体を消費し `limit_break` を+1（上限5回。ボーナスの正は `master/character.md` §8.1）。探索中は不可（`tech_state.md` §4）。重複の発生源とレスポンスの `canLimitBreak` は `tech_scout.md` §6 が正 | ①完了済み（`master_id`・重複仕様が確定） | `p4limitbreak-detail`<br>docs/tech | `detail-design` |
| 1 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 2 | 移行 STEP 3-A-2（register / login / logout）。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は java_migration.md §4 の 2-B 表）。初期化は 1c の手順2以降を再利用する（`tech_auth.md` §8 冒頭） | 3-A-1c の完了 | `step3a2-auth`<br>backend | `test-list` → `dev` |
| 3 | 移行 STEP 3-B（Phase 1 の game / battle / tower 移植）。**tower は `tech_tower.md` が無く分岐一覧も無い**ため詳細設計から始める（game・battle は分岐一覧あり。詳細は [carryover_notes.md](carryover_notes.md) §1） | 3-A の完了 | `step3b-phase1`<br>backend | `detail-design` → `test-list` → `dev` |
| 4 | **Phase 4 テストリスト作成（拠点・施設・鍛冶屋）**。`tech_base.md` §7・§8（36件）と `tech_forge_{enhance,craft,disassemble}.md` §9〜§11（74件）を失敗するテストへ展開する。Java 側の分岐マーカーは `check_branch_list.py --tests` が見ていない（[carryover_notes.md](carryover_notes.md) §1）ため照合は手で行う | Phase 4 の詳細設計②まで完了。③④とは独立 | `p4base-testlist`<br>backend | `test-list` |

- 上記に載らない**複数セッションにまたがる申し送り**（移行 STEP の順序・環境・確定済み仕様の波及）は [carryover_notes.md](carryover_notes.md) が持つ。着手前にそちらも見る
