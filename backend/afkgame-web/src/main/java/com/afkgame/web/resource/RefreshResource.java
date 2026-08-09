package com.afkgame.web.resource;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/auth/refresh} のリクエストボディ。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §5。制約違反は 422
 * （docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」）。
 *
 * @param refreshToken クライアントが保持しているリフレッシュトークン
 */
public record RefreshResource(@NotBlank String refreshToken) {
}
