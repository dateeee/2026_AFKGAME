# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / `156126f` で **`wt/drop-python-backend` を main へ統合**した（Python/FastAPI 削除、移行 STEP 6 の1〜3が完了）。あわせて `.vscode/launch.json` を Java 構成へ差し替え、ER図に `characters.master_id` を足して `check_schema_triple.py` を exit 0 へ戻した。

**backend を触る作業は STEP 2R 完了まで着手しない**。バックエンドは Spring Boot ではなく **Terasoluna ブランクプロジェクト準拠（war + Tomcat）** で作り直すことが決まっており（既存 Java 80ファイル中70ファイルが Boot 依存）、テスト基盤ごと入れ替わるため先に書いたコード・テストは書き直しになる。方針の正は [tech_selection.md](java_migration/tech_selection.md) §2、手順・進捗の正は [java_migration.md](java_migration.md)（索引 + `java_migration/` 3分冊）。

**Phase 1〜3 の機能はどの言語でも未実装の期間**に入っている（Python 削除を STEP 3〜5 より先に実施したため）。E2E はハーネスと `GET /health` まで疎通済みだが、テスト本体は STEP 5 完了まで赤が正常。

**Phase 4 詳細設計**: 拠点・施設（`tech_base.md`）・①酒場スカウト（`tech_scout.md`）・②鍛冶屋（`tech_forge.md` + 操作別3件）は完了。残りは③限界突破（§1）と④塔6〜8（§2）。

**複数セッションにまたがる申し送りの正は [carryover_notes.md](carryover_notes.md)**（移行 STEP の順序 / Java 実装の流儀と落とし穴 / 確定済み仕様の波及 / 環境・ツール）。着手前にそちらも見る。

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
/detail-design Phase 4 詳細設計 ③限界突破: `POST /api/character/limit-break` の処理フロー・計算式・分岐一覧を確定する。素材＝同一 `master_id` のキャラ1体を消費して `limit_break` を +1（上限5回）
完了条件: `docs/tech/detail/tech_limitbreak.md`（新規）に処理フローと**分岐一覧**を書き、`python scripts/check_branch_list.py` を exit 0 にする。`python scripts/check_doc_size.py` と `python scripts/check_docs.py` も exit 0（区分C・8,000字。超えるなら索引 + 個別ファイル構成にする）
参照: 起点は [docs/data/master/character.md](../data/master/character.md) §8「限界突破（Phase 4〜）」・§8.1「限界突破ボーナス」（**ボーナス数値の正**。実在を確認済み）。**探索中は不可**の根拠は [tech_state.md](../tech/detail/tech_state.md) §4「状態 × 操作の可否」、**素材となる重複キャラの発生源とレスポンスの `canLimitBreak`** は [tech_scout.md](../tech/detail/tech_scout.md) §6「API」が正。列は [tech_db/player.md](../tech/basic/tech_db/player.md) §4「`characters`」で、**`master_id` は Phase 4 で追加する未実装列**（本設計がその追加を確定させる。定義書とER図には記載済み、DDL と Entity は未追加。`InitialCharacterData.id` の Javadoc も同列の追加を待っている）。API 共通規約は `tech_api_common.md`、分岐一覧の記法は [.claude/project/detail-design.md](../../.claude/project/detail-design.md) §4（**見出しは1段のみ**。`###` を重ねると checker が ERROR）
前提: `156126f`（統合済み・main は clean）。**ドキュメントのみを編集するので worktree を作る**: `python scripts/worktree.py add p4limitbreak-detail` → `EnterWorktree` に `path` で移動（領域は docs/tech。§2 の他行と重ならない）。**本タスクは Java を使わない**。必要になった場合の実行形は `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot" "/c/Users/tubas/AppData/Local/Programs/apache-maven-3.9.11/bin/mvn" -f backend/pom.xml clean verify`（2026-08-09 に新規シェルで `mvn -v` 実行確認済み。`mvn`・`java` は PATH にも JAVA_HOME にも無い。出力は CP932 なのでログはファイルへ落として `iconv -f CP932 -t UTF-8` で読む。**`clean` を外すと増分ビルドで `afkgame-initdb` が落ちる** — 理由は carryover_notes.md §4）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 0 | **移行 STEP 2R-0（先行検証）**。[steps.md](java_migration/steps.md) §4「2R-0 で先に潰す不確定要素」の6件（Tomcat の下限版 / 埋め込み PostgreSQL の非Boot 起動 / Flyway の明示起動 / `/health` の version 供給元 / `terasoluna-gfw-parent` の pluginManagement 競合 / プロファイル切替）を実機で確定し、2R-B 以降を実施可能な粒度にする | なし（`156126f` で統合済み）。詰まった項目が出たら [tech_selection.md](java_migration/tech_selection.md) §2 の版・方式を見直す | `tera-2r0`<br>backend | `basic-design` |
| 1 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 2 | **Phase 4 テストリスト作成（拠点・施設・鍛冶屋）**。`tech_base.md` §7・§8（36件）と `tech_forge_{enhance,craft,disassemble}.md` §9〜§11（74件）を失敗するテストへ展開する。分岐マーカーの照合は `check_branch_list.py --tests` が Java でも効く | Phase 4 の詳細設計②まで完了。**ただし 2R 完了まで着手しない**（テスト基盤が非Boot へ入れ替わり、書いたテストが書き直しになる） | `p4base-testlist`<br>backend | `test-list` |
| 3 | 移行 STEP 3-A-2（register / login / logout）。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は [steps.md](java_migration/steps.md) §4 の 2-B 表）。初期化は `PlayerInitializationService.initialize()` をそのまま呼ぶ（`tech_auth.md` §8 冒頭） | **2R 完了まで着手不可**。`885c644` で確定した `initialize()` の呼び方と時刻の受け取り方は [carryover_notes.md](carryover_notes.md) §2 が持つ | `step3a2-auth`<br>backend | `test-list` → `dev` |

- 上記に載らない**複数セッションにまたがる申し送り**（移行 STEP の順序・環境・確定済み仕様の波及）は [carryover_notes.md](carryover_notes.md) が持つ。着手前にそちらも見る
