package com.afkgame.domain.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.afkgame.domain.model.User;
import com.afkgame.env.config.AuthSettings;
import com.afkgame.env.config.MailSettings;
import com.afkgame.env.logging.LogKey;
import com.afkgame.env.logging.LoggerName;

/**
 * {@link VerificationMailSenderImpl} の単体テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth/mail.md §16（メール送信の共通規約）、
 * docs/tech/detail/tech_auth/account.md §10 手順9、
 * docs/tech/detail/tech_auth/password_reset.md §22 手順7。
 *
 * <p>分岐観点: トランザクション同期の 有効 / 無効、コミット / ロールバック、SMTP設定の 有無、
 * 送信の 成功 / 失敗、メール種別（確認 / 再設定）。mail.md §17 の全8行を本クラスが持つ。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code VerificationMailSender#sendPasswordReset(User user, String token)} を追加する。
 *       既存の {@code #send(User, String)}（確認メール）は<b>変更しない</b>。引数名は
 *       {@code token} 固定（logging/application.md §3.1 規約1 の固定表。外すと
 *       {@code check_java_conventions.py} の判定12 が ERROR で止まる）</li>
 *   <li>{@code VerificationMailSenderImpl(MailSettings mailSettings, AuthSettings authSettings)}
 *       のコンストラクタ注入にする。{@code MailSettings} から {@code smtpHost}（送信可否の判定）と
 *       {@code frontendBaseUrl}（リンク生成元）を、{@code AuthSettings} から
 *       {@code verificationTokenExpire} / {@code passwordResetTokenExpire}（本文へ書く有効期限）を
 *       読む。<b>有効期限を本文へ直書きしない</b>（profile.md §5 不変条件6「データ駆動」）</li>
 *   <li>{@code void transmit(String to, String subject, String body)} を<b>パッケージ内可視</b>で
 *       開く。SMTP への実送信だけを担う継ぎ目で、本テストはここを差し替えて件名・本文・宛先を
 *       捕まえる。可視性を絞るのは既存の {@code doSend} と同じ理由（試験から送信失敗を再現する）。
 *       <b>テストがメールライブラリの型に触れないための境界でもある</b> — 送信に使う依存
 *       （{@code spring-context-support} / Jakarta Mail 等）の追加は製造で決めてよく、本テストは
 *       その選択に影響されない</li>
 *   <li>{@code send} / {@code sendPasswordReset} はいずれも「コミット後・トランザクションの外」
 *       （§16.1）と「失敗は WARN のみ」を共有する。種別ごとに変わるのは件名・リンク・有効期限だけ
 *       （§16.3）</li>
 *   <li>{@code smtpHost} が空文字なら {@code transmit} を呼ばず、宛先（{@code LogKey.EMAIL} で
 *       マスク）と種別を INFO ログへ残して正常終了する（§16.2）</li>
 * </ul>
 */
@Tag("unit")
class VerificationMailSenderImplTest {

    private static final String RAW_TOKEN = "raw-verification-token";

    /** 宛先。マスク後の値と区別できるよう、ローカル部を2文字より長くする。 */
    private static final String EMAIL = "user@example.com";

    /** 本文へ混ぜてはいけない個人情報（§16.3 末尾）。一般語だと取りこぼすため固有の値にする。 */
    private static final String DISPLAY_NAME = "テスト表示名";

    /** メール本文のリンク生成元（§16.2 `frontend.base.url` の既定値）。 */
    private static final String FRONTEND_BASE_URL = "http://localhost:5173";

    /** SMTP接続先が設定された状態（§17 #3）。 */
    private static final MailSettings SMTP_CONFIGURED = new MailSettings(
            "smtp.example.com", 587, "smtp-user", "smtp-pass", true, Duration.ofSeconds(5),
            "noreply@afkgame.example", FRONTEND_BASE_URL);

    /** SMTP接続先が未設定の状態（§17 #4）。既定値は空文字で、null にはならない。 */
    private static final MailSettings SMTP_ABSENT = new MailSettings(
            "", 587, "", "", true, Duration.ofSeconds(5),
            "noreply@afkgame.example", FRONTEND_BASE_URL);

    /**
     * 本文の有効期限だけを使う。値は §16.3 の 24時間 / 1時間（`afkgame.properties` の既定と同じ）。
     */
    private static final AuthSettings AUTH_SETTINGS = new AuthSettings(
            "test-secret-value-for-unit-test-32bytes", Duration.ofMinutes(30), Duration.ofDays(30),
            4, 8, 128, Duration.ofDays(90),
            Duration.ofHours(24), Duration.ofHours(1), "");

    /** {@code transmit} が受け取った1通分。 */
    private record SentMail(String to, String subject, String body) {
    }

    /** 実送信の代わりに件名・本文を記録する試験用実装。失敗も制御する。 */
    private static class RecordingSender extends VerificationMailSenderImpl {

        private final List<SentMail> sent = new ArrayList<>();

        private RuntimeException failure;

        private RecordingSender(MailSettings mailSettings) {
            super(mailSettings, AUTH_SETTINGS);
        }

        @Override
        void transmit(String to, String subject, String body) {
            sent.add(new SentMail(to, subject, body));
            if (failure != null) {
                throw failure;
            }
        }

        private SentMail last() {
            return sent.get(sent.size() - 1);
        }
    }

    private final RecordingSender sender = new RecordingSender(SMTP_CONFIGURED);

    private static User user() {
        User user = new User();
        user.setId("user_001");
        user.setEmail(EMAIL);
        user.setDisplayName(DISPLAY_NAME);
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
         *
         * <p>分岐: tech_auth/mail.md §17 #1
         */
        @Test
        void test_トランザクション中は送らずコミット後に送る() {
            TransactionSynchronizationManager.initSynchronization();

            sender.send(user(), RAW_TOKEN);

            // 送信要求を登録しただけで、コミット前には送っていない
            assertThat(sender.sent).isEmpty();

            TransactionSynchronizationUtils.triggerAfterCommit();
            assertThat(sender.sent).hasSize(1);
        }

        /**
         * ロールバックした場合は送信しない（§16.1「ロールバック時」）。
         * コミットが起きない以上 {@code afterCommit} は呼ばれない。
         *
         * <p>分岐: tech_auth/mail.md §17 #2
         */
        @Test
        void test_ロールバックしたら送らない() {
            TransactionSynchronizationManager.initSynchronization();

            sender.send(user(), RAW_TOKEN);
            TransactionSynchronizationManager.clearSynchronization();

            assertThat(sender.sent).isEmpty();
        }

        /**
         * 再設定メールも確認メールと同じくコミット後に送る（§16.1 は種別を問わない）。
         * 再設定要求（password_reset.md §22 手順6・7）はトークンをコミットしてから送信を要求する。
         *
         * <p>分岐: tech_auth/password_reset.md §23 #13
         */
        @Test
        void test_再設定メールもコミット後に送る() {
            TransactionSynchronizationManager.initSynchronization();

            sender.sendPasswordReset(user(), RAW_TOKEN);
            assertThat(sender.sent).isEmpty();

            TransactionSynchronizationUtils.triggerAfterCommit();
            assertThat(sender.sent).hasSize(1);
        }

        /**
         * トランザクションの外から呼ばれた場合は待つ相手が無いため、その場で送る。
         * 待機させると送信要求が失われる。
         *
         * <p>§17 に行を持たない（呼び出し元のコミット / ロールバックのどちらでもないため）。
         * {@code send} の実装分岐なので C1 の分母には入る。
         */
        @Test
        void test_トランザクション外なら即時に送る() {
            sender.send(user(), RAW_TOKEN);

            assertThat(sender.sent).hasSize(1);
        }
    }

    @Nested
    @DisplayName("SMTP設定")
    class TestSmtpSettings {

        /**
         * 接続先が設定されていれば SMTP へ送る（§16.2）。宛先はユーザーのメールアドレス。
         *
         * <p>分岐: tech_auth/mail.md §17 #3
         */
        @Test
        void test_SMTPホストが設定されていればSMTPへ送信する() {
            sender.send(user(), RAW_TOKEN);

            assertThat(sender.sent).hasSize(1);
            assertThat(sender.last().to()).isEqualTo(EMAIL);
        }

        /** ログ出力そのものを検証するための受け皿（coding_standards_backend/test.md §5 の1）。 */
        private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

        private ch.qos.logback.classic.Logger authLogger;

        @BeforeEach
        void setUp() {
            logs.start();
            authLogger = (ch.qos.logback.classic.Logger)
                    LoggerFactory.getLogger(LoggerName.AUTH.loggerName());
            authLogger.addAppender(logs);
        }

        @AfterEach
        void tearDown() {
            authLogger.detachAppender(logs);
            logs.stop();
        }

        /**
         * 接続先が未設定なら送信を行わず、宛先と種別を INFO ログへ残して正常終了する（§16.2）。
         * ローカル開発・テストで SMTP サーバーを立てずに動かすための経路であり、業務エラーにしない。
         *
         * <p>宛先は {@link LogKey#EMAIL} へ渡してマスクする（§16.1「ログ」）。マスク書式そのものは
         * {@code LogMasker} の担当なので、ここでは<b>生値が出ていないこと</b>だけを見る。
         *
         * <p>分岐: tech_auth/mail.md §17 #4
         */
        @Test
        void test_SMTPホストが未設定なら送信せずINFOログを残す() {
            RecordingSender quiet = new RecordingSender(SMTP_ABSENT);

            assertThatCode(() -> quiet.send(user(), RAW_TOKEN)).doesNotThrowAnyException();

            assertThat(quiet.sent).isEmpty();
            assertThat(logs.list).hasSize(1);
            ILoggingEvent event = logs.list.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            // 種別（用途）が分かること。確認メールと再設定メールを同じ文言にしない
            assertThat(event.getFormattedMessage()).contains("確認メール");
            assertThat(event.getMDCPropertyMap().get(LogKey.EMAIL.field()))
                    .isNotEqualTo(EMAIL)
                    .endsWith("@example.com");
        }
    }

    @Nested
    @DisplayName("送信結果")
    class TestSendResult {

        /** ログ出力そのものを検証するための受け皿（coding_standards_backend/test.md §5 の1）。 */
        private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

        private ch.qos.logback.classic.Logger commLogger;

        @BeforeEach
        void setUp() {
            logs.start();
            commLogger = (ch.qos.logback.classic.Logger)
                    LoggerFactory.getLogger(LoggerName.COMM.loggerName());
            commLogger.addAppender(logs);
        }

        @AfterEach
        void tearDown() {
            commLogger.detachAppender(logs);
            logs.stop();
        }

        /**
         * 送信に成功したら通信ログへ END を出し、呼び出し元へ制御を返す（§16.1「ログ」、
         * communication.md §2 規約2・3。方向は out、相手は smtp）。
         *
         * <p>分岐: tech_auth/mail.md §17 #5
         */
        @Test
        void test_送信に成功したら通信ログのSTART_ENDを出す() {
            sender.send(user(), RAW_TOKEN);

            assertThat(sender.sent).hasSize(1);
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

        /**
         * 送信失敗は業務エラーにせず、WARN ログだけを残して例外を伝播させない（§16.1「失敗の扱い」）。
         * コミット後に送るため、ここで例外を投げるとコミット済みの登録処理が失敗として扱われる。
         *
         * <p>分岐: tech_auth/mail.md §17 #6
         *
         * <p>分岐: tech_auth/account.md §11 #13
         */
        @Test
        void test_送信に失敗しても例外を伝播しない() {
            sender.failure = new IllegalStateException("SMTP unreachable");

            assertThatCode(() -> sender.send(user(), RAW_TOKEN)).doesNotThrowAnyException();

            assertThat(sender.sent).hasSize(1);
        }

        /**
         * 再設定メールの送信失敗も同じ扱いで、要求は 200 のまま。<b>コミット済みのトークン行は
         * 消さない</b>（送信はコミット後・トランザクションの外で走るため、失敗しても巻き戻らない）。
         *
         * <p>分岐: tech_auth/password_reset.md §23 #16
         */
        @Test
        void test_再設定メールの送信に失敗しても例外を伝播しない() {
            sender.failure = new IllegalStateException("SMTP unreachable");
            TransactionSynchronizationManager.initSynchronization();

            sender.sendPasswordReset(user(), RAW_TOKEN);

            assertThatCode(TransactionSynchronizationUtils::triggerAfterCommit)
                    .doesNotThrowAnyException();
            assertThat(sender.sent).hasSize(1);
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
            assertThat(sender.sent).hasSize(1);
        }
    }

    @Nested
    @DisplayName("メール種別ごとの本文")
    class TestMailBody {

        /**
         * 確認メールは §16.3 の件名・リンク・有効期限24時間で本文を組む。有効期限は
         * {@code AuthSettings#verificationTokenExpire} から書き出す（直書きしない）。
         *
         * <p>本文はプレーンテキストのみで、宛先以外の個人情報（表示名・パスワード）を入れない
         * （§16.3 末尾）。
         *
         * <p>分岐: tech_auth/mail.md §17 #7
         */
        @Test
        void test_確認メールは件名とリンクと有効期限24時間で本文を組む() {
            sender.send(user(), RAW_TOKEN);

            SentMail mail = sender.last();
            assertThat(mail.subject()).isEqualTo("【AFK GAME】メールアドレスの確認");
            assertThat(mail.body())
                    .contains(FRONTEND_BASE_URL + "/verify-email?token=" + RAW_TOKEN)
                    .contains("24時間")
                    .doesNotContain(DISPLAY_NAME);
        }

        /**
         * 再設定メールは §16.3 の件名・リンク・有効期限1時間で本文を組む。確認メールより短いのは
         * 乗っ取り時に書き換えられる窓を狭めるためで、値は
         * {@code AuthSettings#passwordResetTokenExpire} から書き出す。
         *
         * <p>リンクの経路も確認メール（{@code /verify-email}）と分ける
         * （{@code /password-reset}）。取り違えるとトークンが用途違いで弾かれる（§25 #8）。
         *
         * <p>分岐: tech_auth/mail.md §17 #8
         */
        @Test
        void test_再設定メールは件名とリンクと有効期限1時間で本文を組む() {
            sender.sendPasswordReset(user(), RAW_TOKEN);

            SentMail mail = sender.last();
            assertThat(mail.subject()).isEqualTo("【AFK GAME】パスワードの再設定");
            assertThat(mail.body())
                    .contains(FRONTEND_BASE_URL + "/password-reset?token=" + RAW_TOKEN)
                    .contains("1時間")
                    .doesNotContain(DISPLAY_NAME);
        }
    }
}
