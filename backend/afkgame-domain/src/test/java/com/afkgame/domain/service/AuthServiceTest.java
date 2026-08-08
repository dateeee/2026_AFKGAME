package com.afkgame.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.afkgame.domain.exception.AppException;
import com.afkgame.domain.model.RefreshToken;
import com.afkgame.domain.model.User;
import com.afkgame.domain.repository.RefreshTokenMapper;
import com.afkgame.domain.repository.UserMapper;
import com.afkgame.env.config.AuthProperties;

/**
 * {@link AuthService} の単体テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §1（期限・ローテーション）・§3「ゲストプレイ」・
 * §4「リフレッシュトークン」（再利用検知で全トークン失効）、
 * docs/tech/basic/tech_logging.md「AUTH_ コード一覧」。
 *
 * <p>分岐観点: リフレッシュの 正常 / 該当なし / revoked済み（再利用検知）/ 期限切れ /
 * ユーザー不在、および認証ユーザー取得の 存在する / しない。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final AuthProperties AUTH_PROPERTIES = new AuthProperties(
            "afkgame-test-secret-value-32bytes-or-longer",
            Duration.ofMinutes(30),
            Duration.ofDays(30));

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    private AuthService authService;

    private AuthService authService() {
        if (authService == null) {
            authService = new AuthService(
                    userMapper, refreshTokenMapper, new JwtService(AUTH_PROPERTIES), AUTH_PROPERTIES);
        }
        return authService;
    }

    /** 有効なリフレッシュトークンのレコードを組み立てる。 */
    private static RefreshToken storedToken(String tokenHash) {
        RefreshToken token = new RefreshToken();
        token.setId(1);
        token.setUserId("guest_001");
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        token.setRevoked(false);
        token.setCreatedAt(Instant.now());
        return token;
    }

    private static User storedUser() {
        User user = new User();
        user.setId("guest_001");
        user.setDisplayName("冒険者");
        user.setGuest(true);
        return user;
    }

    @Nested
    @DisplayName("ゲストアカウント作成")
    class TestCreateGuest {

        @Test
        void test_ゲストユーザーとトークンペアを発行する() {
            AuthResult result = authService().createGuest();

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(saved.capture());
            // ID は guest_<UUID>（tech_auth.md §2）
            assertThat(saved.getValue().getId()).startsWith("guest_");
            assertThat(saved.getValue().isGuest()).isTrue();
            assertThat(saved.getValue().isEmailVerified()).isFalse();
            assertThat(saved.getValue().getEmail()).isNull();
            assertThat(saved.getValue().getCreatedAt()).isNotNull();
            assertThat(saved.getValue().getLastLoginAt()).isNotNull();

            assertThat(result.user().getId()).isEqualTo(saved.getValue().getId());
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
        }

        @Test
        void test_リフレッシュトークンは生値を保存せずハッシュを保存する() {
            AuthResult result = authService().createGuest();

            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenMapper).insert(saved.capture());
            assertThat(saved.getValue().getTokenHash())
                    .isNotEqualTo(result.refreshToken())
                    .hasSize(64);
            assertThat(saved.getValue().isRevoked()).isFalse();
            // 有効期限は30日（tech_auth.md §1）
            assertThat(saved.getValue().getExpiresAt())
                    .isAfter(Instant.now().plus(Duration.ofDays(29)));
        }
    }

    @Nested
    @DisplayName("リフレッシュ")
    class TestRefresh {

        /** 生トークンを発行し、それに対応する保存済みレコードを mapper に仕込む。 */
        private String issueAndStore() {
            AuthResult issued = authService().createGuest();
            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenMapper).insert(saved.capture());
            when(refreshTokenMapper.selectByTokenHash(saved.getValue().getTokenHash()))
                    .thenReturn(storedToken(saved.getValue().getTokenHash()));
            return issued.refreshToken();
        }

        @Test
        void test_正常時は旧トークンを失効させ新しいペアを返す() {
            String rawToken = issueAndStore();
            when(userMapper.selectById("guest_001")).thenReturn(storedUser());

            AuthResult result = authService().refresh(rawToken);

            verify(refreshTokenMapper).revokeById(1);
            assertThat(result.refreshToken()).isNotEqualTo(rawToken);
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.user().getId()).isEqualTo("guest_001");
        }

        @Test
        void test_該当レコードが無ければAUTH_REFRESH_INVALIDになる() {
            when(refreshTokenMapper.selectByTokenHash(any())).thenReturn(null);

            assertThatThrownBy(() -> authService().refresh("unknown-token"))
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_REFRESH_INVALID", 401);
        }

        @Test
        void test_revoked済みの再利用は全トークンを失効させる() {
            RefreshToken revoked = storedToken("dummy-hash");
            revoked.setRevoked(true);
            when(refreshTokenMapper.selectByTokenHash(any())).thenReturn(revoked);

            assertThatThrownBy(() -> authService().refresh("reused-token"))
                    .isInstanceOf(AppException.class)
                    .extracting("code")
                    .isEqualTo("AUTH_REFRESH_INVALID");

            verify(refreshTokenMapper).revokeAllByUserId("guest_001");
            verify(refreshTokenMapper, never()).revokeById(any());
        }

        @Test
        void test_期限切れはAUTH_REFRESH_INVALIDになる() {
            RefreshToken expired = storedToken("dummy-hash");
            expired.setExpiresAt(Instant.now().minus(Duration.ofSeconds(1)));
            when(refreshTokenMapper.selectByTokenHash(any())).thenReturn(expired);

            assertThatThrownBy(() -> authService().refresh("expired-token"))
                    .isInstanceOf(AppException.class)
                    .extracting("code")
                    .isEqualTo("AUTH_REFRESH_INVALID");

            verify(refreshTokenMapper, never()).revokeAllByUserId(any());
        }

        @Test
        void test_トークンは正当でもユーザーが居なければAUTH_REFRESH_INVALIDになる() {
            when(refreshTokenMapper.selectByTokenHash(any())).thenReturn(storedToken("dummy-hash"));
            when(userMapper.selectById("guest_001")).thenReturn(null);

            assertThatThrownBy(() -> authService().refresh("orphan-token"))
                    .isInstanceOf(AppException.class)
                    .extracting("code")
                    .isEqualTo("AUTH_REFRESH_INVALID");
        }
    }

    @Nested
    @DisplayName("認証ユーザーの取得")
    class TestFindAuthenticatedUser {

        @Test
        void test_存在すればユーザーを返す() {
            when(userMapper.selectById("guest_001")).thenReturn(storedUser());

            assertThat(authService().findAuthenticatedUser("guest_001").getId()).isEqualTo("guest_001");
        }

        @Test
        void test_存在しなければAUTH_USER_NOT_FOUNDになる() {
            when(userMapper.selectById("guest_404")).thenReturn(null);

            assertThatThrownBy(() -> authService().findAuthenticatedUser("guest_404"))
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_USER_NOT_FOUND", 401);
        }
    }
}
