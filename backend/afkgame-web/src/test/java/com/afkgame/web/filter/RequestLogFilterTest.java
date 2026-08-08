package com.afkgame.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

/**
 * {@link RequestLogFilter} の単体テスト。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「リクエストログ用フィルタ」。
 * リクエストIDの採番・{@code X-Request-ID} ヘッダ付与・処理時間計測・INFOログ出力を担う。
 *
 * <p>分岐観点: 正常終了 / 後続で例外が起きた場合（いずれも MDC を後始末する）。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/guest");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Test
    @DisplayName("レスポンスへ X-Request-ID を付与する")
    void test_リクエストIDをヘッダへ付与する() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(), response, new MockFilterChain());

        String requestId = response.getHeader("X-Request-ID");
        // UUID v4 として解釈できること（tech_logging.md「リクエストID付与」）
        assertThat(UUID.fromString(requestId)).hasToString(requestId);
    }

    @Test
    @DisplayName("後続処理の間だけ MDC にリクエスト情報を載せる")
    void test_後続処理中はMDCへリクエスト情報を載せる() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            assertThat(MDC.get("request_id")).isNotBlank();
            assertThat(MDC.get("method")).isEqualTo("POST");
            assertThat(MDC.get("path")).isEqualTo("/api/auth/guest");
            assertThat(MDC.get("client_ip")).isEqualTo("127.0.0.1");
        };

        filter.doFilter(request(), response, chain);

        // スレッドを再利用しても前のリクエストの値が残らないこと
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("後続で例外が起きても MDC を後始末する")
    void test_例外時もMDCを後始末する() {
        FilterChain failing = (req, res) -> {
            throw new ServletException("後続の失敗");
        };

        assertThatThrownBy(() -> filter.doFilter(request(), new MockHttpServletResponse(), failing))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
