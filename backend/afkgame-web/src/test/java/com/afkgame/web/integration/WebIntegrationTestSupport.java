package com.afkgame.web.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.afkgame.env.test.EmbeddedPostgresSupport;
import com.afkgame.web.config.app.ApplicationContextConfig;
import com.afkgame.web.config.app.SpringSecurityConfig;
import com.afkgame.web.config.web.SpringMvcConfig;
import com.afkgame.web.filter.RequestLogFilter;

import jakarta.servlet.Filter;

/**
 * Web 統合テストの共通基盤（Security フィルタ → DispatcherServlet → Service → MyBatis → DB）。
 *
 * <p>コンテキストは素の Spring（{@code @ExtendWith(SpringExtension)} +
 * {@code @ContextConfiguration} + {@code @WebAppConfiguration}）で起こし、Spring Boot の
 * テスト支援（{@code @SpringBootTest} / {@code @AutoConfigureMockMvc}）は使わない
 * （docs/process/coding_standards_backend/test.md §5）。
 *
 * <p>本番は {@code web.xml} がルートコンテキスト（{@code ApplicationContextConfig} +
 * {@code SpringSecurityConfig}）と DispatcherServlet のコンテキスト（{@code SpringMvcConfig}）を
 * 親子で持つが、テストでは同じ設定クラスを**1つのコンテキストへまとめて**載せる。検証対象は
 * リクエストの流れであって親子分割ではなく、まとめても Bean の解決結果は変わらないため。
 *
 * <p>フィルタは {@code web.xml} と同じ順序で {@link RequestLogFilter} →
 * {@code springSecurityFilterChain} の2つを積む（認証失敗の応答にも {@code X-Request-ID} が要る）。
 *
 * <p>DB は埋め込み PostgreSQL。データベースはコンテキストごとに払い出されるため、コミットする
 * テストどうしが同じ行を見ることはない（{@link EmbeddedPostgresSupport}）。
 */
@Tag("integration")
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {ApplicationContextConfig.class, SpringSecurityConfig.class,
        SpringMvcConfig.class, WebIntegrationTestConfig.class})
@ActiveProfiles("local")
abstract class WebIntegrationTestSupport {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestLogFilter requestLogFilter;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** {@code web.xml} と同じフィルタ順で組んだ MockMvc。 */
    protected MockMvc mockMvc;

    /**
     * 埋め込み PostgreSQL へ接続先を差し替える。
     *
     * @param registry テストコンテキストのプロパティ
     */
    @DynamicPropertySource
    static void embeddedDatabase(DynamicPropertyRegistry registry) {
        String jdbcUrl = EmbeddedPostgresSupport.createDatabase();
        registry.add("database.url", () -> jdbcUrl);
        registry.add("database.user", EmbeddedPostgresSupport::username);
        registry.add("database.password", EmbeddedPostgresSupport::password);
    }

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestLogFilter, springSecurityFilterChain)
                .build();
    }

    /**
     * テストデータを直接書き換えて<b>コミットする</b>。
     *
     * <p>{@code dataSource} は {@code defaultAutoCommit = false}（{@code AfkgameEnvConfig}）なので、
     * トランザクションの外で {@link JdbcTemplate#update} を呼ぶと、DBCP が接続の返却時に
     * ロールバックする。**更新件数は 1 が返るのに値は残らない**ため、素の {@code update} で
     * 用意したフィクスチャは後続のリクエストから見えない。書き換えは必ず本メソッドを通す
     * （読み取りは {@link #jdbcTemplate} を直接使ってよい）。
     *
     * @param sql 実行する SQL
     * @param args バインドする値
     */
    protected void updateFixture(String sql, Object... args) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcTemplate.update(sql, args));
    }
}
