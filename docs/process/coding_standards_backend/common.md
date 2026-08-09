# バックエンドコーディング規約 — 共通

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。**どの層を書くときも本書を先に読む**。
> ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（[basis.md](basis.md) §1）の `ArchitectureInDetail`。本書はそこからの差分だけを持つ。
> レイヤの定義とコンポーネント間の呼び出し可否は [layering.md](layering.md)、層固有の規約は [domain.md](domain.md)（Entity・Repository）・[domain_service.md](domain_service.md)（Service）・[web.md](web.md)（Web層）・[test.md](test.md)（テスト）。
> 層を問わない横断規約は [exception.md](exception.md)（例外）・[logging.md](logging.md)（ログ）。

---

## 2. モジュールとパッケージ

ガイドラインのマルチプロジェクト構成（`[projectName]-domain` / `-web` / `-env`）との対応は `layering.md` §4。

| モジュール | パッケージ | 置くもの | 依存してよい先 |
|-----------|-----------|---------|--------------|
| `afkgame-env` | `com.afkgame.env.config` / `.logging` | 設定保持 Bean・DataSource・Flyway 起動、ログ基盤 | （なし） |
| `afkgame-domain` | `com.afkgame.domain.model` / `.repository` / `.service` / `.masterdata` / `.rng` / `.exception` | Entity、Repository、Service、マスターデータ、RNG、業務例外 | `afkgame-env` |
| `afkgame-web` | `com.afkgame.web.api` / `.resource` / `.config` / `.filter` | `@RestController`、Resource、Security・フィルタ | `afkgame-domain`、`afkgame-env` |
| `afkgame-initdb` | （Java なし） | Flyway マイグレーション SQL | （なし） |

- **依存方向は `web → domain → env` の一方向**。逆流・循環を作らない
- `afkgame-domain` に Web 層の型（Spring MVC・`jakarta.servlet`・`HttpStatus`）を持ち込まない。**HTTP ステータスはドメイン層で扱わない**（業務例外はエラーコードだけを持ち、ステータスは Web 層が決める。[exception.md](exception.md) §4 #4）
- 新しいパッケージを切るときは [tech_structure_backend.md](../../tech/basic/tech_structure_backend.md) §4.1 のツリーへ同時に追記する

## 3. 命名

層に依存する名前（コントローラ・Resource・Entity・Repository・Service）は `domain.md` §5・`web.md` §6 が正。

| 対象 | 規約 | 例 |
|------|------|-----|
| クラス・インタフェース | UpperCamelCase | `AuthService` |
| 設定バインド | `<領域>Properties` | `AuthProperties` |
| 例外 | `<領域>Exception` | `MasterDataException`（自作できるのは起動時例外だけ。[exception.md](exception.md) §2.1） |
| メソッド・変数 | lowerCamelCase。`boolean` の getter は `is` | `isGuest()` |
| 定数 | UPPER_SNAKE_CASE（`private static final`） | `REFRESH_TOKEN_BYTES` |
| パッケージ | 全小文字・単語区切りなし | `masterdata` |

- 略語は先頭のみ大文字（`JwtService`、`ApiExceptionHandler`）

## 4. 全層共通のルール

層別の責務は `domain.md`・`domain_service.md`・`web.md` が持つ。本節は層に依らないもの。

| # | 規約 |
|---|------|
| 1 | **DI はコンストラクタ注入**。フィールド `@Autowired`・setter 注入を使わない。依存は `private final`。ガイドラインの実装例は `@Inject` のフィールド注入（3.4.1.6.2）だが採らない — 依存を不変にでき、テストで手渡すと欠落がコンパイルエラーになるため（`test.md` §5 #3 と同じ根拠） |
| 2 | 現在時刻・乱数は「外から受ける」。乱数は `RandomFactory` から取得して引数で引き回す（[tech_rng.md](../../tech/detail/tech_rng.md) §2）。静的な共有インスタンスを持たない。**暗号用途の `SecureRandom` だけは例外**（スレッドセーフのため `private static final` で共有可。`RandomFactory` をトークン生成に使うと予測可能になる） |
| 3 | テーブル定義書に無い列・テーブルが必要になったら、実装で先行させず**基本設計へ差し戻す**（[phases.md](../phases.md) §3.2.1） |
| 4 | テスト用にパッケージプライベートなコンストラクタを併設したら、**公開コンストラクタへ `@Autowired` を明示する**。候補が2つあると Spring は既定コンストラクタを探して `NoSuchMethodException` で Bean 生成に失敗する。単体テストは通り、**コンテキストを起こす統合テストだけが落ちる**ため気づきにくい |
| 5 | `@Value` で `Duration` を受けるには ISO-8601（`PT30M`）が要る。素の Spring は Boot の緩い形式（`30m`）を解さないため、**設定値は分・日の整数で持ち** Java 側で `Duration.ofMinutes(...)` へ組む。カンマ区切りは `String[]` で受けて `List.of(...)`、空文字は `Long` へ null 変換される |

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
| 10 | **マジックナンバー禁止**。技術的な定数は `private static final` + Javadoc、運用値は `META-INF/spring/*.properties`、ゲームバランス値はマスターデータ（YAML）へ置く |
| 11 | 早期 return でネストを浅くする（3段以上ネストさせない）。ループ内で文字列を `+` 連結しない |
| 12 | 可視性は最小に。`@Override` を省略せず、`@SuppressWarnings` には理由コメントを添える |

## 6. 例外とエラー

**正は [exception.md](exception.md)**（層を問わない。ビジネス例外・システム例外・予期しないエラーの3分類と、送出・変換・ログの規約）。本書では再掲しない。

## 7. ログ

**正は [logging.md](logging.md)**（層を問わない。ログ3種別＝通信ログ・アプリケーションログ・エラーログの定義と出力先、AOP による境界ログ、業務ログの書き方、禁止事項）。本書では再掲しない。

最低限おさえるのは次の3点で、詳細はすべて `logging.md` にある。

| # | 規約 |
|---|------|
| 1 | **入口は `AppLogger` だけ**（`afkgame-env` の `com.afkgame.env.logging`）。`LoggerFactory` の直接呼び出し・`System.out.println`・`printStackTrace` を使わない |
| 2 | ロガー名は `LoggerName` の値を使い、クラスの配置から独立させる（`getLogger(Xxx.class)` を採らない）。**出力先の振り分けはロガー名とレベルだけで決まる** |
| 3 | **ログ項目はメッセージへ埋め込まず `with()` / `reason()` で積む**（`reason=...` `user_id={}` を文字列に書かない） |

## 8. Javadoc・コメント

| # | 規約 |
|---|------|
| 1 | `public` のクラス・メソッドに**日本語の Javadoc** を書く。1行目は「〜する。」の要約1文 |
| 2 | クラス Javadoc に**仕様書の参照先**を `<p>仕様: docs/....md §N` の形で書く。実装の根拠を追える状態にする |
| 3 | `@param`・`@return`・`@throws` を書く。`@throws BusinessException` / `SystemException` にはエラーコードを添える |
| 4 | 段落は `<p>` で区切る。コード片・識別子は `{@code ...}` |
| 5 | 意図的な未実装・仮実装は Javadoc に**理由と解消時期**を書く（`TODO` だけを残さない） |
| 6 | 行コメントは「何をしているか」ではなく「**なぜそうしたか**」を書く。コードを読めば分かることを繰り返さない |

## 9. 禁止事項

各分冊からの再掲（レビュー用の一覧）。

| 禁止 | 代わりに |
|------|---------|
| フィールド `@Autowired`・setter 注入 | コンストラクタ注入（`private final`） |
| コントローラへの業務ロジック記述 | Service へ集約（`domain_service.md` §1） |
| コントローラから Repository の直接呼び出し | Service を通す（`layering.md` §3 の呼び出し可否） |
| SQL の `${}` による文字列組み立て | `#{}` によるパラメータバインド |
| ワイルドカード import | 個別 import |
| ゲームバランス数値のハードコード | マスターデータ YAML・`META-INF/spring/*.properties` |
| `System.out` / `printStackTrace` | `AppLogger`（§7 #1） |
| `LoggerFactory.getLogger(...)` の直接呼び出し | `AppLogger.of(LoggerName.…)`（§7 #1） |
| ログ項目をメッセージへ埋め込む（`reason=...`） | `with()` / `reason()` で積む（§7 #3） |
| 業務コードで START / END・所要時間を手書き | 通信ログ・AOP に任せる（[logging.md](logging.md) §6） |
| 空の `catch`・例外の握りつぶし | 3分類のいずれかへ変換するか再スロー（`exception.md` §3） |
| 静的な可変フィールド（共有状態） | DI か引数で受け渡す |
| `java.util.Date` / `Calendar` | `java.time`（既定は `Instant`） |
| テーブル定義書に無い列・テーブルの追加 | 基本設計へ差し戻し（`phases.md` §3.2.1） |
| 対象Phaseより後の機能の先行実装 | Phase 厳守（将来拡張を考慮した設計は可） |
