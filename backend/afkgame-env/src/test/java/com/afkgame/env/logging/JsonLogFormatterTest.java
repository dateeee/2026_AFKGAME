package com.afkgame.env.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

/**
 * {@link JsonLogFormatter} の単体テスト。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「ログフォーマット」。
 * 本番（{@code LOG_FORMAT=json}）の1行JSONを組み立てる。
 *
 * <p>分岐観点: レベル名の読み替え（WARN → WARNING / それ以外）、例外の有無、
 * エスケープ対象文字（{@code "}・{@code \}・改行・復帰・タブ・制御文字）とそれ以外。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class JsonLogFormatterTest {

    /** JSON へ {@code \}{@code uXXXX} 形式で出力されることを確認する制御文字。 */
    private static final String CONTROL_CHAR = String.valueOf((char) 1);

    private final JsonLogFormatter formatter = new JsonLogFormatter();

    private static LoggingEvent event(Level level, String message, Map<String, String> mdc, Throwable throwable) {
        Logger logger = new LoggerContext().getLogger("afkgame.auth");
        LoggingEvent loggingEvent = new LoggingEvent(
                JsonLogFormatterTest.class.getName(), logger, level, message, throwable, null);
        loggingEvent.setMDCPropertyMap(mdc);
        return loggingEvent;
    }

    @Nested
    @DisplayName("基本項目")
    class TestBasicFields {

        @Test
        void test_timestamp_level_logger_messageを出力する() {
            String json = formatter.format(event(Level.INFO, "ゲストアカウント作成", Map.of(), null));

            assertThat(json).endsWith("}\n");
            assertThat(json).contains("\"level\":\"INFO\"");
            assertThat(json).contains("\"logger\":\"afkgame.auth\"");
            assertThat(json).contains("\"message\":\"ゲストアカウント作成\"");
            // ISO 8601 の UTC（tech_api/common.md §5.0 の日時規約と揃える）
            assertThat(json).containsPattern("\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z\"");
        }

        @Test
        void test_MDCの項目を同じ階層へ展開する() {
            String json = formatter.format(event(
                    Level.INFO, "リクエスト完了",
                    Map.of("request_id", "550e8400-e29b"), null));

            assertThat(json).contains("\"request_id\":\"550e8400-e29b\"");
        }
    }

    @Nested
    @DisplayName("レベル名")
    class TestLevelName {

        @Test
        void test_WARNはWARNINGへ読み替える() {
            String json = formatter.format(event(Level.WARN, "認証失敗", Map.of(), null));

            assertThat(json).contains("\"level\":\"WARNING\"");
        }

        @Test
        void test_WARN以外はそのまま出力する() {
            String json = formatter.format(event(Level.ERROR, "未捕捉例外", Map.of(), null));

            assertThat(json).contains("\"level\":\"ERROR\"");
        }
    }

    @Nested
    @DisplayName("例外")
    class TestException {

        @Test
        void test_例外があればスタックトレースを含める() {
            String json = formatter.format(event(
                    Level.ERROR, "未捕捉例外", Map.of(), new IllegalStateException("失敗")));

            assertThat(json).contains("\"exception\":\"java.lang.IllegalStateException: 失敗");
        }

        @Test
        void test_例外がなければexceptionを出力しない() {
            String json = formatter.format(event(Level.INFO, "正常", Map.of(), null));

            assertThat(json).doesNotContain("\"exception\"");
        }
    }

    @Nested
    @DisplayName("JSONエスケープ")
    class TestEscape {

        @Test
        void test_制御文字と記号をエスケープする() {
            String json = formatter.format(event(
                    Level.INFO, "a\"b\\c\nd\re\tf" + CONTROL_CHAR + "g", Map.of(), null));

            assertThat(json).contains("\"message\":\"a\\\"b\\\\c\\nd\\re\\tf\\u0001g\"");
        }

        @Test
        void test_MDCのキーもエスケープする() {
            String json = formatter.format(event(
                    Level.INFO, "メッセージ", Map.of("wrapped\"key", "値"), null));

            assertThat(json).contains("\"wrapped\\\"key\":\"値\"");
        }
    }
}
