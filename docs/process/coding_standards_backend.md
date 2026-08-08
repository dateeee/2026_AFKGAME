# AFK GAME — バックエンドコーディング規約（Java）

> `backend/` の Java 実装が従う規約の**正**。フロントエンド（Vue 3 / TypeScript）は別書 `coding_standards_frontend.md`（未整備）。
> 位置づけ・改訂手順は [phases.md](phases.md) §3.2.2、遵守の判定は [development_process.md](development_process.md) §4「製造完了ゲート」。
> 技術スタック一覧は [profile.md](../../.claude/project/profile.md) §3、**テストコードの記述規約**は [test-list.md](../../.claude/project/test-list.md) §5 が正（本書で重複させない）。
> エージェント向けの要約は [.claude/references/coding-standards-backend.md](../../.claude/references/coding-standards-backend.md)（本書からの派生）。

---

## 1. 適用範囲と原則

| 項目 | 内容 |
|------|------|
| 対象 | `backend/` 配下の全 `.java`、Mapper XML、`application*.yml`、`logback-*.xml`、Flyway SQL |
| 非対象 | `frontend/`（別書）、`scripts/`・`.claude/scripts/`（開発補助の Python） |

| # | 原則 |
|---|------|
| 1 | **本書に無い判断は近傍の既存コードに倣う**。同じ層の既存クラスと書き方を揃えることを好みより優先する |
| 2 | **レイヤの責務を越えない**（§2 の依存方向・§4 の層別規約） |
| 3 | **仕様の正はドキュメント**。バランス数値・エラーコード・スキーマをコードに埋め込まない（[spec_ownership.md](spec_ownership.md)） |
| 4 | 規約と既存コードが食い違っていたら、直さずに [known_issues.md](../backlog/known_issues.md) へ記録する |

## 2. モジュールとパッケージ

| モジュール | パッケージ | 置くもの | 依存してよい先 |
|-----------|-----------|---------|--------------|
| `afkgame-env` | `com.afkgame.env.config` / `.logging` | `@ConfigurationProperties`、ログ基盤 | （なし） |
| `afkgame-domain` | `com.afkgame.domain.model` / `.repository` / `.service` / `.masterdata` / `.rng` / `.exception` | Entity、Mapper、Service、マスターデータ、RNG、業務例外 | `afkgame-env` |
| `afkgame-web` | `com.afkgame.web.api` / `.resource` / `.config` / `.filter` | `@RestController`、Resource、Security・フィルタ | `afkgame-domain`、`afkgame-env` |
| `afkgame-initdb` | （Java なし） | Flyway マイグレーション SQL | （なし） |

- **依存方向は `web → domain → env` の一方向**。逆流・循環を作らない
- `afkgame-domain` に Web 層の型（Spring MVC・`jakarta.servlet`・`HttpStatus`）を持ち込まない。HTTP ステータスを扱う必要がある場合は `int` で保持する（`AppException` がその例）
- 新しいパッケージを切るときは [tech_structure.md](../tech/basic/tech_structure.md) §2 のツリーへ同時に追記する

## 3. 命名

| 対象 | 規約 | 例 |
|------|------|-----|
| クラス・インタフェース | UpperCamelCase | `AuthService` |
| コントローラ | `<リソース>Api`。`Controller` 接尾辞は使わない | `AuthApi`・`HealthApi` |
| Resource（DTO） | `<用途>Resource` | `AuthResource`・`ErrorResource` |
| Entity | テーブル名の単数形 | `User`（`users`）・`RefreshToken` |
| Mapper | `<Entity>Mapper`。XML は**同名・同パッケージのリソース配下** | `UserMapper.java` ↔ `com/afkgame/domain/repository/UserMapper.xml` |
| Service | `<領域>Service` | `AuthService` |
| 設定バインド | `<領域>Properties` | `AuthProperties` |
| 例外 | `<領域>Exception` | `AppException`・`MasterDataException` |
| Mapper メソッド | `select` / `insert` / `update` / `delete` + `By<条件>` | `selectById`・`revokeAllByUserId` |
| メソッド・変数 | lowerCamelCase。`boolean` の getter は `is` | `isGuest()` |
| 定数 | UPPER_SNAKE_CASE（`private static final`） | `REFRESH_TOKEN_BYTES` |
| パッケージ | 全小文字・単語区切りなし | `masterdata` |

- **JSON のフィールド名は lowerCamelCase**。Jackson の既定でそのまま出るため `@JsonProperty` での改名をしない（[tech_api_common.md](../tech/basic/tech_api_common.md) §5.0）
- **DB 列は snake_case、Java フィールドは lowerCamelCase**。変換は MyBatis の `map-underscore-to-camel-case: true` に任せ、`<result>` の手書きマッピングを増やさない。列名の正は [tech_db.md](../tech/basic/tech_db.md)
- 略語は先頭のみ大文字（`JwtService`、`ApiExceptionHandler`）

## 4. レイヤ別の規約

| 層 | 規約 |
|----|------|
| コントローラ（`api`） | マッピング・入力検証・Resource 変換**だけ**を書く。業務分岐・計算・DB アクセスを置かない。ボディは `@Valid @RequestBody`。戻り値は Resource（`ResponseEntity` はステータスやヘッダを変える場合のみ） |
| サービス（`service`） | ビジネスロジックの唯一の置き場。トランザクション境界は **Service の public メソッドに `@Transactional`**（Mapper・Controller には付けない）。複数 Mapper をまたぐ更新は1メソッドに閉じる |
| Mapper・Entity | Entity は永続化の器。ロジック・導出プロパティを持たない。SQL は XML に置き、値は `#{}` でバインドする。取得は N+1 を作らない（JOIN・一括取得） |
| Resource（`resource`） | `record` + Bean Validation（Jakarta）。リクエスト用とレスポンス用を**兼用しない**。ドメイン型からの変換は `public static from(...)` に集約する |
| 設定・フィルタ（`config`・`filter`） | 設定値は `@ConfigurationProperties`（`afkgame.*`）で受ける。`@Value` の直書きをしない |

- **DI はコンストラクタ注入**。フィールド `@Autowired`・setter 注入を使わない。依存は `private final`
- 現在時刻・乱数は「外から受ける」。乱数は `RandomFactory` から取得して引数で引き回す（[tech_rng.md](../tech/detail/tech_rng.md) §2）。静的な共有インスタンスを持たない
- テーブル定義書に無い列・テーブルが必要になったら、実装で先行させず**基本設計へ差し戻す**（[phases.md](phases.md) §3.2.1）

## 5. Java 記述規約

| # | 規約 |
|---|------|
| 1 | インデントは**半角スペース4つ**（タブ禁止）、継続行は8つ。1行は120字を目安に折る |
| 2 | `import` は `java` → `javax` → `org` → `com` → その他（`jakarta` 等）の順。グループ間に空行1つ。**ワイルドカード import 禁止**、未使用 import を残さない |
| 3 | 文字コードは UTF-8、改行は LF、ファイル末尾に改行1つ |
| 4 | フィールドは原則 `private final`。可変が必要なのは MyBatis がマッピングする Entity だけ |
| 5 | 不変のデータ構造は `record`（Resource・マスターデータ・戻り値の組）。getter/setter を持つ class は Entity に限る |
| 6 | `var` は右辺から型が自明なときのみ使う |
| 7 | `null` を返しうるメソッドは Javadoc に明記する。`Optional` は**戻り値にのみ**使い、フィールド・引数に使わない |
| 8 | 日時は `java.time`。既定は `Instant`（DB は `timestamptz`）。`java.util.Date`・`Calendar` を使わない |
| 9 | ゴールド・経験値などの整数は `long`。浮動小数で保持しない。丸めは [tech_numeric.md](../tech/detail/tech_numeric.md) に従う |
| 10 | **マジックナンバー禁止**。技術的な定数は `private static final` + Javadoc、運用値は `application.yml`、ゲームバランス値はマスターデータ（YAML）へ置く |
| 11 | 早期 return でネストを浅くする（3段以上ネストさせない）。ループ内で文字列を `+` 連結しない |
| 12 | 可視性は最小に。`@Override` を省略せず、`@SuppressWarnings` には理由コメントを添える |

## 6. 例外とエラー

| # | 規約 |
|---|------|
| 1 | クライアントへ返す業務エラーは **`AppException(code, message, status)` のみ**を投げる。コード体系の正は [tech_logging.md](../tech/basic/tech_logging.md) |
| 2 | コントローラで `try-catch` しない。応答への変換は `ApiExceptionHandler`（`@RestControllerAdvice`）へ集約する |
| 3 | 検査例外を新設しない（`RuntimeException` 派生にする）。ライブラリの検査例外は捕捉して `AppException` か `IllegalStateException` に変換する |
| 4 | **例外を握りつぶさない**。空の `catch` を書かない。到達しない `catch` は理由コメントを添えて `IllegalStateException` にする |
| 5 | 応答メッセージに内部情報（SQL・スタックトレース・テーブル構造・ライブラリ名）を載せない |
| 6 | 認証・認可の失敗理由を出し分けない（探索の手がかりになるため）。詳細はログにだけ残す |
| 7 | 失敗時にロールバックさせたくない副作用（不正検知による失効など）は `@Transactional(noRollbackFor = ...)` を明示し、理由を Javadoc に書く |

## 7. ログ

| # | 規約 |
|---|------|
| 1 | SLF4J を使う。`private static final Logger logger = LoggerFactory.getLogger("afkgame.<領域>")` の形で、**ロガー名体系の名前**を指定する（`getLogger(Xxx.class)` にしない）。名前の正は [tech_logging.md](../tech/basic/tech_logging.md)「ロガー名体系」 |
| 2 | レベルの使い分け・出力フォーマット・マスク規則も同ファイルが正。本書では再掲しない |
| 3 | メッセージはプレースホルダ `{}` で組む（文字列連結・`String.format` を使わない） |
| 4 | パスワード・トークン生値・メールアドレスをそのまま出さない |
| 5 | `request_id` などの横断項目は MDC（`RequestLogFilter`）が載せる。各所で詰め直さない |
| 6 | `System.out.println`・`printStackTrace` を使わない |

## 8. Javadoc・コメント

| # | 規約 |
|---|------|
| 1 | `public` のクラス・メソッドに**日本語の Javadoc** を書く。1行目は「〜する。」の要約1文 |
| 2 | クラス Javadoc に**仕様書の参照先**を `<p>仕様: docs/....md §N` の形で書く。実装の根拠を追える状態にする |
| 3 | `@param`・`@return`・`@throws` を書く。`@throws AppException` にはエラーコードを添える |
| 4 | 段落は `<p>` で区切る。コード片・識別子は `{@code ...}` |
| 5 | 意図的な未実装・仮実装は Javadoc に**理由と解消時期**を書く（`TODO` だけを残さない） |
| 6 | 行コメントは「何をしているか」ではなく「**なぜそうしたか**」を書く。コードを読めば分かることを繰り返さない |

## 9. 禁止事項

| 禁止 | 代わりに |
|------|---------|
| フィールド `@Autowired`・setter 注入 | コンストラクタ注入（`private final`） |
| コントローラへの業務ロジック記述 | Service へ集約 |
| SQL の `${}` による文字列組み立て | `#{}` によるパラメータバインド |
| ワイルドカード import | 個別 import |
| ゲームバランス数値のハードコード | マスターデータ YAML・`application.yml` |
| `System.out` / `printStackTrace` | SLF4J のロガー |
| 空の `catch`・例外の握りつぶし | `AppException` へ変換するか再スロー |
| 静的な可変フィールド（共有状態） | DI か引数で受け渡す |
| `java.util.Date` / `Calendar` | `java.time`（既定は `Instant`） |
| テーブル定義書に無い列・テーブルの追加 | 基本設計へ差し戻し（[phases.md](phases.md) §3.2.1） |
| 対象Phaseより後の機能の先行実装 | Phase 厳守（将来拡張を考慮した設計は可） |

## 10. 適用と検証

| 手段 | 対象 | コマンド・スキル |
|------|------|----------------|
| コンパイル | 構文・型 | `cd backend && mvn -q compile` |
| テスト・カバレッジ | 振る舞い・C1 100% | `cd backend && mvn verify`（JaCoCo） |
| レビュー | 本書への適合（命名・層の責務・セキュリティ・一貫性） | `backend-review` スキル（観点の正は [.claude/project/review-code.md](../../.claude/project/review-code.md) §2） |

- 新規・改修したコードは本書に従う。**既存コードの一括是正はしない**（見つけた逸脱は [known_issues.md](../backlog/known_issues.md) へ記録し、その箇所を触るときに直す）
- 本書の改訂は基本設計工程で行う（[phases.md](phases.md) §3.2.2）。改訂したら `.claude/references/coding-standards-backend.md` を**同じ変更で**追随させる
- **Checkstyle・Spotless は未導入**。書式・import 順・命名は現状 `backend-review` の目視で担保する。自動化は Java 移行の完了（[java_migration.md](../backlog/java_migration.md) STEP 6）以降に検討する
