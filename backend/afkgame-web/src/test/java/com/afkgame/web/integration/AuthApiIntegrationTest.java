package com.afkgame.web.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * 認証の横断基盤（Security フィルタ → コントローラ → MyBatis → DB）が連結して動くことの統合テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §3「ゲストプレイ」・§4（ローテーションと不正検知）、
 * docs/tech/basic/tech_logging.md「AUTH_ コード一覧」「統一エラーレスポンス形式」、
 * docs/tech/nonfunctional/tech_security.md §11.2（CORS）。
 *
 * <p>DB は埋め込み PostgreSQL（zonky）。経緯は docs/backlog/java_migration.md §2。
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** ゲストを作成し、応答（accessToken / refreshToken / user）を返す。 */
    private JsonNode createGuest() throws Exception {
        String body = mockMvc.perform(post("/api/auth/guest"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    @Test
    @DisplayName("POST /api/auth/guest はゲストを永続化してトークンペアを返す")
    void test_ゲスト作成がDBへ反映される() throws Exception {
        JsonNode guest = createGuest();

        String userId = guest.at("/user/id").asText();
        assertThat(userId).startsWith("guest_");
        assertThat(guest.at("/user/isGuest").asBoolean()).isTrue();
        assertThat(guest.at("/user/emailVerified").asBoolean()).isFalse();
        assertThat(guest.at("/accessToken").asText()).isNotBlank();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_guest FROM users WHERE id = ?", Boolean.class, userId)).isTrue();
        // 生のリフレッシュトークンはDBへ保存しない（ハッシュのみ）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND token_hash = ?",
                Integer.class, userId, guest.at("/refreshToken").asText())).isZero();
    }

    @Test
    @DisplayName("POST /api/auth/refresh は新しいトークンペアを返し、旧トークンを失効させる")
    void test_リフレッシュでトークンがローテーションする() throws Exception {
        JsonNode guest = createGuest();
        String oldRefreshToken = guest.at("/refreshToken").asText();

        String refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(guest.at("/user/id").asText()))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(refreshed).at("/refreshToken").asText())
                .isNotEqualTo(oldRefreshToken);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked = TRUE",
                Integer.class, guest.at("/user/id").asText())).isEqualTo(1);
    }

    @Test
    @DisplayName("失効済みリフレッシュトークンの再利用は 401 になり、全トークンが失効する")
    void test_再利用検知で全トークンを失効させる() throws Exception {
        JsonNode guest = createGuest();
        String oldRefreshToken = guest.at("/refreshToken").asText();
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(oldRefreshToken)));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_INVALID"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());

        // 401 を返しても失効はロールバックしない（tech_auth.md §4「不正検知」）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked = FALSE",
                Integer.class, guest.at("/user/id").asText())).isZero();
    }

    @Test
    @DisplayName("不正な本文は 422 を返す")
    void test_本文が不正なら422を返す() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"x\",\"unknownField\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("認証必須のエンドポイントはトークンの状態に応じた AUTH_ コードで 401 を返す")
    void test_未認証リクエストを401で拒否する() throws Exception {
        mockMvc.perform(get("/api/game/state"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_HEADER_MISSING"))
                // ログ突合のためリクエストIDを常に返す（tech_api_common.md「共通ヘッダ」）
                .andExpect(header().exists("X-Request-ID"));

        mockMvc.perform(get("/api/game/state").header("Authorization", "Token abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_FORMAT"));

        mockMvc.perform(get("/api/game/state").header("Authorization", "Bearer broken.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    @DisplayName("発行したアクセストークンは Security フィルタを通過する")
    void test_発行したトークンで認証を通過する() throws Exception {
        String accessToken = createGuest().at("/accessToken").asText();

        // 認証は通り、未実装パスとして 404 になる（401 でないことが通過の証拠）
        mockMvc.perform(get("/api/game/state").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("HTTP_404"));
    }

    @Test
    @DisplayName("許可オリジンからの事前リクエストに CORS ヘッダを返す")
    void test_許可オリジンへCORSヘッダを返す() throws Exception {
        mockMvc.perform(options("/api/auth/guest")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-ID"));
    }
}
