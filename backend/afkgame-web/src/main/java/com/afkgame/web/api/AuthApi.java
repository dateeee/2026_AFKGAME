package com.afkgame.web.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.afkgame.domain.model.User;
import com.afkgame.domain.service.AuthService;
import com.afkgame.web.resource.AuthResource;
import com.afkgame.web.resource.LoginResource;
import com.afkgame.web.resource.LogoutResource;
import com.afkgame.web.resource.RefreshResource;
import com.afkgame.web.resource.RegisterResource;
import com.afkgame.web.resource.StatusResource;

import jakarta.validation.Valid;

/**
 * 認証エンドポイント。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §5、登録・ログイン・ログアウトは
 * docs/tech/detail/tech_auth_account.md §9〜§15。**ログアウトだけが認証必須**で、他は認証不要
 * （docs/tech/basic/tech_api_common.md §5.0「認証不要な例外」）。
 *
 * <p>link-account・verify-email・google・password-reset は STEP 3-A-3 以降で追加する
 * （docs/backlog/java_migration.md）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApi {

    private final AuthService authService;

    public AuthApi(AuthService authService) {
        this.authService = authService;
    }

    /**
     * ゲストアカウントを作成してトークンペアを発行する。
     *
     * @return トークンペアとユーザー情報
     */
    @PostMapping("/guest")
    public AuthResource createGuest() {
        return AuthResource.from(authService.createGuest());
    }

    /**
     * リフレッシュトークンで新しいトークンペアを取得する（ローテーション）。
     *
     * @param body リフレッシュトークン
     * @return 新しいトークンペアとユーザー情報
     */
    @PostMapping("/refresh")
    public AuthResource refresh(@Valid @RequestBody RefreshResource body) {
        return AuthResource.from(authService.refresh(body.refreshToken()));
    }

    /**
     * メールアドレスとパスワードでアカウントを登録し、トークンペアを発行する。
     *
     * @param body メールアドレスとパスワード
     * @return トークンペアとユーザー情報
     */
    @PostMapping("/register")
    public AuthResource register(@Valid @RequestBody RegisterResource body) {
        return AuthResource.from(authService.register(body.email(), body.password()));
    }

    /**
     * メールアドレスとパスワードでログインし、トークンペアを発行する。
     *
     * @param body メールアドレスとパスワード
     * @return トークンペアとユーザー情報
     */
    @PostMapping("/login")
    public AuthResource login(@Valid @RequestBody LoginResource body) {
        return AuthResource.from(authService.login(body.email(), body.password()));
    }

    /**
     * リフレッシュトークンを失効させてログアウトする。
     *
     * @param user 認証ユーザー（{@code JwtAuthenticationFilter} が置く principal）
     * @param body 失効させるリフレッシュトークン
     * @return 成功応答（{@code {"status": "ok"}}）
     */
    @PostMapping("/logout")
    public StatusResource logout(@AuthenticationPrincipal User user,
            @Valid @RequestBody LogoutResource body) {
        authService.logout(user.getId(), body.refreshToken());
        return StatusResource.ok();
    }
}
