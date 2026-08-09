package com.afkgame.env.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@link AppLogger} と {@link LogEntry} の単体テスト。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「ログフォーマット」「機密情報のマスク規則」。
 * ログ項目は MDC へ載せ、text 形式では末尾の {@code key=value}、JSON 形式では独立フィールドとして出力される。
 *
 * <p>分岐観点: レベル（INFO / WARNING / ERROR）、値の有無（null は項目を積まない）、
 * マスクの要否、原因例外の有無、出力後の MDC 復元（元の値の有無）。
 * 骨格構築の横断基盤であり詳細設計の分岐一覧を持たないため、分岐マーカーは付けない。
 */
@Tag("unit")
class AppLoggerTest {

    private final AppLogger logger = AppLogger.of(LoggerName.AUTH);

    /** ログ出力そのものを検証するための受け皿（coding_standards_backend/test.md §5「新規実装から適用する」1）。 */
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private Logger authLogger;

    @BeforeEach
    void setUp() {
        logs.start();
        authLogger = (Logger) LoggerFactory.getLogger(LoggerName.AUTH.loggerName());
        authLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        authLogger.detachAppender(logs);
        logs.stop();
        MDC.clear();
    }

    private ILoggingEvent lastEvent() {
        return logs.list.get(logs.list.size() - 1);
    }

    @Nested
    @DisplayName("ログレベル")
    class TestLevel {

        @Test
        void test_infoはINFOで出力する() {
            logger.info("ログイン").log();

            assertThat(lastEvent().getLevel()).isEqualTo(Level.INFO);
            assertThat(lastEvent().getFormattedMessage()).isEqualTo("ログイン");
        }

        @Test
        void test_warnはWARNで出力する() {
            logger.warn("ログイン失敗").log();

            assertThat(lastEvent().getLevel()).isEqualTo(Level.WARN);
        }

        @Test
        void test_errorはERRORで出力する() {
            logger.error("未捕捉例外").log();

            assertThat(lastEvent().getLevel()).isEqualTo(Level.ERROR);
        }

        @Test
        void test_ロガー名は体系の名前を使う() {
            logger.info("ログイン").log();

            assertThat(lastEvent().getLoggerName()).isEqualTo("afkgame.auth");
        }

        @Test
        void test_メッセージのプレースホルダを解決する() {
            logger.info("{} {} {}ms", "POST", "/api/auth/login", 45).log();

            assertThat(lastEvent().getFormattedMessage()).isEqualTo("POST /api/auth/login 45ms");
        }
    }

    @Nested
    @DisplayName("ログ項目")
    class TestFields {

        @Test
        void test_項目をMDCへ載せて出力する() {
            logger.warn("ログイン失敗")
                    .reason(LogReason.PASSWORD_MISMATCH)
                    .with(LogKey.USER_ID, "user_001")
                    .log();

            assertThat(lastEvent().getMDCPropertyMap())
                    .containsEntry("reason", "password_mismatch")
                    .containsEntry("user_id", "user_001");
            // 項目はメッセージへ埋め込まない（JSON 形式で独立フィールドになるため）
            assertThat(lastEvent().getFormattedMessage()).isEqualTo("ログイン失敗");
        }

        @Test
        void test_数値の項目は文字列として載せる() {
            logger.info("リクエスト完了").with(LogKey.DURATION_MS, 45L).log();

            assertThat(lastEvent().getMDCPropertyMap()).containsEntry("duration_ms", "45");
        }

        @Test
        void test_値がnullなら項目を積まない() {
            logger.warn("認証失敗").with(LogKey.USER_ID, null).log();

            assertThat(lastEvent().getMDCPropertyMap()).doesNotContainKey("user_id");
        }

        @Test
        void test_横断項目は出力時点の値が焼き込まれる() {
            MDC.put(LogKey.REQUEST_ID.field(), "550e8400-e29b");

            logger.info("ログイン").with(LogKey.USER_ID, "user_001").log();

            assertThat(lastEvent().getMDCPropertyMap())
                    .containsEntry("request_id", "550e8400-e29b")
                    .containsEntry("user_id", "user_001");
        }
    }

    @Nested
    @DisplayName("MDC の後始末")
    class TestMdcRestore {

        @Test
        void test_出力後は積んだ項目を取り除く() {
            logger.warn("ログイン失敗").reason(LogReason.EMAIL_NOT_FOUND).log();

            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }

        @Test
        void test_横断項目を消さない() {
            MDC.put(LogKey.REQUEST_ID.field(), "550e8400-e29b");

            logger.info("ログイン").with(LogKey.USER_ID, "user_001").log();

            assertThat(MDC.get(LogKey.REQUEST_ID.field())).isEqualTo("550e8400-e29b");
            assertThat(MDC.get(LogKey.USER_ID.field())).isNull();
        }

        @Test
        void test_同じキーが既にあれば元の値へ戻す() {
            MDC.put(LogKey.USER_ID.field(), "user_before");

            logger.info("ログイン").with(LogKey.USER_ID, "user_001").log();

            assertThat(lastEvent().getMDCPropertyMap()).containsEntry("user_id", "user_001");
            assertThat(MDC.get(LogKey.USER_ID.field())).isEqualTo("user_before");
        }
    }

    @Nested
    @DisplayName("機密情報のマスク")
    class TestMasking {

        @Test
        void test_トークンはマスクして載せる() {
            logger.warn("認証失敗").with(LogKey.TOKEN, "abc123456789wxyz").log();

            assertThat(lastEvent().getMDCPropertyMap()).containsEntry("token", "abc1****wxyz");
        }

        @Test
        void test_メールアドレスはマスクして載せる() {
            logger.warn("登録失敗").with(LogKey.EMAIL, "abcdef@example.com").log();

            assertThat(lastEvent().getMDCPropertyMap()).containsEntry("email", "ab***@example.com");
        }

        @Test
        void test_マスク対象でない項目はそのまま載せる() {
            logger.info("ログイン").with(LogKey.USER_ID, "user_001").log();

            assertThat(lastEvent().getMDCPropertyMap()).containsEntry("user_id", "user_001");
        }
    }

    @Nested
    @DisplayName("原因例外")
    class TestCause {

        @Test
        void test_原因例外を添えるとスタックトレースが残る() {
            logger.error("ヘルスチェック: DB疎通に失敗").cause(new IllegalStateException("失敗")).log();

            assertThat(lastEvent().getThrowableProxy()).isNotNull();
            assertThat(lastEvent().getThrowableProxy().getMessage()).isEqualTo("失敗");
        }

        @Test
        void test_原因例外が無ければスタックトレースを残さない() {
            logger.warn("ログイン失敗").reason(LogReason.EMAIL_NOT_FOUND).log();

            assertThat(lastEvent().getThrowableProxy()).isNull();
        }

        @Test
        void test_原因例外とプレースホルダを併用できる() {
            logger.error("フレームワーク内部エラー")
                    .with(LogKey.STATUS_CODE, 500L)
                    .cause(new IllegalStateException("失敗"))
                    .log();

            assertThat(lastEvent().getMDCPropertyMap()).containsEntry("status_code", "500");
            assertThat(lastEvent().getThrowableProxy()).isNotNull();
        }
    }
}
