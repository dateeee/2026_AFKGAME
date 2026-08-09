package com.afkgame.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.afkgame.domain.model.User;
import com.afkgame.env.logging.LoggerName;

/**
 * {@link VerificationMailSenderImpl} の単体テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth/mail.md §16.1（送信のタイミングと失敗の扱い）、
 * docs/tech/detail/tech_auth/account.md §10 手順9。
 *
 * <p>分岐観点: トランザクション同期の 有効 / 無効、コミット / ロールバック、送信の 成功 / 失敗。
 * 実際の送信手段（SMTP接続・本文の組み立て・宛先マスク）は仮実装のままで、確定は移行 STEP 3-A-3
 * （docs/backlog/java_migration.md）。そのため mail.md §17 の分岐マーカーは本クラスでは付けず、
 * §17 を Red へ展開するのは 3-A-3 のテストリスト工程が行う。
 *
 * <p>本クラスが担うのは「いつ送るか」と「失敗をどう扱うか」だけで、これは送信手段が確定しても
 * 変わらない。{@code AuthServiceImpl} 側は送信境界が例外を投げない前提で登録を完結させる。
 */
@Tag("unit")
class VerificationMailSenderImplTest {

    private static final String RAW_TOKEN = "raw-verification-token";

    /** 送信要求を記録するだけの試験用実装。実送信の代わりに呼ばれた回数と失敗を制御する。 */
    private static class RecordingSender extends VerificationMailSenderImpl {

        private int sent;

        private RuntimeException failure;

        @Override
        void doSend(User user, String rawToken) {
            sent++;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private final RecordingSender sender = new RecordingSender();

    private static User user() {
        User user = new User();
        user.setId("user_001");
        user.setEmail("user@example.com");
        return user;
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Nested
    @DisplayName("送信のタイミング")
    class TestTiming {

        /**
         * トランザクションに参加している間は送らず、コミット後に送る（§16.1「実行位置」）。
         * これによりロールバックした場合に、存在しないアカウント宛の確認リンクが出ない。
         */
        @Test
        void test_トランザクション中は送らずコミット後に送る() {
            TransactionSynchronizationManager.initSynchronization();

            sender.send(user(), RAW_TOKEN);

            // 送信要求を登録しただけで、コミット前には送っていない
            assertThat(sender.sent).isZero();

            TransactionSynchronizationUtils.triggerAfterCommit();
            assertThat(sender.sent).isEqualTo(1);
        }

        /**
         * ロールバックした場合は送信しない（§16.1「ロールバック時」）。
         * コミットが起きない以上 {@code afterCommit} は呼ばれない。
         */
        @Test
        void test_ロールバックしたら送らない() {
            TransactionSynchronizationManager.initSynchronization();

            sender.send(user(), RAW_TOKEN);
            TransactionSynchronizationManager.clearSynchronization();

            assertThat(sender.sent).isZero();
        }

        /**
         * トランザクションの外から呼ばれた場合は待つ相手が無いため、その場で送る。
         * 待機させると送信要求が失われる。
         */
        @Test
        void test_トランザクション外なら即時に送る() {
            sender.send(user(), RAW_TOKEN);

            assertThat(sender.sent).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("送信の失敗")
    class TestFailure {

        /**
         * 送信失敗は業務エラーにせず、WARN ログだけを残して例外を伝播させない（§16.1「失敗の扱い」）。
         * コミット後に送るため、ここで例外を投げるとコミット済みの登録処理が失敗として扱われる。
         *
         * <p>分岐: tech_auth/account.md §11 #13
         */
        @Test
        void test_送信に失敗しても例外を伝播しない() {
            sender.failure = new IllegalStateException("SMTP unreachable");

            assertThatCode(() -> sender.send(user(), RAW_TOKEN)).doesNotThrowAnyException();

            assertThat(sender.sent).isEqualTo(1);
        }

        /**
         * コミット後の送信で失敗した場合も同じ。{@code afterCommit} の例外は
         * トランザクションマネージャ経由で呼び出し元へ伝わるため、境界の内側で握る。
         */
        @Test
        void test_コミット後の送信に失敗しても例外を伝播しない() {
            sender.failure = new IllegalStateException("SMTP unreachable");
            TransactionSynchronizationManager.initSynchronization();

            sender.send(user(), RAW_TOKEN);

            assertThatCode(TransactionSynchronizationUtils::triggerAfterCommit)
                    .doesNotThrowAnyException();
            assertThat(sender.sent).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("送信の通信ログ")
    class TestCommunicationLog {

        /** ログ出力そのものを検証するための受け皿（coding_standards_backend/test.md §5「新規実装から適用する」1）。 */
        private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

        private ch.qos.logback.classic.Logger commLogger;

        @BeforeEach
        void setUp() {
            logs.start();
            commLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LoggerName.COMM.loggerName());
            commLogger.addAppender(logs);
        }

        @AfterEach
        void tearDown() {
            commLogger.detachAppender(logs);
            logs.stop();
        }

        /**
         * 送信（送信手段確定前の仮実装でも）は通信ログのSTART / ENDを対で出す
         * （communication.md §2 規約2・3。方向はout、相手はsmtp）。
         */
        @Test
        void test_送信のSTART_ENDをdirection_out_target_smtpで出す() {
            new VerificationMailSenderImpl().doSend(user(), RAW_TOKEN);

            assertThat(logs.list).hasSize(2);
            assertThat(logs.list.get(0).getFormattedMessage()).isEqualTo("START");
            assertThat(logs.list.get(1).getFormattedMessage()).isEqualTo("END");
            assertThat(logs.list.get(0).getMDCPropertyMap())
                    .containsEntry("direction", "out")
                    .containsEntry("target", "smtp");
            assertThat(logs.list.get(1).getMDCPropertyMap())
                    .containsEntry("direction", "out")
                    .containsEntry("target", "smtp");
        }
    }
}
