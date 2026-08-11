package com.afkgame.web.resource.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/link-account} のリクエストボディ。
 *
 * <p>仕様: docs/tech/detail/tech_auth/link.md §18 手順5。制約違反は 422
 * （docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」）。
 *
 * <p><b>3項目とも必須にしない</b>。メール連携（{@code email}+{@code password}）と Google連携
 * （{@code googleAuthCode}）のちょうど一方を受けるため、{@code @NotBlank} を付けると
 * Google連携のボディが 422 で落ちる。**どちらも無い・両方あるの判定は 400
 * {@code AUTH_LINK_PAYLOAD_INVALID}（§19 #5・#6）でサービス層が持つ** — 422 ではないため
 * Bean Validation では表せない。
 *
 * @param email          連携するメールアドレス（254文字以内。長さの正は account.md §9「入力長」）
 * @param password       生のパスワード（8文字以上128文字以下。tech_auth.md §1「パスワード要件」）
 * @param googleAuthCode Google の認可コード
 */
public record LinkAccountResource(
        @Email @Size(max = 254) String email,
        @Size(min = 8, max = 128) String password,
        String googleAuthCode) {
}
