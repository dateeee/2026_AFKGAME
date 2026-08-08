# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-08 / 対応コミット: `885c644`（移行 STEP 3-A-1 レビュー指摘のセグメント1 = 高・中6件を適用）。レビュー結果は [docs/reviews/backend-review/2026-08-08_221814.md](../reviews/backend-review/2026-08-08_221814.md)（差分・11件・高1/中6/低4）で、**残りは低4件 + ドキュメント（ISSUE-607〜611）= セグメント2**。これを終えると製造完了ゲートが閉じる。手順・進捗の正は [java_migration.md](java_migration.md)

**セグメント1で決まった流儀（STEP 3-A-2 以降へ波及する）**: ①**`APP_ENV` は必須**（`application.yml` の既定値 `local` を廃止。未設定なら起動失敗）。**`@SpringBootTest` には `@ActiveProfiles("local")` を付ける**（付けないとコンテキストロードで落ちる）②**時刻は `Clock` を受け取る**（ISSUE-605 案A）。`afkgame-env` の `TimeConfig` が `Clock.systemUTC()` を Bean 化しているので、新しいサービスは `Instant.now()` を直接呼ばずコンストラクタで受ける ③**`PlayerInitializationService.initialize()` は `Propagation.MANDATORY`**。register からも `@Transactional` 配下で呼ぶ（外から呼ぶと `IllegalTransactionStateException`）④`tech_auth.md` §8.3 に**分岐 #11（初期所持アイテムのID重複）を新設**したため、初期化トランザクションの2行は **#12・#13** へ繰り下がっている。

**レビュー由来の未消化2件**（本ファイルの行にしない申し送り。正は [carryover_notes.md](carryover_notes.md)）: Java 規約チェッカーの常設化と、Phase 3 Python 実装（`c3e9a2b`）が未レビューである件。

**分岐マーカーの照合が Java へ効くようになった**（`fbf2073`）。`check_branch_list.py --tests` の走査先が移行前の `backend/tests/unit/*.py` だけで、かつ節番号の正規表現が `§8.3` の枝番に非対応だったため、Java テストのマーカーを1件も見ていなかった。両方を直したので、以後は `--tests` の exit 0 が対応漏れゼロの根拠になる（手で照合しなくてよい）。

**`#2` の決着**: `uq_players_user_id` 違反に対応する業務エラーコードは AUTH_ 一覧に無く、公開APIからは到達しない経路のため**新設しない**。`DuplicateKeyException` をそのまま送出し 500 `INTERNAL_UNEXPECTED_ERROR` として扱う（3-A-2 の register でも同じ判断を使う）。

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
/dev 移行 STEP 3-A-1 レビュー指摘の修正適用 セグメント2: docs/reviews/backend-review/2026-08-08_221814.md の低4件 + ドキュメント（ISSUE-607〜611）を適用する。これで11件すべてが片付き、STEP 3-A-1 の製造完了ゲートが閉じる（.claude/project/dev.md §7「指摘対応まで完了してゲート通過」）
完了条件: `mvn verify` 全モジュール成功・JaCoCo branch 100% 維持。ドキュメントを触ったら `python scripts/check_doc_size.py` と `python scripts/check_docs.py`、分岐を足したら `python scripts/check_branch_list.py --tests` を exit 0 にする
参照: docs/reviews/backend-review/2026-08-08_221814.md の ISSUE-607（行347）〜ISSUE-611（行514）が起点。**ISSUE-607 は登録が要るのは 4xx のみ**（セグメント1の ISSUE-604 で 5xx は `INTERNAL_UNEXPECTED_ERROR` へ寄せたため、`HTTP_<status>` が出るのは 4xx だけになった）。ISSUE-608（静的 `SecureRandom`）で規約本文を改訂するなら派生の .claude/references/coding-standards-backend.md も同時更新（profile.md §7 規約6）で、**`coding_standards_backend.md` は 7,984字 / 残り16字**なので既存節の圧縮を同じ編集にまとめる（同 規約7）
前提: `885c644` でセグメント1（ISSUE-601〜606）を適用済み（`mvn verify` BUILD SUCCESS・テスト131件 PASS・JaCoCo branch 100%）。**ファイルを編集するので worktree を作る**: `python scripts/worktree.py add fix-bereview-3a1b` → `EnterWorktree` に `path` で移動（領域は backend + docs。§2 優先3・優先4 と backend が重なるため並行させない）。**環境（2026-08-08 に実行確認済み）**: `mvn`・`java` は PATH にも JAVA_HOME にも無く、Bash から動くのは `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot" "/c/Users/tubas/AppData/Local/Programs/apache-maven-3.9.11/bin/mvn" -f backend/pom.xml verify` の形（出力は CP932 なのでログはファイルへ落として `iconv -f CP932 -t UTF-8` で読む）。**1クラスだけ流すときは `-Dtest=X -Dsurefire.failIfNoSpecifiedTests=false`、`-pl <module>` には必ず `-am` を付ける**（付けないと `~/.m2` の古い成果物を拾い、変更が効いていない結果を見る）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 0 | **Phase 4 詳細設計 ③限界突破** `POST /api/character/limit-break`。素材＝同一 `master_id` のキャラ1体を消費し `limit_break` を+1（上限5回。ボーナスの正は `master/character.md` §8.1）。探索中は不可（`tech_state.md` §4）。重複の発生源とレスポンスの `canLimitBreak` は `tech_scout.md` §6 が正 | ①完了済み（`master_id`・重複仕様が確定） | `p4limitbreak-detail`<br>docs/tech | `detail-design` |
| 1 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 2 | **`test_check_branch_list.py` の35件が setup エラーで実行できない**のを直す。`fbf2073` が `TEST_DIR` を `PY_TEST_DIR` + `JAVA_TEST_GLOB` へ分けた際にテスト側が追随せず、`monkeypatch.setattr(mod, "TEST_DIR", ...)`（28・199行目）が `AttributeError`。**本体は動くが回帰テストが無い状態**。あわせて Java 走査（`JAVA_TEST_GLOB`）のテストを足す | なし（`fbf2073` 済み） | `fix-branchlist-tests`<br>scripts | `dev` |
| 3 | **Phase 4 テストリスト作成（拠点・施設・鍛冶屋）**。`tech_base.md` §7・§8（36件）と `tech_forge_{enhance,craft,disassemble}.md` §9〜§11（74件）を失敗するテストへ展開する。分岐マーカーの照合は `check_branch_list.py --tests` が Java でも効く（`fbf2073`）ので手照合は不要 | Phase 4 の詳細設計②まで完了。③④とは独立 | `p4base-testlist`<br>backend | `test-list` |
| 4 | 移行 STEP 3-A-2（register / login / logout）。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は java_migration.md §4 の 2-B 表）。初期化は `PlayerInitializationService.initialize()` をそのまま呼ぶ（`tech_auth.md` §8 冒頭） | **着手可**（`885c644` でセグメント1が済み、`initialize()` の呼び方と時刻の受け取り方が確定した）。書くときは本ファイル冒頭「セグメント1で決まった流儀」の①〜③に従う | `step3a2-auth`<br>backend | `test-list` → `dev` |

- 上記に載らない**複数セッションにまたがる申し送り**（移行 STEP の順序・環境・確定済み仕様の波及）は [carryover_notes.md](carryover_notes.md) が持つ。着手前にそちらも見る
