# バックエンドコーディング規約 — ログ

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。**ログを書く・ログの出力先を触るときは本書が正**。
> 本書は「**どのログを・誰が・どう書くか**」を決める。ログフォーマット・項目名・ロガー名体系・マスク規則・
> `reason` の値・エラーコード体系の正は [tech_logging.md](../../tech/basic/tech_logging.md)（重複させない）。
> 例外の3分類と送出・変換は [exception.md](exception.md)、フィルタの作り方と登録順は [filter.md](filter.md)。

---

## 1. ログ3種別と出力先

ログは**用途で3種別に分け、別ファイルへ出す**。1つのファイルに混ぜると、監視（エラー）・通信の追跡・処理の追跡でそれぞれ必要な粒度が両立しないため。

| # | 種別 | ファイル | ロガー名 | 何を出すか | 出力主体 |
|---|------|---------|---------|-----------|---------|
| 1 | **通信ログ** | `communication.log` | `afkgame.comm` | プロセス境界をまたぐ通信の START / END | `RequestLogFilter`（受信）・外部通信クラス（送信） |
| 2 | **アプリケーションログ** | `application.log` | `afkgame.layer`（境界）<br>`afkgame.<領域>`（業務） | 層をまたぐ呼び出しの START / END（AOP）と業務イベント | `LayerLoggingInterceptor`・業務コード |
| 3 | **エラーログ** | `error.log` | （全ロガー） | ERROR レベルの**転記** | appender のしきい値フィルタ（コードは書かない） |

- **3種別は排他ではない**。エラーログは種別1・2から ERROR を転記した**アラート用の集約**で、原本は元のファイルにも残る。監視（`tech_operations.md` §12.3「ERROR ログ件数」）は `error.log` だけを見れば足りる状態を保つ
- 出力先は環境変数 `LOG_DIR`（既定 `${catalina.base:-.}/logs`）。**コードからファイル名・パスを参照しない**
- ローテーションは `TimeBasedRollingPolicy` で日次・`gz` 圧縮・**保持14日**（バックアップの保持期間 `tech_operations_procedure.md` §12.5 と揃える）
- コンソール（`catalina.out`）は `LOG_FORMAT=text`（local）で全レベル、`json`（production）は **ERROR のみ**。本番でファイルとコンソールの二重肥大を作らない

### 1.1 appender とロガーの割り当て

出力先は**ロガー名だけで決まる**。`logback.xml` が `<include resource="logback-appenders-${LOG_FORMAT:-text}.xml"/>` で形式ごとの appender 定義を読み分ける（`<springProfile>`・`<if>` が使えない事情は `logback.xml` のコメント）。

| appender | 出力先 | フィルタ |
|----------|--------|---------|
| `COMMUNICATION` | `${LOG_DIR}/communication.log` | — |
| `APPLICATION` | `${LOG_DIR}/application.log` | — |
| `ERROR_ALERT` | `${LOG_DIR}/error.log` | `ThresholdFilter level=ERROR` |
| `CONSOLE` | 標準出力 | text: なし / json: `ThresholdFilter level=ERROR` |

| logger | additivity | appender-ref |
|--------|-----------|-------------|
| `afkgame.comm` | `false` | `COMMUNICATION` + `ERROR_ALERT` + `CONSOLE` |
| `afkgame`（`comm` 以外の全領域） | 既定 | （root へ委ねる） |
| `root` | — | `APPLICATION` + `ERROR_ALERT` + `CONSOLE` |

| # | 規約 |
|---|------|
| 1 | **出力先の振り分けはロガー名とレベルだけで行う**。appender 名・ファイル名をコードへ書かない |
| 2 | ロガー名は `LoggerName` の値を使う。enum に無い名前を文字列で書かない（新しい領域を出すときに enum へ足す） |
| 3 | `LoggerFactory` の直接呼び出し・`System.out.println`・`printStackTrace` を使わない。入口は `AppLogger` だけ |
| 4 | `afkgame.comm` は `additivity=false` なので `ERROR_ALERT` を**明示的に**紐づける。外すと通信の ERROR がアラートから漏れる |

## 2. 通信ログ（communication.log）

対象は**プロセス境界をまたぐ通信**。受信（クライアント → API）と送信（API → 外部サービス）の両方を、1通信につき **START 1行 + END 1行**で残す。

| # | 規約 |
|---|------|
| 1 | **受信は `RequestLogFilter` だけが出す**。コントローラ・Service から通信ログを書かない（`filter.md` §1 の使い分けに従う） |
| 2 | **送信は外部通信を行うクラスが出す**（メール送信・外部API呼び出し）。呼び出しの直前に START、`finally` で END |
| 3 | **START と END は必ず対で出す**。例外で抜ける経路でも END を落とさない（`try` / `finally` で組む） |
| 4 | 方向は `direction`（`in` / `out`）で区別する。送信は相手を `target` に書く（`smtp`・`google_oauth` 等の固定値） |
| 5 | **ボディを出さない**。出すのはメタ情報（メソッド・パス・ステータス・所要時間・方向・相手）だけ。リクエストボディ・レスポンスボディ・クエリ文字列の値を載せない |
| 6 | レベルは INFO。HTTP 4xx / 5xx は「通信としては成立した」ので INFO のまま END を残す。**通信自体が成立しなかったとき**（接続不能・タイムアウト）だけ END を ERROR にする |
| 7 | 業務上の失敗理由（`reason`）を通信ログに書かない。それは §4 の業務ログが持つ |

**出力例（受信・text 形式）:**

```
[2026-08-09 14:38:30] INFO  afkgame.comm: START direction=in method=POST path=/api/auth/login client_ip=127.0.0.1 request_id=550e8400
[2026-08-09 14:38:30] INFO  afkgame.comm: END   direction=in method=POST path=/api/auth/login status_code=200 duration_ms=45 player_id=user_001 request_id=550e8400
```

**出力例（送信）:**

```
[2026-08-09 14:38:31] INFO  afkgame.comm: START direction=out target=smtp request_id=550e8400
[2026-08-09 14:38:31] INFO  afkgame.comm: END   direction=out target=smtp status_code=250 duration_ms=310 request_id=550e8400
```

| 項目 | START | END | 備考 |
|------|-------|-----|------|
| `direction` | ○ | ○ | `in` / `out` |
| `method` / `path` / `client_ip` | ○（受信） | ○（受信） | 受信のみ |
| `target` | ○（送信） | ○（送信） | 送信のみ |
| `status_code` / `duration_ms` | — | ○ | END でのみ確定する |
| `request_id` ほか横断項目 | ○ | ○ | MDC が載せる（各所で詰め直さない） |

## 3. アプリケーションログ①：AOP による境界ログ

層をまたぐ呼び出しの START / END は **AOP が出す**。各メソッドの先頭・末尾に手書きすると、書き漏れ・書式のばらつき・業務コードの見通し低下がそのまま残るため。

| # | 境界 | 対象 |
|---|------|------|
| 1 | **Web ↔ Domain** | `com.afkgame.domain.service` の Service（`ServiceImpl`）の public メソッド |
| 2 | **Domain ↔ Repository** | `com.afkgame.domain.repository` の Repository の public メソッド |

コントローラそのものは対象にしない（受信の START / END は §2 の通信ログが持つ。二重に出さない）。

| # | 規約 |
|---|------|
| 1 | 境界ログは **`LayerLoggingInterceptor`（`afkgame-env` の `com.afkgame.env.logging`）だけが出す**。同じ内容を業務コードで手書きしない |
| 2 | 適用は `AspectJExpressionPointcutAdvisor` を境界ごとに1本ずつ Bean 定義して行い、**ポイントカット式は `META-INF/spring/afkgame.properties` が持つ**。`@Aspect` のアノテーションへ定数で書くと `afkgame-env` が `afkgame-domain` のパッケージ名を抱え、依存方向（`common.md` §2）に反するため |
| 3 | **START / END を対で出す**。END は例外で抜けた経路でも必ず出し、その場合は `reason=exception` を付ける（例外そのものの記録は §5 の担当で、ここでは ERROR にしない） |
| 4 | レベルは **INFO 固定**。引数・戻り値まで常時出力する（本番で事後追跡できることを優先する。ログ量は §1 のローテーションで受け止める） |
| 5 | ロガー名は `afkgame.layer`。出力するのは `signature`（`AuthServiceImpl#login` 形式）・`args`・`result`・`duration_ms` |
| 6 | **Spring AOP はプロキシ経由でのみ効く**。同一クラス内の自己呼び出しは記録されない（`@Transactional` と同じ制約）。境界をまたぐ呼び出しを内部呼び出しへ畳まない |
| 7 | Interceptor に**ログ以外の副作用を持たせない**。例外は必ずそのまま再スローする（握らない・差し替えない） |

```
[2026-08-09 14:38:30] INFO  afkgame.layer: START signature=AuthServiceImpl#login args=[ab***@example.com, ****] request_id=550e8400
[2026-08-09 14:38:30] INFO  afkgame.layer: START signature=UserRepository#findByEmail args=[ab***@example.com] request_id=550e8400
[2026-08-09 14:38:30] INFO  afkgame.layer: END   signature=UserRepository#findByEmail result=User(id=user_001) duration_ms=3 request_id=550e8400
[2026-08-09 14:38:30] INFO  afkgame.layer: END   signature=AuthServiceImpl#login result=AuthResult(userId=user_001) duration_ms=42 request_id=550e8400
```

### 3.1 引数・戻り値の出力規則（マスクは AOP 側で完結させる）

引数・戻り値を常時 INFO で出す以上、**マスク漏れは設計で塞ぐ**。`toString()` の実装任せにしない。整形は `com.afkgame.env.logging` の共通部品が担い、呼び出し側にマスクを書かせない（`tech_logging.md`「機密情報のマスク規則」と同じ方針）。

| # | 規約 |
|---|------|
| 1 | **パラメータ名が機密名に一致したら値を伏せる**。機密名は共通部品が持つ固定表（`password`・`rawPassword`・`newPassword`・`token`・`accessToken`・`refreshToken`・`secret`・`credential` → `****`／`email` → `LogKey.EMAIL` と同じマスク） |
| 2 | 名前で判定できないもの（戻り値・Entity・Resource のフィールド）は、**機密フィールドを `toString()` から外す**ことで担保する。Lombok は `@ToString.Exclude`、手書きは対象フィールドを含めない。**機密項目を持つクラスを新設・改修したら同時に対応する**（レビュー観点。§6） |
| 3 | 生のトークン・パスワードを返すメソッドは、**戻り値を出力対象から外す注釈**を付けて `****` にする（例: `JwtService#createAccessToken`） |
| 4 | **コレクション・配列・`Map` は要素を展開せず件数だけ**出す（`List(size=12)`）。ログ量とマスク漏れの両方を同時に抑える |
| 5 | 1値あたり **200文字で打ち切り**、末尾に `...` を付ける |
| 6 | `null` は `null`、`Optional` は中身へ #1〜#5 を適用する。`void` の戻り値は `result` を出さない |

## 4. アプリケーションログ②：業務ログ（AppLogger）

AOP が残すのは「**どこを通ったか**」だけ。「**なぜその結果になったか**」は業務コードが `AppLogger` で残す。

| # | 規約 |
|---|------|
| 1 | `private static final AppLogger logger = AppLogger.of(LoggerName.<領域>)` の形で持つ（使い方の正は `tech_logging.md`「ログの書き方（共通部品）」） |
| 2 | **項目はメッセージへ埋め込まず `with()` / `reason()` で積む**（`"reason=" + r` や `user_id={}` を書かない）。JSON 形式で独立フィールドになる形を唯一の書き方にする |
| 3 | **境界の通過・引数・所要時間を書かない**（§3 の AOP が出す）。業務ログが持つのは分岐の理由と結果の要約だけ |
| 4 | 想定内の失敗は WARNING + `reason`。`reason` の値は `LogReason` へ足す（文字列リテラルで書かない）。レベルの使い分けの正は `tech_logging.md`「ログレベル方針」 |
| 5 | メッセージのプレースホルダは `{}`（文字列連結・`String.format` を使わない） |
| 6 | 横断項目（`request_id`・`player_id` ほか）は MDC（`RequestLogFilter`・`JwtAuthenticationFilter`）が載せる。各所で詰め直さない |
| 7 | パスワード・トークン生値・メールアドレスをそのまま出さない。トークン・メールは `LogKey.TOKEN` / `LogKey.EMAIL` へ渡せば自動でマスクされる |

## 5. エラーログ（error.log）

`error.log` は**アラート用**。「1件でも出たら内容を確認する」（`tech_operations.md` §12.3）が成り立つ状態を保つことが本節の目的で、ノイズを1件も入れない。

| # | 規約 |
|---|------|
| 1 | `error.log` は **ERROR レベルの転記**であり、専用の出力 API を作らない。`AppLogger.error()` を通常どおり使えば自動で載る |
| 2 | ERROR を出すのは**運用者・開発者の対処が要るとき**だけ（`exception.md` §1 の分類2・分類3）。利用者の操作で解消するもの（分類1）は WARNING + `reason` に留める |
| 3 | ERROR には**必ず原因例外を `cause(e)` で添える**（スタックトレース付き）。メッセージだけの ERROR を出さない |
| 4 | **同じ例外を二重に ERROR しない**。出すのは**送出元（分類2）**か **`ApiExceptionHandler`（分類3）**のどちらか一方 |
| 5 | ERROR には `request_id` が載っている（MDC）。エラー応答が返す `requestId` との突合を壊さない（`tech_logging.md`「グローバル例外ハンドラ」） |
| 6 | **定常的に出る ERROR を残さない**。恒常的に出るものは WARNING へ落とすか原因を直す。リトライで回復する一過性の失敗は、**回復しなかったときだけ** ERROR |

## 6. 禁止事項とレビュー観点

| 禁止 | 代わりに |
|------|---------|
| `LoggerFactory.getLogger(...)` の直接呼び出し | `AppLogger.of(LoggerName.…)`（§1 #3） |
| `System.out.println` / `printStackTrace` | `AppLogger`（§1 #3） |
| appender 名・ログファイルのパスをコードに書く | ロガー名で振り分ける（§1 #1） |
| ログ項目をメッセージへ埋め込む（`"reason=" + r`） | `with()` / `reason()`（§4 #2） |
| 業務コードで START / END・所要時間を手書き | 通信ログ（§2）・AOP（§3）に任せる |
| リクエスト／レスポンスボディの出力 | メタ情報のみ（§2 #5） |
| 引数・戻り値を `toString()` 任せで出す | 共通部品のマスク規則（§3.1） |
| 機密フィールドを `toString()` に含めたまま Entity・Resource を追加する | `@ToString.Exclude`（§3.1 #2） |
| 想定内の失敗を ERROR で出す | WARNING + `reason`（§5 #2） |
| 例外を握りつぶしてログだけ出す | `exception.md` §3 #7 の3分類へ変換するか再スロー |
| START だけ・END だけを出す | 必ず対で出す（§2 #3・§3 #3） |

## 7. テストと分担

| # | 規約 |
|---|------|
| 1 | ログ要件のあるクラスは `ListAppender<ILoggingEvent>` でレベル・メッセージ・MDC を検証する（`test.md` §1 #1） |
| 2 | 通信ログは **START / END が対で出ること**を検証する。異常系（例外送出・タイムアウト）でも END が出ることを含める |
| 3 | 境界ログは**マスク結果をアサートする**（機密名の引数が `****` になる・コレクションが件数表記になる）。整形の内部実装ではなく出力文字列を検証する |
| 4 | AOP の適用有無は**結合テストで確認する**。プロキシ経由でないと効かないため、モックを直接呼ぶ単体テストでは境界ログが出ない |

| 決めていること | 正 |
|---------------|-----|
| ログ3種別・出力先・ローテーション・出力主体・書き方 | **本書** |
| ログフォーマット・ログ項目名・ロガー名体系・マスク規則・`reason` の値・エラーコード体系 | [tech_logging.md](../../tech/basic/tech_logging.md) |
| 例外の3分類・送出・応答への変換 | [exception.md](exception.md) |
| フィルタの作り方・登録順・採らないもの | [filter.md](filter.md) |
| 環境変数（`LOG_LEVEL` / `LOG_FORMAT` / `LOG_DIR`）の既定値 | [tech_operations.md](../../tech/nonfunctional/tech_operations.md) §12.2 |
