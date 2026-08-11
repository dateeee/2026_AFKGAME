package com.afkgame.web.filter;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.terasoluna.gfw.common.exception.ResourceNotFoundException;
import org.terasoluna.gfw.common.exception.ResultMessagesNotificationException;
import org.terasoluna.gfw.common.exception.SystemException;

import com.afkgame.env.logging.AppLogger;
import com.afkgame.env.logging.LogKey;
import com.afkgame.env.logging.LoggerName;
import com.afkgame.web.resource.common.ErrorResource;

import tools.jackson.core.exc.StreamReadException;

/**
 * 全APIのエラー応答を統一形式へそろえるグローバル例外ハンドラ。
 *
 * <p>仕様: docs/tech/basic/tech_error_handling.md「統一エラーレスポンス形式」「グローバル例外ハンドラ」、
 * 変換規約は docs/process/coding_standards_backend/exception.md §4。
 * 業務例外はコードだけを持ち、<b>ステータスと文言は {@link ErrorCatalog} が決める</b>（同 §4 #4）。
 * 例外の {@code getMessage()} は 4xx・5xx とも応答へ写さない（同 §4 #1）。
 *
 * <p>分類2（{@link SystemException}）・分類3（未捕捉）はどちらも 500 と
 * {@code INTERNAL_UNEXPECTED_ERROR} にそろえ、内部情報を載せない。両者を区別するのはログだけで、
 * 分類2には原因を特定できるコードが残る（同 §1）。
 *
 * <p>{@link ResponseEntityExceptionHandler} を継承することで、Spring MVC が投げる標準例外
 * （405・404・415 など）も統一形式へ寄せる。
 *
 * <p>フィルタ内で起きた認証エラーは本クラスを通らない（Spring Security の
 * {@link ApiAuthenticationEntryPoint} が同じ形式で応答する）。
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final AppLogger middlewareLogger = AppLogger.of(LoggerName.MIDDLEWARE);

    /** 型・範囲・必須の違反（tech_api/common.md「HTTPステータスコードの使い分け」の 422）。 */
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    private static final String VALIDATION_MESSAGE = "リクエストの入力値が不正です";

    /** 業務コードを持たない Spring MVC 標準例外のコード接頭辞（4xx のみ）。 */
    private static final String HTTP_CODE_PREFIX = "HTTP_";

    /**
     * 業務エラー（分類1）。コードは例外が持ち、ステータスと文言は対応表が決める。
     *
     * <p>{@link org.terasoluna.gfw.common.exception.BusinessException} と
     * {@link ResourceNotFoundException} を共通の親でまとめて受ける（両者の違いはステータスの決め方だけ）。
     * 404 は対応表を引かず常に 404 とする（exception.md §2.1）。
     *
     * @param e 業務例外
     * @return 統一エラー応答
     */
    @ExceptionHandler(ResultMessagesNotificationException.class)
    public ResponseEntity<ErrorResource> handleBusinessException(ResultMessagesNotificationException e) {
        String code = ErrorCatalog.codeOf(e);
        ErrorCatalog.Entry entry = ErrorCatalog.find(code);
        int status = e instanceof ResourceNotFoundException ? HttpStatus.NOT_FOUND.value() : entry.status();
        return ResponseEntity.status(status).body(ErrorResource.of(code, entry.message()));
    }

    /**
     * システム例外（分類2）。運用者が原因を特定できるコードをログに残し、応答は 500 の定型文にそろえる。
     *
     * @param e システム例外
     * @return 500 の統一エラー応答
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ErrorResource> handleSystemException(SystemException e) {
        middlewareLogger.error("システム例外").with(LogKey.ERROR_CODE, e.getCode()).cause(e).log();
        return internalError();
    }

    /**
     * 未捕捉例外（分類3）。スタックトレースはログにのみ残す。
     *
     * @param e 想定外の例外
     * @return 500 の統一エラー応答
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResource> handleUnexpected(Exception e) {
        middlewareLogger.error("未捕捉例外").cause(e).log();
        return internalError();
    }

    /**
     * Bean Validation 違反は 400 ではなく 422 で返し、違反項目を {@code details} に載せる。
     *
     * <p>どの項目が落ちたかをクライアントが特定できるようにする（規約 §4 #9）。
     * <b>入力値そのもの（{@code rejectedValue}）は載せない</b> — パスワード等が応答とログへ回るため。
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResource.Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResource.Detail(error.getField(), error.getCode()))
                .toList();
        middlewareLogger.warn("バリデーションエラー").log();
        return new ResponseEntity<>(ErrorResource.of(VALIDATION_ERROR, VALIDATION_MESSAGE, details),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * 本文が読めない場合を「解析できたか」で切り分ける（規約 §4 #10）。
     *
     * <p>JSON として解析できない構文破損は <b>400</b>（送信処理のバグ）、解析はできたが
     * スキーマに合わない未知フィールド・型不一致は <b>422</b>（入力のやり直し）。どちらも
     * {@link HttpMessageNotReadableException} で届くため、原因例外の型で判定する
     * （Jackson 3 では {@link StreamReadException} が構文、{@code DatabindException} が変換の失敗）。
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof StreamReadException) {
            middlewareLogger.warn("本文の構文エラー").log();
            return new ResponseEntity<>(httpStatusError(HttpStatus.BAD_REQUEST.value()), HttpStatus.BAD_REQUEST);
        }
        middlewareLogger.warn("バリデーションエラー").log();
        return new ResponseEntity<>(ErrorResource.of(VALIDATION_ERROR, VALIDATION_MESSAGE),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * 上記以外の Spring MVC 標準例外。コードは {@code HTTP_<status>} とする。
     *
     * <p>4xx・5xx とも例外の {@code getMessage()} を応答へ載せない（規約 §4 #1）。5xx は内部の
     * クラス名・変換先の型名が入り、4xx も {@code MethodArgumentTypeMismatchException} 等が
     * 変換先のクラス名を含むため。切り分けに要る情報はログにだけ残す。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            middlewareLogger.error("フレームワーク内部エラー")
                    .with(LogKey.STATUS_CODE, statusCode.value())
                    .cause(ex)
                    .log();
            return new ResponseEntity<>(internalErrorBody(), headers, statusCode);
        }
        middlewareLogger.warn("リクエスト不正")
                .with(LogKey.STATUS_CODE, statusCode.value())
                .cause(ex)
                .log();
        return new ResponseEntity<>(httpStatusError(statusCode.value()), headers, statusCode);
    }

    /** 業務コードを持たない 4xx の応答。文言は定型文で、ステータスだけをコードにする。 */
    private static ErrorResource httpStatusError(int status) {
        return ErrorResource.of(HTTP_CODE_PREFIX + status, ErrorCatalog.GENERIC_MESSAGE);
    }

    private static ResponseEntity<ErrorResource> internalError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(internalErrorBody());
    }

    /** 分類2・分類3に共通の 500 応答。内部情報を載せない（規約 §4 #1）。 */
    private static ErrorResource internalErrorBody() {
        return ErrorResource.of(ErrorCatalog.INTERNAL_ERROR,
                ErrorCatalog.find(ErrorCatalog.INTERNAL_ERROR).message());
    }
}
