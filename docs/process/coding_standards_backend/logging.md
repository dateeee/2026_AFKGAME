# バックエンドコーディング規約 — ログ

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。**ログを書く・ログの出力先を触るときは本書が正**。
> 本書は「**どのログを・誰が・どう書くか**」を決める。ログフォーマット・項目名・ロガー名体系・マスク規則・
> `reason` の値・エラーコード体系の正は [tech_logging.md](../../tech/basic/tech_logging.md)（重複させない）。
> 例外の3分類と送出・変換は [exception.md](exception.md)、フィルタの作り方と登録順は [filter.md](filter.md)。

---

**種別ごとの書き方は分冊が持つ**。本書は3種別に共通する §1 出力先・§5 エラーログ・§6 禁止事項・§7 テストと分担を持つ。

| 分冊 | 担当節 | 読むとき |
|------|-------|---------|
| [logging/communication.md](logging/communication.md) | §2 通信ログ | リクエスト受信・外部サービス呼び出しのログを触るとき |
| [logging/application.md](logging/application.md) | §3 AOP による境界ログ / §4 業務ログ（`AppLogger`） | 業務コードでログを書くとき・AOP の境界ログを触るとき |

---

## 1. ログ3種別と出力先

ログは**用途で3種別に分け、別ファイルへ出す**。1つのファイルに混ぜると、監視（エラー）・通信の追跡・処理の追跡でそれぞれ必要な粒度が両立しないため。

| # | 種別 | ファイル | ロガー名 | 何を出すか | 出力主体 |
|---|------|---------|---------|-----------|---------|
| 1 | **通信ログ**（§2） | `communication.log` | `afkgame.comm` | プロセス境界をまたぐ通信の START / END | `RequestLogFilter`（受信）・外部通信クラス（送信） |
| 2 | **アプリケーションログ**（§3・§4） | `application.log` | `afkgame.layer`（境界）<br>`afkgame.<領域>`（業務） | 層をまたぐ呼び出しの START / END（AOP）と業務イベント | `LayerLoggingInterceptor`・業務コード |
| 3 | **エラーログ**（§5） | `error.log` | （全ロガー） | ERROR レベルの**転記** | appender のしきい値フィルタ（コードは書かない） |

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

## 5. エラーログ（error.log）

`error.log` は**アラート用**。「1件でも出たら内容を確認する」（`tech_operations.md` §12.3）が成り立つ状態を保つことが本節の目的で、ノイズを1件も入れない。

| # | 規約 |
|---|------|
| 1 | `error.log` は **ERROR レベルの転記**であり、専用の出力 API を作らない。`AppLogger.error()`（[logging/application.md](logging/application.md) §4）を通常どおり使えば自動で載る |
| 2 | ERROR を出すのは**運用者・開発者の対処が要るとき**だけ（`exception.md` §1 の分類2・分類3）。利用者の操作で解消するもの（分類1）は WARNING + `reason` に留める |
| 3 | ERROR には**必ず原因例外を `cause(e)` で添える**（スタックトレース付き）。メッセージだけの ERROR を出さない |
| 4 | **同じ例外を二重に ERROR しない**。出すのは**送出元（分類2）**か **`ApiExceptionHandler`（分類3）**のどちらか一方 |
| 5 | ERROR には `request_id` が載っている（MDC）。エラー応答が返す `requestId` との突合を壊さない（`tech_logging.md`「グローバル例外ハンドラ」） |
| 6 | **定常的に出る ERROR を残さない**。恒常的に出るものは WARNING へ落とすか原因を直す。リトライで回復する一過性の失敗は、**回復しなかったときだけ** ERROR |

## 6. 禁止事項とレビュー観点

§2 の実体は [logging/communication.md](logging/communication.md)、§3・§4 の実体は [logging/application.md](logging/application.md) にある。

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
| ログ3種別・出力先・ローテーション・出力主体、エラーログ、禁止事項、テスト | **本書** |
| 通信ログ（受信・送信）の START / END と出力項目 | [logging/communication.md](logging/communication.md) |
| AOP による境界ログ・引数／戻り値のマスク、業務ログ（`AppLogger` ほか共通部品）の使い方 | [logging/application.md](logging/application.md) |
| ログフォーマット・ログ項目名・ロガー名体系・マスク規則・`reason` の値・エラーコード体系 | [tech_logging.md](../../tech/basic/tech_logging.md) |
| 例外の3分類・送出・応答への変換 | [exception.md](exception.md) |
| フィルタの作り方・登録順・採らないもの | [filter.md](filter.md) |
| 環境変数（`LOG_LEVEL` / `LOG_FORMAT` / `LOG_DIR`）の既定値 | [tech_operations.md](../../tech/nonfunctional/tech_operations.md) §12.2 |
