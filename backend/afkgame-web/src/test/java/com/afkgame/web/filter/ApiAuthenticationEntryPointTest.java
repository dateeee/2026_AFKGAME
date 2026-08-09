package com.afkgame.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.terasoluna.gfw.common.exception.BusinessException;
import org.terasoluna.gfw.common.message.ResultMessages;

import com.afkgame.env.logging.LogKey;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ApiAuthenticationEntryPoint} の単体テスト。
 *
 * <p>仕様: docs/tech/basic/tech_error_handling.md「AUTH_ コード一覧」「統一エラーレスポンス形式」。
 * 認証フィルタが記録した失敗理由を 401 の統一エラー応答へ変換する。
 *
 * <p>分岐観点: 失敗理由の記録あり（そのコードを返す）/ 記録なし（{@code AUTH_HEADER_MISSING}）。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class ApiAuthenticationEntryPointTest {

    private final ApiAuthenticationEntryPoint entryPoint =
            new ApiAuthenticationEntryPoint(JsonMapper.builder().build());

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private MockHttpServletResponse commence(BusinessException failure) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/game/state");
        if (failure != null) {
            request.setAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE, failure);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(request, response, new InsufficientAuthenticationException("未認証"));
        return response;
    }

    /**
     * 記録された失敗理由のコードを返す。
     *
     * <p>ステータスと文言は例外ではなく {@link ErrorCatalog} が決める（規約 exception.md §4 #4）。
     * 例外の {@code getMessage()}（{@code ResultMessages} の {@code toString()}）は応答へ出さない。
     */
    @Test
    @DisplayName("記録された失敗理由のコードを返す")
    void test_記録された失敗理由を返す() throws Exception {
        MockHttpServletResponse response =
                commence(new BusinessException(ResultMessages.error().add("AUTH_TOKEN_EXPIRED")));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"AUTH_TOKEN_EXPIRED\"")
                .contains("\"message\":\"アクセストークンの有効期限が切れています\"")
                .doesNotContain("ResultMessage");
    }

    @Test
    @DisplayName("記録が無ければ AUTH_HEADER_MISSING を返す")
    void test_記録が無ければヘッダ不足として返す() throws Exception {
        MDC.put(LogKey.REQUEST_ID.field(), "test-request-id");

        MockHttpServletResponse response = commence(null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"AUTH_HEADER_MISSING\"")
                .contains("\"requestId\":\"test-request-id\"");
    }
}
