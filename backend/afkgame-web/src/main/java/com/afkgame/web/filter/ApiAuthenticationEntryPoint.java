package com.afkgame.web.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.afkgame.domain.exception.AppException;
import com.afkgame.env.logging.AppLogger;
import com.afkgame.env.logging.LogReason;
import com.afkgame.env.logging.LoggerName;
import com.afkgame.web.resource.ErrorResource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * 未認証リクエストの拒否応答を、統一エラー形式の 401 で返す。
 *
 * <p>仕様: docs/tech/basic/tech_error_handling.md「AUTH_ コード一覧」「統一エラーレスポンス形式」。
 * {@link JwtAuthenticationFilter} が記録した失敗理由をそのまま使い、記録が無い場合
 * （＝{@code Authorization} ヘッダ自体が無い）は {@code AUTH_HEADER_MISSING} とする。
 *
 * <p>フィルタ内の失敗は {@link ApiExceptionHandler} を通らないため、応答の組み立てを本クラスが担う。
 * JSON は Spring MVC の変換器と同じ {@link JsonMapper}（Jackson 3。定義は
 * {@code com.afkgame.web.config.app.ApplicationContextConfig}）で書き出し、形式をそろえる。
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final AppLogger logger = AppLogger.of(LoggerName.AUTH);

    private final JsonMapper jsonMapper;

    public ApiAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        AppException failure = (AppException) request.getAttribute(
                JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);
        if (failure == null) {
            logger.warn("認証失敗").reason(LogReason.HEADER_MISSING).log();
            failure = new AppException("AUTH_HEADER_MISSING", "Authorization header missing", 401);
        }

        response.setStatus(failure.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(),
                ErrorResource.of(failure.getCode(), failure.getMessage()));
    }
}
