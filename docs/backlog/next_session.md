# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。**着手前に §0 を読む**。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)、worktree 運用の正は [worktree_guide.md](../process/worktree_guide.md)。

最終更新: 2026-08-09 / `3933cd8` で **移行 STEP 2R-E（テスト基盤の再構築）を完了**した。`_migration/` のテスト25件を戻して**同ディレクトリを削除**（退避73件をすべて処理し終えた）。`@SpringBootTest`／`@AutoConfigureMockMvc`／zonky を `@ExtendWith(SpringExtension)` + `@ContextConfiguration`（Web は + `@WebAppConfiguration`）と `EmbeddedPostgres` の直接起動へ置き換え、接続先は `@DynamicPropertySource` で差し替える方式にした（`DataSource` Bean は上書きしない）。埋め込み PostgreSQL の起動と DB 払い出しは **`afkgame-env` の test-jar が配る `EmbeddedPostgresSupport`**（サーバーは JVM に1つ・DB はテスト用コンテキストごと）。surefire／failsafe／JaCoCo の分離設定を親 POM へ入れ直し、テスト依存（assertj・hamcrest・json-path・embedded-postgres）を追加した。**`mvn clean install` は exit 0 で、単体89件・統合45件が緑、branch は domain 40/40・web 10/10 の 100%**。詳細は [changelog.md](../changelog.md) の 2026-08-09 先頭行。**STEP 2R の残りは 2R-F のみ**（これで STEP 2R が完了する）。**起動には `SPRING_PROFILES_ACTIVE`（`local` / `production`）が必須**（未設定なら落ちる）。

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
/dev 移行 STEP 2R-F（実行・デプロイの切替）: war を Tomcat 11.0 へ配備できる状態にし、Vite プロキシ・launch.json・運用手順を非Boot構成へ合わせる
完了条件: ①Tomcat 11.0 へ `afkgame-web.war` を配備して `GET /health` が 200（`db:ok`）を返す②`POST /api/auth/guest` が 200 でトークンペアを返す（①②で **STEP 2R が完了**）③`.vscode/launch.json` の `Backend (Spring Boot)`（`mainClass: com.afkgame.web.AfkgameApplication` は 2R-D で削除済みのため今は起動しない）を war 配備の手順へ差し替える④Vite の `server.proxy` を Tomcat のコンテキストパスへ合わせる⑤[tech_operations.md](../tech/nonfunctional/tech_operations.md) §12 と [tech_operations_procedure.md](../tech/nonfunctional/tech_operations_procedure.md) の起動・配備手順を実機どおりに直す⑥`frontend/tests/e2e/support/serve-backend.mjs` に `SPRING_PROFILES_ACTIVE` 等の環境変数付与を入れ、`GET /health` まで疎通する（E2E テスト本体は Phase 1〜3 の API が無いため赤のままで正常）
参照: 手順と完了判定は [steps.md](java_migration/steps.md) §4「STEP 2R」の 2R-F 行、Tomcat の版とプロファイル切替は同§4「2R-0 の確定結果」、E2E ハーネスの起動手順は [carryover_notes.md](carryover_notes.md) §4。反映先は [tech_operations.md](../tech/nonfunctional/tech_operations.md) §12
前提: main の最新 `3933cd8`（2R-E 完了。`mvn clean install` が exit 0 の状態）。**backend を触るので worktree を作る**: `python scripts/worktree.py add tera-2rf` → `EnterWorktree` に `path` で移動（領域は backend）。**JDK 17.0.20 / Maven 3.9.11 / Node v22.18.0 は新規シェルで実行して確認済み**（PATH・`JAVA_HOME` とも反映済み）。出力は CP932 なのでログはファイルへ落として `iconv -f CP932 -t UTF-8` で読む。**ユーザー操作が要る不足が2件ある（着手前に解消を依頼する）**: ⓐ**Tomcat 11.0 が未導入**（`CATALINA_HOME` 未設定・PATH に `catalina` なし・インストール先も見当たらない。`%LOCALAPPDATA%\Temp\tomcat.8100.*` は 2R-0 の残骸で使えない）→ 11.0.x の zip を展開して置き場所を決めるところから ⓑ**Docker デーモンが停止中**（`docker version` が npipe へ接続できない）→ 完了条件①の `db:ok` には `docker compose up -d` の `afkgame-postgres`（:5432）が要る。`JWT_SECRET`・`DATABASE_PASSWORD` は local プロファイルが既定値を持つので設定は任意
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。wt 名は採番済みで、その worktree が既にあれば着手中（§0）。

| 優先 | タスク | 前提 | wt 名 / 領域 | 工程スキル |
|------|-------|------|------------|-----------|
| 1 | **Phase 4 ③限界突破の詳細設計**。`POST /api/character/limit-break` の処理フロー・計算式・分岐一覧を `docs/tech/detail/tech_limitbreak.md`（新規）へ。素材＝同一 `master_id` のキャラ1体で `limit_break` +1（上限5回）。`check_branch_list.py` を exit 0 にする。起点は [character.md](../data/master/character.md) §8・§8.1（ボーナス数値の正）、可否は [tech_state.md](../tech/detail/tech_state.md) §4、`canLimitBreak` は [tech_scout.md](../tech/detail/tech_scout.md) §6。**`characters.master_id` は Phase 4 で追加する未実装列**（定義書とER図のみ記載済み） | なし | `p4limitbreak-detail`<br>docs/tech | `detail-design` |
| 2 | **Phase 4 ④ダンジョン3（塔6〜8）のマスターデータ**。`docs/data/towers/` に3ファイルを追加し `TOWERS_OVERVIEW.md` と `master_data.md` の索引を更新する。書式は既存の `009_黄昏の塔.md` 等に揃える | なし | `towers-6to8`<br>docs/data | `detail-design` |
| 3 | **移行バックログの文字数是正**。[carryover_notes.md](carryover_notes.md)（残り23字）を分割する。H2 が上限超過で圧縮では解消しない（残量WARN 22件の棚卸しも同時に。`steps.md` は残り353字なので急がない） | なし。ただし**移行 STEP と同時に走らせない**（同じファイルを触る） | `docsize-migration`<br>docs/backlog | `doc-size` |
| 4 | **`afkgame-env` を JaCoCo の分岐100%ゲートへ載せる**。`AfkgameSettingsConfig` のプロファイル検査（4分岐）に単体テストが無く 100% を満たせないため、env だけ 2R-E でもゲート対象外にしてある。テストを足して `afkgame-env/pom.xml` へ jacoco プラグインを宣言する | **2R-F 完了後**（backend 領域が空くまで待つ） | `env-jacoco`<br>backend | `unit-test` |

- **2R 完了後に解禁される行**（キューが空いたら戻す）: Phase 4 テストリスト作成（拠点・施設・鍛冶屋。`tech_base.md` §7・§8 の36件 + `tech_forge_*` の74件）、移行 STEP 3-A-2（register / login / logout）。順序の正は [carryover_notes.md](carryover_notes.md) §1
- 上記に載らない**複数セッションにまたがる申し送り**は [carryover_notes.md](carryover_notes.md) が持つ。着手前にそちらも見る
