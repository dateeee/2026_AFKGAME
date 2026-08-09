package com.afkgame.env.logging;

/**
 * ログ項目 {@code reason} の値。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「失敗理由（reason）の値」。
 * クライアントへは失敗理由を出し分けず（coding_standards_backend/exception.md §4 #2）、
 * 内部の切り分けは本 enum が担う。
 *
 * <p>値を文字列リテラルで書かないことで、表記ゆれと綴り誤りを防ぐ。
 */
public enum LogReason {

    /** {@code Authorization} ヘッダが無い。 */
    HEADER_MISSING("header_missing"),

    /** {@code Bearer } で始まらない。 */
    INVALID_FORMAT("invalid_format"),

    /** アクセストークンの有効期限切れ。 */
    TOKEN_EXPIRED("token_expired"),

    /** 署名不正・{@code sub} 欠落。 */
    INVALID_TOKEN("invalid_token"),

    /** トークンの用途（{@code typ}）が想定と異なる。 */
    INVALID_TOKEN_TYPE("invalid_token_type"),

    /** トークンは正当だがユーザーが存在しない。 */
    USER_NOT_FOUND("user_not_found"),

    /** 登録時にメールが使用済み。 */
    EMAIL_TAKEN("email_taken"),

    /** 重複確認の通過後に一意制約違反で使用済みと判明した。 */
    EMAIL_TAKEN_CONFLICT("email_taken_conflict"),

    /** ログイン時に該当するメールのユーザーが存在しない。 */
    EMAIL_NOT_FOUND("email_not_found"),

    /** ログイン対象がパスワードを持たない（Google連携のみのアカウント）。 */
    PASSWORD_NOT_SET("password_not_set"),

    /** パスワードが一致しない。 */
    PASSWORD_MISMATCH("password_mismatch"),

    /** AOP境界ログのEND出力時、例外で抜けた（logging/application.md §3 規約3）。 */
    EXCEPTION("exception");

    private final String value;

    LogReason(String value) {
        this.value = value;
    }

    /**
     * ログへ出力する値を返す。
     *
     * @return snake_case の理由コード
     */
    public String value() {
        return value;
    }
}
