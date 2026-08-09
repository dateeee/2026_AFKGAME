package com.afkgame.domain.config.mybatis;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * Mybatis config.
 */
public class MybatisConfig {

    /**
     * Settings Mybatis config.
     * @return Configured {@link Configuration}
     */
    public static Configuration configuration() {
        Configuration configuration = new Configuration();
        setSettings(configuration);
        setTypeAliases(configuration.getTypeAliasRegistry());
        setTypeHandlers(configuration.getTypeHandlerRegistry());
        return configuration;
    }

    /**
     * Settings MyBatis behaves.
     * @param configuration Accepted at configuration
     */
    private static void setSettings(Configuration configuration) {
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLazyLoadingEnabled(true);
        configuration.setDefaultFetchSize(100);
        // configuration.setDefaultExecutorType(ExecutorType.REUSE);
        // configuration.setJdbcTypeForNull(JdbcType.NULL);
        // configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
    }

    /**
     * Settings type aliases.
     * <p>
     * {@code com.afkgame.domain.model} は登録しない。Entity の {@code Character} が MyBatis 組み込みの
     * エイリアス {@code Character}（{@code java.lang.Character}）と衝突し、
     * {@code SqlSessionFactory} の生成が {@code TypeException} で落ちるため。マッピング XML は
     * どれも完全修飾名で型を書いており（{@code type="com.afkgame.domain.model.User"} など）、
     * エイリアスに依存していない。
     * </p>
     * @param typeAliasRegistry Accepted at configuration
     */
    private static void setTypeAliases(TypeAliasRegistry typeAliasRegistry) {
        typeAliasRegistry.registerAliases("com.afkgame.domain.repository");
        // typeAliasRegistry.registerAliases("com.afkgame.infra.mybatis.typehandler");
    }

    /**
     * Settings type handlers.
     * @param typeHandlerRegistry Accepted at configuration
     */
    private static void setTypeHandlers(TypeHandlerRegistry typeHandlerRegistry) {
        // typeHandlerRegistry.register("com.afkgame.infra.mybatis.typehandler");
    }
}
