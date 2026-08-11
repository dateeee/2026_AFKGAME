package com.afkgame.env.config.app;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.afkgame.env.config.AuthSettings;
import com.afkgame.env.config.CorsSettings;
import com.afkgame.env.config.GameSettings;
import com.afkgame.env.config.MailSettings;

/**
 * 設定ファイルの値を読み、設定保持 Bean を組み立てる。
 *
 * <p>キー・既定値の正は docs/tech/basic/tech_backend.md §4.2、環境変数の正は
 * docs/tech/nonfunctional/tech_operations.md §12.2。{@code @ConfigurationProperties} は
 * Spring Boot の機能のため使わず、{@code @Value} で読む場所を**本クラス1か所**に閉じる
 * （他層は {@link GameSettings} 等を注入して参照し、環境変数もプロパティも直接読まない）。
 */
@Configuration
public class AfkgameSettingsConfig {

    /**
     * 許可するアクティブプロファイル（docs/tech/nonfunctional/tech_operations.md §12.1）。
     */
    private static final List<String> ALLOWED_PROFILES = List.of("local", "production");

    /**
     * 起動時に環境識別を検査する。
     *
     * <p>{@code SPRING_PROFILES_ACTIVE} に既定値を置かないため、未設定・想定外の値なら起動を中止する
     * （設定漏れが local へのフォールバックとして黙って成立し、開発用の既定署名鍵で本番が起動するのを
     * 防ぐ。docs/tech/nonfunctional/tech_operations.md §12.2）。
     *
     * @param environment 実行環境
     * @throws IllegalStateException アクティブプロファイルが {@code local} / {@code production}
     *         のいずれか1つでない場合
     */
    public AfkgameSettingsConfig(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length != 1 || !ALLOWED_PROFILES.contains(activeProfiles[0])) {
            throw new IllegalStateException("SPRING_PROFILES_ACTIVE must be exactly one of "
                    + ALLOWED_PROFILES + " but was " + Arrays.toString(activeProfiles));
        }
    }

    /**
     * ゲーム進行の設定保持 Bean を組み立てる。
     *
     * @param tickIntervalSeconds 1 tick の間隔（秒）
     * @param turnsPerTick 1 tick あたりのターン数
     * @param offlineEfficiency オフライン時の報酬効率
     * @param maxOfflineHours オフライン報酬の最大蓄積時間
     * @param fastCalcThreshold 簡略計算へ切り替える未処理tick数の境界
     * @param maxBattleLogRecords DB保持ログ件数の上限
     * @param maxLogPerResponse 1レスポンスあたりのログ件数の上限
     * @param maxPlayerLevel プレイヤーLVの上限
     * @param maxGold ゴールドの上限
     * @param battleRngSeed 戦闘乱数のシード。未設定なら {@code null}
     * @return 組み立てた {@link GameSettings}
     */
    @Bean
    public GameSettings gameSettings(
            @Value("${afkgame.tick.interval.seconds}") int tickIntervalSeconds,
            @Value("${afkgame.turns.per.tick}") int turnsPerTick,
            @Value("${afkgame.offline.efficiency}") double offlineEfficiency,
            @Value("${afkgame.max.offline.hours}") int maxOfflineHours,
            @Value("${afkgame.fast.calc.threshold}") int fastCalcThreshold,
            @Value("${afkgame.max.battle.log.records}") int maxBattleLogRecords,
            @Value("${afkgame.max.log.per.response}") int maxLogPerResponse,
            @Value("${afkgame.max.player.level}") int maxPlayerLevel,
            @Value("${afkgame.max.gold}") long maxGold,
            @Value("${afkgame.battle.rng.seed}") Long battleRngSeed) {
        return new GameSettings(tickIntervalSeconds, turnsPerTick, offlineEfficiency,
                maxOfflineHours, fastCalcThreshold, maxBattleLogRecords, maxLogPerResponse,
                maxPlayerLevel, maxGold, battleRngSeed);
    }

    /**
     * 認証の設定保持 Bean を組み立てる。
     *
     * <p>有効期限は設定ファイルでは単位付きの整数で持ち（素の Spring は Boot の {@code 30m} 形式を
     * 解釈しないため）、ここで {@link Duration} へ組み立てる。
     *
     * @param secret JWT の署名鍵
     * @param accessTokenExpireMinutes アクセストークンの有効期限（分）
     * @param refreshTokenExpireDays リフレッシュトークンの有効期限（日）
     * @param bcryptStrength bcrypt のストレッチ回数
     * @param passwordMinLength パスワードの最小文字数
     * @param passwordMaxLength パスワードの最大文字数
     * @param guestExpireDays ゲストアカウントの有効期限（日）
     * @param verificationTokenExpireHours メール確認トークンの有効期間（時間）
     * @param passwordResetTokenExpireHours パスワード再設定トークンの有効期間（時間）
     * @param googleClientId Google OAuth のクライアントID（未設定なら空文字）
     * @return 組み立てた {@link AuthSettings}
     */
    @Bean
    public AuthSettings authSettings(
            @Value("${afkgame.auth.secret}") String secret,
            @Value("${afkgame.auth.access.token.expire.minutes}") int accessTokenExpireMinutes,
            @Value("${afkgame.auth.refresh.token.expire.days}") int refreshTokenExpireDays,
            @Value("${afkgame.auth.bcrypt.strength}") int bcryptStrength,
            @Value("${afkgame.auth.password.min.length}") int passwordMinLength,
            @Value("${afkgame.auth.password.max.length}") int passwordMaxLength,
            @Value("${afkgame.auth.guest.expire.days}") int guestExpireDays,
            @Value("${afkgame.auth.verification.token.expire.hours}") int verificationTokenExpireHours,
            @Value("${afkgame.auth.password.reset.token.expire.hours}") int passwordResetTokenExpireHours,
            @Value("${afkgame.auth.google.client.id}") String googleClientId) {
        return new AuthSettings(secret, Duration.ofMinutes(accessTokenExpireMinutes),
                Duration.ofDays(refreshTokenExpireDays), bcryptStrength, passwordMinLength,
                passwordMaxLength, Duration.ofDays(guestExpireDays),
                Duration.ofHours(verificationTokenExpireHours),
                Duration.ofHours(passwordResetTokenExpireHours), googleClientId);
    }

    /**
     * メール送信の設定保持 Bean を組み立てる。
     *
     * <p>キーの正は docs/tech/detail/tech_auth/mail.md §16.2。{@code SMTP_HOST} 等は未設定を
     * 許容する（送信せずログへ残す動作へ倒すため）ので、既定値を空文字にして起動は止めない。
     *
     * @param smtpHost SMTP の接続先ホスト
     * @param smtpPort SMTP の接続先ポート
     * @param smtpUser SMTP 認証のユーザー
     * @param smtpPassword SMTP 認証のパスワード
     * @param smtpStarttlsRequired STARTTLS を必須にするか
     * @param smtpTimeoutMillis SMTP の通信タイムアウト（ミリ秒）
     * @param from 差出人アドレス
     * @param frontendBaseUrl メール本文のリンク生成元
     * @return 組み立てた {@link MailSettings}
     */
    @Bean
    public MailSettings mailSettings(
            @Value("${mail.smtp.host}") String smtpHost,
            @Value("${mail.smtp.port}") int smtpPort,
            @Value("${mail.smtp.user}") String smtpUser,
            @Value("${mail.smtp.password}") String smtpPassword,
            @Value("${mail.smtp.starttls.required}") boolean smtpStarttlsRequired,
            @Value("${mail.smtp.timeout}") long smtpTimeoutMillis,
            @Value("${mail.from}") String from,
            @Value("${frontend.base.url}") String frontendBaseUrl) {
        return new MailSettings(smtpHost, smtpPort, smtpUser, smtpPassword, smtpStarttlsRequired,
                Duration.ofMillis(smtpTimeoutMillis), from, frontendBaseUrl);
    }

    /**
     * CORS の設定保持 Bean を組み立てる。
     *
     * @param origins 許可オリジン（カンマ区切り）
     * @return 組み立てた {@link CorsSettings}
     */
    @Bean
    public CorsSettings corsSettings(@Value("${afkgame.cors.origins}") String[] origins) {
        return new CorsSettings(List.of(origins));
    }
}
