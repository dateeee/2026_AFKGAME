package com.afkgame.domain.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.afkgame.domain.exception.AppException;
import com.afkgame.env.config.AuthSettings;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * アクセストークン（JWT）の発行と検証。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §1（有効期限）・§4（ペイロード）。
 * 失敗はコードで区別する（docs/tech/basic/tech_logging.md「AUTH_ コード一覧」）。
 * クライアントは {@code AUTH_TOKEN_EXPIRED} なら refresh を試し、
 * {@code AUTH_INVALID_TOKEN} なら再ログインへ倒すため、両者を混ぜない。
 */
@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger("afkgame.auth");

    /** アクセストークンの用途クレーム。用途の違うトークンを取り違えないために検証する（tech_auth.md §4）。 */
    private static final String TOKEN_TYPE_ACCESS = "access";

    private final SecretKey signingKey;
    private final Duration accessTokenExpire;
    private final Clock clock;

    public JwtService(AuthSettings authSettings, Clock clock) {
        // 鍵長が不足していれば起動時に例外となる（32バイト以上。tech_security.md §11.8）
        this.signingKey = Keys.hmacShaKeyFor(authSettings.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpire = authSettings.accessTokenExpire();
        this.clock = clock;
    }

    /**
     * アクセストークンを発行する。
     *
     * <p>{@code role}・{@code isGuest} は載せるだけで検証しない。権限とゲスト種別は DB の
     * {@code users} を正とし、{@link AuthService#findAuthenticatedUser(String)} が引き直す
     * （トークン内の値を信用すると、DB 側の変更がトークン失効まで反映されないため）。
     *
     * @param userId ユーザーID
     * @param guest  ゲストアカウントかどうか
     * @return 署名済みJWT
     */
    public String createAccessToken(String userId, boolean guest) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(userId)
                .claim("type", TOKEN_TYPE_ACCESS)
                .claim("role", "user")
                .claim("isGuest", guest)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessTokenExpire)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * アクセストークンを検証してユーザーIDを取り出す。
     *
     * <p>用途クレーム {@code type} が {@code access} でないものは受理しない。同じ署名鍵で発行された
     * 別用途のトークン（メール確認・パスワードリセット等）をアクセストークンとして通さないため。
     *
     * @param token 検証するトークン
     * @return {@code sub} クレームのユーザーID
     * @throws AppException 期限切れ（{@code AUTH_TOKEN_EXPIRED}）または不正（{@code AUTH_INVALID_TOKEN}）
     */
    public String parseUserId(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            logger.warn("認証失敗 reason=token_expired");
            throw new AppException("AUTH_TOKEN_EXPIRED", "Token expired", 401);
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("認証失敗 reason=invalid_token");
            throw new AppException("AUTH_INVALID_TOKEN", "Invalid token", 401);
        }

        if (!TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class))) {
            logger.warn("認証失敗 reason=invalid_token_type");
            throw new AppException("AUTH_INVALID_TOKEN", "Invalid token type", 401);
        }

        String userId = claims.getSubject();
        if (userId == null) {
            logger.warn("認証失敗 reason=invalid_token");
            throw new AppException("AUTH_INVALID_TOKEN", "Invalid token payload", 401);
        }
        return userId;
    }
}
