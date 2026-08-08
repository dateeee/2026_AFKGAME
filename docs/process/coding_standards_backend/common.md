# バックエンドコーディング規約 — 共通

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。**どの層を書くときも本書を先に読む**。
> ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（索引 §1）の `Overview/ApplicationLayering`・`ArchitectureInDetail`。本書はそこからの差分だけを持つ。
> 層固有の規約は [domain.md](domain.md)（ドメイン層）・[web.md](web.md)（Web層）・[test.md](test.md)（テスト）。節番号は分割前の索引のものを維持している。

---

## 2. モジュールとパッケージ

| モジュール | パッケージ | 置くもの | 依存してよい先 |
|-----------|-----------|---------|--------------|
| `afkgame-env` | `com.afkgame.env.config` / `.logging` | `@ConfigurationProperties`、ログ基盤 | （なし） |
| `afkgame-domain` | `com.afkgame.domain.model` / `.repository` / `.service` / `.masterdata` / `.rng` / `.exception` | Entity、Mapper、Service、マスターデータ、RNG、業務例外 | `afkgame-env` |
| `afkgame-web` | `com.afkgame.web.api` / `.resource` / `.config` / `.filter` | `@RestController`、Resource、Security・フィルタ | `afkgame-domain`、`afkgame-env` |
| `afkgame-initdb` | （Java なし） | Flyway マイグレーション SQL | （なし） |

- **依存方向は `web → domain → env` の一方向**。逆流・循環を作らない
- `afkgame-domain` に Web 層の型（Spring MVC・`jakarta.servlet`・`HttpStatus`）を持ち込まない。HTTP ステータスを扱う必要がある場合は `int` で保持する（`AppException` がその例）
- 新しいパッケージを切るときは [tech_structure.md](../../tech/basic/tech_structure.md) §2 のツリーへ同時に追記する

## 3. 命名

層に依存する名前（コントローラ・Resource・Entity・Mapper・Service）は [domain.md](domain.md) §5・[web.md](web.md) §6 が正。

| 対象 | 規約 | 例 |
|------|------|-----|
| クラス・インタフェース | UpperCamelCase | `AuthService` |
| 設定バインド | `<領域>Properties` | `AuthProperties` |
| 例外 | `<領域>Exception` | `AppException`・`MasterDataException` |
| メソッド・変数 | lowerCamelCase。`boolean` の getter は `is` | `isGuest()` |
| 定数 | UPPER_SNAKE_CASE（`private static final`） | `REFRESH_TOKEN_BYTES` |
| パッケージ | 全小文字・単語区切りなし | `masterdata` |

- 略語は先頭のみ大文字（`JwtService`、`ApiExceptionHandler`）

## 4. 全層共通のルール

層別の責務は [domain.md](domain.md)・[web.md](web.md) が持つ。本節は層に依らないもの。

| # | 規約 |
|---|------|
| 1 | **DI はコンストラクタ注入**。フィールド `@Autowired`・setter 注入を使わない。依存は `private final` |
| 2 | 現在時刻・乱数は「外から受ける」。乱数は `RandomFactory` から取得して引数で引き回す（[tech_rng.md](../../tech/detail/tech_rng.md) §2）。静的な共有インスタンスを持たない |
| 3 | テーブル定義書に無い列・テーブルが必要になったら、実装で先行させず**基本設計へ差し戻す**（[phases.md](../phases.md) §3.2.1） |

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
| 9 | ゴールド・経験値などの整数は `long`。浮動小数で保持しない。丸めは [tech_numeric.md](../../tech/detail/tech_numeric.md) に従う |
| 10 | **マジックナンバー禁止**。技術的な定数は `private static final` + Javadoc、運用値は `application.yml`、ゲームバランス値はマスターデータ（YAML）へ置く |
| 11 | 早期 return でネストを浅くする（3段以上ネストさせない）。ループ内で文字列を `+` 連結しない |
| 12 | 可視性は最小に。`@Override` を省略せず、`@SuppressWarnings` には理由コメントを添える |

## 6. 例外とエラー

応答への変換とメッセージの作法は [web.md](web.md) §5、トランザクションとの関係は [domain.md](domain.md) §2。

| # | 規約 |
|---|------|
| 1 | クライアントへ返す業務エラーは **`AppException(code, message, status)` のみ**を投げる。コード体系の正は [tech_logging.md](../../tech/basic/tech_logging.md) |
| 2 | 検査例外を新設しない（`RuntimeException` 派生にする）。ライブラリの検査例外は捕捉して `AppException` か `IllegalStateException` に変換する |
| 3 | **例外を握りつぶさない**。空の `catch` を書かない。到達しない `catch` は理由コメントを添えて `IllegalStateException` にする |

## 7. ログ

| # | 規約 |
|---|------|
| 1 | SLF4J を使う。`private static final Logger logger = LoggerFactory.getLogger("afkgame.<領域>")` の形で、**ロガー名体系の名前**を指定する（`getLogger(Xxx.class)` にしない）。名前の正は [tech_logging.md](../../tech/basic/tech_logging.md)「ロガー名体系」 |
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

各分冊からの再掲（レビュー用の一覧）。

| 禁止 | 代わりに |
|------|---------|
| フィールド `@Autowired`・setter 注入 | コンストラクタ注入（`private final`） |
| コントローラへの業務ロジック記述 | Service へ集約（[domain.md](domain.md) §2） |
| SQL の `${}` による文字列組み立て | `#{}` によるパラメータバインド |
| ワイルドカード import | 個別 import |
| ゲームバランス数値のハードコード | マスターデータ YAML・`application.yml` |
| `System.out` / `printStackTrace` | SLF4J のロガー |
| 空の `catch`・例外の握りつぶし | `AppException` へ変換するか再スロー |
| 静的な可変フィールド（共有状態） | DI か引数で受け渡す |
| `java.util.Date` / `Calendar` | `java.time`（既定は `Instant`） |
| テーブル定義書に無い列・テーブルの追加 | 基本設計へ差し戻し（[phases.md](../phases.md) §3.2.1） |
| 対象Phaseより後の機能の先行実装 | Phase 厳守（将来拡張を考慮した設計は可） |
