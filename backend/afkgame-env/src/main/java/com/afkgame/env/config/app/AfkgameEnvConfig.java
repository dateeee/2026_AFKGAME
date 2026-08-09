package com.afkgame.env.config.app;

import java.time.Duration;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.terasoluna.gfw.common.time.ClockFactory;
import org.terasoluna.gfw.common.time.DefaultClockFactory;

/**
 * Define settings for the environment.
 * <p>
 * 接続先・プールの値は {@code META-INF/spring/afkgame-infra.properties}（正は
 * tech_operations.md §12.2）。スキーマ適用は Flyway が持つため、雛形の
 * {@code DataSourceInitializer}（H2 用スクリプトの流し込み）は落としている。
 * </p>
 */
@Configuration
@Import({AfkgameSettingsConfig.class, LocalProfileConfig.class, TimeConfig.class})
public class AfkgameEnvConfig {

    /**
     * Flyway が読むマイグレーションの配置（{@code afkgame-initdb} が同梱する）。
     */
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    /**
     * DataSource.driverClassName property.
     */
    @Value("${database.driver.class.name}")
    private String driverClassName;

    /**
     * DataSource.url property.
     */
    @Value("${database.url}")
    private String url;

    /**
     * DataSource.username property.
     */
    @Value("${database.user}")
    private String username;

    /**
     * DataSource.password property.
     */
    @Value("${database.password}")
    private String password;

    /**
     * DataSource.maxTotal property.
     */
    @Value("${cp.max.active}")
    private Integer maxActive;

    /**
     * DataSource.maxIdle property.
     */
    @Value("${cp.max.idle}")
    private Integer maxIdle;

    /**
     * DataSource.minIdle property.
     */
    @Value("${cp.min.idle}")
    private Integer minIdle;

    /**
     * DataSource.maxWaitMillis property.
     */
    @Value("${cp.max.wait}")
    private Integer maxWait;

    /**
     * Configure {@link ClockFactory}.
     * @return Bean of configured {@link DefaultClockFactory}
     */
    @Bean("dateFactory")
    public ClockFactory dateFactory() {
        return new DefaultClockFactory();
    }

    /**
     * Configure {@link DataSource} bean.
     * @return Bean of configured {@link BasicDataSource}
     */
    @Bean(name = "dataSource", destroyMethod = "close")
    public DataSource dataSource() {
        BasicDataSource bean = new BasicDataSource();
        bean.setDriverClassName(driverClassName);
        bean.setUrl(url);
        bean.setUsername(username);
        bean.setPassword(password);
        bean.setDefaultAutoCommit(false);
        bean.setMaxTotal(maxActive);
        bean.setMaxIdle(maxIdle);
        bean.setMinIdle(minIdle);
        bean.setMaxWait(Duration.ofMillis(maxWait));
        return bean;
    }

    /**
     * Configure {@link Flyway} bean.
     * <p>
     * 起動時にマイグレーションを自動適用する（tech_operations.md §12.4 の適用手順）。
     * DB を使う Bean は {@code @DependsOn("flyway")} で本 Bean の後に生成させ、スキーマ未適用の
     * DB へアクセスしないようにする。
     * </p>
     * @param dataSource DataSource used for migration
     * @return Bean of configured {@link Flyway}
     */
    @Bean(name = "flyway", initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations(MIGRATION_LOCATION).load();
    }

    /**
     * Configure {@link TransactionManager} bean.
     * @param dataSource DataSource used for transaction management
     * @return Bean of configured {@link DataSourceTransactionManager}
     */
    @Bean("transactionManager")
    public TransactionManager transactionManager(DataSource dataSource) {
        DataSourceTransactionManager bean = new DataSourceTransactionManager();
        bean.setDataSource(dataSource);
        bean.setRollbackOnCommitFailure(true);
        return bean;
    }
}
