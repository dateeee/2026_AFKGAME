package com.afkgame.web.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.afkgame.domain.service.AuthService;
import com.afkgame.domain.service.JwtService;
import com.afkgame.env.config.CorsProperties;
import com.afkgame.web.filter.ApiAuthenticationEntryPoint;
import com.afkgame.web.filter.JwtAuthenticationFilter;
import com.afkgame.web.filter.RequestLogFilter;

/**
 * 認証・CORS の設定。
 *
 * <p>仕様: docs/tech/nonfunctional/tech_security.md §11.2（CORS）・§11.5（認証必須の既定）、
 * docs/tech/basic/tech_api_common.md §5.0（認証不要な例外の一覧）、
 * docs/tech/detail/tech_auth.md §1（ステートレスなJWT認証）。
 *
 * <p>CSRF 対策は行わない。Cookie を使わず {@code Authorization: Bearer} のみで認証するため、
 * 構造上 CSRF の対象外（tech_security.md §11.1）。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 認証不要なエンドポイント。
     *
     * <p>一覧の正は tech_api_common.md §5.0「認証不要な例外」。ここには**実装済みのものだけ**を
     * 並べる（未実装のパスを先に開けない）。register・login・verify-email・google・
     * password-reset は移植時（java_migration.md STEP 3・4）に追加する。
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/health",
            "/api/auth/guest",
            "/api/auth/refresh",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtService jwtService, AuthService authService,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, authService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * CORS 設定。許可オリジンは環境変数で明示し、ワイルドカードは使わない（§11.2）。
     *
     * @param corsProperties 許可オリジン
     * @return CORS 設定
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.origins());
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(),
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
}
