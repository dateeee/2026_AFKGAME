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

    /** リフレッシュトークンに該当する行が無い。 */
    REFRESH_NOT_FOUND("refresh_not_found"),

    /** リフレッシュトークンの有効期限切れ。 */
    REFRESH_EXPIRED("refresh_expired"),

    /** 失効済みのリフレッシュトークンが再利用された。 */
    REFRESH_REUSED("refresh_reused"),

    /** ログアウト時、リフレッシュトークンの持ち主が認証ユーザーと異なる。 */
    REFRESH_OWNER_MISMATCH("refresh_owner_mismatch"),

    /** 移行のペイロードで連携方法が一意に決まらない（どちらも無い・両方ある）。 */
    LINK_PAYLOAD_INVALID("link_payload_invalid"),

    /** Google連携を要求されたが {@code GOOGLE_CLIENT_ID} が未設定。 */
    GOOGLE_NOT_CONFIGURED("google_not_configured"),

    /** Google連携を要求されたが Phase 2 では未対応。 */
    GOOGLE_NOT_IMPLEMENTED("google_not_implemented"),

    /** 移行対象が既に本登録済み。 */
    ALREADY_REGISTERED("already_registered"),

    /** 確認トークンに該当する行が無い。 */
    VERIFICATION_NOT_FOUND("verification_not_found"),

    /** 確認トークンの用途が {@code verify_email} でない（再設定トークンの流用）。 */
    VERIFICATION_PURPOSE_MISMATCH("verification_purpose_mismatch"),

    /** 確認トークンの有効期限切れ。 */
    VERIFICATION_EXPIRED("verification_expired"),

    /** 再設定トークンに該当する行が無い。 */
    RESET_NOT_FOUND("reset_not_found"),

    /** 再設定トークンの用途が {@code password_reset} でない（確認トークンの流用）。 */
    RESET_PURPOSE_MISMATCH("reset_purpose_mismatch"),

    /** 再設定トークンが使用済み（メール確認と違い冪等にしない）。 */
    RESET_USED("reset_used"),

    /** 再設定トークンの有効期限切れ。 */
    RESET_EXPIRED("reset_expired"),

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
