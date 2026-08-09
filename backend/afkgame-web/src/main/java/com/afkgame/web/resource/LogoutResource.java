package com.afkgame.web.resource;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/auth/logout} のリクエストボディ。
 *
 * <p>仕様: docs/tech/detail/tech_auth_account.md §14 手順2。制約違反は 422
 * （docs/tech/basic/tech_api_common.md「HTTPステータスコードの使い分け」）。
 *
 * @param refreshToken 失効させるリフレッシュトークン（生値）
 */
public record LogoutResource(@NotBlank String refreshToken) {
}
