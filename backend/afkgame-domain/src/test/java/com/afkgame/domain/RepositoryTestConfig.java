package com.afkgame.domain;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.afkgame.domain.config.app.AfkgameInfraConfig;

/**
 * Repository 統合テストのコンテキスト定義。
 *
 * <p>{@code afkgame-domain} には本番の起動クラスが無いため、これが最小のコンテキストになる。
 * インフラ層（{@link AfkgameInfraConfig} → {@code AfkgameEnvConfig}）だけを起こし、Service や
 * マスターデータは載せない（検証対象は SQL とマッピングのため）。
 *
 * <p>接続先は {@code META-INF/spring/afkgame-infra.properties} の {@code database.*} を、
 * テスト側の {@code @DynamicPropertySource}（{@code RepositoryTestSupport}）が埋め込み
 * PostgreSQL へ差し替える。{@code DataSource} の Bean 定義そのものは本番と同じものを使う
 * （移行 STEP 2R-E。Spring Boot の {@code @SpringBootTest} +
 * {@code @AutoConfigureEmbeddedDatabase} を置き換えた）。
 */
@Configuration
@Import({AfkgameInfraConfig.class})
public class RepositoryTestConfig {

    /**
     * Configure {@link PropertySourcesPlaceholderConfigurer} bean.
     *
     * <p>本番のルートコンテキストでは {@code ApplicationContextConfig} が持つ（{@code afkgame-web}）。
     * domain 単体でコンテキストを起こすテストにも同じ読み込み範囲が要るため、ここで定義する。
     *
     * @param properties Property files to be read
     * @return Bean of configured {@link PropertySourcesPlaceholderConfigurer}
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer(
            @Value("classpath*:/META-INF/spring/*.properties") Resource... properties) {
        PropertySourcesPlaceholderConfigurer bean = new PropertySourcesPlaceholderConfigurer();
        bean.setLocations(properties);
        return bean;
    }

    /**
     * Configure {@link JdbcTemplate} bean.
     *
     * <p>フィクスチャの作成と検証に使う（検証対象の Repository へ依存させないため）。
     *
     * @param dataSource DataSource
     * @return Bean of configured {@link JdbcTemplate}
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
