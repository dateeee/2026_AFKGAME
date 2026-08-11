package com.afkgame.web.resource.common;

/**
 * 返す値を持たない操作の成功応答（{@code {"status": "ok"}}）。
 *
 * <p>仕様: docs/tech/detail/tech_auth/account.md §14「出口条件」。
 *
 * @param status 処理結果。成功は {@code ok}
 */
public record StatusResource(String status) {

    /** 成功を表す値。 */
    private static final String OK = "ok";

    /**
     * 成功応答を作る。
     *
     * @return {@code {"status": "ok"}} を表す Resource
     */
    public static StatusResource ok() {
        return new StatusResource(OK);
    }
}
