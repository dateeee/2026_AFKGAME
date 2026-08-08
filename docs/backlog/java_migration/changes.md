# Java/Terasoluna 移行 — 対応表と仕様変更点

> [java_migration.md](../java_migration.md) の分冊。担当は **§3・§5**。手順と進捗の正は親（索引）にある。

---

## 3. 対応表（Python → Java）

| 現行 | 移行後 |
|------|------|
| `routers/` (FastAPI) | `afkgame-web` の `@RestController`（アプリケーション層） |
| `services/` | `afkgame-domain` の Service（ドメイン層） |
| `schemas/`（Pydantic / CamelModel） | Resource クラス + Bean Validation |
| `models/`（SQLAlchemy） | Entity + MyBatis3 Mapper（インタフェース + XML） |
| `db/database.py` | `afkgame-env` の DataSource 設定 |
| `dependencies.py`（DI） | Spring DI（コンストラクタ注入） |
| `middleware.py` | Servlet Filter / `HandlerInterceptor` |
| `exceptions.py` + 例外ハンドラ | Terasoluna の例外体系 + `@RestControllerAdvice` |
| `logging_config.py` | `logback.xml` |
| `config.py` の定数 | `META-INF/spring/*.properties` + 設定保持用の `@Bean` |
| `master_data/`（Python定数） | YAML リソース + 起動時ローダ → `record`（`afkgame-domain`） |
| `rng.py` | `java.util.Random` を注入（[tech_rng.md](../../tech/detail/tech_rng.md) が正） |
| Alembic | Flyway |
| pytest / pytest-cov | JUnit 5 / JaCoCo |
| uvicorn | war + Tomcat |

振り分けは**値の正の所在**で決める。`config.py` にあっても、正が `docs/data/master/`・`docs/design/systems/` にある値（初期キャラ・初期所持アイテム・装備スロット）は YAML マスターデータへ寄せる。

`scripts/*.py`（ドキュメント検証・レビュー退避）は開発補助のため**Python のまま維持**する。

## 5. 移行に伴う仕様変更点

API契約は不変だが、以下は言語差・実行形態の違いにより仕様側の見直しが必要。

| 項目 | 内容 |
|------|------|
| 乱数の再現性 | Python の Mersenne Twister と Java の乱数は互換性がない。**同一シードでも結果は一致しない**。シード固定テストの期待値は Java 側で再生成する |
| tick の排他 | SQLAlchemy のセッション前提から、Spring の `@Transactional` + 行ロック前提へ読み替える |
| マイグレーション履歴 | Alembic の既存5リビジョンは Flyway の `V1` 初期スキーマへ畳む（移行前後で同一スキーマになることを確認する） |
| DBMS の統一 | SQLite を廃止し `local`・`production` とも PostgreSQL にする。段階移行の前提が消えるため、型マッピングの SQLite 列・ロック方式の分岐・バックアップの二本立て・容量による移行判断ラインを削除する |
| tick のロック | SQLite の `BEGIN IMMEDIATE` 前提をやめ、`SELECT ... FOR UPDATE` の行ロックに一本化する（[tech_tick.md](../../tech/detail/tech_tick.md) §3.1 が正） |
| マスターデータ | Python 定数 → YAML リソース。数値の正は `docs/data/master/` のまま変わらないが、**再ビルドなしで差し替え可能**になる。ローダは起動時にスキーマ検証し、不正なら起動を中止する |
| 実行形態 | 実行可能 jar + systemd を廃止し、**war を Tomcat へデプロイ**する。Nginx 配下に置く点は変わらない。運用手順（起動・停止・ログ出力先・ヘルスチェック）は Tomcat 前提へ全面的に書き直す |
| APIドキュメント | springdoc-openapi（未実装）を採らない。**`/docs`（Swagger UI）は提供しない**。API仕様の正は [tech_api.md](../../tech/basic/tech_api.md)・[tech_api_common.md](../../tech/basic/tech_api_common.md) の記述だけになる |
| 設定ファイル | `application.yml`（YAML・Spring プロファイル）→ `META-INF/spring/*.properties`。`@ConfigurationProperties` による束ね方も使えないため、設定値の受け取り方を再設計する |
| ログ設定 | `logback-spring.xml` の `<springProfile>` は Boot 拡張のため使えない。`logback.xml` へ移し、環境別の切り替えを別方式にする |
