package com.afkgame.env.config;

import java.time.Duration;

/**
 * メール送信の設定値を保持する。
 *
 * <p>値の正は docs/tech/detail/tech_auth/mail.md §16.2、環境変数の正は
 * docs/tech/nonfunctional/tech_operations.md §12.2。
 * 組み立ては {@link com.afkgame.env.config.app.AfkgameSettingsConfig} が1か所で行う。
 *
 * <p>{@code smtpHost} が空文字のときは送信を行わず宛先と用途を INFO ログへ残すだけにする
 * （mail.md §16.2）。判定と送信は {@code VerificationMailSenderImpl}（{@code afkgame-domain} の
 * {@code service.auth}）が行い、
 * 本 Bean は接続先・差出人・リンク生成元を渡す。
 *
 * @param smtpHost SMTP の接続先ホスト（{@code SMTP_HOST}）。未設定なら空文字
 * @param smtpPort SMTP の接続先ポート（{@code SMTP_PORT}）
 * @param smtpUser SMTP 認証のユーザー（{@code SMTP_USER}）。未設定なら空文字
 * @param smtpPassword SMTP 認証のパスワード（{@code SMTP_PASSWORD}）。未設定なら空文字
 * @param smtpStarttlsRequired STARTTLS を必須にするか（{@code SMTP_STARTTLS_REQUIRED}）。
 *     {@code false} は TLS 無しのローカル SMTP 向けで、平文送信を許す
 * @param smtpTimeout SMTP の通信タイムアウト（設定ファイルではミリ秒の整数で持つ）
 * @param from 差出人アドレス
 * @param frontendBaseUrl メール本文のリンク生成元（{@code FRONTEND_BASE_URL}）
 */
public record MailSettings(
        String smtpHost,
        int smtpPort,
        String smtpUser,
        String smtpPassword,
        boolean smtpStarttlsRequired,
        Duration smtpTimeout,
        String from,
        String frontendBaseUrl) {
}
