package com.afkgame.web.config.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.terasoluna.gfw.security.web.logging.UserIdMDCPutFilter;

/**
 * Bean definition to configure SpringSecurity.
 * <p>
 * 画面を持たないため、雛形のフォームログイン・ログアウト・エラー画面転送は落としている。
 * </p>
 * <p>
 * CSRF 対策は行わない。Cookie を使わず {@code Authorization: Bearer} のみで認証するため構造上
 * CSRF の対象外であり、セッションも持たない（tech_security.md §11.1・§11.2 の
 * {@code allowCredentials: false}、tech_auth.md §1 のステートレスなJWT認証。
 * 移行 STEP 2R-C の確定結果）。
 * </p>
 * <p>
 * 認証方式（JWT フィルタ）・認証不要パス・CORS 設定は、依存する {@code JwtService} 等の移植と
 * あわせて移行 STEP 2R-D で入れる（java_migration/steps.md）。
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SpringSecurityConfig {

    /**
     * Configure {@link SecurityFilterChain} bean.
     * @param http Builder class for setting up authentication and authorization
     * @return Bean of configured {@link SecurityFilterChain}
     * @throws Exception Exception that occurs when setting HttpSecurity
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.addFilterAfter(userIdMDCPutFilter(), AnonymousAuthenticationFilter.class);
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(authz -> authz.requestMatchers("/**").permitAll());

        return http.build();
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
