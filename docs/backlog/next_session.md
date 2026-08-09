# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / `4affa5a` で **移行 STEP 2R-B（REST 専用の土台）を完了**した。Archetype `5.11.0.RELEASE` から雛形を生成し、`backend/` を**非Boot の4モジュール**（`afkgame-{web,domain,env,initdb}`）へ差し替え済み。`mvn clean install` が exit 0 で `afkgame-web.war` を出す（増分の `mvn verify` も exit 0）。Thymeleaf・画面 HTML 12件・`styles.css`・`-selenium`・雛形の Maven プロファイル・H2・`sql-maven-plugin`・CodeList・トランザクショントークンを除去し、**failsafe に `verify` ゴール**を追加した。Java Config 6種は `com.afkgame.{domain,web,env}.config.*` に配置済み。**既存コード73件は `backend/_migration/` に退避してある**（2R-D・2R-E で `git mv` で戻し、空にして削除する）。**Boot 依存は main コード6件だけ**と判明したため Entity 7件・マッピング XML・マスターデータ YAML・`V1` は現位置のまま。STEP 2R の次は **2R-C**。

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
/dev 移行 STEP 2R-C（設定の移植）: `web.xml`・Java Config・`META-INF/spring/*.properties`・`logback.xml`・DataSource・Flyway 起動を、雛形の既定値から本プロジェクトの設定へ移す
完了条件: `mvn clean install` が exit 0 のまま、①`afkgame.properties` に [tech_structure_backend.md](../tech/basic/tech_structure_backend.md) §4.2 の10キー＋認証系定数が入り、`@Value` で読む**設定保持 Bean を `afkgame-env` に1か所**用意（`@ConfigurationProperties` は使わない）②Flyway を `@Bean(initMethod = "migrate")` で起動し DB を使う Bean へ `@DependsOn("flyway")` ③`logback.xml` へ既存の text/json 切替を移植（`<springProfile>` は使えない）④**Jackson 3（`tools.jackson`）と 2（`com.fasterxml`）のどちらの `HttpMessageConverter` を使うかをここで決める**（雛形の依存に同居する）⑤CSRF の扱いを決める（雛形の既定は有効。REST + JWT の正は `tech_security.md` §11.2）
参照: 設定値・キー命名の正は [tech_structure_backend.md](../tech/basic/tech_structure_backend.md) §4.2、ログは [tech_logging.md](../tech/basic/tech_logging.md)「設定値」、環境変数の上書きは [tech_operations.md](../tech/nonfunctional/tech_operations.md) §12.2。移植元は `backend/_migration/afkgame-env/` の `application*.yml` 3件・`logback-*.xml` 3件・`config/` 4件
前提: main の最新 `4affa5a`（2R-B 完了。土台はビルドが通る状態）。**backend を触るので worktree を作る**: `python scripts/worktree.py add tera-2rc` → `EnterWorktree` に `path` で移動（領域は backend）。**JDK 17 と Maven は新規シェルで確認済み**（Maven 3.9.11 / Adoptium 17.0.20。PATH・`JAVA_HOME` とも反映済み）。出力は CP932 なのでログはファイルへ落として `iconv -f CP932 -t UTF-8` で読む。**`afkgame-infra.properties` は今 local 固定値**（`jdbc:postgresql://localhost:5432/afkgame`）で、環境変数の上書きは未配線。**`/health` の version 用リソースフィルタ（`resources-filtered/META-INF/spring/build.properties`）は未設定**。Docker デーモンは停止中だが `mvn clean install` には不要。**[steps.md](java_migration/steps.md) は残り250字**なので、追記するなら同じ編集で圧縮も行う
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 0 | **移行 STEP 2R-D（既存実装の移植）**。`_migration/` の main コード42件を戻して Boot 依存6件を除去する（`@ConfigurationProperties` 3 → 設定保持 Bean、`JsonLogFormatter`、`AfkgameApplication` 廃止、`HealthApi` の `BuildProperties` → リソースフィルタ）。`domain/{repository,service,masterdata,rng}` は Boot 非依存だが **jjwt と `jackson-dataformat-yaml` の依存追加が要る** | **2R-C 完了後**。流儀と落とし穴は [carryover_notes.md](carryover_notes.md) §2、退避先の一覧は [steps.md](java_migration/steps.md) §4「2R-B の結果」 | `tera-2rd`<br>backend | `dev` |
| 1 | **Phase 4 ③限界突破の詳細設計**。`POST /api/character/limit-break` の処理フロー・計算式・分岐一覧を `docs/tech/detail/tech_limitbreak.md`（新規）へ。素材＝同一 `master_id` のキャラ1体で `limit_break` +1（上限5回）。`check_branch_list.py` を exit 0 にする。起点は [character.md](../data/master/character.md) §8・§8.1（ボーナス数値の正）、可否は [tech_state.md](../tech/detail/tech_state.md) §4、`canLimitBreak` は [tech_scout.md](../tech/detail/tech_scout.md) §6。**`characters.master_id` は Phase 4 で追加する未実装列**（定義書とER図のみ記載済み） | なし | `p4limitbreak-detail`<br>docs/tech | `detail-design` |
| 2 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 3 | **移行 STEP 2R-E（テスト基盤の再構築）**。`_migration/` のテスト28件を戻し、`@SpringBootTest`/`@AutoConfigureMockMvc`/zonky を `SpringExtension` + `@ContextConfiguration` + `EmbeddedPostgres.builder().start()` へ置き換える。surefire/failsafe/JaCoCo の分離設定（branch 100%）を入れ直し、**`_migration/` を空にして削除する** | **2R-D 完了後**。`afkgame-initdb` の surefire skip は外さない（[carryover_notes.md](carryover_notes.md) §4） | `tera-2re`<br>backend | `test-list` |
| 4 | **移行 STEP 2R-F（実行・デプロイの切替）**。Tomcat 11.0 への war 配備手順、Vite プロキシ、`launch.json`、[tech_operations.md](../tech/nonfunctional/tech_operations.md) §12 の反映。`GET /health` が 200（`db:ok`）でゲスト認証が通るところまで通す＝**STEP 2R の完了判定** | **2R-E 完了後**。E2E ハーネスの起動手順は [carryover_notes.md](carryover_notes.md) §4 | `tera-2rf`<br>backend | `dev` |

- **2R 完了後に解禁される行**（キューが空いたら戻す）: Phase 4 テストリスト作成（拠点・施設・鍛冶屋。`tech_base.md` §7・§8 の36件 + `tech_forge_*` の74件）、移行 STEP 3-A-2（register / login / logout）。順序の正は [carryover_notes.md](carryover_notes.md) §1
- 上記に載らない**複数セッションにまたがる申し送り**は [carryover_notes.md](carryover_notes.md) が持つ。着手前にそちらも見る
