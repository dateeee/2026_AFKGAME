package com.afkgame.web.resource;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/password-reset/request} のリクエストボディ。
 *
 * <p>仕様: docs/tech/detail/tech_auth/password_reset.md §22 手順1。制約違反は 422
 * （docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」）。
 *
 * @param email 再設定するアカウントのメールアドレス（254文字以内。RFC 5321 の上限。
 *        長さの正は account.md §9「入力長」）
 */
public record PasswordResetRequestResource(
        @NotBlank @Email @Size(max = 254) String email) {
}
