package com.afkgame.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.afkgame.domain.model.User;

/**
 * {@link VerificationMailSender} の実装。
 *
 * <p><b>仮実装</b>: 送信手段（SMTP設定・本文・再送）と「コミット後・トランザクションの外」で
 * 送るための仕組みは verify-email の詳細設計で確定するため、現時点では送信要求を WARN ログに
 * 記録するだけで実際の送信を行わない。解消時期は移行 STEP 3-A-3
 * （docs/backlog/java_migration.md）。生のトークンはログへ出さない（tech_auth_account.md §9）。
 */
@Service
public class VerificationMailSenderImpl implements VerificationMailSender {

    private static final Logger logger = LoggerFactory.getLogger("afkgame.auth");

    /** {@inheritDoc} */
    @Override
    public void send(User user, String rawToken) {
        logger.warn("確認メールは未送信（送信手段は移行 STEP 3-A-3 で実装） user_id={}", user.getId());
    }
}
