# AFK GAME — バックエンド構成

> [tech_spec.md](../tech_spec.md) §4。リポジトリ全体のディレクトリ構成とフロントエンド構成は [tech_structure.md](tech_structure.md) §2〜§3。
> 移行期間中の技術選定の正は [tech_selection.md](../../backlog/java_migration/tech_selection.md) §2、手順と進捗は [java_migration.md](../../backlog/java_migration.md)。

## 4. バックエンド構成

| レイヤー | 技術 | 役割 |
|---------|------|------|
| フレームワーク | Terasoluna Server Framework for Spring **5.11.0.RELEASE**（Spring MVC / Java 17） | REST API。**war を Tomcat 11.0（Servlet 6.1）へ配備**（10.1 でも動作）。**Spring Boot は使わない** |
| ビルド | Maven（マルチモジュール） | 親POMは `terasoluna-gfw-parent`。failsafe には `verify` ゴールを持たせる（無いと結合テストが失敗してもビルドが落ちない） |
| データアクセス | MyBatis3 | DB操作（Repository インタフェース + マッピング XML） |
| バリデーション | Bean Validation（Jakarta） | リクエストの制約定義 |
| JSON | Jackson | camelCase でのシリアライズ |
| APIドキュメント | **提供しない** | springdoc は Boot 前提のため廃止。API仕様の正は [tech_api.md](tech_api.md)（`/docs` は無い） |
| DB | PostgreSQL（`local` は Docker Compose） | データ永続化。ゴールドは `BIGINT`（64bit）カラムで管理 |
| マイグレーション | Flyway | `afkgame-initdb` のSQLを `@Bean(initMethod = "migrate")` で起動時に適用し、DBを使うBeanへ `@DependsOn("flyway")` を付ける |
| マスターデータ | YAML リソース + 起動時ローダ | 数値の正は `docs/data/`。再ビルドなしで差し替え可能 |
| 認証 | Spring Security + JWT（Phase 2〜） | ユーザー認証・セッション管理 |
| OAuth | Google OAuth 2.0（Phase 2〜） | Googleアカウント連携 |
| RESTクライアント | `RestClient` + `HttpComponentsClientHttpRequestFactory`（Phase 2〜） | 外部API呼び出し。**`RestTemplate` は使わない**（§4.3） |
| パスワードハッシュ | `BCryptPasswordEncoder`（strength = 12） | パスワード保存 |

- `afkgame-web` は war を作り、`WEB-INF/web.xml` に `ContextLoaderListener`・`DispatcherServlet`・サーブレットフィルタを定義する（Boot の起動クラスは持たない）
- モジュールのパッケージと依存方向の正は [coding_standards_backend/common.md](../../process/coding_standards_backend/common.md) §2

## 4.1 backend/ のディレクトリ構成

```
backend/                           # Terasoluna サーバー（war を Tomcat へ配備）
├── pom.xml                        # 親POM（Maven マルチモジュール）
├── afkgame-domain/                # ドメイン層 (com.afkgame.domain)
│   ├── model/                     # Entity（テーブル定義の正は tech_db.md）
│   │   ├── Player, Character, Item / Equipment, ShopDailyState, ShopDailySlot（Phase 2〜）
│   │   └── User, RefreshToken, EmailVerificationToken（Phase 2〜）/ Party, PartyMember, CharacterSkill（Phase 3〜）
│   ├── repository/                # Repository インタフェース + 同名のマッピング XML（MyBatis3）
│   ├── service/                   # ビジネスロジック（インタフェース `〜Service` + 実装 `〜ServiceImpl`）
│   │   ├── BattleService（戦闘計算・エンカウント。オフライン報酬含む）, GameStateBuilder（状態レスポンス構築）
│   │   ├── EquipmentService / ShopDailyService / AuthService（Phase 2〜）
│   │   └── PartyService / SkillService（Phase 3〜）, BaseService / ForgeService（Phase 4〜）
│   ├── masterdata/                # マスターデータの record + YAML ローダ
│   │   └── Enemies, Towers, Items, Equipments, Characters, Notices（Phase 3〜）
│   ├── (src/main/resources/masterdata/)  # マスターデータ本体（YAML。数値の正は docs/data/）
│   ├── config/{app,mybatis}/      # AfkgameDomainConfig・AfkgameInfraConfig・MybatisConfig
│   └── rng/                       # RandomFactory（乱数源の生成。tech_rng.md §2）
├── afkgame-web/                   # アプリケーション層 (com.afkgame.web)。war を作る
│   ├── api/                       # @RestController
│   │   ├── AuthApi, GameApi, BattleApi, TowerApi, ShopApi / EquipmentApi（Phase 2〜）
│   │   └── PartyApi, NoticeApi（Phase 3〜）/ BaseApi, ForgeApi（Phase 4〜）/ BossRushApi ほか（Phase 5〜）
│   ├── resource/                  # Resource(DTO) + Bean Validation（API I/O）
│   ├── config/{app,web}/          # ApplicationContextConfig・SpringSecurityConfig・SpringMvcConfig
│   ├── filter/                    # リクエストIDログ・共通例外ハンドラ
│   ├── (src/main/webapp/WEB-INF/web.xml)  # サーブレット定義
│   └── (src/main/resources-filtered/META-INF/spring/build.properties)  # /health の version
├── afkgame-env/                   # 環境依存設定（META-INF/spring/*.properties・DataSource・Flyway 起動・logback.xml）
└── afkgame-initdb/                # Flyway マイグレーションSQL（1リリース = 1バージョン）
```

`build.properties` には Maven のリソースフィルタが `${project.version}` を埋める。war のマニフェスト（`Package.getImplementationVersion()`）は war を作らない単体・結合テストで読めないため採らない。

## 4.2 設定値

`afkgame-env/src/main/resources/META-INF/spring/afkgame.properties` に置く。

| キー | 既定値 | 意味 |
|------|-------|------|
| `afkgame.tick.interval.seconds` | 60 | 1 tick の間隔（秒） |
| `afkgame.turns.per.tick` | 3 | 1 tick あたりのターン数 |
| `afkgame.offline.efficiency` | 1.0 | オフライン時の報酬効率（オンラインと同一） |
| `afkgame.max.offline.hours` | 24 | オフライン報酬の最大蓄積時間 |
| `afkgame.fast.calc.threshold` | 100 | これを超える（101以上の）未処理tickは簡略計算に切り替え |
| `afkgame.max.battle.log.records` | 100 | DB保持ログ件数上限 |
| `afkgame.max.log.per.response` | 50 | 1レスポンスあたりのログ件数上限 |
| `afkgame.max.player.level` | 9999 | プレイヤーLV上限 |
| `afkgame.max.gold` | 9223372036854775807 | ゴールド上限（64bit符号付き整数最大値） |
| `afkgame.battle.rng.seed` | （空） | 戦闘乱数のシード。既定は未設定（[tech_rng.md](../detail/tech_rng.md) §2 の調査時のみ固定） |

- **受け取り方**: `@ConfigurationProperties` は Boot 機能のため使わない。`afkgame-env` の `@Configuration` が `@Value` で読んで設定保持 Bean を1か所で組み立て、他層はその Bean を注入する（`@Value` の直書きと環境変数の直接参照をしない。[coding_standards_backend/web.md](../../process/coding_standards_backend/web.md) §4）
- **キーはドット区切りのみ**にする。環境変数での上書きは素の Spring の変換（`database.url` ↔ `DATABASE_URL`）に依存し、ハイフンは変換されないため（[tech_operations.md](../nonfunctional/tech_operations.md) §12.2）
- 認証系の定数（トークン期限・bcrypt strength・パスワード要件・ゲスト期限・確認/再設定トークンの有効期間）も同ファイルに置く（値の正は [tech_auth.md](../detail/tech_auth.md)、確認/再設定トークンは [tech_auth/mail.md](../detail/tech_auth/mail.md) §16.3。本書では列挙しない）
- メール送信の設定（SMTP接続先・認証・タイムアウト・差出人・リンク生成元）も同ファイルに置く。キー名が `afkgame.` 始まりでないのは、キー・既定値の正が [tech_auth/mail.md](../detail/tech_auth/mail.md) §16.2 の表だからである（本書では列挙しない）

## 4.3 外部API呼び出し（RESTクライアント）

外部サービスへの HTTP 呼び出しは `RestClient` で行う。`RestTemplate` は Spring 7.1 で非推奨・8.0 で削除予定のため新規実装では採らない（採用の正は [tech_selection.md](../../backlog/java_migration/tech_selection.md) §2）。利用先は Phase 2〜 の Google OAuth（認可コード → トークン交換、アクセストークン → ユーザー情報取得）だけである。

| 項目 | 規約 |
|------|------|
| `ClientHttpRequestFactory` | `HttpComponentsClientHttpRequestFactory` を **Bean で明示構成する**（高機能な通信設定が行えるため、というガイドラインの推奨に従う）。classpath 検出による自動選択に任せない（下表の値が効かず既定のままになる） |
| 置き場所 | `RestClient` は `AfkgameInfraConfig`（`afkgame-domain` の `config.app`）で1つ組み立て、呼び出す Service へ注入する。値の保持 Bean は `afkgame-env` 側に置く（`MailSettings` と同じ流儀。§4.2） |
| 失敗の扱い | 再試行はしない。タイムアウト・接続不能は例外として呼び出し元へ伝播させ、API のエラー応答へ変換する（エラーコードは Google OAuth の詳細設計で確定する） |
| ログ | START / END を通信ログへ出す（`direction=out`・`target=google_oauth`）。**アクセストークン・IDトークン・認可コードの生値をログへ出さない**。規約の正は [logging/communication.md](../../process/coding_standards_backend/logging/communication.md) §2 |

設定値は §4.2 と同じファイル・同じ受け取り方（設定保持 Bean 経由）とする。

| 設定 | プロパティキー | 環境変数 | 既定値 |
|------|--------------|---------|-------|
| 接続タイムアウト | `http.client.connect.timeout` | —（環境変数で上書きしない） | `3000`（ミリ秒） |
| 応答タイムアウト | `http.client.response.timeout` | —（同上） | `5000`（ミリ秒） |
| プール上限（全体） | `http.client.pool.max.total` | —（同上） | `20` |
| プール上限（ルート単位） | `http.client.pool.max.per.route` | —（同上） | `10` |

シングルプレイ専用で外部呼び出しはログイン時に限られるため、プールは小さく取る。応答タイムアウトはメール送信（[tech_auth/mail.md](../detail/tech_auth/mail.md) §16.1）と同じ5秒に揃えた。
