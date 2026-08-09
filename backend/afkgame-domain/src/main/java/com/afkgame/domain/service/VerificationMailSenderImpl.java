package com.afkgame.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.afkgame.domain.model.User;
import com.afkgame.env.logging.AppLogger;
import com.afkgame.env.logging.LogKey;
import com.afkgame.env.logging.LoggerName;

/**
 * {@link VerificationMailSender} の実装。
 *
 * <p>仕様: docs/tech/detail/tech_auth/mail.md §16.1。「コミット後・トランザクションの外で送る」
 * 担保を本クラスが持ち、トランザクションに参加している間は送信要求を {@code afterCommit} へ預ける。
 * ロールバックした場合は {@code afterCommit} が呼ばれないため送信しない。
 *
 * <p><b>仮実装</b>: 送信手段（SMTP設定・本文・宛先マスク）は verify-email の詳細設計で確定するため、
 * {@link #doSend} は送信要求を WARN ログへ記録するだけで実際の送信を行わない。解消時期は
 * 移行 STEP 3-A-3（docs/backlog/java_migration.md）。生のトークンはログへ出さない
 * （tech_auth/account.md §9）。
 */
@Service
public class VerificationMailSenderImpl implements VerificationMailSender {

    private static final AppLogger logger = AppLogger.of(LoggerName.AUTH);

    /** {@inheritDoc} */
    @Override
    public void send(User user, String rawToken) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 待つ相手が無いまま遅延させると送信要求が失われるため、その場で送る
            sendQuietly(user, rawToken);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendQuietly(user, rawToken);
            }
        });
    }

    /**
     * 送信の失敗を WARN ログにとどめ、呼び出し元へ伝播させない（mail.md §16.1「失敗の扱い」）。
     *
     * <p>コミット後に呼ばれるため、ここで例外を投げるとコミット済みの登録処理が失敗として扱われる。
     */
    private void sendQuietly(User user, String rawToken) {
        try {
            doSend(user, rawToken);
        } catch (RuntimeException e) {
            logger.warn("確認メールの送信に失敗").with(LogKey.USER_ID, user.getId()).cause(e).log();
        }
    }

    /**
     * 確認メールを実際に送る。
     *
     * <p>仮実装のため送信を行わず、送信要求の記録だけを残す（解消時期は移行 STEP 3-A-3）。
     * 送信失敗を試験から再現できるよう、可視性をパッケージ内に限って開いている。
     *
     * @param user 宛先のユーザー
     * @param rawToken 確認トークンの生値（メール本文のリンクに載せる）
     */
    void doSend(User user, String rawToken) {
        logger.warn("確認メールは未送信（送信手段は移行 STEP 3-A-3 で実装）")
                .with(LogKey.USER_ID, user.getId())
                .log();
    }
}
