package com.afkgame.env.config;

import java.time.Duration;

/**
 * 認証の設定値を保持する。
 *
 * <p>値の正は docs/tech/detail/tech_auth.md §1（トークン期限・bcrypt strength・パスワード要件・
 * ゲスト期限）と docs/tech/nonfunctional/tech_security.md §11.3（パスワード長）、環境変数の正は
 * docs/tech/nonfunctional/tech_operations.md §12.2。
 * 組み立ては {@link com.afkgame.env.config.app.AfkgameSettingsConfig} が1か所で行う。
 *
 * @param secret JWT の署名鍵（{@code JWT_SECRET}）。HS256 のため32バイト以上が必要
 * @param accessTokenExpire アクセストークンの有効期限
 * @param refreshTokenExpire リフレッシュトークンの有効期限
 * @param bcryptStrength パスワードハッシュ（bcrypt）のストレッチ回数
 * @param passwordMinLength パスワードの最小文字数
 * @param passwordMaxLength パスワードの最大文字数
 * @param guestExpire ゲストアカウントの有効期限（最終アクセスからの経過）
 * @param verificationTokenExpire メール確認トークンの有効期間（tech_auth/mail.md §16.3）
 * @param passwordResetTokenExpire パスワード再設定トークンの有効期間（同 §16.3。乗っ取り時に
 *        書き換えられる窓を狭めるため確認トークンより短い）
 */
public record AuthSettings(
        String secret,
        Duration accessTokenExpire,
        Duration refreshTokenExpire,
        int bcryptStrength,
        int passwordMinLength,
        int passwordMaxLength,
        Duration guestExpire,
        Duration verificationTokenExpire,
        Duration passwordResetTokenExpire) {
}
