package com.afkgame.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.afkgame.domain.exception.AppException;
import com.afkgame.domain.model.User;
import com.afkgame.domain.service.AuthService;
import com.afkgame.domain.service.JwtService;
import com.afkgame.env.logging.LogKey;

/**
 * {@link JwtAuthenticationFilter} の単体テスト。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「認証エラーの詳細ログ」「AUTH_ コード一覧」、
 * docs/tech/detail/tech_auth.md §7（{@code Authorization: Bearer <token>}）。
 *
 * <p>分岐観点: ヘッダ無し（{@code AUTH_HEADER_MISSING} を既定に委ねる）/ Bearer でない
 * （{@code AUTH_INVALID_FORMAT}）/ トークン不正・期限切れ（{@link JwtService} のコードを引き継ぐ）/
 * ユーザー不在（{@code AUTH_USER_NOT_FOUND}）/ 正常（認証情報を設定する）。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthService authService;

    private JwtAuthenticationFilter filter;

    private JwtAuthenticationFilter filter() {
        if (filter == null) {
            filter = new JwtAuthenticationFilter(jwtService, authService);
        }
        return filter;
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/game/state");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    /** フィルタを通し、記録された認証失敗を返す（失敗が無ければ null）。 */
    private AppException runFilter(MockHttpServletRequest request) throws Exception {
        filter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return (AppException) request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);
    }

    @Nested
    @DisplayName("認証しないケース")
    class TestUnauthenticated {

        @Test
        @DisplayName("Authorization ヘッダが無ければ失敗を記録せず素通しする（既定コードに委ねる）")
        void test_ヘッダが無ければ素通しする() throws Exception {
            assertThat(runFilter(request(null))).isNull();

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(jwtService, never()).parseUserId(any());
        }

        @Test
        void test_Bearer形式でなければAUTH_INVALID_FORMATを記録する() throws Exception {
            AppException failure = runFilter(request("Basic dXNlcjpwYXNz"));

            assertThat(failure.getCode()).isEqualTo("AUTH_INVALID_FORMAT");
            assertThat(failure.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void test_トークンが不正なら検証時のコードを引き継ぐ() throws Exception {
            when(jwtService.parseUserId("bad-token"))
                    .thenThrow(new AppException("AUTH_INVALID_TOKEN", "Invalid token", 401));

            AppException failure = runFilter(request("Bearer bad-token"));

            assertThat(failure.getCode()).isEqualTo("AUTH_INVALID_TOKEN");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void test_ユーザーが存在しなければAUTH_USER_NOT_FOUNDを記録する() throws Exception {
            when(jwtService.parseUserId("orphan-token")).thenReturn("guest_404");
            when(authService.findAuthenticatedUser("guest_404"))
                    .thenThrow(new AppException("AUTH_USER_NOT_FOUND", "User not found", 401));

            AppException failure = runFilter(request("Bearer orphan-token"));

            assertThat(failure.getCode()).isEqualTo("AUTH_USER_NOT_FOUND");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("認証できるケース")
    class TestAuthenticated {

        @Test
        void test_正当なトークンなら認証情報とMDCを設定する() throws Exception {
            User user = new User();
            user.setId("guest_001");
            when(jwtService.parseUserId("good-token")).thenReturn("guest_001");
            when(authService.findAuthenticatedUser("guest_001")).thenReturn(user);

            MockHttpServletRequest request = request("Bearer good-token");
            MockFilterChain chain = new MockFilterChain();
            filter().doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE)).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isSameAs(user);
            // ログ突合のため認証済みユーザーIDを MDC へ載せる（tech_logging.md「リクエストログ用フィルタ」）
            assertThat(MDC.get(LogKey.PLAYER_ID.field())).isEqualTo("guest_001");
        }
    }
}
