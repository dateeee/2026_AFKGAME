# バックエンドコーディング規約 — サーブレットフィルタ

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。**フィルタを作る・登録する・順序を決めるとき**に従う。
> ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（[basis.md](basis.md) §1）の 7.1（ロギング）と 9（Security）。本書はそこからの差分だけを持つ。
> 全層共通の規約は [common.md](common.md)、Web層の責務は [web.md](web.md)、インターセプタは [interceptor.md](interceptor.md)。

---

## 1. フィルタとインターセプタの使い分け

**横断処理をどちらで書くかの判断は本節が正**（`interceptor.md` では再掲しない）。

| 観点 | サーブレットフィルタ | `HandlerInterceptor` |
|------|-------------------|---------------------|
| 動く場所 | `DispatcherServlet` の**外側**（サーブレットコンテナ） | `DispatcherServlet` の**内側**、ハンドラの前後 |
| 通る範囲 | 全リクエスト（ハンドラが決まらない 404、`OPTIONS`、エラー転送を含む） | `DispatcherServlet` が受け、ハンドラが決まったリクエスト |
| 取れる情報 | `HttpServletRequest` / `HttpServletResponse` だけ | ＋ `HandlerMethod`（呼ばれるコントローラのメソッドとアノテーション） |
| 応答の書き換え | できる（`HttpServletResponseWrapper` で包む） | **できない**（`interceptor.md` §2） |
| 例外の行き先 | `ApiExceptionHandler` を**通らない**（応答を自分で組む。§4） | `HandlerExceptionResolver` を**通る**（`ApiExceptionHandler` が統一形式へ変換する） |
| 登録 | `web.xml` の `filter-mapping`（順序は宣言順） | `SpringMvcConfig#addInterceptors`（パスパターンで絞る） |

| # | 判断 |
|---|------|
| 1 | リクエスト・レスポンスのストリームを触る／全リクエストに漏れなく効かせる／Spring Security のチェーンと順序を組む → **フィルタ** |
| 2 | ハンドラ（メソッド・アノテーション・パス）で挙動を変える／拒否を例外で表して `ApiExceptionHandler` に任せる → **インターセプタ** |
| 3 | どちらでも成り立つなら **インターセプタを既定にする**。対象を明示的に絞れ、例外の扱いが `ApiExceptionHandler` へそろうため |
| 4 | どちらにも**業務ロジックを置かない**（横断処理だけ。`web.md` §4 #2）。特定の機能でしか要らない判定は Service へ寄せる |

## 2. 作り方

| # | 規約 |
|---|------|
| 1 | `org.springframework.web.filter.OncePerRequestFilter` を継承する。`jakarta.servlet.Filter` を直接 implements しない（forward・include・非同期ディスパッチでの二重実行を避けるため） |
| 2 | パッケージは `com.afkgame.web.filter`、クラス名は `<役割>Filter`（`common.md` §3） |
| 3 | 業務ロジック・DB 更新を置かない。`@Transactional` を付けない（トランザクション境界は Service の public メソッド。[domain/service.md](domain/service.md) §4） |
| 4 | **リクエストボディを読まない**。フィルタで読むと下流（`HttpMessageConverter`）が読めなくなる。どうしても要るなら `ContentCachingRequestWrapper` で包み、理由を Javadoc に書く |
| 5 | `filterChain.doFilter(...)` を必ず呼ぶ。呼ばずに戻ってよいのは、そのフィルタが応答を**書き切った**ときだけ |
| 6 | MDC へ入れた項目は `finally` で必ず捨てる（スレッドが使い回されるため。`common.md` §7 #5） |
| 7 | 設定値は `afkgame-env` の設定保持 Bean を注入して読む（`web.md` §4 #1）。Bean にしないフィルタ（§3 #4）へはコンストラクタ引数で渡す |
| 8 | クラス Javadoc に仕様の参照先（`common.md` §8）と、**なぜインターセプタでなくフィルタなのか**を書く（§1 のどの行に当たるか） |

## 3. 登録と順序

登録経路は2つあり、**どちらで登録するかでフィルタの作り方が変わる**。

| 経路 | 対象 | Bean にするか |
|------|------|--------------|
| `web.xml` の `filter-mapping` | 全リクエストに効かせるもの | クラスを直接指定するか、`DelegatingFilterProxy` + **ルートコンテキストの Bean**（`@Component`） |
| `SpringSecurityConfig#filterChain` の `addFilterBefore` / `addFilterAfter` | Security のチェーン内でだけ動くもの | **Bean にしない**（#4） |

**`web.xml` が定める順**（先頭が最外）。

| # | フィルタ | 出所 | 役割 |
|---|---------|------|------|
| 1 | `MDCClearFilter` | gfw | MDC を確実に空にしてから次へ渡す |
| 2 | `RequestLogFilter` | 自作（`DelegatingFilterProxy`） | `X-Request-ID` の採番・MDC 格納・アクセスログ（`tech_logging.md`） |
| 3 | `CharacterEncodingFilter` | Spring | UTF-8 の強制 |
| 4 | `springSecurityFilterChain` | Spring Security（`DelegatingFilterProxy`） | 認証チェーン（内側は下表） |

**Security チェーン内**（`SpringSecurityConfig` が組む）。

| フィルタ | 位置 | 役割 |
|---------|------|------|
| `UserIdMDCPutFilter`（gfw） | `AnonymousAuthenticationFilter` の後 | 認証済みユーザーIDを MDC へ |
| `JwtAuthenticationFilter`（自作） | `UsernamePasswordAuthenticationFilter` の前 | `Authorization: Bearer` の検証。**判定だけを行い拒否の応答は返さない**（§4 #2） |

| # | 規約 |
|---|------|
| 1 | 順序は `web.xml` の**宣言順**が決める。`@Order`・`@WebFilter` は効かないので使わない |
| 2 | MDC を使うフィルタは `MDCClearFilter` より後ろに置く |
| 3 | 認証失敗の応答にもリクエストIDを載せるため、`RequestLogFilter` は `springSecurityFilterChain` より**前**に置く |
| 4 | Security のチェーンにだけ載せるフィルタは **Bean にしない**（`SpringSecurityConfig` が `new` する）。コンポーネント走査で Bean になると `web.xml` 側の登録と取り違えるうえ、`OncePerRequestFilter` がコンテナにも二重登録されうる |
| 5 | `web.xml` から `DelegatingFilterProxy` で引くフィルタは**ルートコンテキスト**の Bean にする（`ApplicationContextConfig` が `com.afkgame.web.filter` を走査する。`SpringMvcConfig` では走査しない） |
| 6 | フィルタを増減したら**本節の表を同じ変更で直す**。順序は動作に直結するため、コード側のコメントだけに残さない |

## 4. 例外と応答

| # | 規約 |
|---|------|
| 1 | **フィルタ内の例外は `ApiExceptionHandler`（`@RestControllerAdvice`）を通らない**。応答が要るならフィルタ側で組み立てる。REST 専用で `error-page` を定義しない（§5）ため、投げっぱなしにするとコンテナ既定の HTML が返って統一形式が崩れる |
| 2 | 認証の失敗は `ApiAuthenticationEntryPoint` が統一エラー形式で返す（[exception.md](exception.md) §4 #8。形式を変えない）。`JwtAuthenticationFilter` は失敗理由をリクエスト属性へ記録するだけにする — 認証不要なエンドポイントでは失敗しても素通りさせる必要があるため |
| 3 | 応答の JSON はルートコンテキストの `JsonMapper` を注入して書き出す。Spring MVC の変換器と**同じインスタンス**を使い、形式をそろえる |
| 4 | 上記以外の予期しない例外（`exception.md` §1 の分類3）は**最外の自作フィルタ**（`RequestLogFilter`）で捕捉し、ERROR + スタックトレースを記録したうえで統一エラー形式の 500（`INTERNAL_UNEXPECTED_ERROR`）を返す。ハンドラにも `error-page` にも届かないため、ここで閉じないとログにも応答形式にも穴が開く |
| 5 | 例外を握りつぶさない・空の `catch` を書かない（`exception.md` §3 #7）。#4 の捕捉も**ログを残したうえで**応答へ変換する |

## 5. 採らないもの（雛形・ガイドラインとの差分）

| 対象 | 判断 | 理由 |
|------|------|------|
| `ExceptionLoggingFilter`（gfw・7.1） | **採らない** | `ExceptionLogger` に依存する（`exception.md` §5 #3 で不採用）。例外コードも雛形の `e.xx.fw.*` で本プロジェクトの体系（`tech_logging.md`）と別物になる。フィルタ内の例外ログは §4 #4 が担う |
| `XTrackMDCPutFilter`（gfw） | 採らない | 相関IDは `X-Request-ID` に一本化し、採番と MDC 格納は `RequestLogFilter` が担う |
| `HttpSessionEventLoggingListener`・`session-config` | 採らない | ステートレス（`SessionCreationPolicy.STATELESS`）でセッションを持たない |
| `error-page`（4.3.3.1.4） | 定義しない | 画面が無く、応答は統一エラー形式の JSON で返す（`exception.md` §4 #7） |
| CSRF トークンのフィルタ（9.5） | 使わない | Cookie を認証に使わない（`web.md` §7 #2） |

## 6. テスト

| # | 規約 |
|---|------|
| 1 | 単体テストは `MockHttpServletRequest` / `MockHttpServletResponse` / `MockFilterChain` を組んで `doFilter(...)` を直接呼ぶ（Spring コンテキストを起動しない） |
| 2 | **順序と連結**（Security チェーンとの前後、認証失敗応答にリクエストIDが載ること）は単体では担保できない。MockMvc の結合テストで確かめる（[test.md](test.md) §1） |
| 3 | MDC を使うフィルタは**後始末**（処理後に `MDC.getCopyOfContextMap()` が空）を必ず検証する。例外で抜けた経路も含める |
| 4 | 配置・命名・タグ・分岐マーカーは `test.md` が正。本書では再掲しない |

## 7. 本書が持たないもの（分担）

| 内容 | 正 |
|------|-----|
| インターセプタの使い方（フック・登録・拒否の書き方） | [interceptor.md](interceptor.md) |
| 例外の3分類・応答への変換・エラーコード | [exception.md](exception.md) |
| ロガー名体系・ログ項目・マスク規則・リクエストログの仕様 | [tech_logging.md](../../tech/basic/tech_logging.md) |
| 認証方式・CORS・セキュリティ対策の一覧 | [tech_auth.md](../../tech/detail/tech_auth.md)・[tech_security.md](../../tech/nonfunctional/tech_security.md) |
| Web層の責務・コントローラ・Resource | [web.md](web.md) |
| ディレクトリツリー・モジュール構成 | [tech_structure_backend.md](../../tech/basic/tech_structure_backend.md) §4 |
