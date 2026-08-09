# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / `638a889` で **移行 STEP 2R-C（設定の移植）を完了**した。`afkgame.properties`（§4.2 の10キー＋認証系定数）と**設定保持 Bean**（`env/config/{Game,Auth,Cors}Settings` を `AfkgameSettingsConfig` が `@Value` で1か所から組む。`@ConfigurationProperties` は不使用）、Flyway の `@Bean(initMethod = "migrate")` ＋ `@DependsOn("flyway")`、`logback.xml` の text/json 切替、**Jackson 3（`tools.jackson`）採用**、**CSRF 無効 + STATELESS** まで入れた。`mvn clean install` は exit 0。環境差分は `@Profile("local")` + `@PropertySource`（`META-INF/spring/local/`）で、**起動には `SPRING_PROFILES_ACTIVE`（`local` / `production`）が必須**（未設定なら起動時に落ちる）。**`LOG_FORMAT=json` の項目名は logback 既定のままの暫定**で、`JsonLogFormatter` の差し替えは 2R-D。**既存コード73件は引き続き `backend/_migration/` にある**（2R-D・2R-E で `git mv` で戻し、空にして削除する）。STEP 2R の次は **2R-D**。

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
/dev 移行 STEP 2R-D（既存実装の移植）: `backend/_migration/` の main コード42件を `git mv` で戻し、Boot 依存を除去して 2R-C の非Boot 構成へつなぐ
完了条件: `mvn clean install` が exit 0 のまま、①`env/config` の `@ConfigurationProperties` 3件（`AuthProperties`・`CorsProperties`・`GameProperties`）は**戻さず削除**し、参照側を 2R-C の `com.afkgame.env.config.{Auth,Cors,Game}Settings` へ差し替える②`JsonLogFormatter` を Boot の `StructuredLogEncoder` 非依存の形へ書き直し、`logback-encoder-json.xml` の暫定（logback 既定の項目名）を仕様どおりの項目へ差し替える③`AfkgameApplication` を廃止する（war 起動のため不要）④`HealthApi` の `BuildProperties` を Maven のリソースフィルタ（`src/main/resources-filtered/META-INF/spring/build.properties` へ `${project.version}`）へ置き換える⑤`_migration` の `web/config/SecurityConfig` の CORS・JWT フィルタ連鎖を 2R-C の `SpringSecurityConfig`（csrf 無効・STATELESS 済み）へ**統合する**（`@EnableWebSecurity` を二重に作らない）⑥`afkgame-domain` へ jjwt と `jackson-dataformat-yaml` を追加（版は Maven Central へ1回問い合わせてから書く）
参照: 退避先の一覧は [steps.md](java_migration/steps.md) §4「2R-B の結果」、2R-C で確定した設定の受け取り方は同§4「2R-C の結果」、実装の流儀と落とし穴は [carryover_notes.md](carryover_notes.md) §2。JSON ログの項目は [tech_logging.md](../tech/basic/tech_logging.md)「ログフォーマット」、`/health` の version は [steps.md](java_migration/steps.md) §4「2R-0 の確定結果」
前提: main の最新 `638a889`（2R-C 完了。ビルドが通る状態）。**backend を触るので worktree を作る**: `python scripts/worktree.py add tera-2rd` → `EnterWorktree` に `path` で移動（領域は backend）。**JDK 17 と Maven は新規シェルで確認済み**（Maven 3.9.11 / Adoptium 17.0.20。PATH・`JAVA_HOME` とも反映済み）。出力は CP932 なのでログはファイルへ落として `iconv -f CP932 -t UTF-8` で読む。**テスト28件は戻さない**（2R-E の担当）。**コンテキストを起こす確認には `SPRING_PROFILES_ACTIVE=local` が要る**。Docker デーモンは停止中だが `mvn clean install` には不要。**[steps.md](java_migration/steps.md) は残り5字・[carryover_notes.md](carryover_notes.md) は残り23字**なので、記録は [changelog.md](../changelog.md)（上限対象外）へ寄せ、両ファイルは状態セルの更新だけに留める
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **Phase 4 ③限界突破の詳細設計**。`POST /api/character/limit-break` の処理フロー・計算式・分岐一覧を `docs/tech/detail/tech_limitbreak.md`（新規）へ。素材＝同一 `master_id` のキャラ1体で `limit_break` +1（上限5回）。`check_branch_list.py` を exit 0 にする。起点は [character.md](../data/master/character.md) §8・§8.1（ボーナス数値の正）、可否は [tech_state.md](../tech/detail/tech_state.md) §4、`canLimitBreak` は [tech_scout.md](../tech/detail/tech_scout.md) §6。**`characters.master_id` は Phase 4 で追加する未実装列**（定義書とER図のみ記載済み） | なし | `p4limitbreak-detail`<br>docs/tech | `detail-design` |
| 2 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 3 | **移行 STEP 2R-E（テスト基盤の再構築）**。`_migration/` のテスト28件を戻し、`@SpringBootTest`/`@AutoConfigureMockMvc`/zonky を `SpringExtension` + `@ContextConfiguration` + `EmbeddedPostgres.builder().start()` へ置き換える。surefire/failsafe/JaCoCo の分離設定（branch 100%）を入れ直し、**`_migration/` を空にして削除する** | **2R-D 完了後**。`afkgame-initdb` の surefire skip は外さない（[carryover_notes.md](carryover_notes.md) §4） | `tera-2re`<br>backend | `test-list` |
| 4 | **移行 STEP 2R-F（実行・デプロイの切替）**。Tomcat 11.0 への war 配備手順、Vite プロキシ、`launch.json`、[tech_operations.md](../tech/nonfunctional/tech_operations.md) §12 の反映。`GET /health` が 200（`db:ok`）でゲスト認証が通るところまで通す＝**STEP 2R の完了判定** | **2R-E 完了後**。E2E ハーネスの起動手順は [carryover_notes.md](carryover_notes.md) §4 | `tera-2rf`<br>backend | `dev` |
| 5 | **移行バックログ2件の文字数是正**。[steps.md](java_migration/steps.md)（残り5字）と [carryover_notes.md](carryover_notes.md)（残り23字）を分割する。どちらも H2 が上限超過で、圧縮では解消しない（残量WARN 22件の棚卸しも同時に） | なし。ただし**移行 STEP と同時に走らせない**（同じファイルを触る） | `docsize-migration`<br>docs/backlog | `doc-size` |

- **2R 完了後に解禁される行**（キューが空いたら戻す）: Phase 4 テストリスト作成（拠点・施設・鍛冶屋。`tech_base.md` §7・§8 の36件 + `tech_forge_*` の74件）、移行 STEP 3-A-2（register / login / logout）。順序の正は [carryover_notes.md](carryover_notes.md) §1
- 上記に載らない**複数セッションにまたがる申し送り**は [carryover_notes.md](carryover_notes.md) が持つ。着手前にそちらも見る
