package com.afkgame.web.resource.auth;

import com.afkgame.domain.model.User;

/**
 * 認証応答に含めるユーザー情報。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §5。プロパティ名は camelCase
 * （docs/tech/basic/tech_api/common.md §5.0）。パスワードハッシュ等の機密は含めない。
 *
 * @param id            ユーザーID（{@code user_<UUID>} / {@code guest_<UUID>}）
 * @param email         メールアドレス。ゲストは null
 * @param displayName   表示名
 * @param isGuest       ゲストアカウントかどうか
 * @param emailVerified メール確認済みかどうか
 */
public record UserResource(
        String id,
        String email,
        String displayName,
        boolean isGuest,
        boolean emailVerified) {

    public static UserResource from(User user) {
        return new UserResource(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.isGuest(), user.isEmailVerified());
    }
}
