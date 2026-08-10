package com.afkgame.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date; // 規約例外: JJWT の expiration(Date) が java.util.Date を要求する

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.terasoluna.gfw.common.exception.BusinessException;

import com.afkgame.env.config.AuthSettings;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * {@link JwtServiceImpl} の単体テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §1（有効期限）・§4（JWT構造）、
 * docs/tech/basic/tech_error_handling.md「AUTH_ コード一覧」（期限切れと不正の区別）。
 *
 * <p>分岐観点: 検証成功 / 期限切れ（{@code AUTH_TOKEN_EXPIRED}）/ 署名不正・改竄
 * （{@code AUTH_INVALID_TOKEN}）/ {@code sub} 欠落（{@code AUTH_INVALID_TOKEN}）。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class JwtServiceImplTest {

    private static final String SECRET = "afkgame-test-secret-value-32bytes-or-longer";

    // 発行時刻と JJWT の期限判定を同じ時間軸に置くため、実時間のクロックを渡す
    private final JwtService jwtService = new JwtServiceImpl(
            new AuthSettings(SECRET, Duration.ofMinutes(30), Duration.ofDays(30),
                    12, 8, 128, Duration.ofDays(90), Duration.ofHours(24), Duration.ofHours(1),
                    null),
            Clock.systemUTC());

    /**
     * 業務例外が載せたエラーコードを取り出す。
     *
     * <p>HTTP ステータスは例外が持たず Web 層の対応表が決めるため、コードだけを検証する
     * （規約 exception.md §4 #4）。
     */
    private static String codeOf(Throwable e) {
        return ((BusinessException) e).getResultMessages().getList().get(0).getCode();
    }

    private static Claims claimsOf(String token, String secret) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Nested
    @DisplayName("アクセストークンの発行")
    class TestCreateAccessToken {

        @Test
        void test_仕様どおりのペイロードを持つ() {
            String token = jwtService.createAccessToken("user_001", false);

            Claims claims = claimsOf(token, SECRET);
            assertThat(claims.getSubject()).isEqualTo("user_001");
            assertThat(claims.get("type")).isEqualTo("access");
            assertThat(claims.get("role")).isEqualTo("user");
            assertThat(claims.get("isGuest")).isEqualTo(false);
            assertThat(claims.getIssuedAt()).isNotNull();
            // 有効期限は発行から30分（tech_auth.md §1）
            assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                    .isEqualTo(Duration.ofMinutes(30));
        }

        @Test
        void test_ゲストはisGuestがtrueになる() {
            String token = jwtService.createAccessToken("guest_001", true);

            assertThat(claimsOf(token, SECRET).get("isGuest")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("アクセストークンの検証")
    class TestParseUserId {

        @Test
        void test_正当なトークンからユーザーIDを取り出す() {
            String token = jwtService.createAccessToken("guest_001", true);

            assertThat(jwtService.parseUserId(token)).isEqualTo("guest_001");
        }

        @Test
        void test_期限切れはAUTH_TOKEN_EXPIREDになる() {
            Instant past = Instant.now().minus(Duration.ofHours(1));
            String expired = Jwts.builder()
                    .subject("user_001")
                    .issuedAt(Date.from(past))
                    .expiration(Date.from(past.plus(Duration.ofMinutes(30))))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .compact();

            assertThatThrownBy(() -> jwtService.parseUserId(expired))
                    .isInstanceOf(BusinessException.class)
                    .extracting(JwtServiceImplTest::codeOf)
                    .isEqualTo("AUTH_TOKEN_EXPIRED");
        }

        @Test
        void test_署名が違うトークンはAUTH_INVALID_TOKENになる() {
            String otherSecret = "another-test-secret-value-32bytes-or-longer";
            String forged = Jwts.builder()
                    .subject("user_001")
                    .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(30))))
                    .signWith(Keys.hmacShaKeyFor(otherSecret.getBytes(StandardCharsets.UTF_8)))
                    .compact();

            assertThatThrownBy(() -> jwtService.parseUserId(forged))
                    .isInstanceOf(BusinessException.class)
                    .extracting(JwtServiceImplTest::codeOf)
                    .isEqualTo("AUTH_INVALID_TOKEN");
        }

        @Test
        void test_トークンの体をなさない文字列はAUTH_INVALID_TOKENになる() {
            assertThatThrownBy(() -> jwtService.parseUserId("not-a-token"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(JwtServiceImplTest::codeOf)
                    .isEqualTo("AUTH_INVALID_TOKEN");
        }

        @Test
        void test_typeがaccessでないトークンはAUTH_INVALID_TOKENになる() {
            String wrongType = Jwts.builder()
                    .subject("user_001")
                    .claim("type", "refresh")
                    .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(30))))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .compact();

            assertThatThrownBy(() -> jwtService.parseUserId(wrongType))
                    .isInstanceOf(BusinessException.class)
                    .extracting(JwtServiceImplTest::codeOf)
                    .isEqualTo("AUTH_INVALID_TOKEN");
        }

        @Test
        void test_subを持たないトークンはAUTH_INVALID_TOKENになる() {
            String noSubject = Jwts.builder()
                    .claim("type", "access")
                    .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(30))))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .compact();

            assertThatThrownBy(() -> jwtService.parseUserId(noSubject))
                    .isInstanceOf(BusinessException.class)
                    .extracting(JwtServiceImplTest::codeOf)
                    .isEqualTo("AUTH_INVALID_TOKEN");
        }
    }
}
