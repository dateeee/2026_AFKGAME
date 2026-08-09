# Java/Terasoluna 移行 — 技術選定

> [java_migration.md](../java_migration.md) の分冊。担当は **§2**。手順と進捗の正は親（索引）にある。

---

## 2. 技術選定

| 領域 | 採用 | 補足 |
|------|------|------|
| フレームワーク | TERASOLUNA Server Framework for Java (5.x) **5.11.0.RELEASE** | **Spring Boot は使わない**。ブランクプロジェクト準拠の Spring MVC 構成（Spring Framework 7.0 系） |
| 実行形態 | **war を Tomcat 11.0（Servlet 6.1）へデプロイ** | 版の根拠と 10.1 での実測は [steps.md](steps.md) §4「2R-0 の確定結果」。Nginx 配下に置く点は変わらない。実行可能 jar + systemd は採らない |
| 言語・ビルド | Java 17 / Maven マルチモジュール | 親POMは `terasoluna-gfw-parent`。**Java Config 版**のブランクプロジェクト準拠 |
| データアクセス | **MyBatis3** | ブランクプロジェクトの MyBatis3 版を起点にする |
| マイグレーション | Flyway | Alembic の既存5リビジョンは初期スキーマ `V1` へ畳む |
| JSON | Jackson | camelCase 維持（`CamelModel` 相当の変換は不要）。**雛形の依存には Jackson 3（`tools.jackson`）と 2（`com.fasterxml`）が同居する**ため、どちらの `HttpMessageConverter` を使うかは 2R-C で明示的に決める（`fail-on-unknown-properties` 相当の設定先が変わる） |
| APIドキュメント | **提供しない** | springdoc は Spring Boot 前提のため廃止。API仕様の正は [tech_api.md](../../tech/basic/tech_api.md)（`/docs` は無くなる） |
| 入力検証 | Bean Validation（Jakarta） | Pydantic のフィールド制約を移す |
| 認証 | Spring Security + JJWT | 方式・期限は [tech_auth.md](../../tech/detail/tech_auth.md) が正（変更なし） |
| パスワード | `BCryptPasswordEncoder`（strength 12） | 既存ハッシュと互換（同一 bcrypt 形式） |
| RESTクライアント | **`RestClient`**（`ClientHttpRequestFactory` は `HttpComponentsClientHttpRequestFactory` を明示構成） | 外部API呼び出しの唯一の手段。**`RestTemplate` は採らない**（Spring 7.1 で非推奨・8.0 で削除予定。ガイドラインも新規は `RestClient` を推奨）。利用先は Phase 2〜 の Google OAuth のみ。`httpclient5` は `afkgame-domain` へ入れる（版は `spring-boot-dependencies` が管理）。タイムアウト・プールの値の正は [tech_backend.md](../../tech/basic/tech_backend.md) §4.3 |
| ログ | Logback + MDC | `X-Request-ID` は MDC で引き回す。設定は `logback.xml`（Boot 拡張の `logback-spring.xml` と `<springProfile>` は使えない） |
| 単体テスト | JUnit 5 + Mockito + JaCoCo | surefire で `unit` タグのみ。C1 = **branch 100%** |
| 依存脆弱性スキャン | OWASP Dependency-Check（Maven プラグイン） | `pip-audit` の置き換え |
| 統合テスト | MockMvc（`@ExtendWith(SpringExtension)` + `@ContextConfiguration` + `@WebAppConfiguration`）+ **埋め込み PostgreSQL** | failsafe で `integration` タグのみ（計測外）。DBは `embedded-postgres` を `EmbeddedPostgres.builder().start()` で直接起動する（`local` と同じ 16 系・Docker 不要）。**`embedded-database-spring-test` は Boot 前提のため使わない**。実測は `steps.md` §4 |
| E2E | Playwright（既存を流用） | 変更なし |
| 設定 | `META-INF/spring/*.properties` + 環境変数 | `.env` / `config.py` の置き換え。`@ConfigurationProperties` は Boot 機能のため使わない。環境の切替は `SPRING_PROFILES_ACTIVE`、値の上書きは `DATABASE_URL` 等の環境変数で行う（雛形の Maven プロファイルは使わない。`steps.md` §4） |
| DB | **PostgreSQL に統一** | `local` は Docker Compose、`production` は EC2 同居。ドライバは `postgresql` JDBC。SQLite は採用しない（[changes.md](changes.md) §5） |
| マスターデータ | **YAML リソース + 起動時ローダ** | `afkgame-domain` の `src/main/resources/masterdata/`。起動時に `record` へ読み込み、不変 Map で公開する |

### モジュール構成

ブランクプロジェクト（マルチプロジェクト構成）の標準に従う。`-web` / `-domain` / `-env` / `-initdb` は現行の4モジュールと同名。`-selenium` は E2E に Playwright を使うため作らない。

| モジュール | パッケージ | 内容 |
|------|------|------|
| `afkgame-domain` | `com.afkgame.domain.model` | Entity |
| | `.repository` | Repository インタフェース + 同名のマッピング XML（MyBatis3） |
| | `.service` | ビジネスロジック |
| | `.masterdata` | マスターデータの `record` + YAML ローダ（YAML 本体は `src/main/resources/masterdata/`） |
| | `.config.app` | `AfkgameDomainConfig`・`AfkgameInfraConfig`（`@MapperScan` + `SqlSessionFactoryBean`） |
| | `.config.mybatis` | `MybatisConfig` |
| `afkgame-web` | `com.afkgame.web.api` | `@RestController` |
| | `.resource` | Resource(DTO) + Bean Validation |
| | `.config.app` | `ApplicationContextConfig`・`SpringSecurityConfig` |
| | `.config.web` | `SpringMvcConfig`（`DispatcherServlet` 用コンテキスト） |
| | `.filter` | リクエストIDログ・共通例外ハンドラ |
| `afkgame-env` | `.config.app` | `AfkgameEnvConfig`（DataSource）・`META-INF/spring/*.properties` |
| `afkgame-initdb` | — | Flyway マイグレーション（雛形の SQL Maven Plugin は使わず既存の `V1` を維持） |

`afkgame-web` は war を作り、`src/main/webapp/WEB-INF/web.xml` に `ContextLoaderListener`・`DispatcherServlet`・サーブレットフィルタを定義する。配置の正は [tech_backend.md](../../tech/basic/tech_backend.md) §4.1。

起点は Java Config + MyBatis3 のマルチプロジェクト用 Archetype `terasoluna-gfw-multi-web-blank-thymeleaf-mybatis3-archetype`（`5.11.0.RELEASE`）。REST 専用のため、生成物から Thymeleaf・Welcome画面・エラー画面・静的リソース一式を落とす（JSP 版ではなく Thymeleaf 版を起点にするのは、削除後に残る依存が少ないため）。

親POMは `org.terasoluna.gfw:terasoluna-gfw-parent:5.11.0.RELEASE`。これが terasoluna-dependencies 経由で `spring-boot-dependencies` 4.0.2 を import するため、**Spring 系ライブラリの版は個別指定しない**（Spring Framework 7.0.3 / Spring Security 7.0.2 が入る）。Spring Boot 本体（`spring-boot-starter-*`・`spring-boot-maven-plugin`）は使わない。
