package com.afkgame.domain.repository;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.afkgame.domain.model.User;

/**
 * {@code users} の Repository。
 *
 * <p>骨格構築（docs/backlog/java_migration.md STEP 2-B）と、登録・ログイン
 * （docs/tech/detail/tech_auth_account.md §10・§12。STEP 3-A-2）で必要な操作を持つ。
 * アカウント移行・Google連携で使う操作は STEP 3-A-3 以降で追加する。
 */
public interface UserRepository {

    /**
     * IDでユーザーを取得する。
     *
     * @param id ユーザーID
     * @return 該当ユーザー。存在しなければ null
     */
    User findById(String id);

    /**
     * メールアドレスでユーザーを取得する。
     *
     * <p>ゲストは {@code email} が NULL のため、どのメールでも一致しない
     * （tech_auth_account.md §12 末尾）。
     *
     * @param email メールアドレス
     * @return 該当ユーザー。存在しなければ null
     */
    User findByEmail(String email);

    /**
     * ユーザーを登録する。
     *
     * @param user 登録するユーザー
     */
    void save(User user);

    /**
     * 最終ログイン時刻を更新する。
     *
     * @param id ユーザーID
     * @param lastLoginAt 更新後の最終ログイン時刻
     */
    void updateLastLoginAt(@Param("id") String id, @Param("lastLoginAt") Instant lastLoginAt);
}
