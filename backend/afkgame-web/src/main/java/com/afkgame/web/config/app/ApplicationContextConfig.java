package com.afkgame.web.config.app;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.terasoluna.gfw.common.exception.ExceptionCodeResolver;
import org.terasoluna.gfw.common.exception.ExceptionLogger;
import org.terasoluna.gfw.common.exception.SimpleMappingExceptionCodeResolver;
import org.terasoluna.gfw.web.exception.ExceptionLoggingFilter;

import com.afkgame.domain.config.app.AfkgameDomainConfig;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Application context.
 * <p>
 * {@code com.afkgame.web.filter} を**ルートコンテキストで**走査する（サーブレットコンテキスト側の
 * {@code SpringMvcConfig} では走査しない）。{@code RequestLogFilter} は {@code web.xml} の
 * {@code DelegatingFilterProxy} から、{@code ApiAuthenticationEntryPoint} は
 * {@link SpringSecurityConfig} から引かれ、どちらもルートコンテキストを見るため。
 * {@code ApiExceptionHandler}（{@code @RestControllerAdvice}）は
 * {@code DispatcherServlet} が祖先コンテキストまで探すのでルートに置いても効く。
 * </p>
 */
@Configuration
@EnableAspectJAutoProxy
@ComponentScan(basePackages = {"com.afkgame.web.filter"})
@Import({AfkgameDomainConfig.class})
public class ApplicationContextConfig {

    /**
     * Configure {@link JsonMapper} bean.
     * <p>
     * JSON は Jackson 3（{@code tools.jackson}）で扱う（移行 STEP 2R-C の確定結果）。未定義
     * フィールドを含むリクエストは 422 で拒否するため {@code FAIL_ON_UNKNOWN_PROPERTIES} を
     * 有効にする（Jackson 3 の既定は無効。tech_security.md §11.3・tech_api_common.md §5.0）。
     * </p>
     * <p>
     * Spring MVC の変換器（{@code SpringMvcConfig}）と、フィルタ内で応答を組み立てる
     * {@code ApiAuthenticationEntryPoint} が同じインスタンスを使い、JSON の形式をそろえる。
     * </p>
     * @return Bean of configured {@link JsonMapper}
     */
    @Bean("jsonMapper")
    public JsonMapper jsonMapper() {
        return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * Configure {@link PasswordEncoder} bean.
     * @return Bean of configured {@link DelegatingPasswordEncoder}
     */
    @Bean("passwordEncoder")
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> idToPasswordEncoder = new HashMap<>();
        idToPasswordEncoder.put("pbkdf2", pbkdf2PasswordEncoder());
        idToPasswordEncoder.put("bcrypt", bCryptPasswordEncoder());
        /*
         * When using commented out PasswordEncoders, you need to add bcprov-jdk18on.jar to the
         * dependency. idToPasswordEncoder.put("argon2", argon2PasswordEncoder());
         * idToPasswordEncoder.put("scrypt", sCryptPasswordEncoder());
         */
        return new DelegatingPasswordEncoder("pbkdf2", idToPasswordEncoder);
    }

    /**
     * Configure {@link Pbkdf2PasswordEncoder} bean.
     * @return Bean of configured {@link Pbkdf2PasswordEncoder}
     */
    @Bean
    public Pbkdf2PasswordEncoder pbkdf2PasswordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * Configure {@link BCryptPasswordEncoder} bean.
     * @return Bean of configured {@link BCryptPasswordEncoder}
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * When using commented out PasswordEncoders, you need to add bcprov-jdk18on.jar to the
     * dependency.
     * 
     * @Bean public Argon2PasswordEncoder argon2PasswordEncoder() { return
     * Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }
     * 
     * @Bean public SCryptPasswordEncoder sCryptPasswordEncoder() { return
     * SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8(); }
     */

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
     * Configure {@link MessageSource} bean.
     * @return Bean of configured {@link ResourceBundleMessageSource}
     */
    @Bean("messageSource")
    public MessageSource messageSource() {
        ResourceBundleMessageSource bean = new ResourceBundleMessageSource();
        bean.setBasenames("i18n/application-messages");
        return bean;
    }

    /**
     * Configure {@link ExceptionCodeResolver} bean.
     * @return Bean of configured {@link SimpleMappingExceptionCodeResolver}
     */
    @Bean("exceptionCodeResolver")
    public ExceptionCodeResolver exceptionCodeResolver() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("ResourceNotFoundException", "e.xx.fw.5001");
        map.put("InvalidTransactionTokenException", "e.xx.fw.7001");
        map.put("BusinessException", "e.xx.fw.8001");
        map.put(".DataAccessException", "e.xx.fw.9002");
        SimpleMappingExceptionCodeResolver bean = new SimpleMappingExceptionCodeResolver();
        bean.setExceptionMappings(map);
        bean.setDefaultExceptionCode("e.xx.fw.9001");
        return bean;
    }

    /**
     * Configure {@link ExceptionLogger} bean.
     * @return Bean of configured {@link ExceptionLogger}
     */
    @Bean("exceptionLogger")
    public ExceptionLogger exceptionLogger() {
        ExceptionLogger bean = new ExceptionLogger();
        bean.setExceptionCodeResolver(exceptionCodeResolver());
        return bean;
    }

    /**
     * Configure {@link ExceptionLoggingFilter} bean.
     * @return Bean of configured {@link ExceptionLoggingFilter}
     */
    @Bean("exceptionLoggingFilter")
    public ExceptionLoggingFilter exceptionLoggingFilter() {
        ExceptionLoggingFilter bean = new ExceptionLoggingFilter();
        bean.setExceptionLogger(exceptionLogger());
        return bean;
    }
}
