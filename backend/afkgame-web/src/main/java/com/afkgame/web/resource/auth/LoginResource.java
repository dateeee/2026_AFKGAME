package com.afkgame.web.resource.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/auth/login} のリクエストボディ。
 *
 * <p>仕様: docs/tech/detail/tech_auth/account.md §12 手順1。制約違反は 422
 * （docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」）。
 *
 * <p>登録時の「8文字以上」はログインでは課さない。要件変更より前に作られた短いパスワードの
 * アカウントを締め出さないため（§12 手順1）。
 *
 * @param email    メールアドレス
 * @param password 生のパスワード
 */
public record LoginResource(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
