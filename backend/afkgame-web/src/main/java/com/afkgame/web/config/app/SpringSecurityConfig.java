package com.afkgame.web.config.app;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.terasoluna.gfw.security.web.logging.UserIdMDCPutFilter;

import com.afkgame.domain.service.AuthService;
import com.afkgame.domain.service.JwtService;
import com.afkgame.env.config.CorsSettings;
import com.afkgame.web.filter.ApiAuthenticationEntryPoint;
import com.afkgame.web.filter.JwtAuthenticationFilter;
import com.afkgame.web.filter.RequestLogFilter;

/**
 * Bean definition to configure SpringSecurity.
 * <p>
 * 画面を持たないため、雛形のフォームログイン・ログアウト・エラー画面転送は落としている。
 * </p>
 * <p>
 * 仕様: docs/tech/nonfunctional/tech_security.md §11.2（CORS）・§11.5（認証必須の既定）、
 * docs/tech/basic/tech_api_common.md §5.0（認証不要な例外の一覧）、
 * docs/tech/detail/tech_auth.md §1（ステートレスなJWT認証）。
 * </p>
 * <p>
 * CSRF 対策は行わない。Cookie を使わず {@code Authorization: Bearer} のみで認証するため構造上
 * CSRF の対象外であり、セッションも持たない（tech_security.md §11.1・§11.2 の
 * {@code allowCredentials: false}、tech_auth.md §1 のステートレスなJWT認証。
 * 移行 STEP 2R-C の確定結果）。
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SpringSecurityConfig {

    /**
     * 認証不要なエンドポイント。
     * <p>
     * 一覧の正は tech_api_common.md §5.0「認証不要な例外」。ここには**実装済みのものだけ**を
     * 並べる（未実装のパスを先に開けない）。verify-email・google・password-reset は移植時
     * （java_migration.md STEP 3-A-3・4）に追加する。
     * </p>
     * <p>
     * **logout は載せない**。認証必須であり（tech_auth_account.md §9・§14 手順1）、
     * ここへ足すと無効なアクセストークンでも他人のリフレッシュトークンを指せてしまう。
     * </p>
     */
    private static final String[] PUBLIC_ENDPOINTS = {"/health", "/api/auth/guest",
            "/api/auth/refresh", "/api/auth/register", "/api/auth/login"};

    /**
     * Configure {@link SecurityFilterChain} bean.
     * @param http Builder class for setting up authentication and authorization
     * @param jwtService アクセストークンの検証
     * @param authService 認証ユーザーの取得
     * @param authenticationEntryPoint 認証失敗時の応答
     * @param corsConfigurationSource CORS 設定
     * @return Bean of configured {@link SecurityFilterChain}
     * @throws Exception Exception that occurs when setting HttpSecurity
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
            AuthService authService, ApiAuthenticationEntryPoint authenticationEntryPoint,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.addFilterAfter(userIdMDCPutFilter(), AnonymousAuthenticationFilter.class);
        http.csrf(csrf -> csrf.disable());
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(authz -> authz.requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .anyRequest().authenticated());
        http.exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint));
        http.addFilterBefore(new JwtAuthenticationFilter(jwtService, authService),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configure {@link CorsConfigurationSource} bean.
     * <p>
     * 許可オリジンは環境変数で明示し、ワイルドカードは使わない（tech_security.md §11.2）。
     * </p>
     * @param corsSettings 許可オリジン
     * @return Bean of configured {@link UrlBasedCorsConfigurationSource}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsSettings corsSettings) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsSettings.origins());
        configuration.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(),
                HttpMethod.PUT.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // 問い合わせ時にログと突合できるよう、リクエストIDをクライアントへ見せる
        configuration.setExposedHeaders(List.of(RequestLogFilter.REQUEST_ID_HEADER));
        // Cookie を使わないため資格情報は許可しない
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configure {@link UserIdMDCPutFilter} bean.
     * @return Bean of configured {@link UserIdMDCPutFilter}
     */
    @Bean("userIdMDCPutFilter")
    public UserIdMDCPutFilter userIdMDCPutFilter() {
        return new UserIdMDCPutFilter();
    }
}
