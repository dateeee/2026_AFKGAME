package com.afkgame.web.resource;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code GET /api/auth/verify-email} のクエリパラメータ。
 *
 * <p>仕様: docs/tech/detail/tech_auth/verify.md §20 手順1。制約違反は 422
 * （docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」）。
 *
 * <p>クエリ1個だが Resource へ束ねる。素の {@code @RequestParam} では**未指定が 400
 * {@code HTTP_400}** になり、未指定・空文字とも 422 {@code VALIDATION_ERROR} とする §21 #2 を
 * 満たせないため。束ねると両方が {@code MethodArgumentNotValidException} になり、
 * {@code ApiExceptionHandler} の既存の変換がそのまま効く。
 *
 * @param token メール本文のリンクに載った生のトークン
 */
public record VerifyEmailResource(@NotBlank String token) {
}
