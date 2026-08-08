# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-08 / 対応コミット: `679c7e7`（移行 STEP 3-A-1 レビュー指摘のセグメント2 = 低4件 + ドキュメント ISSUE-607〜611 を適用）。**11件すべてが片付き、STEP 3-A-1 の製造完了ゲートが閉じた**（`mvn clean verify` BUILD SUCCESS・単体89 + 結合45 = 134件 PASS・JaCoCo branch 100%）。レビュー結果は [docs/reviews/backend-review/2026-08-08_221814.md](../reviews/backend-review/2026-08-08_221814.md)。手順・進捗の正は [java_migration.md](java_migration.md)

**セグメント2で決まった流儀**: ①**Mapper XML の resultMap は明示するが、理由に「自動変換と食い違うため」と書かない**（規約 §3 は変換を `map-underscore-to-camel-case` に任せる方針。実際に食い違うのは `is_guest` ↔ `guest` のような boolean getter だけで、`UserMapper.xml` のコメントのみが正しい）②**マスターデータレジストリ4件は `(loader, resourcePath)` の package-private コンストラクタと `contains()` を持つ形にそろった**。5件目を足すときはこれに倣う（異常系フィクスチャは domain 側 `masterdata-invalid/` に置き、web 側の統合テストはコンテキスト起動失敗の検証だけに使う）

**セグメント1で決まった流儀（STEP 3-A-2 以降へ波及する）**: ①**`APP_ENV` は必須**（`application.yml` の既定値 `local` を廃止。未設定なら起動失敗）。**`@SpringBootTest` には `@ActiveProfiles("local")` を付ける**（付けないとコンテキストロードで落ちる）②**時刻は `Clock` を受け取る**（ISSUE-605 案A）。`afkgame-env` の `TimeConfig` が `Clock.systemUTC()` を Bean 化しているので、新しいサービスは `Instant.now()` を直接呼ばずコンストラクタで受ける ③**`PlayerInitializationService.initialize()` は `Propagation.MANDATORY`**。register からも `@Transactional` 配下で呼ぶ（外から呼ぶと `IllegalTransactionStateException`）④`tech_auth.md` §8.3 に**分岐 #11（初期所持アイテムのID重複）を新設**したため、初期化トランザクションの2行は **#12・#13** へ繰り下がっている。

**レビュー由来の未消化2件**（本ファイルの行にしない申し送り。正は [carryover_notes.md](carryover_notes.md)）: Java 規約チェッカーの常設化と、Phase 3 Python 実装（`c3e9a2b`）が未レビューである件。

**コーディング規約が索引 + 4分冊になった**（`499e161`）。[coding_standards_backend.md](../process/coding_standards_backend.md) は索引（適用範囲・**準拠元 = TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版**・原則・分冊索引）だけを持ち、規約本体は `coding_standards_backend/` の common / domain / web / test にある。**common が旧 §2〜§9 の節番号を維持**しているので既存の節参照はパス変更だけで有効（旧 §4「レイヤ別の規約」のみ層別へ分解）。**規約はガイドラインとの差分だけを持つ**方針なので、追記の前にガイドライン側に同じ記述が無いかを見る。テストコードの記述規約の正も `.claude/project/test-list.md` §5 から `coding_standards_backend/test.md` へ移った。

**単体テストと結合テストが実行レベルで分かれた**（`641bab1`）。surefire が `@Tag("unit")` のみ、failsafe が `@Tag("integration")` のみを回し、**JaCoCo の C1 判定は単体テストだけで行う**（failsafe 側は `argLine` 上書きで agent を外している）。`mvn verify` は従来どおり両方を通す。Terasoluna ガイドラインとの差分（採らないと決めた DBUnit・`MockMvcTester`・`@InjectMocks` の理由、新規実装から適用するログ検証・Bean Validation 検証）の正は [.claude/project/unit-test.md](../../.claude/project/unit-test.md) §8。

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
/detail-design Phase 4 詳細設計 ③限界突破: `POST /api/character/limit-break` の処理フロー・計算式・分岐一覧を確定する。素材＝同一 `master_id` のキャラ1体を消費して `limit_break` を +1（上限5回）
完了条件: `docs/tech/detail/tech_limitbreak.md`（新規）に処理フローと**分岐一覧**を書き、`python scripts/check_branch_list.py` を exit 0 にする。`python scripts/check_doc_size.py` と `python scripts/check_docs.py` も exit 0（区分C・8,000字。超えるなら索引 + 個別ファイル構成にする）
参照: 起点は [docs/data/master/character.md](../data/master/character.md) §8「限界突破（Phase 4〜）」・§8.1「限界突破ボーナス」（**ボーナス数値の正**。実在を確認済み）。**探索中は不可**の根拠は [tech_state.md](../tech/detail/tech_state.md) §4「状態 × 操作の可否」、**素材となる重複キャラの発生源とレスポンスの `canLimitBreak`** は [tech_scout.md](../tech/detail/tech_scout.md) §6「API」が正。列は [tech_db/player.md](../tech/basic/tech_db/player.md) §4「`characters`」で、**`master_id` は Phase 4 で追加する未実装列**（本設計がその追加を確定させる。`InitialCharacterData.id` の Javadoc も同列の追加を待っている）。API 共通規約は `tech_api_common.md`、分岐一覧の記法は [.claude/project/detail-design.md](../../.claude/project/detail-design.md) §4（**見出しは1段のみ**。`###` を重ねると checker が ERROR）
前提: `679c7e7` で STEP 3-A-1 の製造完了ゲートが閉じている。**ドキュメントのみを編集するので worktree を作る**: `python scripts/worktree.py add p4limitbreak-detail` → `EnterWorktree` に `path` で移動（領域は docs/tech。§2 の他行と重ならない）。**コーディング規約は索引 + 4分冊になった**（下記）ので、規約へ触れる必要が出たら書き先は `coding_standards_backend/` 側。**環境（2026-08-08 に実行確認済み）**: 本タスクは Java を使わないが、必要なら `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot" "/c/Users/tubas/AppData/Local/Programs/apache-maven-3.9.11/bin/mvn" -f backend/pom.xml verify`（`mvn`・`java` は PATH にも JAVA_HOME にも無い。出力は CP932 なのでログはファイルへ落として `iconv -f CP932 -t UTF-8` で読む）。**`mvn verify` は増分ビルドだと `afkgame-initdb` で落ちるため `clean` を付ける**（理由は carryover_notes.md）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 0 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 1 | **`test_check_branch_list.py` の35件が setup エラーで実行できない**のを直す。`fbf2073` が `TEST_DIR` を `PY_TEST_DIR` + `JAVA_TEST_GLOB` へ分けた際にテスト側が追随せず、`monkeypatch.setattr(mod, "TEST_DIR", ...)`（28・199行目）が `AttributeError`。**本体は動くが回帰テストが無い状態**。あわせて Java 走査（`JAVA_TEST_GLOB`）のテストを足す | なし（`fbf2073` 済み） | `fix-branchlist-tests`<br>scripts | `dev` |
| 2 | **Phase 4 テストリスト作成（拠点・施設・鍛冶屋）**。`tech_base.md` §7・§8（36件）と `tech_forge_{enhance,craft,disassemble}.md` §9〜§11（74件）を失敗するテストへ展開する。分岐マーカーの照合は `check_branch_list.py --tests` が Java でも効く（`fbf2073`）ので手照合は不要 | Phase 4 の詳細設計②まで完了。③④とは独立 | `p4base-testlist`<br>backend | `test-list` |
| 3 | 移行 STEP 3-A-2（register / login / logout）。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は java_migration.md §4 の 2-B 表）。初期化は `PlayerInitializationService.initialize()` をそのまま呼ぶ（`tech_auth.md` §8 冒頭） | **着手可**（`885c644` でセグメント1が済み、`initialize()` の呼び方と時刻の受け取り方が確定した）。書くときは本ファイル冒頭「セグメント1で決まった流儀」の①〜③に従う | `step3a2-auth`<br>backend | `test-list` → `dev` |

- 上記に載らない**複数セッションにまたがる申し送り**（移行 STEP の順序・環境・確定済み仕様の波及）は [carryover_notes.md](carryover_notes.md) が持つ。着手前にそちらも見る
