package com.afkgame.domain.service;

import java.time.Duration;
import java.util.Properties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.afkgame.domain.model.User;
import com.afkgame.env.config.AuthSettings;
import com.afkgame.env.config.MailSettings;
import com.afkgame.env.logging.AppLogger;
import com.afkgame.env.logging.LogKey;
import com.afkgame.env.logging.LoggerName;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * {@link VerificationMailSender} の実装。
 *
 * <p>仕様: docs/tech/detail/tech_auth/mail.md §16。「コミット後・トランザクションの外で送る」
 * 担保を本クラスが持ち、トランザクションに参加している間は送信要求を {@code afterCommit} へ預ける。
 * ロールバックした場合は {@code afterCommit} が呼ばれないため送信しない（§16.1）。
 *
 * <p>種別（確認 / 再設定）で変わるのは件名・リンク・有効期限だけで、送信のタイミングと失敗の扱いは
 * 共通のため {@link MailKind} に差分を寄せて経路を1本にしている（§16.3）。有効期限は本文へ直書きせず
 * {@link AuthSettings} から書き出す（profile.md §5 不変条件6「データ駆動」）。
 *
 * <p>メールライブラリ（Jakarta Mail）の型は {@link #transmit} の中だけに閉じる。送信手段を
 * 差し替えても本文の組み立てとログの規約が影響を受けないようにするためで、単体テストも
 * この継ぎ目を差し替えて件名・本文・宛先を検証する。
 */
@Service
public class VerificationMailSenderImpl implements VerificationMailSender {

    private static final AppLogger logger = AppLogger.of(LoggerName.AUTH);

    private static final AppLogger commLogger = AppLogger.of(LoggerName.COMM);

    /** 送信先（communication.md §2「target」の固定値）。 */
    private static final String TARGET_SMTP = "smtp";

    /** 送信の方向（communication.md §2「direction」）。 */
    private static final String DIRECTION_OUT = "out";

    /** 件名・本文の文字コード。プレーンテキストのみを送る（§16.3）。 */
    private static final String MAIL_CHARSET = "UTF-8";

    /**
     * 本文の雛形（§16.3）。①操作の説明 ②リンク ③有効期限 ④破棄してよい旨 の4点を含み、
     * 宛先以外の個人情報（表示名・パスワード）を入れない。
     */
    private static final String BODY_TEMPLATE = """
            AFK GAME をご利用いただきありがとうございます。
            下記のリンクを開いて%sを完了してください。

            %s

            このリンクは%d時間で無効になります。
            心当たりが無い場合は、このメールを破棄してください。
            """;

    private final MailSettings mailSettings;
    private final Duration verificationTokenExpire;
    private final Duration passwordResetTokenExpire;

    /**
     * 設定値を受け取る。
     *
     * @param mailSettings SMTP接続先・差出人・リンク生成元（§16.2）
     * @param authSettings 本文へ書く有効期限（§16.3）
     */
    public VerificationMailSenderImpl(MailSettings mailSettings, AuthSettings authSettings) {
        this.mailSettings = mailSettings;
        this.verificationTokenExpire = authSettings.verificationTokenExpire();
        this.passwordResetTokenExpire = authSettings.passwordResetTokenExpire();
    }

    /** {@inheritDoc} */
    @Override
    public void send(User user, String token) {
        requestSend(user, token, MailKind.VERIFICATION);
    }

    /** {@inheritDoc} */
    @Override
    public void sendPasswordReset(User user, String token) {
        requestSend(user, token, MailKind.PASSWORD_RESET);
    }

    /**
     * 送信をコミット後まで遅らせる（§16.1「実行位置」）。
     *
     * <p>トランザクションに参加していなければ待つ相手が無く、遅延させると送信要求が失われるため
     * その場で送る。
     */
    private void requestSend(User user, String token, MailKind kind) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendQuietly(user, token, kind);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendQuietly(user, token, kind);
            }
        });
    }

    /**
     * 送信の失敗を WARN ログにとどめ、呼び出し元へ伝播させない（§16.1「失敗の扱い」）。
     *
     * <p>コミット後に呼ばれるため、ここで例外を投げるとコミット済みの登録・再設定要求が
     * 失敗として扱われる。再試行はしない（§16.1「再試行」）。
     */
    private void sendQuietly(User user, String token, MailKind kind) {
        try {
            doSend(user, token, kind);
        } catch (RuntimeException e) {
            logger.warn("{}の送信に失敗", kind.label()).with(LogKey.USER_ID, user.getId()).cause(e).log();
        }
    }

    /**
     * 本文を組み立てて送信する。
     *
     * <p>接続先が未設定なら送信を行わず、宛先（マスク済み）と種別を INFO ログへ残して正常終了する
     * （§16.2）。ローカル開発・テストで SMTP サーバーを立てずに動かすための経路であり、
     * 業務エラーにしない。
     *
     * <p>通信ログ（START / END）は送信の成否によらず対で出す（communication.md §2 規約3）。
     * 送信を行わない経路では相手と通信しないため出さない。
     *
     * @param user 宛先のユーザー
     * @param token トークンの生値（メール本文のリンクに載せる）
     * @param kind メールの種別
     */
    private void doSend(User user, String token, MailKind kind) {
        if (!StringUtils.hasText(mailSettings.smtpHost())) {
            logger.info("SMTP未設定のため{}を送信しない", kind.label())
                    .with(LogKey.EMAIL, user.getEmail())
                    .log();
            return;
        }

        String link = mailSettings.frontendBaseUrl() + kind.path() + "?token=" + token;
        String body = BODY_TEMPLATE.formatted(kind.action(), link, expireOf(kind).toHours());

        commLogger.info("START").with(LogKey.DIRECTION, DIRECTION_OUT).with(LogKey.TARGET, TARGET_SMTP).log();
        try {
            transmit(user.getEmail(), kind.subject(), body);
        } finally {
            commLogger.info("END").with(LogKey.DIRECTION, DIRECTION_OUT).with(LogKey.TARGET, TARGET_SMTP).log();
        }
    }

    /** 本文へ書く有効期限を種別から引く（§16.3）。値は設定に持たせ、本文へ直書きしない。 */
    private Duration expireOf(MailKind kind) {
        return kind == MailKind.VERIFICATION ? verificationTokenExpire : passwordResetTokenExpire;
    }

    /**
     * SMTP サーバーへ1通を送る。
     *
     * <p>メールライブラリに触れる唯一の場所で、送信失敗を試験から再現できるよう可視性を
     * パッケージ内に限って開いている。<b>分岐を持たせない</b> — 単体テストは本メソッドを
     * 差し替えて通るため、ここに分岐を書くと C1 の未達になる。
     *
     * <p>接続先を設定するなら認証情報も設定する前提とし（§16.2 は SMTP認証を接続先と対で挙げる）、
     * 認証は常に有効にする。STARTTLS を要求するかは設定から流し込む（§16.2
     * {@code mail.smtp.starttls.required}。既定の {@code true} では STARTTLS を広告しない
     * サーバーへの送信が失敗し、平文へ落ちない）。
     *
     * @param to 宛先アドレス
     * @param subject 件名
     * @param body 本文（プレーンテキスト）
     * @throws IllegalStateException 送信に失敗した場合（呼び出し元が WARN へ落とす）
     */
    void transmit(String to, String subject, String body) {
        String timeoutMillis = String.valueOf(mailSettings.smtpTimeout().toMillis());

        Properties props = new Properties();
        props.put("mail.smtp.host", mailSettings.smtpHost());
        props.put("mail.smtp.port", String.valueOf(mailSettings.smtpPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required",
                String.valueOf(mailSettings.smtpStarttlsRequired()));
        props.put("mail.smtp.connectiontimeout", timeoutMillis);
        props.put("mail.smtp.timeout", timeoutMillis);
        props.put("mail.smtp.writetimeout", timeoutMillis);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(mailSettings.smtpUser(), mailSettings.smtpPassword());
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(mailSettings.from()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, MAIL_CHARSET);
            message.setText(body, MAIL_CHARSET);
            Transport.send(message);
        } catch (MessagingException e) {
            // 検査例外のまま上げず、§16.1「失敗の扱い」の WARN へ落とせる非検査例外へ振り直す
            throw new IllegalStateException("SMTPへの送信に失敗", e);
        }
    }

    /**
     * メールの種別ごとの差分（§16.3）。
     *
     * <p>有効期限だけは設定値から引くため本 enum が持たない（{@link #expireOf} が種別から引く）。
     */
    private enum MailKind {

        /** メールアドレスの確認メール（account.md §10 手順9・link.md §18 手順12）。 */
        VERIFICATION("確認メール", "【AFK GAME】メールアドレスの確認", "/verify-email", "メールアドレスの確認"),

        /** パスワード再設定メール（password_reset.md §22 手順7）。 */
        PASSWORD_RESET("再設定メール", "【AFK GAME】パスワードの再設定", "/password-reset", "パスワードの再設定");

        private final String label;
        private final String subject;
        private final String path;
        private final String action;

        MailKind(String label, String subject, String path, String action) {
            this.label = label;
            this.subject = subject;
            this.path = path;
            this.action = action;
        }

        /** ログへ出す種別の呼び名。確認メールと再設定メールを同じ文言にしない。 */
        private String label() {
            return label;
        }

        /** メールの件名。 */
        private String subject() {
            return subject;
        }

        /** リンクのパス。確認と再設定で分ける（取り違えると用途違いで弾かれる）。 */
        private String path() {
            return path;
        }

        /** 本文の「操作の説明」に入れる語。 */
        private String action() {
            return action;
        }
    }
}
