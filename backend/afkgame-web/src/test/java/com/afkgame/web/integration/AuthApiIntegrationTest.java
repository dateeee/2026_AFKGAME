package com.afkgame.web.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 認証の横断基盤（Security フィルタ → コントローラ → MyBatis → DB）が連結して動くことの統合テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §3「ゲストプレイ」・§4（ローテーションと不正検知）、
 * docs/tech/basic/tech_error_handling.md「AUTH_ コード一覧」「統一エラーレスポンス形式」、
 * docs/tech/nonfunctional/tech_security.md §11.2（CORS）。
 *
 * <p>コンテキストと DB の起こし方は {@link WebIntegrationTestSupport}。
 */
class AuthApiIntegrationTest extends WebIntegrationTestSupport {

    /** 登録・ログインで使う生パスワード（DBへ保存されないことの照合にも使う）。 */
    private static final String PASSWORD = "securepass123";

    /** 応答の読み取りは本番と同じ Jackson 3 の {@code JsonMapper} を使う。 */
    @Autowired
    private JsonMapper jsonMapper;

    /** ゲストを作成し、応答（accessToken / refreshToken / user）を返す。 */
    private JsonNode createGuest() throws Exception {
        String body = mockMvc.perform(post("/api/auth/guest"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body);
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    private static String credentialBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** メールとパスワードで登録し、応答（accessToken / refreshToken / user）を返す。 */
    private JsonNode register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body);
    }

    /** {@code users.last_login_at} の現在値。 */
    private Timestamp lastLoginAt(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT last_login_at FROM users WHERE id = ?", Timestamp.class, userId);
    }

    /**
     * 生トークンの SHA-256（16進小文字）。
     *
     * <p>確認メールは送らないため生値を受け取れない。DBの {@code token_hash} を既知の生値の
     * ハッシュへ差し替えて確認APIを叩くために使う。<b>ハッシュ方式（tech_auth/account.md §9）が
     * 実装と食い違えば照合が外れて赤くなる</b>ので、方式の固定も兼ねる。
     */
    private static String sha256Hex(String rawToken) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
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

    /**
     * ゲスト作成が「プレイ可能な初期状態」まで作ることを、実DBの行で確認する。
     * 手順ごとの分岐は PlayerInitializationServiceImplTest が持ち、ここでは連結と永続化（コミット）を見る。
     *
     * <p>分岐: tech_auth.md #12
     */
    @Test
    @DisplayName("POST /api/auth/guest は Player・設定・初期キャラ・9スロット・初期アイテムまで作る")
    void test_ゲスト作成でプレイヤーの初期状態まで永続化される() throws Exception {
        String userId = createGuest().at("/user/id").asText();

        String playerId = jdbcTemplate.queryForObject(
                "SELECT id FROM players WHERE user_id = ?", String.class, userId);
        assertThat(playerId).isNotBlank();
        // 塔外で作られる（tech_auth.md §8.2 手順2）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM players WHERE id = ? AND gold = 0 AND current_tower_id IS NULL"
                        + " AND current_floor IS NULL AND target_floor IS NULL",
                Integer.class, playerId)).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM player_settings WHERE player_id = ?",
                Integer.class, playerId)).isEqualTo(1);

        String characterId = jdbcTemplate.queryForObject(
                "SELECT id FROM characters WHERE player_id = ?", String.class, playerId);
        // 作成直後は全快（tech_auth.md §8.2 手順4）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM characters WHERE id = ? AND hp = max_hp AND level = 1",
                Integer.class, characterId)).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM character_equip_slots"
                        + " WHERE character_id = ? AND equipment_id IS NULL",
                Integer.class, characterId)).isEqualTo(9);

        // 初期所持アイテム（initial_player.yml。正は master/item.md §3.5）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory_items WHERE player_id = ? AND item_id = 'hp_potion'",
                Integer.class, playerId)).isEqualTo(5);
    }

    /**
     * 登録が「ユーザー + 確認トークン + プレイヤー」までを1つのトランザクションで作り、コミットする
     * ことを実DBの行で確認する。手順ごとの分岐は {@code AuthServiceImplTest} が持ち、ここでは連結と
     * 永続化（生パスワードを保存しない・確認トークンが残る）を見る。
     *
     * <p>分岐: tech_auth/account.md §11 #10
     */
    @Test
    @DisplayName("POST /api/auth/register は bcrypt ハッシュ・確認トークン・プレイヤーを永続化する")
    void test_登録が実DBへ反映される() throws Exception {
        String email = "register@example.com";

        JsonNode registered = register(email);

        String userId = registered.at("/user/id").asText();
        assertThat(userId).startsWith("user_");
        assertThat(registered.at("/user/isGuest").asBoolean()).isFalse();
        // 未確認のままログイン・プレイできる（tech_auth.md §3）
        assertThat(registered.at("/user/emailVerified").asBoolean()).isFalse();

        // 保存するのは bcrypt（strength 12）のハッシュだけで、生パスワードは残さない（§9）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, userId))
                .startsWith("$2a$12$").isNotEqualTo(PASSWORD);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, userId)).isEqualTo(email);

        // 確認メール用のトークンが未使用で1件残る（§10 手順6）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_verification_tokens"
                        + " WHERE user_id = ? AND purpose = 'verify_email' AND used = FALSE",
                Integer.class, userId)).isEqualTo(1);

        // プレイヤーの初期化まで同じトランザクションで終える（§10 手順7）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM players WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(1);
    }

    /**
     * 重複は 409 で止まり、2人目のユーザーもプレイヤーも確認トークンも残さない。
     *
     * <p>分岐: tech_auth/account.md §11 #8
     */
    @Test
    @DisplayName("同じメールでの再登録は 409 になり、ユーザーもプレイヤーも増やさない")
    void test_メール重複の登録は409で何も作らない() throws Exception {
        String email = "duplicate@example.com";
        String userId = register(email).at("/user/id").asText();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialBody(email, "anotherpass123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_TAKEN"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM players WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_verification_tokens WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(1);
    }

    /**
     * ログインが `last_login_at` を進め、**既存のリフレッシュトークンを失効させない**ことを
     * 実DBの行で確認する（複数端末の同時ログインを許容する。§12 手順6・手順7）。
     *
     * <p>分岐: tech_auth/account.md §13 #10
     */
    @Test
    @DisplayName("POST /api/auth/login は last_login_at を進め、既存のリフレッシュトークンを残す")
    void test_ログインが実DBへ反映される() throws Exception {
        String email = "login@example.com";
        JsonNode registered = register(email);
        String userId = registered.at("/user/id").asText();
        Timestamp registeredAt = lastLoginAt(userId);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId))
                .andReturn().getResponse().getContentAsString();
        JsonNode loggedIn = jsonMapper.readTree(body);

        assertThat(lastLoginAt(userId)).isAfter(registeredAt);

        // 登録で発行した1本と、ログインで発行した1本が、どちらも有効なまま並ぶ
        assertThat(loggedIn.at("/refreshToken").asText())
                .isNotEqualTo(registered.at("/refreshToken").asText());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked = FALSE",
                Integer.class, userId)).isEqualTo(2);
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

        assertThat(jsonMapper.readTree(refreshed).at("/refreshToken").asText())
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
                // ログ突合のためリクエストIDを常に返す（tech_api/common.md「共通ヘッダ」）
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

    /**
     * ログアウトは認証必須（tech_auth/account.md §9・§14 手順1）。アクセストークンが無効なら
     * AUTH_ コードで 401 を返し、ボディで指したリフレッシュトークンを失効させない。
     * 期限切れトークンの分岐は {@code JwtAuthenticationFilterTest} が持つ。
     *
     * <p>Security フィルタは DispatcherServlet より前に拒否するため、**エンドポイントが未実装でも
     * 401 は返る**。空振りを防ぐため、末尾に「認証を通せば同じリクエストが成功して失効する」
     * 対照実験を置く。
     *
     * <p>分岐: tech_auth/account.md §15 #2
     */
    @Test
    @DisplayName("アクセストークンが無効なログアウトは 401 になり、リフレッシュトークンを失効させない")
    void test_無効なアクセストークンのログアウトはトークンを失効させない() throws Exception {
        JsonNode guest = createGuest();
        String userId = guest.at("/user/id").asText();
        String body = refreshBody(guest.at("/refreshToken").asText());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_HEADER_MISSING"));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Token abc")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_FORMAT"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer broken.token.value")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked = FALSE",
                Integer.class, userId)).isEqualTo(1);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + guest.at("/accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked = FALSE",
                Integer.class, userId)).isZero();
    }

    /**
     * アカウント移行は認証必須（link.md §18 入口条件）。アクセストークンが無効なら AUTH_ コードで
     * 401 を返し、ユーザーを本登録化しない。Security フィルタは DispatcherServlet より前に拒否する
     * ため、**エンドポイントが未実装でも 401 は返る**。空振りを防ぐため、末尾に「認証を通せば同じ
     * リクエストが成功する」対照実験を置く。
     *
     * <p>分岐: tech_auth/link.md §19 #2
     */
    @Test
    @DisplayName("アクセストークンが無効な移行は 401 になり、ユーザーを本登録化しない")
    void test_無効なアクセストークンの移行はユーザーを変更しない() throws Exception {
        String email = "link-unauthorized@example.com";
        JsonNode guest = createGuest();
        String userId = guest.at("/user/id").asText();
        String body = credentialBody(email, PASSWORD);

        mockMvc.perform(post("/api/auth/link-account")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_HEADER_MISSING"));

        mockMvc.perform(post("/api/auth/link-account").header("Authorization", "Token abc")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_FORMAT"));

        mockMvc.perform(post("/api/auth/link-account")
                        .header("Authorization", "Bearer broken.token.value")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_guest FROM users WHERE id = ?", Boolean.class, userId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isZero();

        mockMvc.perform(post("/api/auth/link-account")
                        .header("Authorization", "Bearer " + guest.at("/accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_guest FROM users WHERE id = ?", Boolean.class, userId)).isFalse();
    }

    /**
     * 移行が「ユーザーの本登録化 + 確認トークン」を1つのトランザクションで確定し、**ゲームデータを
     * 作り直さない**ことを実DBの行で確認する（tech_auth.md §3）。手順ごとの分岐は
     * {@code AuthServiceImplTest} が持ち、ここでは連結と永続化を見る。
     *
     * <p>分岐: tech_auth/link.md §19 #20
     */
    @Test
    @DisplayName("POST /api/auth/link-account はゲストを本登録化し、プレイヤーを作り直さない")
    void test_アカウント移行が実DBへ反映される() throws Exception {
        String email = "link@example.com";
        JsonNode guest = createGuest();
        String userId = guest.at("/user/id").asText();
        String playerId = jdbcTemplate.queryForObject(
                "SELECT id FROM players WHERE user_id = ?", String.class, userId);

        String responseBody = mockMvc.perform(post("/api/auth/link-account")
                        .header("Authorization", "Bearer " + guest.at("/accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode linked = jsonMapper.readTree(responseBody);

        // ID は変えずに本登録化する（§18 手順8）
        assertThat(linked.at("/user/id").asText()).isEqualTo(userId);
        assertThat(linked.at("/user/isGuest").asBoolean()).isFalse();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE id = ? AND is_guest = FALSE"
                        + " AND email = ? AND email_verified = FALSE",
                Integer.class, userId, email)).isEqualTo(1);
        // 保存するのは bcrypt（strength 12）のハッシュだけで、生パスワードは残さない（§9）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, userId))
                .startsWith("$2a$12$").isNotEqualTo(PASSWORD);

        // 確認メール用のトークンが未使用で1件残る（§18 手順9）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_verification_tokens"
                        + " WHERE user_id = ? AND purpose = 'verify_email' AND used = FALSE",
                Integer.class, userId)).isEqualTo(1);

        // ゲームデータは作り直さない（同じ player 行がそのまま残る）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT id FROM players WHERE user_id = ?", String.class, userId))
                .isEqualTo(playerId);

        // 既存のリフレッシュトークンは失効させない（§18 手順10）。ゲスト作成時の1本＋移行の1本
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked = FALSE",
                Integer.class, userId)).isEqualTo(2);
    }

    /**
     * メール確認が {@code email_verified} と {@code used} を1つのトランザクションで確定することを
     * 実DBの行で確認する。**認証不要**で、ログイン状態を持たないブラウザからも通る（§20 入口条件）。
     *
     * <p>分岐: tech_auth/verify.md §21 #15
     */
    @Test
    @DisplayName("GET /api/auth/verify-email は認証不要で email_verified と used を確定する")
    void test_メール確認が実DBへ反映される() throws Exception {
        String email = "verify@example.com";
        String userId = register(email).at("/user/id").asText();
        String rawToken = "integration-verification-token";
        // 確認メールを送らないため生値を受け取れない。既知の生値のハッシュへ差し替える
        jdbcTemplate.update("UPDATE email_verification_tokens SET token_hash = ? WHERE user_id = ?",
                sha256Hex(rawToken), userId);

        mockMvc.perform(get("/api/auth/verify-email").param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT email_verified FROM users WHERE id = ?", Boolean.class, userId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT used FROM email_verification_tokens WHERE user_id = ?",
                Boolean.class, userId)).isTrue();
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
