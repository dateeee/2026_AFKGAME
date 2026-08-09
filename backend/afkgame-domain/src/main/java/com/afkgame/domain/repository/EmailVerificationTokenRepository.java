package com.afkgame.domain.repository;

import com.afkgame.domain.model.EmailVerificationToken;

/**
 * {@code email_verification_tokens} の Repository。
 *
 * <p>確認・再設定トークンはトークンハッシュで独立して検索されるため、{@code users} の従ではなく
 * 主体として扱う（{@code refresh_tokens} と同じ扱い。docs/process/coding_standards_backend/domain.md §3）。
 *
 * <p>登録（docs/tech/detail/tech_auth/account.md §10 手順6）で必要な操作のみを持つ。照合・使用済み更新は
 * verify-email・password-reset の実装（docs/backlog/java_migration.md STEP 3-A-3）で追加する。
 */
public interface EmailVerificationTokenRepository {

    /**
     * 確認・再設定トークンを登録する。
     *
     * @param emailVerificationToken 登録するレコード
     */
    void save(EmailVerificationToken emailVerificationToken);
}
