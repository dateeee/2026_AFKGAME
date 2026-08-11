package com.afkgame.web.filter;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.afkgame.env.logging.AppLogger;
import com.afkgame.env.logging.LogKey;
import com.afkgame.env.logging.LoggerName;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 全APIリクエストにリクエストIDを付与し、通信ログ（START / END）を出す。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「リクエストログ用フィルタ」。
 * リクエストIDは MDC で引き回し、レスポンスヘッダ {@code X-Request-ID} とエラー応答
 * （{@link com.afkgame.web.resource.common.ErrorResource}）へ載せてログとの突合を可能にする。
 * START / END の出力項目・対の規約は
 * {@code coding_standards_backend/logging/communication.md} §2 が正。
 *
 * <p>Spring Security のフィルタチェーンより先に動かす。認証失敗の応答にもリクエストIDが要るため。
 * war 構成では順序を {@code web.xml} の {@code filter-mapping} が決めるので、本クラスの登録
 * （{@code DelegatingFilterProxy} の {@code requestLogFilter}）を
 * {@code springSecurityFilterChain} より前に置く。
 */
@Component
public class RequestLogFilter extends OncePerRequestFilter {

    /** リクエストIDのレスポンスヘッダ名（tech_api/common.md「共通ヘッダ」）。 */
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    /** 受信の方向（communication.md §2「direction」）。 */
    private static final String DIRECTION_IN = "in";

    private static final AppLogger logger = AppLogger.of(LoggerName.COMM);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        response.setHeader(REQUEST_ID_HEADER, requestId);

        MDC.put(LogKey.REQUEST_ID.field(), requestId);
        MDC.put(LogKey.METHOD.field(), request.getMethod());
        MDC.put(LogKey.PATH.field(), request.getRequestURI());
        MDC.put(LogKey.CLIENT_IP.field(), request.getRemoteAddr());

        logger.info("START").with(LogKey.DIRECTION, DIRECTION_IN).log();

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            MDC.put(LogKey.STATUS_CODE.field(), String.valueOf(response.getStatus()));
            MDC.put(LogKey.DURATION_MS.field(), String.valueOf(durationMs));
            logger.info("END").with(LogKey.DIRECTION, DIRECTION_IN).log();
            MDC.clear();
        }
    }
}
