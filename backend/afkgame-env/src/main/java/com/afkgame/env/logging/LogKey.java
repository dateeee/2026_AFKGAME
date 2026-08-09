package com.afkgame.env.logging;

import java.util.function.UnaryOperator;

/**
 * ログ項目の名前。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「ログフォーマット」「機密情報のマスク規則」。
 * 値は MDC へ載せ、text 形式では末尾の {@code key=value}、JSON 形式では独立フィールドとして出力される。
 * **API 応答の camelCase とは別体系**で、項目名は snake_case とする。
 *
 * <p>機密情報のマスクは本 enum が持つ。生値を {@link LogEntry#with(LogKey, String)} へ渡せば、
 * 対象の項目は自動でマスクされる（呼び出し側でマスクを書かない）。
 */
public enum LogKey {

    /** リクエストID。付与は {@code RequestLogFilter}（横断項目）。 */
    REQUEST_ID("request_id"),

    /** 認証済みユーザーID。付与は {@code JwtAuthenticationFilter}（横断項目）。 */
    PLAYER_ID("player_id"),

    /** クライアントIP（横断項目）。 */
    CLIENT_IP("client_ip"),

    /** HTTPメソッド（横断項目）。 */
    METHOD("method"),

    /** リクエストパス（横断項目）。 */
    PATH("path"),

    /** HTTPステータスコード。 */
    STATUS_CODE("status_code"),

    /** 処理時間（ミリ秒）。 */
    DURATION_MS("duration_ms"),

    /** 失敗理由。値は {@link LogReason}。 */
    REASON("reason"),

    /** 処理対象のユーザーID。認証済みを表す {@link #PLAYER_ID} とは別項目。 */
    USER_ID("user_id"),

    /** トークン値。先頭4文字と末尾4文字だけを残す。 */
    TOKEN("token", LogMasker::maskToken),

    /** メールアドレス。ローカル部の先頭2文字とドメインだけを残す。 */
    EMAIL("email", LogMasker::maskEmail);

    private final String field;
    private final UnaryOperator<String> masker;

    LogKey(String field) {
        this(field, UnaryOperator.identity());
    }

    LogKey(String field, UnaryOperator<String> masker) {
        this.field = field;
        this.masker = masker;
    }

    /**
     * ログへ出力する項目名を返す。
     *
     * @return snake_case の項目名
     */
    public String field() {
        return field;
    }

    /**
     * 項目の規則に従って値をマスクする。
     *
     * @param value 生の値（null 不可）
     * @return マスク後の値。マスク対象でない項目はそのまま
     */
    String masked(String value) {
        return masker.apply(value);
    }
}
