package com.afkgame.web.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.terasoluna.gfw.common.exception.BusinessException;

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
 * ステータスと文言は {@link ErrorCatalog} から引き、ハンドラ経由の応答と同じ値にそろえる
 * （規約 exception.md §4 #8「形式を変えない」）。JSON は Spring MVC の変換器と同じ
 * {@link JsonMapper}（Jackson 3。定義は
 * {@code com.afkgame.web.config.app.ApplicationContextConfig}）で書き出す。
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final AppLogger logger = AppLogger.of(LoggerName.AUTH);

    /** {@code Authorization} ヘッダ自体が無い場合の既定コード。 */
    private static final String HEADER_MISSING = "AUTH_HEADER_MISSING";

    private final JsonMapper jsonMapper;

    public ApiAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        BusinessException failure = (BusinessException) request.getAttribute(
                JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);

        String code;
        if (failure == null) {
            logger.warn("認証失敗").reason(LogReason.HEADER_MISSING).log();
            code = HEADER_MISSING;
        } else {
            code = ErrorCatalog.codeOf(failure);
        }

        ErrorCatalog.Entry entry = ErrorCatalog.find(code);
        response.setStatus(entry.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), ErrorResource.of(code, entry.message()));
    }
}
