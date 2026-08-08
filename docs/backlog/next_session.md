# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-08 / 対応コミット: `b2f20b1`（移行 STEP 3-A-1c を Red → Green にした）＋ `40d37c4`（Phase 4 鍛冶屋の詳細設計。§2 優先0 から消化）。`PlayerInitializationService`（`tech_auth.md` §8.2 手順2〜6）を新設して `AuthService.createGuest()` へ結線し、`POST /api/auth/guest` が Player・PlayerSettings・初期キャラ1体・9スロット・初期アイテムまで作るようになった。`mvn verify` 全モジュール成功・127件 PASS・JaCoCo branch 100%。**STEP 3-A-1 は完了**。手順・進捗の正は [java_migration.md](java_migration.md)

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
/backend-review 移行 STEP 3-A-1c の製造完了ゲート: 差分モード（既定）で 3-A-1a〜1c の Java 実装をレビューする。主対象は PlayerInitializationService（新規・手順2〜6）と AuthService の結線、レジストリ3件と Mapper 5件。観点は層の責務・トランザクション境界・規約適合
完了条件: レポートを docs/reviews/ へ出力し、指摘の要否をユーザーと合意するところまで（**修正の適用は次セッション**へ回す。profile.md §6 規律5）
参照: .claude/references/coding-standards-backend.md（正は docs/process/coding_standards_backend.md）と docs/reviews/ の既存レポート書式。実装は backend/afkgame-domain/src/main/java/com/afkgame/domain/service/
前提: `b2f20b1` で 3-A-1c が Green（mvn verify 成功・127件 PASS・JaCoCo branch 100%）。**読み取りのみなので worktree は作らない**（[worktree_guide.md](../process/worktree_guide.md) §5.1）。**判断が要る点2件**: ①新サービスは現在時刻を `Instant.now()` で直接取っている（規約 §2「現在時刻は外から受ける」に対し、既存 AuthService の流儀へ合わせた。`Clock` 注入へ寄せるかは規約側の判断）②新サービスにロガーを置いていない（ゲスト作成のログは AuthService が出す。ロガー名体系の未整備は known_issues #18）。**環境（2026-08-08 に実行確認済み）**: `mvn`・`java` は PATH にも JAVA_HOME にも無く、Bash から動くのは `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot" "/c/Users/tubas/AppData/Local/Programs/apache-maven-3.9.11/bin/mvn" -f backend/pom.xml verify` の形（JAVA_HOME をインラインで与えないと "JAVA_HOME is not defined correctly" で落ちる。出力は CP932 なのでログはファイルへ落として `iconv -f CP932 -t UTF-8` で読む）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 0 | **Phase 4 詳細設計 ③限界突破** `POST /api/character/limit-break`。素材＝同一 `master_id` のキャラ1体を消費し `limit_break` を+1（上限5回。ボーナスの正は `master/character.md` §8.1）。探索中は不可（`tech_state.md` §4）。重複の発生源とレスポンスの `canLimitBreak` は `tech_scout.md` §6 が正 | ①完了済み（`master_id`・重複仕様が確定） | `p4limitbreak-detail`<br>docs/tech | `detail-design` |
| 1 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 2 | **`test_check_branch_list.py` の35件が setup エラーで実行できない**のを直す。`fbf2073` が `TEST_DIR` を `PY_TEST_DIR` + `JAVA_TEST_GLOB` へ分けた際にテスト側が追随せず、`monkeypatch.setattr(mod, "TEST_DIR", ...)`（28・199行目）が `AttributeError`。**本体は動くが回帰テストが無い状態**。あわせて Java 走査（`JAVA_TEST_GLOB`）のテストを足す | なし（`fbf2073` 済み） | `fix-branchlist-tests`<br>scripts | `dev` |
| 3 | **Phase 4 テストリスト作成（拠点・施設・鍛冶屋）**。`tech_base.md` §7・§8（36件）と `tech_forge_{enhance,craft,disassemble}.md` §9〜§11（74件）を失敗するテストへ展開する。分岐マーカーの照合は `check_branch_list.py --tests` が Java でも効く（`fbf2073`）ので手照合は不要 | Phase 4 の詳細設計②まで完了。③④とは独立 | `p4base-testlist`<br>backend | `test-list` |
| 4 | 移行 STEP 3-A-2（register / login / logout）。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は java_migration.md §4 の 2-B 表）。初期化は `PlayerInitializationService.initialize()` をそのまま呼ぶ（`tech_auth.md` §8 冒頭） | なし（3-A-1c 完了済み） | `step3a2-auth`<br>backend | `test-list` → `dev` |

- 上記に載らない**複数セッションにまたがる申し送り**（移行 STEP の順序・環境・確定済み仕様の波及）は [carryover_notes.md](carryover_notes.md) が持つ。着手前にそちらも見る
