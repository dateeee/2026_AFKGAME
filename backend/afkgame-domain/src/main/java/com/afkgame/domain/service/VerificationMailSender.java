package com.afkgame.domain.service;

import com.afkgame.domain.model.User;

/**
 * 確認メールの送信境界。
 *
 * <p>仕様: docs/tech/detail/tech_auth_account.md §10 手順9。呼び出し元（{@link AuthService}）は
 * 送信の成否を応答へ反映せず、失敗は WARN ログだけを残す。
 *
 * <p>送信手段を差し替えられるようインタフェースで受ける
 * （docs/process/coding_standards_backend/domain_service.md §3 #8）。
 * 現行の実装 {@link VerificationMailSenderImpl} は WARN ログのみの仮実装。
 */
public interface VerificationMailSender {

    /**
     * 確認メールの送信を要求する。
     *
     * @param user 宛先のユーザー
     * @param rawToken 確認トークンの生値（メール本文のリンクに載せる）
     */
    void send(User user, String rawToken);
}
