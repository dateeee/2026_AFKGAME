# Java/Terasoluna 移行 — STEP の詳細

> [java_migration.md](../java_migration.md) の分冊。担当は **§4**。STEP 一覧表（進捗の正）は親（索引）にあり、本ファイルは各 STEP の中身だけを持つ。

---

## 4. STEP 一覧（詳細）

### STEP 1: 基本設計・規約の改訂（完了）

コードより先に仕様書を Java/Terasoluna 前提へ改訂した。対象は [tech_selection.md](tech_selection.md) §2 の技術選定に触れる記述のみで、**ゲーム仕様・API契約・DBスキーマは変更していない**。内訳は [changelog.md](../../changelog.md) 2026-08-08。

§2 で確定した2点（**PostgreSQL 統一**・**マスターデータの YAML 外出し**）の反映も本 STEP に含む（影響は [changes.md](changes.md) §5）。

**`tech_db/` 各テーブルの「実装:」行だけは据え置いた** — `check_schema_triple.py` が models 照合に使うアンカーで、Python models が実体である間は書き換えられない（切替は STEP 6）。DDL 照合はテーブル名で対応づけるため本行に依存しない。

### STEP 2: 骨格構築（完了）

機能を持たない共通基盤を先に固める。完了時点で「`GET /health` が 200（`db:ok`）・ゲスト認証が通る」状態にする。

1セッションで閉じないため3セグメントへ割って進めた。

| セグメント | 内容 | 状態 |
|-----------|------|------|
| 2-A | Terasoluna MyBatis3 blank project からモジュール生成 + `local` 用 PostgreSQL の Docker Compose 定義 / Flyway 初期スキーマ（[tech_db.md](../../tech/basic/tech_db.md) が正）/ `GET /health` | 完了 |
| 2-B | 統一エラーレスポンス・例外ハンドラ・リクエストIDログ / Spring Security による JWT・ゲスト認証（CORS・`logback-spring.xml` を含む） | 完了 |
| 2-C | RNG・設定プロパティ（`@ConfigurationProperties`）・マスターデータの YAML ローダ基盤（起動時に検証し、不正なら起動失敗） | 完了 |

2-C はローダ基盤のみで、実データは動作確認用の `items.yml`（HPポーション1件）だけを置いた。**各マスターデータの YAML 化と `record` 追加は、それを使う機能を移植する STEP 3〜5 で同時に行う**（先に全件を YAML 化しても参照側が無く検証されないため）。

2-B が横断基盤の範囲外として見送り、STEP 3 へ持ち越した項目:

| 項目 | 見送りの理由 |
|------|------------|
| `POST /api/auth/guest` の Player・キャラクター・装備スロット・初期ポーション初期化（現状は User + トークンのみ） | 初期値がマスターデータ側にある |
| `SecurityConfig` の認証不要パス（現状は `/health`・`/api/auth/{guest,refresh}` のみ） | 未実装のパスを先に開けない。一覧の正は [tech_api_common.md](../../tech/basic/tech_api_common.md) §5.0 |
| `BCryptPasswordEncoder`（strength 12） | 利用者が register・login しか無い |

**この STEP の成果は 2R で作り直す**（下記）。上表の見送り項目は 2R 後の STEP 3 へそのまま引き継ぐ。

### STEP 2R: ブランクプロジェクト構成への再構築（未着手）

STEP 2 で作った骨格は Spring Boot アプリで、ガイドラインのブランクプロジェクトとは別物だった（`terasoluna-gfw-*` への依存も `org.terasoluna` の利用も無く、`terasoluna.version` は未参照のまま残っていた）。[tech_selection.md](tech_selection.md) §2 の改訂に合わせて土台を作り直す。**API契約・DBスキーマ・ゲーム仕様は変更しない**。

影響範囲は Java 80ファイル中 **70ファイルが `org.springframework.boot` を参照**（`@ConfigurationProperties` 9・`@SpringBootTest` 5・`@AutoConfigureMockMvc` 3・`BuildProperties` 11）。設定とテスト基盤は全面的に置き換わる。

| セグメント | 内容 | 状態 |
|-----------|------|------|
| 2R-0 | 先行検証（下表の不確定要素を潰す。ここで詰まったら §2 の版・方式を見直す） | 未着手 |
| 2R-A | 仕様書・規約の再改訂（§2 の変更点を `tech_structure` / `tech_operations` / `coding_standards_backend` へ反映） | 未着手 |
| 2R-B | Archetype から雛形を生成し、View 一式を落として REST 専用の土台にする（`mvn clean install` で war が出るまで） | 未着手 |
| 2R-C | 設定の移植（`web.xml`・Java Config 6種・`*.properties`・`logback.xml`・DataSource・Flyway 起動） | 未着手 |
| 2R-D | 既存実装の移植（domain 34 + web 13 + env 5 の main コードから Boot 依存を除去） | 未着手 |
| 2R-E | テスト基盤の再構築（テスト28ファイル。surefire/failsafe/JaCoCo の分離設定は維持する） | 未着手 |
| 2R-F | 実行・デプロイの切替（Tomcat 起動、Vite プロキシ、`launch.json`、[tech_operations.md](../../tech/nonfunctional/tech_operations.md) §12 反映） | 未着手 |

完了条件は STEP 2 と同じ「`GET /health` が 200（`db:ok`）・ゲスト認証が通る」に加え、**移植済みの単体テストが branch 100% のまま通ること**。

#### 2R-0 で先に潰す不確定要素

| 項目 | 確認すること |
|------|------------|
| Tomcat の版 | 雛形の `web.xml` は Servlet 6.0 宣言だが、Spring Framework 7.0 系が要求する実際の下限を実機で確定する |
| 埋め込み PostgreSQL | `embedded-database-spring-test` は Boot 前提。`embedded-postgres` の直接起動へ置き換えられるか |
| Flyway | Boot の自動マイグレーションが無くなるため、`@Bean` での明示起動に移せるか（`V1` は据え置き） |
| `/health` の version | `BuildProperties` が使えない。マニフェストまたは Maven のリソースフィルタで代替する |
| ビルドプラグイン | JaCoCo・surefire/failsafe・OWASP Dependency-Check が `terasoluna-gfw-parent` の pluginManagement と競合しないか |
| プロファイル切替 | `application-{local,production}.yml` 相当を Boot 無しでどう切り替えるか |

### STEP 3〜5: Phase 単位の移植

各 Phase とも **分岐一覧 → JUnit テスト（Red）→ 実装（Green）** の順で進める（TDD 方針は維持）。
実装は [coding_standards_backend.md](../../process/coding_standards_backend.md) に従う（STEP 2 のコードから抽出した規約。チェックリストは [.claude/references/coding-standards-backend.md](../../../.claude/references/coding-standards-backend.md)）。

| STEP | スコープ |
|------|------|
| 3 | auth / game / battle / tower（Phase 1） |
| 4 | equipment / shop（Phase 2・日替わり含む） |
| 5 | party / skill（Phase 3 製造①の実装済み分） |

各 STEP の完了基準は共通で、**単体テスト branch 100% + API統合テスト全PASS + 該当 Phase の E2E 全PASS**。

移植時にあわせて処理するもの:

- 詳細設計の「現行実装との差異」節（[tech_rng.md](../../tech/detail/tech_rng.md) §6・[tech_tick.md](../../tech/detail/tech_tick.md) §6）は Python 実装の行番号を指す。該当機能を Java で実装したら節ごと削除する
- [known_issues.md](../known_issues.md) §2 の未対応項目のうち、移植対象の機能に紐づくものを1件ずつ再確認して解消する

### STEP 6: 切替と後始末

1. Vite の `/api` プロキシ先・`.vscode/launch.json` の実行構成を Java 側へ向ける
2. `tech_db/` 各テーブルの「実装:」行を Entity 参照へ切り替え、`check_schema_triple.py` の models 照合を削除する（Python models の削除と同時。DDL 照合が引き継ぐ）
3. デプロイ手順（war + Tomcat）の反映は 2R-F で済ませる。本 STEP では最終構成での再確認のみ
4. E2E 全PASS を確認後に `backend/`（Python）を削除
5. 本ファイル群（`java_migration.md` と `java_migration/`）を削除し、[changelog.md](../../changelog.md) へ完了を1行記録
