package com.afkgame.web.resource;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/register} のリクエストボディ。
 *
 * <p>仕様: docs/tech/detail/tech_auth/account.md §10 手順1。制約違反は 422
 * （docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」）。
 *
 * @param email    登録するメールアドレス（254文字以内。RFC 5321 の上限。長さの正は §9「入力長」）
 * @param password 生のパスワード（8文字以上128文字以下。tech_auth.md §1「パスワード要件」）
 */
public record RegisterResource(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128) String password) {
}
