package com.afkgame.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

/**
 * {@link RequestLogFilter} の単体テスト。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「リクエストログ用フィルタ」、
 * 出力項目・START/END対の規約は coding_standards_backend/logging/communication.md §2。
 * リクエストIDの採番・{@code X-Request-ID} ヘッダ付与・処理時間計測・通信ログ出力を担う。
 *
 * <p>分岐観点: 正常終了 / 後続で例外が起きた場合（いずれも MDC を後始末する）。
 * ログ本文は {@link ListAppender} で受けて検証する（Terasoluna 単体テストガイドライン 10.2.3）。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();

    /** ログ出力そのものを検証するための受け皿（coding_standards_backend/test.md §5「新規実装から適用する」1）。 */
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private ch.qos.logback.classic.Logger commLogger;

    @BeforeEach
    void setUp() {
        logs.start();
        commLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("afkgame.comm");
        commLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        commLogger.detachAppender(logs);
        logs.stop();
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
    @DisplayName("START / END の通信ログを afkgame.comm へ対で出す")
    void test_通信ログをSTART_END対でafkgame_commへ出力する() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(), response, new MockFilterChain());

        assertThat(logs.list).hasSize(2);
        ILoggingEvent start = logs.list.get(0);
        assertThat(start.getLevel()).isEqualTo(Level.INFO);
        assertThat(start.getFormattedMessage()).isEqualTo("START");
        assertThat(start.getMDCPropertyMap()).containsEntry("direction", "in");

        ILoggingEvent end = logs.list.get(1);
        assertThat(end.getLevel()).isEqualTo(Level.INFO);
        assertThat(end.getFormattedMessage()).isEqualTo("END");
        // MDC は finally で clear されるが、ログイベントには出力時点の値が焼き込まれている
        assertThat(end.getMDCPropertyMap())
                .containsEntry("direction", "in")
                .containsEntry("status_code", "200")
                .containsKeys("request_id", "duration_ms");
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
