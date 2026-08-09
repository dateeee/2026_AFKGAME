package com.afkgame.domain.config.app;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.terasoluna.gfw.common.exception.ExceptionLogger;
import org.terasoluna.gfw.common.exception.ResultMessagesLoggingInterceptor;

import com.afkgame.env.logging.LayerLoggingInterceptor;

import jakarta.validation.Validator;

/**
 * Bean definitions for domain layer.
 */
@Configuration
@EnableTransactionManagement
@ComponentScan(basePackages = {"com.afkgame.domain"})
@Import({AfkgameInfraConfig.class})
public class AfkgameDomainConfig {

    /**
     * Configure {@link Validator} bean.
     * <p>
     * マスターデータの起動時スキーマ検証（{@code MasterDataLoader}）が使う。Spring MVC の
     * {@code mvcValidator} はサーブレットコンテキスト側にあり domain からは引けないため、
     * ルートコンテキストにも Bean Validation の実装を1つ載せる
     * （移行前は Spring Boot の spring-boot-starter-validation が補っていた）。
     * </p>
     * @return Bean of configured {@link LocalValidatorFactoryBean}
     */
    @Bean("validator")
    public LocalValidatorFactoryBean validator() {
        return new LocalValidatorFactoryBean();
    }

    /**
     * Configure {@link ResultMessagesLoggingInterceptor} bean.
     * @param exceptionLogger Bean defined by ApplicationContextConfig#exceptionLogger
     * @see com.afkgame.web.config.app.ApplicationContextConfig#exceptionLogger()
     * @return Bean of configured {@link ResultMessagesLoggingInterceptor}
     */
    @Bean("resultMessagesLoggingInterceptor")
    public ResultMessagesLoggingInterceptor resultMessagesLoggingInterceptor(
            ExceptionLogger exceptionLogger) {
        ResultMessagesLoggingInterceptor bean = new ResultMessagesLoggingInterceptor();
        bean.setExceptionLogger(exceptionLogger);
        return bean;
    }

    /**
     * Configure messages logging AOP advisor.
     * @param resultMessagesLoggingInterceptor Bean defined by #resultMessagesLoggingInterceptor
     * @see #resultMessagesLoggingInterceptor(ExceptionLogger)
     * @return Advisor configured for PointCut
     */
    @Bean
    public Advisor resultMessagesLoggingInterceptorAdvisor(
            ResultMessagesLoggingInterceptor resultMessagesLoggingInterceptor) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("@within(org.springframework.stereotype.Service)");
        return new DefaultPointcutAdvisor(pointcut, resultMessagesLoggingInterceptor);
    }

    /**
     * Configure {@link LayerLoggingInterceptor} bean.
     * @return Bean of configured {@link LayerLoggingInterceptor}
     */
    @Bean
    public LayerLoggingInterceptor layerLoggingInterceptor() {
        return new LayerLoggingInterceptor();
    }

    /**
     * Configure the Web ↔ Domain boundary logging advisor (Service层).
     * <p>
     * ポイントカット式は {@code afkgame-env} の {@code META-INF/spring/afkgame.properties} が持つ
     * （{@code afkgame-domain} が {@code afkgame-web} 側のパッケージ名を抱えないため。
     * logging/application.md §3 規約2）。
     * </p>
     * @param layerLoggingInterceptor Bean defined by #layerLoggingInterceptor
     * @param pointcutExpression Service層のポイントカット式
     * @return Advisor configured for PointCut
     */
    @Bean
    public Advisor serviceLayerLoggingAdvisor(LayerLoggingInterceptor layerLoggingInterceptor,
            @Value("${afkgame.logging.layer.pointcut.service}") String pointcutExpression) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(pointcutExpression);
        return new DefaultPointcutAdvisor(pointcut, layerLoggingInterceptor);
    }

    /**
     * Configure the Domain ↔ Repository boundary logging advisor.
     * @param layerLoggingInterceptor Bean defined by #layerLoggingInterceptor
     * @param pointcutExpression Repository層のポイントカット式
     * @return Advisor configured for PointCut
     */
    @Bean
    public Advisor repositoryLayerLoggingAdvisor(LayerLoggingInterceptor layerLoggingInterceptor,
            @Value("${afkgame.logging.layer.pointcut.repository}") String pointcutExpression) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(pointcutExpression);
        return new DefaultPointcutAdvisor(pointcut, layerLoggingInterceptor);
    }
}
