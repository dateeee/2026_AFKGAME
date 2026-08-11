package com.afkgame.domain.service.auth;

import com.afkgame.domain.model.User;

/**
 * 認証成功時に発行したトークンペアと対象ユーザー。
 *
 * <p>応答形式は docs/tech/detail/tech_auth.md §5 が正。
 * リフレッシュトークンは生値であり、DBにはハッシュのみを保存する。
 *
 * @param user         対象ユーザー
 * @param accessToken  アクセストークン（JWT）
 * @param refreshToken リフレッシュトークン（生値。クライアントへ返す唯一の機会）
 */
public record AuthResult(User user, String accessToken, String refreshToken) {

    /**
     * トークンを伏せた表現を返す（AOP境界ログの {@code result} 出力対策。
     * logging/application.md §3.1 規約2「戻り値のフィールドをtoString()から外す」）。
     */
    @Override
    public String toString() {
        return "AuthResult[user=" + user + ", accessToken=****, refreshToken=****]";
    }
}
