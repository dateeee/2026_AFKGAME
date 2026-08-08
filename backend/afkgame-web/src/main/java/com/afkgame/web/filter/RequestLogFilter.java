package com.afkgame.web.filter;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 全APIリクエストにリクエストIDを付与し、処理時間つきの INFO ログを出す。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「リクエストログ用フィルタ」。
 * リクエストIDは MDC で引き回し、レスポンスヘッダ {@code X-Request-ID} とエラー応答
 * （{@link com.afkgame.web.resource.ErrorResource}）へ載せてログとの突合を可能にする。
 *
 * <p>Spring Security のフィルタチェーンより先に動かす。認証失敗の応答にもリクエストIDが要るため。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    /** リクエストIDのレスポンスヘッダ名（tech_api_common.md「共通ヘッダ」）。 */
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    /** リクエストIDの MDC キー。ログ項目名としてそのまま出力される。 */
    public static final String MDC_REQUEST_ID = "request_id";

    /** 認証済みユーザーIDの MDC キー。付与は {@link JwtAuthenticationFilter}。 */
    public static final String MDC_PLAYER_ID = "player_id";

    private static final Logger logger = LoggerFactory.getLogger("afkgame.middleware");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        response.setHeader(REQUEST_ID_HEADER, requestId);

        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());
        MDC.put("client_ip", request.getRemoteAddr());

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            MDC.put("status_code", String.valueOf(response.getStatus()));
            MDC.put("duration_ms", String.valueOf(durationMs));
            logger.info("{} {} {} {}ms",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.clear();
        }
    }
}
