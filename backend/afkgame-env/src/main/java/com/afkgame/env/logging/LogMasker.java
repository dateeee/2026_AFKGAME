package com.afkgame.env.logging;

/**
 * 機密情報のマスク規則。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「機密情報のマスク規則」。
 * 適用先は {@link LogKey} が持つため、ログを書く側は生値を渡すだけでよい。
 *
 * <p>残す文字を引いても伏せ字が1文字も残らない長さでは、全体を伏せる。
 * 伏せ字は固定長にして、元の値の長さを推測させない。
 */
final class LogMasker {

    /** 値を丸ごと伏せるときの文字列。 */
    private static final String FULL_MASK = "****";

    /** ローカル部を伏せるときの文字列（tech_logging.md の例 {@code ab***@example.com} に合わせる）。 */
    private static final String LOCAL_PART_MASK = "***";

    /** トークンの先頭・末尾に残す文字数。 */
    private static final int TOKEN_VISIBLE = 4;

    /** メールアドレスのローカル部の先頭に残す文字数。 */
    private static final int EMAIL_VISIBLE = 2;

    private LogMasker() {
    }

    /**
     * トークン値をマスクする。
     *
     * @param value 生のトークン値（null 不可）
     * @return 先頭4文字 + 伏せ字 + 末尾4文字。9文字未満なら伏せ字のみ
     */
    static String maskToken(String value) {
        if (value.length() <= TOKEN_VISIBLE * 2) {
            return FULL_MASK;
        }
        return value.substring(0, TOKEN_VISIBLE) + FULL_MASK + value.substring(value.length() - TOKEN_VISIBLE);
    }

    /**
     * メールアドレスをマスクする。
     *
     * @param value 生のメールアドレス（null 不可）
     * @return ローカル部の先頭2文字 + 伏せ字 + ドメイン。{@code @} が無ければ伏せ字のみ
     */
    static String maskEmail(String value) {
        int atMark = value.indexOf('@');
        if (atMark < 0) {
            return FULL_MASK;
        }

        String localPart = value.substring(0, atMark);
        String domain = value.substring(atMark);
        if (localPart.length() <= EMAIL_VISIBLE) {
            return LOCAL_PART_MASK + domain;
        }
        return localPart.substring(0, EMAIL_VISIBLE) + LOCAL_PART_MASK + domain;
    }
}
