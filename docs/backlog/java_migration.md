# AFK GAME — Java/Terasoluna 移行計画

> 状態ファイル（[documentation_rules.md](../process/documentation_rules.md) §10）。移行完了時にファイルごと削除する。
> 移行後の構成の正は [tech_structure.md](../tech/basic/tech_structure.md) §2〜§4。本書は**手順と進捗**のみを持ち、仕様は各成果物へ反映して重複させない。

---

## 1. 前提と完了条件

バックエンドを Python/FastAPI から Java/Terasoluna（MyBatis3）へ全面移行する。

| 項目 | 内容 |
|------|------|
| 対象 | `backend/` 配下のみ。フロントエンド（Vue SPA）は**無改修** |
| 方式 | 一括書き換え。並行運用（ストラングラー）は行わない |
| 着手時点 | Phase 1〜2 完了、Phase 3 製造①（パーティ・スキル基盤）まで実装済み |
| 完了条件 | フロント無改修で Phase 1〜3 の E2E が全PASS、かつ単体テストが分岐100% |

**API契約を変えないことが全体の制約**。[tech_api.md](../tech/basic/tech_api.md)・[tech_api_common.md](../tech/basic/tech_api_common.md) が正であり、移行で変更しない。JSON のプロパティ名は camelCase を維持する。

## 2. 技術選定

| 領域 | 採用 | 補足 |
|------|------|------|
| フレームワーク | Terasoluna Server Framework for Spring 5.x（Spring Boot 3 / Spring Framework 6） | 実行可能 jar。Nginx 配下に systemd で常駐 |
| 言語・ビルド | Java 17 / Maven マルチモジュール | Terasoluna blank project 準拠 |
| データアクセス | **MyBatis3** | Terasoluna の MyBatis3 blank project を起点にする |
| マイグレーション | Flyway | Alembic の既存4リビジョンは初期スキーマ `V1` へ畳む |
| JSON | Jackson | camelCase 維持（`CamelModel` 相当の変換は不要） |
| APIドキュメント | springdoc-openapi | FastAPI 自動生成の `/docs`（Swagger UI）を維持する |
| 入力検証 | Bean Validation（Jakarta） | Pydantic のフィールド制約を移す |
| 認証 | Spring Security + JJWT | 方式・期限は [tech_auth.md](../tech/detail/tech_auth.md) が正（変更なし） |
| パスワード | `BCryptPasswordEncoder`（strength 12） | 既存ハッシュと互換（同一 bcrypt 形式） |
| ログ | Logback + MDC | `X-Request-ID` は MDC で引き回す |
| テスト | JUnit 5 + Mockito + JaCoCo | C1 = JaCoCo **branch 100%** |
| 依存脆弱性スキャン | OWASP Dependency-Check（Maven プラグイン） | `pip-audit` の置き換え |
| 統合テスト | MockMvc（`@SpringBootTest`） | FastAPI TestClient の置き換え |
| E2E | Playwright（既存を流用） | 変更なし |
| 設定 | `application.yml` + 環境変数 | `.env` / `config.py` の置き換え |
| DB | **PostgreSQL に統一** | `local` は Docker Compose、`production` は EC2 同居。ドライバは `postgresql` JDBC。SQLite は採用しない（§5） |
| マスターデータ | **YAML リソース + 起動時ローダ** | `afkgame-domain` の `src/main/resources/masterdata/`。起動時に `record` へ読み込み、不変 Map で公開する |

### モジュール構成

Terasoluna blank project の標準構成に従う。

| モジュール | パッケージ | 内容 |
|------|------|------|
| `afkgame-domain` | `com.afkgame.domain.model` | Entity |
| | `.repository` | MyBatis3 Mapper インタフェース + 同名の Mapper XML |
| | `.service` | ビジネスロジック |
| | `.masterdata` | マスターデータの `record` + YAML ローダ（YAML 本体は `src/main/resources/masterdata/`） |
| `afkgame-web` | `com.afkgame.web.api` | `@RestController` |
| | `.resource` | Resource(DTO) + Bean Validation |
| | `.config` | Security・Jackson・`@ConfigurationProperties` |
| | `.filter` | リクエストIDログ・共通例外ハンドラ |
| `afkgame-env` | — | 環境依存設定（`application.yml`・DataSource） |
| `afkgame-initdb` | — | Flyway マイグレーション |

配置の正は [tech_structure.md](../tech/basic/tech_structure.md) §2。

## 3. 対応表（Python → Java）

| 現行 | 移行後 |
|------|------|
| `routers/` (FastAPI) | `afkgame-web` の `@RestController`（アプリケーション層） |
| `services/` | `afkgame-domain` の Service（ドメイン層） |
| `schemas/`（Pydantic / CamelModel） | Resource クラス + Bean Validation |
| `models/`（SQLAlchemy） | Entity + MyBatis3 Mapper（インタフェース + XML） |
| `db/database.py` | `afkgame-env` の DataSource 設定 |
| `dependencies.py`（DI） | Spring DI（`@Autowired` / コンストラクタ注入） |
| `middleware.py` | Servlet Filter / `HandlerInterceptor` |
| `exceptions.py` + 例外ハンドラ | Terasoluna の例外体系 + `@RestControllerAdvice` |
| `logging_config.py` | `logback-spring.xml` |
| `config.py` の定数 | `@ConfigurationProperties` クラス |
| `master_data/`（Python定数） | YAML リソース + 起動時ローダ → `record`（`afkgame-domain`） |
| `rng.py` | `java.util.Random` を注入（[tech_rng.md](../tech/detail/tech_rng.md) が正） |
| Alembic | Flyway |
| pytest / pytest-cov | JUnit 5 / JaCoCo |
| uvicorn | Spring Boot 実行可能 jar |

`scripts/*.py`（ドキュメント検証・レビュー退避）は**Python のまま維持**する。アプリではなく開発補助のため移行対象外。

## 4. STEP 一覧

| STEP | 内容 | 状態 |
|------|------|------|
| 0 | 技術選定（§2） | 完了 |
| 1 | 基本設計・規約の改訂（ドキュメント先行） | 完了 |
| 2 | Java 側の骨格構築（横断基盤） | 未着手 |
| 3 | Phase 1 スコープの移植 | 未着手 |
| 4 | Phase 2 スコープの移植 | 未着手 |
| 5 | Phase 3 実装済み分の移植 | 未着手 |
| 6 | 切替と Python 資産の削除 | 未着手 |

### STEP 1: 基本設計・規約の改訂

コードより先に仕様書を Java/Terasoluna 前提へ改訂する。対象は §2 の技術選定に触れる記述のみで、**ゲーム仕様・API契約・DBスキーマは変更しない**。

| 対象 | 改訂内容 |
|------|------|
| `README.md` | 技術スタック表・セットアップ・ディレクトリ構成・主なコマンド |
| `CLAUDE.md` | 実装規約・テスト標準 |
| `tech_structure.md` | §2 ディレクトリ構成・§4 バックエンド構成・設定値 |
| `tech_architecture.md` | 構成図中の FastAPI 表記・MVP開発方針 |
| `tech_logging.md` | ロガー実装・フォーマット |
| `tech_operations.md` | §12.2 環境変数・§12.4 マイグレーション・§12.7 チェック |
| `tech_security.md` | 入力検証・レート制限の実現方式 |
| `tech_rng.md` | 乱数実装と再現性の前提 |
| `tech_tick.md` | 排他・トランザクション境界の実現方式 |
| `tech_db.md` | 型マッピング・ORM 表記のみ（**スキーマ定義は変更しない**） |
| `tech_db/` 各テーブル | 変更しない。「実装:」行は `scripts/check_schema_triple.py` が三者一致検証に使うアンカーで、Python models が実体である間は書き換えられない（STEP 6 で切替） |
| `system_architecture.md` + 配下 | 構成図・tick フロー・デプロイ図 |
| `glossary.md` | 技術用語 |
| `development_process.md`・`phases.md` | テスト標準・コマンド |
| `.claude/project/**` | 対象ファイル一覧・コマンド・技術規約・テストパターン |

§2 で確定した2点（**PostgreSQL 統一**・**マスターデータの YAML 外出し**）の仕様書反映も本 STEP に含む。影響は §5 の該当行を参照。

完了基準: `check_doc_size.py` と `check_docs.py` が exit 0、`doc-review` の指摘ゼロ。

### STEP 2: 骨格構築

機能を持たない共通基盤を先に固める。完了時点で「`GET /health` が 200（`db:ok`）・ゲスト認証が通る」状態にする。

1. Terasoluna MyBatis3 blank project からモジュール生成 + `local` 用 PostgreSQL の Docker Compose 定義
2. 統一エラーレスポンス・例外ハンドラ・リクエストIDログ
3. Spring Security による JWT / ゲスト認証
4. Flyway 初期スキーマ（[tech_db.md](../tech/basic/tech_db.md) が正）
5. RNG・設定プロパティ・マスターデータの YAML ローダ基盤（起動時に検証し、不正なら起動失敗）

### STEP 3〜5: Phase 単位の移植

各 Phase とも **分岐一覧 → JUnit テスト（Red）→ 実装（Green）** の順で進める（TDD 方針は維持）。

| STEP | スコープ |
|------|------|
| 3 | auth / game / battle / tower（Phase 1） |
| 4 | equipment / shop（Phase 2・日替わり含む） |
| 5 | party / skill（Phase 3 製造①の実装済み分） |

各 STEP の完了基準は共通で、**単体テスト branch 100% + API統合テスト全PASS + 該当 Phase の E2E 全PASS**。

移植時にあわせて処理するもの:

- 詳細設計の「現行実装との差異」節（[tech_rng.md](../tech/detail/tech_rng.md) §6・[tech_tick.md](../tech/detail/tech_tick.md) §6）は Python 実装の行番号を指す。該当機能を Java で実装したら節ごと削除する
- [known_issues.md](known_issues.md) §2 の未対応項目のうち、移植対象の機能に紐づくものを1件ずつ再確認して解消する

### STEP 6: 切替と後始末

1. Vite の `/api` プロキシ先・`.vscode/launch.json` の実行構成を Java 側へ向ける
2. `tech_db/` 各テーブルの「実装:」行と `scripts/check_schema_triple.py` の `IMPL` 正規表現を Entity 参照へ切り替える（Python models の削除と同時に行う）
3. デプロイ手順（jar + systemd）を [tech_operations.md](../tech/nonfunctional/tech_operations.md) へ反映
4. E2E 全PASS を確認後に `backend/`（Python）を削除
5. 本ファイルを削除し、[changelog.md](../changelog.md) へ完了を1行記録

## 5. 移行に伴う仕様変更点

API契約は不変だが、以下は言語差により仕様側の見直しが必要。

| 項目 | 内容 |
|------|------|
| 乱数の再現性 | Python の Mersenne Twister と Java の乱数は互換性がない。**同一シードでも結果は一致しない**。シード固定テストの期待値は Java 側で再生成する |
| tick の排他 | SQLAlchemy のセッション前提から、Spring の `@Transactional` + 行ロック前提へ読み替える |
| マイグレーション履歴 | Alembic の既存4リビジョンは Flyway の `V1` 初期スキーマへ畳む（移行前後で同一スキーマになることを確認する） |
| DBMS の統一 | SQLite を廃止し `local`・`production` とも PostgreSQL にする。段階移行（規模到達で SQLite → PostgreSQL）の前提が消えるため、型マッピングの SQLite 列・ロック方式の分岐・バックアップの二本立て・容量による移行判断ラインを削除する |
| tick のロック | SQLite の `BEGIN IMMEDIATE` 前提をやめ、`SELECT ... FOR UPDATE` の行ロックに一本化する（[tech_tick.md](../tech/detail/tech_tick.md) §3.1 が正） |
| マスターデータ | Python 定数 → YAML リソース。数値の正は `docs/data/master/` のまま変わらないが、**再ビルドなしで差し替え可能**になる。ローダは起動時にスキーマ検証し、不正なら起動を中止する |
