package com.afkgame.web.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.afkgame.domain.model.User;
import com.afkgame.domain.service.AuthService;
import com.afkgame.web.resource.AuthResource;
import com.afkgame.web.resource.LinkAccountResource;
import com.afkgame.web.resource.LoginResource;
import com.afkgame.web.resource.LogoutResource;
import com.afkgame.web.resource.RefreshResource;
import com.afkgame.web.resource.RegisterResource;
import com.afkgame.web.resource.StatusResource;
import com.afkgame.web.resource.VerifyEmailResource;

import jakarta.validation.Valid;

/**
 * 認証エンドポイント。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §5、登録・ログイン・ログアウトは
 * docs/tech/detail/tech_auth/account.md §9〜§15、アカウント移行は
 * docs/tech/detail/tech_auth/link.md §18、メール確認は docs/tech/detail/tech_auth/verify.md §20。
 * **ログアウトと link-account だけが認証必須**で、他は認証不要
 * （docs/tech/basic/tech_api/common.md §5.0「認証不要な例外」）。
 *
 * <p>google・password-reset は STEP 3-A-3 セグメント②以降で追加する
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

    /**
     * ゲストアカウントを本登録化し、新しいトークンペアを発行する。
     *
     * <p>認証ユーザーは <b>ID ではなくユーザー自身</b>をサービスへ渡す。移行可否の判定
     * （link.md §18 手順4）がアクセストークンに載った {@code isGuest} ではなくユーザーの現在値を
     * 見るため。
     *
     * @param user 認証ユーザー（{@code JwtAuthenticationFilter} が置く principal）
     * @param body メールアドレスとパスワード、または Google の認可コード
     * @return トークンペアとユーザー情報
     */
    @PostMapping("/link-account")
    public AuthResource linkAccount(@AuthenticationPrincipal User user,
            @Valid @RequestBody LinkAccountResource body) {
        return AuthResource.from(
                authService.linkAccount(user, body.email(), body.password(), body.googleAuthCode()));
    }

    /**
     * 確認トークンでメールアドレスの確認を済ませる。
     *
     * <p>認証不要（verify.md §20 入口条件）。メールクライアントから別ブラウザで開かれるため、
     * ログイン状態を要求しない。
     *
     * @param query 確認トークン（クエリ {@code token}）
     * @return 成功応答（{@code {"status": "ok"}}）
     */
    @GetMapping("/verify-email")
    public StatusResource verifyEmail(@Valid @ModelAttribute VerifyEmailResource query) {
        authService.verifyEmail(query.token());
        return StatusResource.ok();
    }
}
