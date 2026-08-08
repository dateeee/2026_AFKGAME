package com.afkgame.web.resource;

import org.slf4j.MDC;

import com.afkgame.web.filter.RequestLogFilter;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 統一エラーレスポンス。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「統一エラーレスポンス形式」。
 * 全APIのエラー応答はコードによらずこの形式で返す
 * （{@code {"error": {"code", "message", "requestId"}}}）。
 * クライアントは**メッセージ文字列ではなくコードで**分岐する。
 *
 * @param error エラー本体
 */
public record ErrorResource(Body error) {

    /**
     * エラー本体。
     *
     * @param code      エラーコード（tech_logging.md「エラーコード体系」）
     * @param message   利用者向けメッセージ
     * @param requestId リクエストID。ログとの突合に使う（採番前なら null で出力しない）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, String requestId) {
    }

    /**
     * 現在のリクエストIDを添えてエラー応答を組み立てる。
     *
     * @param code    エラーコード
     * @param message 利用者向けメッセージ
     * @return エラー応答
     */
    public static ErrorResource of(String code, String message) {
        return new ErrorResource(new Body(code, message, MDC.get(RequestLogFilter.MDC_REQUEST_ID)));
    }
}
