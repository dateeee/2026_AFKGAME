package com.afkgame.web.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.afkgame.domain.model.User;
import com.afkgame.domain.service.AuthResult;
import com.afkgame.domain.service.AuthService;
import com.afkgame.web.filter.ApiExceptionHandler;

/**
 * {@link AuthApi} の単体テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §5（リクエスト/レスポンス例）、
 * docs/tech/detail/tech_auth/account.md §9〜§15（登録・ログイン・ログアウト）、
 * docs/tech/basic/tech_api/common.md §5.0（ボディのキーは camelCase）。
 *
 * <p>分岐観点: ゲスト作成 / リフレッシュ（いずれも同じ応答形式）。エラー系はサービス層が
 * {@code AppException} を投げ、{@code ApiExceptionHandler} が応答へ変換する。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * ゲスト作成とリフレッシュには分岐マーカーを付けない。
 *
 * <p><b>登録・ログイン・ログアウト（移行 STEP 3-A-2）</b>: 本クラスは分岐一覧 §11・§13・§15 の
 * うち **Bean Validation・HTTP ステータス・エラーコード・引数の受け渡し**が決める分岐を持つ。
 * サービス層が決める分岐（重複判定・照合・失効）は {@code AuthServiceImplTest}、認証必須の拒否
 * （§15 #2）は Security フィルタチェーンが要るため {@code AuthApiIntegrationTest} が持つ。
 *
 * <p>Bean Validation 違反を 422 で受けるため {@link ApiExceptionHandler} を、ログアウトの
 * {@code @AuthenticationPrincipal} を解決するため {@link AuthenticationPrincipalArgumentResolver}
 * を standalone の MockMvc へ載せる（本番では前者は {@code @RestControllerAdvice}、後者は
 * Spring Security の引数リゾルバとして自動で効く）。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code AuthApi#register(@Valid @RequestBody RegisterResource)} → {@code AuthResource}</li>
 *   <li>{@code AuthApi#login(@Valid @RequestBody LoginResource)} → {@code AuthResource}</li>
 *   <li>{@code AuthApi#logout(@AuthenticationPrincipal User, @Valid @RequestBody LogoutResource)}
 *       → {@code StatusResource}（{@code {"status": "ok"}}）</li>
 *   <li>{@code RegisterResource(String email, String password)}:
 *       {@code email} は {@code @NotBlank @Email @Size(max = 254)}、
 *       {@code password} は {@code @NotBlank @Size(min = 8, max = 128)}（§10 手順1・§9「入力長」）</li>
 *   <li>{@code LoginResource(String email, String password)}: {@code email} は
 *       {@code @NotBlank @Email}、{@code password} は {@code @NotBlank}
 *       （**8文字以上はログインでは課さない**。§12 手順1）</li>
 *   <li>{@code LogoutResource(String refreshToken)}: {@code @NotBlank}</li>
 *   <li>{@code StatusResource(String status)}</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AuthApiTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthApi(authService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static AuthResult authResult() {
        User user = new User();
        user.setId("guest_550e8400");
        user.setDisplayName("冒険者");
        user.setGuest(true);
        user.setEmailVerified(false);
        return new AuthResult(user, "access-token", "refresh-token");
    }

    /** メール登録済みユーザーの認証結果（登録・ログインの応答）。 */
    private static AuthResult registeredResult() {
        User user = new User();
        user.setId("user_001");
        user.setEmail("user@example.com");
        user.setGuest(false);
        user.setEmailVerified(false);
        return new AuthResult(user, "access-token", "refresh-token");
    }

    private static String credentialBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** ローカル部の上限（RFC 5321）。{@code @Email} が長さも検査するため境界内に収める。 */
    private static final int MAX_LOCAL_PART_LENGTH = 64;

    /** ドメインラベルの上限（RFC 1035）。超えると {@code @Email} が形式違反として弾く。 */
    private static final int MAX_DOMAIN_LABEL_LENGTH = 63;

    /**
     * 全体がちょうど {@code length} 文字になるメールアドレスを組み立てる。
     *
     * <p>見たいのは長さの境界（§11 #3・#4）だけなので、**形式は妥当なまま**にする。
     * ローカル部を単純に伸ばすと上限64文字を超えて形式違反になり、長さの分岐へ到達しない。
     */
    private static String emailOfLength(int length) {
        String localPart = "a".repeat(MAX_LOCAL_PART_LENGTH);
        int domainLength = length - localPart.length() - 1;
        StringBuilder domain = new StringBuilder();
        while (domain.length() < domainLength) {
            if (domain.length() > 0) {
                domain.append('.');
            }
            domain.append("a".repeat(
                    Math.min(domainLength - domain.length(), MAX_DOMAIN_LABEL_LENGTH)));
        }
        return localPart + "@" + domain;
    }

    @Nested
    @DisplayName("POST /api/auth/guest")
    class TestCreateGuest {

        @Test
        void test_トークンペアとユーザー情報を返す() throws Exception {
            when(authService.createGuest()).thenReturn(authResult());

            mockMvc.perform(post("/api/auth/guest"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.user.id").value("guest_550e8400"))
                    .andExpect(jsonPath("$.user.displayName").value("冒険者"))
                    .andExpect(jsonPath("$.user.isGuest").value(true))
                    .andExpect(jsonPath("$.user.emailVerified").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class TestRefresh {

        @Test
        void test_受け取ったトークンでローテーションする() throws Exception {
            when(authService.refresh(any())).thenReturn(authResult());

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"old-refresh-token\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

            verify(authService).refresh("old-refresh-token");
        }
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class TestRegister {

        private static final String PASSWORD = "securepass123";

        /**
         * 妥当な形式なら検証を通り、そのままサービスへ渡る（手順2へ進む）。
         *
         * <p>分岐: tech_auth/account.md §11 #1
         */
        @Test
        void test_妥当な形式のメールはサービスへ渡る() throws Exception {
            when(authService.register(any(), any())).thenReturn(registeredResult());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody("user@example.com", PASSWORD)))
                    .andExpect(status().isOk());

            verify(authService).register("user@example.com", PASSWORD);
        }

        /**
         * 形式違反は 422 で止め、ユーザーを作らない（手順1）。
         *
         * <p>分岐: tech_auth/account.md §11 #2
         */
        @ParameterizedTest(name = "email={0}")
        @ValueSource(strings = {
            "user.example.com",  // @ を欠く
            "@example.com",      // ローカル部が無い
            "user@"              // ドメイン部が無い
        })
        void test_メール形式が不正なら422を返す(String email) throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(email, PASSWORD)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).register(any(), any());
        }

        /**
         * 上限ちょうど（254文字）は受け付ける。上限の正は §9「入力長」（RFC 5321）。
         *
         * <p>分岐: tech_auth/account.md §11 #3
         */
        @Test
        void test_254文字ちょうどのメールは受け付ける() throws Exception {
            when(authService.register(any(), any())).thenReturn(registeredResult());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(emailOfLength(254), PASSWORD)))
                    .andExpect(status().isOk());
        }

        /**
         * 上限超過（255文字）は 422。
         *
         * <p>分岐: tech_auth/account.md §11 #4
         */
        @Test
        void test_255文字のメールは422を返す() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(emailOfLength(255), PASSWORD)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).register(any(), any());
        }

        /**
         * 許容範囲の両端（8文字・128文字ちょうど）は受け付ける（tech_auth.md §1「パスワード要件」、
         * 長さの正は §9「入力長」）。
         *
         * <p>分岐: tech_auth/account.md §11 #5
         */
        @ParameterizedTest(name = "length={0}")
        @ValueSource(ints = {8, 128})
        void test_8文字以上128文字以下のパスワードは受け付ける(int length) throws Exception {
            when(authService.register(any(), any())).thenReturn(registeredResult());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody("user@example.com", "a".repeat(length))))
                    .andExpect(status().isOk());
        }

        /**
         * 下限未満は 422。空文字も同じ分岐に含む。
         *
         * <p>分岐: tech_auth/account.md §11 #6
         */
        @ParameterizedTest(name = "password=\"{0}\"")
        @ValueSource(strings = {
            "1234567",  // 下限の1つ手前
            ""          // 空文字
        })
        void test_7文字以下のパスワードは422を返す(String password) throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody("user@example.com", password)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).register(any(), any());
        }

        /**
         * 上限超過（129文字）は 422。ハッシュ化のコストを入力側で頭打ちにする（§9「入力長」）。
         *
         * <p>分岐: tech_auth/account.md §11 #14
         */
        @Test
        void test_129文字以上のパスワードは422を返す() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody("user@example.com", "a".repeat(129))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).register(any(), any());
        }

        /**
         * 成功時の応答（§5「POST /api/auth/register」）。サービス層のコミットは
         * {@code AuthServiceImplTest} が持ち、ここでは 200 と本文の形だけを見る。
         *
         * <p>分岐: tech_auth/account.md §11 #10
         */
        @Test
        void test_登録が成功すれば200でトークンペアとユーザー情報を返す() throws Exception {
            when(authService.register(any(), any())).thenReturn(registeredResult());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody("user@example.com", PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.user.id").value("user_001"))
                    .andExpect(jsonPath("$.user.email").value("user@example.com"))
                    .andExpect(jsonPath("$.user.isGuest").value(false))
                    .andExpect(jsonPath("$.user.emailVerified").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class TestLogin {

        private static final String PASSWORD = "securepass123";

        /**
         * <p>分岐: tech_auth/account.md §13 #1
         */
        @Test
        void test_妥当な形式のメールはサービスへ渡る() throws Exception {
            when(authService.login(any(), any())).thenReturn(registeredResult());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody("user@example.com", PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"));

            verify(authService).login("user@example.com", PASSWORD);
        }

        /**
         * <p>分岐: tech_auth/account.md §13 #2
         */
        @ParameterizedTest(name = "email={0}")
        @ValueSource(strings = {
            "user.example.com",  // @ を欠く
            "@example.com",      // ローカル部が無い
            "user@"              // ドメイン部が無い
        })
        void test_メール形式が不正なら422を返す(String email) throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(email, PASSWORD)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).login(any(), any());
        }

        /**
         * 空でない値があれば手順2へ進む。**登録時の「8文字以上」はログインでは課さない**ため、
         * 要件変更より前に作られた短いパスワードもそのままサービスへ渡る（§12 手順1）。
         *
         * <p>分岐: tech_auth/account.md §13 #3
         */
        @Test
        void test_8文字未満のパスワードもサービスへ渡る() throws Exception {
            when(authService.login(any(), any())).thenReturn(registeredResult());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody("user@example.com", "old123")))
                    .andExpect(status().isOk());

            verify(authService).login("user@example.com", "old123");
        }

        /**
         * <p>分岐: tech_auth/account.md §13 #4
         */
        @ParameterizedTest(name = "body={0}")
        @ValueSource(strings = {
            "{\"email\":\"user@example.com\",\"password\":\"\"}",  // 空文字
            "{\"email\":\"user@example.com\"}"                     // 未指定
        })
        void test_パスワードが未指定または空なら422を返す(String body) throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).login(any(), any());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout")
    class TestLogout {

        private static final String BODY = "{\"refreshToken\":\"raw-refresh-token\"}";

        /** アクセストークン検証後に {@code JwtAuthenticationFilter} が置く principal を再現する。 */
        private void authenticated(String userId) {
            User user = new User();
            user.setId(userId);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, List.of()));
        }

        /**
         * 手順1で特定した認証ユーザーのIDを、そのままサービスへ渡す。
         * 無効なアクセストークンを拒む側（#2）は Security フィルタチェーンが要るため
         * {@code AuthApiIntegrationTest} が持つ。
         *
         * <p>分岐: tech_auth/account.md §15 #1
         */
        @Test
        void test_有効なアクセストークンなら認証ユーザーのIDでログアウトする() throws Exception {
            authenticated("user_001");

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isOk());

            verify(authService).logout("user_001", "raw-refresh-token");
        }

        /**
         * ボディに {@code refreshToken} があれば手順3へ進み、成功応答を返す（§14 出口条件）。
         *
         * <p>分岐: tech_auth/account.md §15 #3
         */
        @Test
        void test_refreshTokenを受け取り成功応答を返す() throws Exception {
            authenticated("user_001");

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));
        }

        /**
         * <p>分岐: tech_auth/account.md §15 #4
         */
        @ParameterizedTest(name = "body={0}")
        @ValueSource(strings = {
            "{\"refreshToken\":\"\"}",  // 空文字
            "{}"                        // 未指定
        })
        void test_refreshTokenが未指定または空なら422を返す(String body) throws Exception {
            authenticated("user_001");

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).logout(any(), any());
        }
    }
}
