package com.afkgame.web.resource;

import java.util.List;

import org.slf4j.MDC;

import com.afkgame.env.logging.LogKey;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 統一エラーレスポンス。
 *
 * <p>仕様: docs/tech/basic/tech_error_handling.md「統一エラーレスポンス形式」。
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
     * @param code      エラーコード（tech_error_handling.md「エラーコード体系」）
     * @param message   利用者向けメッセージ
     * @param requestId リクエストID。ログとの突合に使う（採番前なら null で出力しない）
     * @param details   入力チェック違反の項目一覧。{@code VALIDATION_ERROR} のときだけ添える
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, String requestId, List<Detail> details) {
    }

    /**
     * 入力チェック違反の1項目。
     *
     * <p>仕様: tech_error_handling.md「入力チェック違反の `details`」。
     * <b>入力値そのもの（{@code rejectedValue}）は載せない</b> — パスワード・トークンが
     * 応答とアクセスログへ回るため。文言はフロントエンドが {@code target} + {@code code} から組み立てる。
     *
     * @param target 違反したプロパティ名（camelCase。ネストはドット区切り）
     * @param code   Bean Validation の制約名（{@code NotBlank}・{@code Size} 等）
     */
    public record Detail(String target, String code) {
    }

    /**
     * 現在のリクエストIDを添えてエラー応答を組み立てる。
     *
     * @param code    エラーコード
     * @param message 利用者向けメッセージ
     * @return エラー応答
     */
    public static ErrorResource of(String code, String message) {
        return new ErrorResource(new Body(code, message, requestId(), null));
    }

    /**
     * 違反項目を添えてエラー応答を組み立てる。
     *
     * @param code    エラーコード
     * @param message 利用者向けメッセージ
     * @param details 違反項目の一覧
     * @return エラー応答
     */
    public static ErrorResource of(String code, String message, List<Detail> details) {
        return new ErrorResource(new Body(code, message, requestId(), details));
    }

    private static String requestId() {
        return MDC.get(LogKey.REQUEST_ID.field());
    }
}
