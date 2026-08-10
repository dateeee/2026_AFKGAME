package com.afkgame.web.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/password-reset/confirm} のリクエストボディ。
 *
 * <p>仕様: docs/tech/detail/tech_auth/password_reset.md §24 手順1。制約違反は 422
 * （docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」）。
 *
 * <p>フィールド名は API の camelCase をそのまま使う（tech_api/common.md §5.0。Jackson の変換は不要）。
 *
 * @param token メール本文のリンクに載った生のトークン
 * @param newPassword 新しい生のパスワード（8文字以上128文字以下。tech_auth.md §1「パスワード要件」）
 */
public record PasswordResetConfirmResource(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 128) String newPassword) {
}
