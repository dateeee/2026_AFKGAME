package com.afkgame.web.integration;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Web 統合テストが本番の設定へ足す Bean。
 *
 * <p>本番のコンテキスト（{@code ApplicationContextConfig} / {@code SpringSecurityConfig} /
 * {@code SpringMvcConfig}）はそのまま使い、テストの検証にだけ要るものをここへ寄せる。
 * 接続先の差し替えは {@code @DynamicPropertySource}（{@link WebIntegrationTestSupport}）が行うため、
 * {@code DataSource} の定義は上書きしない。
 */
@Configuration
public class WebIntegrationTestConfig {

    /**
     * Configure {@link JdbcTemplate} bean.
     *
     * <p>API 経由の書き込みが実際に永続化されたかを、Repository を通さずに確かめるために使う。
     *
     * @param dataSource DataSource
     * @return Bean of configured {@link JdbcTemplate}
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
