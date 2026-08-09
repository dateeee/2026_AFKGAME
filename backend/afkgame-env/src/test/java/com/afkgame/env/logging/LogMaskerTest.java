package com.afkgame.env.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link LogMasker} の単体テスト。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「機密情報のマスク規則」。
 * 残す文字数を引いても伏せ字が残らない長さでは、全体を伏せる。
 *
 * <p>分岐観点: トークンは残せる長さか否か、メールは {@code @} の有無とローカル部が残せる長さか否か。
 * 骨格構築の横断基盤であり詳細設計の分岐一覧を持たないため、分岐マーカーは付けない。
 */
@Tag("unit")
class LogMaskerTest {

    @Nested
    @DisplayName("トークン")
    class TestToken {

        @Test
        void test_9文字以上なら先頭4文字と末尾4文字を残す() {
            assertThat(LogMasker.maskToken("abc123456789wxyz")).isEqualTo("abc1****wxyz");
        }

        @Test
        void test_境界の9文字でも先頭4文字と末尾4文字を残す() {
            assertThat(LogMasker.maskToken("abc1Xwxyz")).isEqualTo("abc1****wxyz");
        }

        @Test
        void test_8文字以下は全体を伏せる() {
            // 先頭4 + 末尾4 を残すと生値がそのまま出るため、長さも残さず固定の伏せ字にする
            assertThat(LogMasker.maskToken("abc1wxyz")).isEqualTo("****");
        }

        @Test
        void test_空文字も全体を伏せる() {
            assertThat(LogMasker.maskToken("")).isEqualTo("****");
        }
    }

    @Nested
    @DisplayName("メールアドレス")
    class TestEmail {

        @Test
        void test_ローカル部が3文字以上なら先頭2文字とドメインを残す() {
            assertThat(LogMasker.maskEmail("abcdef@example.com")).isEqualTo("ab***@example.com");
        }

        @Test
        void test_境界の3文字でも先頭2文字とドメインを残す() {
            assertThat(LogMasker.maskEmail("abc@example.com")).isEqualTo("ab***@example.com");
        }

        @Test
        void test_ローカル部が2文字以下ならドメインだけ残す() {
            // 先頭2文字を残すとローカル部が丸ごと出るため伏せる
            assertThat(LogMasker.maskEmail("ab@example.com")).isEqualTo("***@example.com");
        }

        @Test
        void test_アットマークが無ければ全体を伏せる() {
            assertThat(LogMasker.maskEmail("not-an-email")).isEqualTo("****");
        }
    }
}
