package com.afkgame.web.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.afkgame.domain.service.AuthService;
import com.afkgame.web.resource.AuthResource;
import com.afkgame.web.resource.RefreshResource;

import jakarta.validation.Valid;

/**
 * 認証エンドポイント。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §5。認証不要
 * （docs/tech/basic/tech_api_common.md §5.0「認証不要な例外」）。
 *
 * <p>骨格構築（docs/backlog/java_migration.md STEP 2-B）の範囲としてゲスト作成とリフレッシュのみを持つ。
 * register・login・logout・link-account・verify-email・google・password-reset は STEP 3 で追加する。
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
}
