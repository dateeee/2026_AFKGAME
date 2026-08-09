# バックエンドコーディング規約 — インターセプタ

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。**`HandlerInterceptor` を作る・登録するとき**に従う。
> ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（[basis.md](basis.md) §1）の 7.1（ロギング）と 9（Security）。本書はそこからの差分だけを持つ。
> **フィルタとの使い分けの正は [filter.md](filter.md) §1**（本書では再掲しない）。全層共通の規約は [common.md](common.md)、Web層の責務は [web.md](web.md)。

---

## 1. 位置づけ

`HandlerInterceptor` は `DispatcherServlet` の**内側**で動く。`HandlerMapping` が呼び先（`HandlerMethod`）を決めたあと、ハンドラの前後に割り込む。

- 呼び先が分かるため、**パス・コントローラのメソッド・アノテーションで挙動を変えられる**。これがフィルタとの決定的な違いのひとつ
- もうひとつの違いは例外の行き先で、**インターセプタの例外は `HandlerExceptionResolver` を通り `ApiExceptionHandler` が統一形式へ変換する**（フィルタは通らない。`filter.md` §4 #1）
- 逆に、`DispatcherServlet` へ届かないリクエスト（コンテナが弾くもの）と**応答ボディ**には手が届かない（§2）
- 登録経路は `SpringMvcConfig#addInterceptors` の1系統だけ。サーブレットコンテキスト側の設定であり、フィルタの `web.xml` とは別

**現時点で自作インターセプタは無い**（登録されているのは §4 で不採用とした雛形のものだけ）。作るときは新設パッケージ `com.afkgame.web.interceptor` に置き、クラス名は `<役割>Interceptor` とする。**先に `web.md` §1 の表と `tech_backend.md` §4.1 のツリーへ追記してから**作る（[layering.md](layering.md) §2 と同じ扱い）。

## 2. 3つのフックの使い分け

| メソッド | 動くとき | 書いてよいこと | 注意 |
|---------|---------|--------------|------|
| `preHandle` | ハンドラ実行の直前 | 事前検査（認可・レート制限・ヘッダ検査）、`HandlerMethod` のアノテーション読み取り、計測の開始 | `false` を返すと以降を止めるが、**拒否は例外で表す**（§3 #4） |
| `postHandle` | ハンドラの正常終了後・応答の書き出し前 | （原則なし） | `@RestController` は `@ResponseBody` 相当で、**この時点で本文が書き終わっている**。`ModelAndView` も `null` のため触るものが無い。**使わない** |
| `afterCompletion` | 応答完了後（例外で終わったときも呼ばれる） | 後始末（計測の終了、リクエストスコープに置いた値の破棄）、例外の観測 | 応答は変更できない。**ここで例外を投げない**（応答は既に返っており、握り潰されるだけ） |

**応答の内容を変える必要がある処理は、インターセプタで解かない**。ヘッダだけなら `preHandle`、本文が絡むならフィルタ（`filter.md` §1 #1）かコントローラ側で扱う。

## 3. 規約

| # | 規約 |
|---|------|
| 1 | `HandlerInterceptor` を implements し、**使うメソッドだけ**を `@Override` する（既定実装があるため空メソッドを並べない） |
| 2 | 業務ロジックを置かない（横断処理だけ。`web.md` §4 #2）。DB 更新・`@Transactional` を置かない。ドメインの判定が要るなら Service を注入して呼ぶ |
| 3 | **状態をフィールドに持たない**。インスタンスは全リクエストで共有される。リクエストにひもづく値は `request.setAttribute(...)` か MDC へ置き、`afterCompletion` で必ず捨てる |
| 4 | 拒否するときは**例外を投げる**（[exception.md](exception.md) §1 の分類に従い `BusinessException` など）。インターセプタの例外は `ApiExceptionHandler` が統一エラー形式へ変換するため、応答を自分で組み立てない。**`false` + `sendError(...)` は使わない**（コンテナの HTML エラーページになり形式が崩れる） |
| 5 | `preHandle` の `handler` は `HandlerMethod` とは限らない（静的資源・エラー処理では別の型が来る）。**必ず `instanceof` で確かめてからキャストする**。`HandlerMethod` でないときは何もせず `true` を返す |
| 6 | 登録は `SpringMvcConfig#addInterceptors`。対象は `addPathPatterns` / `excludePathPatterns` で**明示的に絞る**（既定の `/**` を惰性で使わない）。順序は登録順が決まりなので、依存があれば理由をコメントに書く |
| 7 | 認証・認可の可否そのものは Spring Security（`SpringSecurityConfig` の `authorizeHttpRequests`）とドメイン層の所有者検証が持つ（`tech_security.md` §11.5）。インターセプタでそれを**二重に**判定しない |
| 8 | クラス Javadoc に仕様の参照先（`common.md` §8）と、**なぜフィルタでなくインターセプタなのか**を書く（`filter.md` §1 のどの行に当たるか） |

## 4. 採らないもの（雛形・ガイドラインとの差分）

| 対象 | 判断 | 理由 |
|------|------|------|
| `TraceLoggingInterceptor`（gfw・7.1） | **採らない** | ハンドラの開始・終了と所要時間は `RequestLogFilter` が全リクエストぶん INFO で出す（`tech_logging.md`「リクエストログ用フィルタ」）。TRACE での二重計測になり、ロガー名も本プロジェクトの体系（`common.md` §7 #1）の外になる |
| `HandlerExceptionResolverLoggingInterceptor` + その `Advisor`（gfw・4.3） | **採らない** | `ExceptionLogger` に依存する（`exception.md` §5 #3 で不採用）。例外ログは送出元（分類1）と `ApiExceptionHandler`（分類2・3）が出す。なお本クラスは `HandlerInterceptor` ではなく `HandlerExceptionResolver#resolveException` に当てる **AOP のインターセプタ**で、`addInterceptors` とは別系統 |
| 認可用インターセプタ（9章） | 現時点で持たない | 認可は §3 #7 の2箇所で足りる。持つことになったら本表と §1 を直してから作る |
| `CodeList`・トランザクショントークンのインターセプタ | 採らない | 画面を持たない REST 専用のため（`SpringMvcConfig` の Javadoc と対） |

## 5. テスト

| # | 規約 |
|---|------|
| 1 | 単体テストは `MockHttpServletRequest` / `MockHttpServletResponse` と、対象メソッドから作った `HandlerMethod` を渡して各フックを直接呼ぶ |
| 2 | §3 #5（`HandlerMethod` でない `handler` が来る経路）を**分岐として必ず1件**持つ |
| 3 | **登録の当たり方**（`addPathPatterns` の対象・除外、ハンドラとの前後、例外が統一形式へ変換されること）は MockMvc の結合テストで確かめる（[test.md](test.md) §1） |
| 4 | 配置・命名・タグ・分岐マーカーは `test.md` が正。本書では再掲しない |

## 6. 本書が持たないもの（分担）

| 内容 | 正 |
|------|-----|
| フィルタとの使い分け・フィルタの作り方と登録順 | [filter.md](filter.md) |
| 例外の3分類・応答への変換・エラーコード | [exception.md](exception.md) |
| ロガー名体系・ログ項目・マスク規則 | [tech_logging.md](../../tech/basic/tech_logging.md) |
| 認可の既定・レート制限の仕様 | [tech_security.md](../../tech/nonfunctional/tech_security.md) |
| Web層の責務・コントローラ・Resource・設定 | [web.md](web.md) |
| ディレクトリツリー・モジュール構成 | [tech_backend.md](../../tech/basic/tech_backend.md) §4 |
