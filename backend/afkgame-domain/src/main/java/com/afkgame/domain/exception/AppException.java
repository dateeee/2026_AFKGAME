package com.afkgame.domain.exception;

/**
 * エラーコードを持つアプリケーション例外。
 *
 * <p>統一エラーレスポンス（docs/tech/basic/tech_error_handling.md「統一エラーレスポンス形式」）へ
 * 変換される唯一の業務例外。コード体系と HTTP ステータスの対応は同ファイルの
 * 「エラーコード体系」「AUTH_ コード一覧」が正。
 *
 * <p>クライアントは**メッセージ文字列ではなくコードで**分岐するため、コードは仕様の値をそのまま使う。
 * ドメイン層に Web の型を持ち込まないよう、ステータスは {@code int} で保持する。
 */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final int status;

    /**
     * @param code    エラーコード（例: {@code AUTH_TOKEN_EXPIRED}）
     * @param message クライアントへ返すメッセージ
     * @param status  HTTP ステータスコード
     */
    public AppException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
