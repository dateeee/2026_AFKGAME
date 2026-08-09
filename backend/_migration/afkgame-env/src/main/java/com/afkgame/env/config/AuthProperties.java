package com.afkgame.env.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 認証の設定値（{@code afkgame.auth.*}）。
 *
 * <p>値の正は docs/tech/detail/tech_auth.md §1、環境変数の正は
 * docs/tech/nonfunctional/tech_operations.md §12.2。
 * アプリケーションコードから環境変数を直接読まず、本クラス経由で参照する。
 *
 * @param secret               JWT の署名鍵（{@code JWT_SECRET}）。HS256 のため32バイト以上が必要
 * @param accessTokenExpire    アクセストークンの有効期限
 * @param refreshTokenExpire   リフレッシュトークンの有効期限
 */
@ConfigurationProperties(prefix = "afkgame.auth")
public record AuthProperties(
        String secret,
        Duration accessTokenExpire,
        Duration refreshTokenExpire) {
}
