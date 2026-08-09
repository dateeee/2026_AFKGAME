package com.afkgame.web.config.web;

import java.util.List;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.Resource;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.terasoluna.gfw.common.exception.ExceptionLogger;
import org.terasoluna.gfw.web.exception.HandlerExceptionResolverLoggingInterceptor;
import org.terasoluna.gfw.web.logging.TraceLoggingInterceptor;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configure SpringMVC.
 * <p>
 * REST API 専用のため、雛形の画面向け設定は落としている（ViewResolver・Thymeleaf・静的リソース
 * ハンドラ・CodeList・トランザクショントークン・RequestDataValueProcessor・画面へ転送する
 * SystemExceptionResolver）。エラー応答は共通例外ハンドラ（{@code com.afkgame.web.filter}）で
 * JSON を返す方式に置き換える（正は tech_api_common.md）。
 * </p>
 * <p>
 * JSON は Jackson 3（{@code tools.jackson}）で扱う。雛形の依存には Jackson 2
 * （{@code com.fasterxml}）も同居するが、Spring Framework 7 の既定が Jackson 3 であり、
 * {@code java.time} 対応が databind に内蔵されているため（2 は {@code jackson-datatype-jsr310} の
 * 登録が要る）3 を採る（移行 STEP 2R-C の確定結果）。
 * </p>
 * <p>
 * 未定義パスは既定サーブレットへ転送せず例外にして、404 も統一エラー形式で返す。このため雛形の
 * {@code configureDefaultServletHandling} は落としている（Spring Framework 7 の
 * {@code DispatcherServlet} は常に {@code NoHandlerFoundException} を送出するので追加の設定は要らない）。
 * </p>
 */
@ComponentScan(basePackages = {"com.afkgame.web.api", "com.afkgame.web.filter"})
@EnableAspectJAutoProxy
@EnableWebMvc
@Configuration
public class SpringMvcConfig implements WebMvcConfigurer {

    /**
     * Configure {@link PropertySourcesPlaceholderConfigurer} bean.
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
     * {@inheritDoc}
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(pageableHandlerMethodArgumentResolver());
        argumentResolvers.add(authenticationPrincipalArgumentResolver());
    }

    /**
     * Configure {@link PageableHandlerMethodArgumentResolver} bean.
     * @return Bean of configured {@link PageableHandlerMethodArgumentResolver}
     */
    @Bean
    public PageableHandlerMethodArgumentResolver pageableHandlerMethodArgumentResolver() {
        return new PageableHandlerMethodArgumentResolver();
    }

    /**
     * Configure {@link AuthenticationPrincipalArgumentResolver} bean.
     * @return Bean of configured {@link AuthenticationPrincipalArgumentResolver}
     */
    @Bean
    public AuthenticationPrincipalArgumentResolver authenticationPrincipalArgumentResolver() {
        return new AuthenticationPrincipalArgumentResolver();
    }

    /**
     * {@inheritDoc}
     * <p>
     * JSON の変換器を Jackson 3 のものへ差し替える。未定義フィールドを含むリクエストは 422 で
     * 拒否するため {@code FAIL_ON_UNKNOWN_PROPERTIES} を有効にする（Jackson 3 の既定は無効。
     * tech_security.md §11.3・tech_api_common.md §5.0）。
     * </p>
     * <p>
     * 既定の変換器は呼び出し元で登録済みのため、ここでは JSON ぶんだけを上書きする。
     * </p>
     */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        builder.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        addInterceptor(registry, traceLoggingInterceptor());
    }

    /**
     * Common processes used in #addInterceptors.
     * @param registry {@link InterceptorRegistry}
     * @param interceptor {@link HandlerInterceptor}
     */
    private void addInterceptor(InterceptorRegistry registry, HandlerInterceptor interceptor) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
    }

    /**
     * Configure {@link TraceLoggingInterceptor} bean.
     * @return Bean of configured {@link TraceLoggingInterceptor}
     */
    @Bean
    public TraceLoggingInterceptor traceLoggingInterceptor() {
        return new TraceLoggingInterceptor();
    }

    /**
     * Configure messages logging AOP.
     * @param exceptionLogger Bean defined by ApplicationContextConfig#exceptionLogger
     * @see com.afkgame.web.config.app.ApplicationContextConfig#exceptionLogger()
     * @return Bean of configured {@link HandlerExceptionResolverLoggingInterceptor}
     */
    @Bean("handlerExceptionResolverLoggingInterceptor")
    public HandlerExceptionResolverLoggingInterceptor handlerExceptionResolverLoggingInterceptor(
            ExceptionLogger exceptionLogger) {
        HandlerExceptionResolverLoggingInterceptor bean =
                new HandlerExceptionResolverLoggingInterceptor();
        bean.setExceptionLogger(exceptionLogger);
        return bean;
    }

    /**
     * Configure messages logging AOP advisor.
     * @param handlerExceptionResolverLoggingInterceptor Bean defined by
     *        #handlerExceptionResolverLoggingInterceptor
     * @see #handlerExceptionResolverLoggingInterceptor(ExceptionLogger)
     * @return Advisor configured for PointCut
     */
    @Bean
    public Advisor handlerExceptionResolverLoggingInterceptorAdvisor(
            HandlerExceptionResolverLoggingInterceptor handlerExceptionResolverLoggingInterceptor) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(
                "execution(* org.springframework.web.servlet.HandlerExceptionResolver.resolveException(..))");
        return new DefaultPointcutAdvisor(pointcut, handlerExceptionResolverLoggingInterceptor);
    }
}
