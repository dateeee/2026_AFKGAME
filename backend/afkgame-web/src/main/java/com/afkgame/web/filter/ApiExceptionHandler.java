package com.afkgame.web.filter;

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

import com.afkgame.domain.exception.AppException;
import com.afkgame.env.logging.AppLogger;
import com.afkgame.env.logging.LogKey;
import com.afkgame.env.logging.LoggerName;
import com.afkgame.web.resource.ErrorResource;

/**
 * 全APIのエラー応答を統一形式へそろえるグローバル例外ハンドラ。
 *
 * <p>仕様: docs/tech/basic/tech_error_handling.md「統一エラーレスポンス形式」「グローバル例外ハンドラ」。
 * 未捕捉例外は ERROR ログにスタックトレースを残し、クライアントには 500 と
 * {@code INTERNAL_UNEXPECTED_ERROR} のみを返す（内部情報を漏らさない）。
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

    /**
     * 業務エラー。コードと HTTP ステータスは例外が持つ値をそのまま使う。
     *
     * @param e 業務例外
     * @return 統一エラー応答
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResource> handleAppException(AppException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ErrorResource.of(e.getCode(), e.getMessage()));
    }

    /**
     * 未捕捉例外。スタックトレースはログにのみ残す。
     *
     * @param e 想定外の例外
     * @return 500 の統一エラー応答
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResource> handleUnexpected(Exception e) {
        middlewareLogger.error("未捕捉例外").cause(e).log();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResource.of("INTERNAL_UNEXPECTED_ERROR", "サーバー内部エラーが発生しました"));
    }

    /** Bean Validation 違反は 400 ではなく 422 で返す。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationError();
    }

    /** 本文が読めない（不正なJSON・未知フィールド）場合も入力値の誤りとして 422 で返す。 */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationError();
    }

    /**
     * 上記以外の Spring MVC 標準例外。コードは {@code HTTP_<status>} とする。
     *
     * <p>5xx（{@code HttpMessageNotWritableException}・{@code ConversionNotSupportedException} 等）は
     * 例外メッセージに内部のクラス名・変換先の型名が入るため、応答へ載せず
     * {@link #handleUnexpected(Exception)} と同じ定型文へそろえる（規約 §4-4）。
     * 4xx は内容が送り手自身のリクエストに閉じるため、そのまま返す。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            middlewareLogger.error("フレームワーク内部エラー")
                    .with(LogKey.STATUS_CODE, statusCode.value())
                    .cause(ex)
                    .log();
            return new ResponseEntity<>(
                    ErrorResource.of("INTERNAL_UNEXPECTED_ERROR", "サーバー内部エラーが発生しました"),
                    headers, statusCode);
        }
        return new ResponseEntity<>(
                ErrorResource.of("HTTP_" + statusCode.value(), ex.getMessage()), headers, statusCode);
    }

    private static ResponseEntity<Object> validationError() {
        middlewareLogger.warn("バリデーションエラー").log();
        return new ResponseEntity<>(
                ErrorResource.of(VALIDATION_ERROR, VALIDATION_MESSAGE), HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
