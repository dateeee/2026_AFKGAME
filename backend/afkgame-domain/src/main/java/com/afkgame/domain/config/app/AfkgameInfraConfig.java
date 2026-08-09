package com.afkgame.domain.config.app;

import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.afkgame.domain.config.mybatis.MybatisConfig;
import com.afkgame.env.config.app.AfkgameEnvConfig;

/**
 * Bean definitions for infrastructure layer.
 */
@Configuration
@MapperScan("com.afkgame.domain.repository")
@Import({AfkgameEnvConfig.class})
public class AfkgameInfraConfig {

    /**
     * Configure {@link SqlSessionFactoryBean} bean.
     * @param dataSource DataSource
     * @see com.afkgame.env.config.app.AfkgameEnvConfig#dataSource()
     * @return Bean of configured {@link SqlSessionFactoryBean}
     */
    @Bean("sqlSessionFactory")
    public SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setConfiguration(MybatisConfig.configuration());
        return bean;
    }
}
