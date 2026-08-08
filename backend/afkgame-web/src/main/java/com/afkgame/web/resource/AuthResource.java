package com.afkgame.web.resource;

import com.afkgame.domain.service.AuthResult;

/**
 * 認証系APIの応答（{@code POST /api/auth/guest}・{@code POST /api/auth/refresh} 他）。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §5。
 *
 * @param accessToken  アクセストークン（{@code Authorization: Bearer} に載せる）
 * @param refreshToken リフレッシュトークン
 * @param user         ユーザー情報
 */
public record AuthResource(String accessToken, String refreshToken, UserResource user) {

    public static AuthResource from(AuthResult result) {
        return new AuthResource(
                result.accessToken(), result.refreshToken(), UserResource.from(result.user()));
    }
}
