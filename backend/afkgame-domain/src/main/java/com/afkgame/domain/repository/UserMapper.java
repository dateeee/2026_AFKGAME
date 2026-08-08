package com.afkgame.domain.repository;

import com.afkgame.domain.model.User;

/**
 * {@code users} テーブルの Mapper。
 *
 * <p>骨格構築（docs/backlog/java_migration.md STEP 2-B）で必要な操作のみを持つ。
 * メール登録・ログイン・アカウント移行で使う操作は STEP 3 で追加する。
 */
public interface UserMapper {

    /**
     * IDでユーザーを取得する。
     *
     * @param id ユーザーID
     * @return 該当ユーザー。存在しなければ null
     */
    User selectById(String id);

    /**
     * ユーザーを登録する。
     *
     * @param user 登録するユーザー
     */
    void insert(User user);
}
