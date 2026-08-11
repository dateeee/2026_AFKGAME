package com.afkgame.domain.repository;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.afkgame.domain.model.User;

/**
 * {@code users} の Repository。
 *
 * <p>骨格構築（docs/backlog/java_migration.md STEP 2-B）と、登録・ログイン
 * （docs/tech/detail/tech_auth/account.md §10・§12。STEP 3-A-2）、アカウント移行・メール確認
 * （docs/tech/detail/tech_auth/link.md §18・docs/tech/detail/tech_auth/verify.md §20。STEP 3-A-3）、
 * パスワード再設定（docs/tech/detail/tech_auth/password_reset.md §24 手順7）で
 * 必要な操作を持つ。Google連携で使う操作は Google OAuth 対応時に追加する。
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
     * （tech_auth/account.md §12 末尾）。
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

    /**
     * ゲストを本登録化する（tech_auth/link.md §18 手順8）。
     *
     * <p>更新するのは {@code email}・{@code password_hash}・{@code is_guest}・
     * {@code email_verified}・{@code last_login_at} の5列だけで、{@code id}・{@code display_name}・
     * {@code created_at} は変えない（移行してもアカウントの同一性とゲームデータを保つため）。
     *
     * <p>{@code is_guest = true} の行だけを更新する。同じゲストで並走したリクエストのうち更新できるのは
     * 1本だけになり、呼び出し側は更新件数0を「他のリクエストが先に本登録化した」として扱える
     * （§18 手順8）。
     *
     * @param user 更新後の値を持つユーザー（{@code id} で特定する）
     * @return 更新件数（既に本登録済みなら 0）
     * @throws org.springframework.dao.DuplicateKeyException 同時移行で {@code uq_users_email} に
     *         違反した場合
     */
    int updateLinkedAccount(User user);

    /**
     * メール確認状態を更新する（tech_auth/verify.md §20 手順7）。
     *
     * @param id ユーザーID
     * @param emailVerified 更新後のメール確認状態
     */
    void updateEmailVerified(@Param("id") String id, @Param("emailVerified") boolean emailVerified);

    /**
     * パスワードを更新する（tech_auth/password_reset.md §24 手順7）。
     *
     * @param id ユーザーID
     * @param passwordHash 更新後の bcrypt ハッシュ
     */
    void updatePasswordHash(@Param("id") String id, @Param("passwordHash") String passwordHash);
}
