package com.afkgame.domain.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date; // 規約例外: JJWT の expiration(Date) / issuedAt(Date) が java.util.Date を要求する

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;
import org.terasoluna.gfw.common.exception.BusinessException;
import org.terasoluna.gfw.common.message.ResultMessages;

import com.afkgame.env.config.AuthSettings;
import com.afkgame.env.logging.AppLogger;
import com.afkgame.env.logging.LogReason;
import com.afkgame.env.logging.LoggerName;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * {@link JwtService} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。本クラスは署名鍵の組み立てと JJWT の呼び出しを担う。
 */
@Service
public class JwtServiceImpl implements JwtService {

    private static final AppLogger logger = AppLogger.of(LoggerName.AUTH);

    /** アクセストークンの用途クレーム。用途の違うトークンを取り違えないために検証する（tech_auth.md §4）。 */
    private static final String TOKEN_TYPE_ACCESS = "access";

    /** 再ログインが必要な失敗。署名不正・用途違い・{@code sub} 欠落を区別させない。 */
    private static final String INVALID_TOKEN = "AUTH_INVALID_TOKEN";

    private final SecretKey signingKey;
    private final Duration accessTokenExpire;
    private final Clock clock;

    public JwtServiceImpl(AuthSettings authSettings, Clock clock) {
        // 鍵長が不足していれば起動時に例外となる（32バイト以上。tech_security.md §11.8）
        this.signingKey = Keys.hmacShaKeyFor(authSettings.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpire = authSettings.accessTokenExpire();
        this.clock = clock;
    }

    /** {@inheritDoc} */
    @Override
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
     * {@inheritDoc}
     *
     * <p>JJWT の例外はここで {@link BusinessException} へ振り直す（期限切れと不正を混ぜない）。
     * 正常稼働時にも起こりうるライブラリ例外の分類し直しであり、規約 exception.md §2 の
     * 「ライブラリ例外 → ビジネス例外」に当たる。
     */
    @Override
    public String parseUserId(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            logger.warn("認証失敗").reason(LogReason.TOKEN_EXPIRED).log();
            throw authError("AUTH_TOKEN_EXPIRED");
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("認証失敗").reason(LogReason.INVALID_TOKEN).log();
            throw authError(INVALID_TOKEN);
        }

        if (!TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class))) {
            logger.warn("認証失敗").reason(LogReason.INVALID_TOKEN_TYPE).log();
            throw authError(INVALID_TOKEN);
        }

        String userId = claims.getSubject();
        if (userId == null) {
            logger.warn("認証失敗").reason(LogReason.INVALID_TOKEN).log();
            throw authError(INVALID_TOKEN);
        }
        return userId;
    }

    /**
     * 認証失敗のビジネス例外を作る。
     *
     * <p>載せるのはコードだけで、文言もステータスも持たせない（規約 exception.md §3 #5・§4 #4）。
     */
    private static BusinessException authError(String code) {
        return new BusinessException(ResultMessages.error().add(code));
    }
}
