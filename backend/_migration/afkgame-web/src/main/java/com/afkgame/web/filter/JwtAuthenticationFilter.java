package com.afkgame.web.filter;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.afkgame.domain.exception.AppException;
import com.afkgame.domain.model.User;
import com.afkgame.domain.service.AuthService;
import com.afkgame.domain.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code Authorization: Bearer <access_token>} を検証して認証情報を組み立てる。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §7、
 * docs/tech/basic/tech_logging.md「認証エラーの詳細ログ」「AUTH_ コード一覧」。
 *
 * <p>本フィルタは認証の可否を判定するだけで、拒否の応答は返さない。認証不要な
 * エンドポイント（{@link com.afkgame.web.config.SecurityConfig}）では失敗しても素通りさせる必要が
 * あるため、失敗理由はリクエスト属性に記録し、実際に拒否する段になって
 * {@link ApiAuthenticationEntryPoint} が応答へ変換する。
 *
 * <p>Bean にはしない。{@code Filter} 型の Bean は Spring Boot がサーブレットフィルタとしても
 * 自動登録するため、Security のフィルタチェーンへの追加と二重に登録されてしまう。
 * 生成は {@link com.afkgame.web.config.SecurityConfig} が行う。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 認証失敗の理由（{@link AppException}）を保持するリクエスト属性のキー。 */
    public static final String AUTH_FAILURE_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".failure";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Logger logger = LoggerFactory.getLogger("afkgame.auth");

    private final JwtService jwtService;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthService authService) {
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        authenticate(request);
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            // 理由は AUTH_HEADER_MISSING。既定として ApiAuthenticationEntryPoint が返す
            return;
        }

        if (!authorization.startsWith(BEARER_PREFIX)) {
            logger.warn("認証失敗 reason=invalid_format");
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE,
                    new AppException("AUTH_INVALID_FORMAT", "Invalid authorization header", 401));
            return;
        }

        try {
            String userId = jwtService.parseUserId(authorization.substring(BEARER_PREFIX.length()));
            User user = authService.findAuthenticatedUser(userId);

            MDC.put(RequestLogFilter.MDC_PLAYER_ID, user.getId());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, List.of()));
        } catch (AppException e) {
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE, e);
        }
    }
}
