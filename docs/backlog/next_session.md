# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / `116282b` で **移行 STEP 2R-A（仕様書・規約の再改訂）を完了**した。2R-0 の確定6件を仕様書・規約・設計図へ反映済み（war + Tomcat 11.0／環境識別を `APP_ENV` から **`SPRING_PROFILES_ACTIVE` へ統合**／設定は `META-INF/spring/*.properties` + 設定保持 Bean・**キーはドット区切りのみ**／Flyway は `@Bean(initMethod = "migrate")` + `@DependsOn`／`/health` の version は Maven のリソースフィルタ／ログは `logback.xml`／統合テストは `SpringExtension` + `@ContextConfiguration`・failsafe に `verify` ゴール）。あわせて上限に張り付いていた2ファイルを分割し、**[tech_structure_backend.md](../tech/basic/tech_structure_backend.md)（§4）と [tech_operations_procedure.md](../tech/nonfunctional/tech_operations_procedure.md)（§12.4〜§12.7）を新設**した。STEP 2R の次は **2R-B**。

**Phase 機能の backend 作業は STEP 2R 完了まで着手しない**（2R 自身のセグメントは除く）。バックエンドは Spring Boot ではなく **Terasoluna ブランクプロジェクト準拠（war + Tomcat）** で作り直すことが決まっており（既存 Java 80ファイル中70ファイルが Boot 依存）、テスト基盤ごと入れ替わるため先に書いたコード・テストは書き直しになる。方針の正は [tech_selection.md](java_migration/tech_selection.md) §2、手順・進捗の正は [java_migration.md](java_migration.md)（索引 + `java_migration/` 3分冊）。

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
/dev 移行 STEP 2R-B（REST 専用の土台を作る）: Archetype `terasoluna-gfw-multi-web-blank-thymeleaf-mybatis3-archetype` の `5.11.0.RELEASE` で雛形を生成し、Thymeleaf・Welcome/エラー画面・静的リソース一式と `-selenium` を落として REST 専用のマルチモジュール構成にする
完了条件: `backend/` が `afkgame-{web,domain,env,initdb}` の4モジュールで `mvn clean install` が exit 0、`afkgame-web` の war が出ること。**雛形の failsafe に `verify` ゴールの execution を足す**（無いと結合テストが失敗してもビルドが通る。2R-0 の確定結果）。既存 Boot 実装の移植は 2R-D、設定の移植は 2R-C の担当なので**本タスクでは触らない**
参照: 生成方式と Archetype の正は [tech_selection.md](java_migration/tech_selection.md) §2「モジュール構成」、確定済みの前提6件は [steps.md](java_migration/steps.md) §4「2R-0 の確定結果」。**配置・技術スタック・設定値の正は [tech_structure_backend.md](../tech/basic/tech_structure_backend.md) §4〜§4.2**（2R-A で反映済み。ここを見ながら組む）
前提: main の最新 `116282b`（2R-A 完了。STEP 2R は着手中で、次が本タスク）。**backend を触るので worktree を作る**: `python scripts/worktree.py add tera-2rb` → `EnterWorktree` に `path` で移動（領域は backend）。**JDK 17 と Maven は PATH・`JAVA_HOME` とも新規シェルへ反映済み**（2026-08-09 に新規シェルで `mvn -version` を実行して確認。Maven 3.9.11 / Adoptium 17.0.20。**以前の「フルパスが要る」という申し送りは解消済み**）。出力は CP932 なのでログはファイルへ落として `iconv -f CP932 -t UTF-8` で読む。`clean` を外すと `afkgame-initdb` が落ちる（理由は carryover_notes.md §4）。**Docker Desktop のデーモンは停止している**（`docker` CLI はある）が、`mvn verify` は埋め込み PostgreSQL なので不要。**main には untracked の残骸がある** — `backend/afkgame.db`・`backend/e2e.db` ほか Python 時代のディレクトリ（`.gitignore` 済みで `git status` には出ない）。消すならユーザーの承認を取ってから `rm -rf` する
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 0 | **移行 STEP 2R-C（設定の移植）**。`web.xml`・Java Config 6種・`META-INF/spring/*.properties`・`logback.xml`・DataSource・Flyway 起動を組む。**Jackson 3（`tools.jackson`）と 2（`com.fasterxml`）のどちらの `HttpMessageConverter` を使うかをここで決める**（雛形の依存に同居する。[tech_selection.md](java_migration/tech_selection.md) §2） | **2R-B 完了後**。設定値・キー命名・ログ切替の正は [tech_structure_backend.md](../tech/basic/tech_structure_backend.md) §4.2 と [tech_logging.md](../tech/basic/tech_logging.md)「設定値」 | `tera-2rc`<br>backend | `dev` |
| 1 | **Phase 4 ③限界突破の詳細設計**。`POST /api/character/limit-break` の処理フロー・計算式・分岐一覧を `docs/tech/detail/tech_limitbreak.md`（新規）へ。素材＝同一 `master_id` のキャラ1体で `limit_break` +1（上限5回）。`check_branch_list.py` を exit 0 にする。起点は [character.md](../data/master/character.md) §8・§8.1（ボーナス数値の正）、可否は [tech_state.md](../tech/detail/tech_state.md) §4、`canLimitBreak` は [tech_scout.md](../tech/detail/tech_scout.md) §6。**`characters.master_id` は Phase 4 で追加する未実装列**（定義書とER図のみ記載済み） | なし | `p4limitbreak-detail`<br>docs/tech | `detail-design` |
| 2 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 3 | **Phase 4 テストリスト作成（拠点・施設・鍛冶屋）**。`tech_base.md` §7・§8（36件）と `tech_forge_{enhance,craft,disassemble}.md` §9〜§11（74件）を失敗するテストへ展開する。分岐マーカーの照合は `check_branch_list.py --tests` が Java でも効く | Phase 4 の詳細設計②まで完了。**ただし 2R 完了まで着手しない**（テスト基盤が非Boot へ入れ替わり、書いたテストが書き直しになる） | `p4base-testlist`<br>backend | `test-list` |
| 4 | 移行 STEP 3-A-2（register / login / logout）。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は [steps.md](java_migration/steps.md) §4 の 2-B 表）。初期化は `PlayerInitializationService.initialize()` をそのまま呼ぶ（`tech_auth.md` §8 冒頭） | **2R 完了まで着手不可**。`885c644` で確定した `initialize()` の呼び方と時刻の受け取り方は [carryover_notes.md](carryover_notes.md) §2 が持つ | `step3a2-auth`<br>backend | `test-list` → `dev` |

- 上記に載らない**複数セッションにまたがる申し送り**は [carryover_notes.md](carryover_notes.md) が持つ。着手前にそちらも見る
