package com.afkgame.web.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.afkgame.domain.service.auth.AuthResult;
import com.afkgame.domain.service.auth.AuthService;
import com.afkgame.web.filter.ApiExceptionHandler;

/**
 * {@link AuthApi} の単体テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §5（リクエスト/レスポンス例）、
 * docs/tech/detail/tech_auth/account.md §9〜§15（登録・ログイン・ログアウト）、
 * docs/tech/basic/tech_api/common.md §5.0（ボディのキーは camelCase）。
 *
 * <p>分岐観点: ゲスト作成 / リフレッシュ（いずれも同じ応答形式）。エラー系はサービス層が
 * {@code BusinessException} を投げ、{@code ApiExceptionHandler} が応答へ変換する。
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
 *
 * <p><b>アカウント移行・メール確認（移行 STEP 3-A-3）</b>: 仕様は
 * docs/tech/detail/tech_auth/link.md §18・docs/tech/detail/tech_auth/verify.md §20。本クラスは
 * 分岐一覧 link.md §19・verify.md §21 のうち **Bean Validation・HTTP ステータス・引数の受け渡し**が
 * 決める分岐を持つ。ペイロードの形（どちらか一方）・種別・重複・トークンの検証はサービス層が
 * 決めるため {@code AuthServiceImplTest}、認証必須の拒否（§19 #2）と実DBへの反映は
 * {@code AuthApiIntegrationTest} が持つ。
 *
 * <p><b>製造工程への申し送り（追加分）</b>:
 * <ul>
 *   <li>{@code AuthApi#linkAccount(@AuthenticationPrincipal User, @Valid @RequestBody
 *       LinkAccountResource)} → {@code AuthResource}。認証ユーザーは <b>ID ではなくユーザー自身</b>を
 *       サービスへ渡す（§18 手順4 が {@code is_guest} の現在値を見るため）</li>
 *   <li>{@code LinkAccountResource(String email, String password, String googleAuthCode)}:
 *       3つとも<b>必須にしない</b>（メール連携と Google連携のちょうど一方を受けるため。
 *       {@code @NotBlank} を付けると Google連携のボディが 422 で落ちる）。
 *       {@code email} は {@code @Email @Size(max = 254)}、{@code password} は
 *       {@code @Size(min = 8, max = 128)}（null は素通り。§18 手順5・§9「入力長」）。
 *       どちらも無い・両方あるの判定は 400 {@code AUTH_LINK_PAYLOAD_INVALID} なので**サービス層**
 *       が持つ（422 ではないため Bean Validation では表せない）</li>
 *   <li>{@code AuthApi#verifyEmail(String token)} → {@code StatusResource}
 *       （{@code GET /api/auth/verify-email?token=xxx}）。クエリ {@code token} は必須で、
 *       <b>未指定・空文字は 422 {@code VALIDATION_ERROR}</b>（§21 #2）。素の
 *       {@code @RequestParam} では未指定が 400 {@code HTTP_400} になるため、Resource へ束ねるか
 *       {@link ApiExceptionHandler} へハンドラを足すかは製造で決める</li>
 *   <li>{@code SpringSecurityConfig} の {@code PUBLIC_ENDPOINTS} へ
 *       {@code /api/auth/verify-email} を足す（認証不要・tech_api/common.md §5.0）。
 *       <b>link-account は足さない</b>（認証必須）</li>
 * </ul>
 *
 * <p><b>パスワード再設定（移行 STEP 3-A-3 セグメント②）</b>: 仕様は
 * docs/tech/detail/tech_auth/password_reset.md §22・§24。本クラスは分岐一覧 §23・§25 のうち
 * <b>Bean Validation・HTTP ステータス・引数の受け渡し</b>が決める分岐（§23 #1〜#4・§25 #1〜#4）を
 * 持つ。対象の有無・トークンの検証・失効はサービス層が決めるため {@code AuthServiceImplTest}、
 * 実DBへの反映は {@code AuthApiIntegrationTest} が持つ。
 *
 * <p><b>製造工程への申し送り（追加分）</b>:
 * <ul>
 *   <li>{@code AuthApi#requestPasswordReset(@Valid @RequestBody PasswordResetRequestResource)}
 *       → {@code StatusResource}（{@code POST /api/auth/password-reset/request}）。
 *       <b>対象の有無によらず 200</b> を返す（§22 出口条件。存否を推測させない）</li>
 *   <li>{@code AuthApi#resetPassword(@Valid @RequestBody PasswordResetConfirmResource)}
 *       → {@code StatusResource}（{@code POST /api/auth/password-reset/confirm}）。
 *       <b>トークンペアを応答へ載せない</b>（§24 手順10。ログイン画面へ戻す）</li>
 *   <li>{@code PasswordResetRequestResource(String email)}: {@code email} は
 *       {@code @NotBlank @Email @Size(max = 254)}（§22 手順1・account.md §9「入力長」）</li>
 *   <li>{@code PasswordResetConfirmResource(String token, String newPassword)}:
 *       {@code token} は {@code @NotBlank}、{@code newPassword} は
 *       {@code @NotBlank @Size(min = 8, max = 128)}（§24 手順1）。フィールド名は API の
 *       camelCase をそのまま使う（tech_api/common.md §5.0。Jackson の変換は不要）</li>
 *   <li>{@code SpringSecurityConfig} の {@code PUBLIC_ENDPOINTS} へ
 *       {@code /api/auth/password-reset/request} と {@code /api/auth/password-reset/confirm} を
 *       足す（どちらも認証不要・§22・§24 入口条件）</li>
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

    /**
     * アカウント移行後の認証結果。**ID は変わらない**ため {@code guest_} 接頭辞のまま
     * {@code isGuest} だけが false になる（link.md §18 手順8）。
     */
    private static AuthResult linkedResult() {
        User user = new User();
        user.setId("guest_550e8400");
        user.setDisplayName("冒険者");
        user.setEmail("user@example.com");
        user.setGuest(false);
        user.setEmailVerified(false);
        return new AuthResult(user, "access-token", "refresh-token");
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

    @Nested
    @DisplayName("POST /api/auth/link-account")
    class TestLinkAccount {

        private static final String PASSWORD = "securepass123";

        private static final String EMAIL = "user@example.com";

        /** アクセストークン検証後に {@code JwtAuthenticationFilter} が置く principal を再現する。 */
        private User authenticated(String userId) {
            User user = new User();
            user.setId(userId);
            user.setGuest(true);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, List.of()));
            return user;
        }

        /**
         * 手順1で特定した認証ユーザー<b>自身</b>をサービスへ渡す（IDだけを渡さない。手順4が
         * {@code is_guest} の現在値を見るため）。無効なアクセストークンを拒む側（#2）は
         * Security フィルタチェーンが要るため {@code AuthApiIntegrationTest} が持つ。
         *
         * <p>分岐: tech_auth/link.md §19 #1
         */
        @Test
        void test_有効なアクセストークンなら認証ユーザー自身をサービスへ渡す() throws Exception {
            User user = authenticated("guest_550e8400");
            when(authService.linkAccount(any(), any(), any(), any())).thenReturn(linkedResult());

            mockMvc.perform(post("/api/auth/link-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(EMAIL, PASSWORD)))
                    .andExpect(status().isOk());

            verify(authService).linkAccount(user, EMAIL, PASSWORD, null);
        }

        /**
         * 妥当な形式なら検証を通り、そのままサービスへ渡る（手順6へ進む）。
         *
         * <p>分岐: tech_auth/link.md §19 #11
         */
        @Test
        void test_妥当な形式のメールはサービスへ渡る() throws Exception {
            authenticated("guest_550e8400");
            when(authService.linkAccount(any(), any(), any(), any())).thenReturn(linkedResult());

            mockMvc.perform(post("/api/auth/link-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody("Link.User@Example.com", PASSWORD)))
                    .andExpect(status().isOk());

            // 正規化はサービス層の責務（§9）。Web層は受け取った表記のまま渡す
            verify(authService).linkAccount(any(), any(), any(), any());
        }

        /**
         * 形式違反は 422 で止め、ユーザーを変更しない（手順5）。
         *
         * <p>分岐: tech_auth/link.md §19 #12
         */
        @ParameterizedTest(name = "email={0}")
        @ValueSource(strings = {
            "user.example.com",  // @ を欠く
            "@example.com",      // ローカル部が無い
            "user@"              // ドメイン部が無い
        })
        void test_メール形式が不正なら422を返す(String email) throws Exception {
            authenticated("guest_550e8400");

            mockMvc.perform(post("/api/auth/link-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(email, PASSWORD)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).linkAccount(any(), any(), any(), any());
        }

        /**
         * 上限ちょうど（254文字）は受け付ける（RFC 5321。長さの正は §9「入力長」）。
         *
         * <p>分岐: tech_auth/link.md §19 #13
         */
        @Test
        void test_254文字ちょうどのメールは受け付ける() throws Exception {
            authenticated("guest_550e8400");
            when(authService.linkAccount(any(), any(), any(), any())).thenReturn(linkedResult());

            mockMvc.perform(post("/api/auth/link-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(emailOfLength(254), PASSWORD)))
                    .andExpect(status().isOk());
        }

        /**
         * 上限超過（255文字）は 422。
         *
         * <p>分岐: tech_auth/link.md §19 #14
         */
        @Test
        void test_255文字のメールは422を返す() throws Exception {
            authenticated("guest_550e8400");

            mockMvc.perform(post("/api/auth/link-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(emailOfLength(255), PASSWORD)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).linkAccount(any(), any(), any(), any());
        }

        /**
         * 許容範囲の両端（8文字・128文字ちょうど）は受け付ける（tech_auth.md §1「パスワード要件」）。
         *
         * <p>分岐: tech_auth/link.md §19 #15
         */
        @ParameterizedTest(name = "length={0}")
        @ValueSource(ints = {8, 128})
        void test_8文字以上128文字以下のパスワードは受け付ける(int length) throws Exception {
            authenticated("guest_550e8400");
            when(authService.linkAccount(any(), any(), any(), any())).thenReturn(linkedResult());

            mockMvc.perform(post("/api/auth/link-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(EMAIL, "a".repeat(length))))
                    .andExpect(status().isOk());
        }

        /**
         * 範囲外は 422。空文字（下限側）も上限超過も同じ分岐に含む（§19 #16）。
         *
         * <p>分岐: tech_auth/link.md §19 #16
         */
        @ParameterizedTest(name = "length={0}")
        @ValueSource(ints = {
            0,    // 空文字
            7,    // 下限の1つ手前
            129   // 上限の1つ先
        })
        void test_7文字以下または129文字以上のパスワードは422を返す(int length) throws Exception {
            authenticated("guest_550e8400");

            mockMvc.perform(post("/api/auth/link-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(EMAIL, "a".repeat(length))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).linkAccount(any(), any(), any(), any());
        }

        /**
         * 成功時の応答（§5）。トランザクションのコミットは {@code AuthServiceImplTest} と
         * {@code AuthApiIntegrationTest} が持ち、ここでは 200 と本文の形だけを見る。
         *
         * <p>分岐: tech_auth/link.md §19 #20
         */
        @Test
        void test_移行が成功すれば200でトークンペアとユーザー情報を返す() throws Exception {
            authenticated("guest_550e8400");
            when(authService.linkAccount(any(), any(), any(), any())).thenReturn(linkedResult());

            mockMvc.perform(post("/api/auth/link-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentialBody(EMAIL, PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    // ID は移行しても変わらない（§18 手順8）
                    .andExpect(jsonPath("$.user.id").value("guest_550e8400"))
                    .andExpect(jsonPath("$.user.email").value(EMAIL))
                    .andExpect(jsonPath("$.user.isGuest").value(false))
                    .andExpect(jsonPath("$.user.emailVerified").value(false));
        }
    }

    @Nested
    @DisplayName("GET /api/auth/verify-email")
    class TestVerifyEmail {

        private static final String RAW_TOKEN = "raw-verification-token";

        /**
         * クエリの生値をそのままサービスへ渡し（手順2へ進む）、成功なら
         * {@code {"status": "ok"}} を 200 で返す（§20 出口条件）。認証は要求しない。
         *
         * <p>分岐: tech_auth/verify.md §21 #1
         */
        @Test
        void test_tokenが指定されていればサービスへ渡る() throws Exception {
            mockMvc.perform(get("/api/auth/verify-email").param("token", RAW_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));

            verify(authService).verifyEmail(RAW_TOKEN);
        }

        /**
         * 必須違反は 422 で止め、サービスを呼ばない（手順1）。未指定と空文字は同じ分岐。
         *
         * <p>分岐: tech_auth/verify.md §21 #2
         */
        @Test
        void test_tokenが未指定または空文字なら422を返す() throws Exception {
            // 未指定
            mockMvc.perform(get("/api/auth/verify-email"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            // 空文字
            mockMvc.perform(get("/api/auth/verify-email").param("token", ""))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).verifyEmail(any());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/password-reset/request")
    class TestRequestPasswordReset {

        private static final String PATH = "/api/auth/password-reset/request";

        private static String requestBody(String email) {
            return "{\"email\":\"" + email + "\"}";
        }

        /**
         * 妥当な形式なら検証を通り、そのままサービスへ渡る（§22 手順2へ進む）。認証は要求しない。
         * 応答は対象の有無によらず 200 {@code {"status": "ok"}}（§22 出口条件）。
         *
         * <p>分岐: tech_auth/password_reset.md §23 #1
         */
        @Test
        void test_妥当な形式のメールはサービスへ渡る() throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody("user@example.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));

            verify(authService).requestPasswordReset("user@example.com");
        }

        /**
         * 形式違反は 422 で止め、サービスを呼ばない（§22 手順1）。必須違反（空文字）も同じ分岐。
         *
         * <p>分岐: tech_auth/password_reset.md §23 #2
         */
        @ParameterizedTest(name = "email={0}")
        @ValueSource(strings = {
            "user.example.com",  // @ を欠く
            "@example.com",      // ローカル部が無い
            "user@",             // ドメイン部が無い
            ""                   // 空文字（必須違反）
        })
        void test_メール形式が不正なら422を返す(String email) throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody(email)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).requestPasswordReset(any());
        }

        /**
         * 上限ちょうど（254文字）は受け付ける。上限の正は account.md §9「入力長」（RFC 5321）。
         *
         * <p>分岐: tech_auth/password_reset.md §23 #3
         */
        @Test
        void test_254文字ちょうどのメールは受け付ける() throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody(emailOfLength(254))))
                    .andExpect(status().isOk());
        }

        /**
         * 上限超過（255文字）は 422。
         *
         * <p>分岐: tech_auth/password_reset.md §23 #4
         */
        @Test
        void test_255文字のメールは422を返す() throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody(emailOfLength(255))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).requestPasswordReset(any());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/password-reset/confirm")
    class TestResetPassword {

        private static final String PATH = "/api/auth/password-reset/confirm";

        private static final String RAW_TOKEN = "raw-password-reset-token";

        private static final String NEW_PASSWORD = "newsecurepass456";

        private static String confirmBody(String token, String newPassword) {
            return "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}";
        }

        /**
         * メール本文のリンクに載った生値をそのままサービスへ渡し（§24 手順2へ進む）、成功なら
         * {@code {"status": "ok"}} を 200 で返す（§24 出口条件）。認証は要求しない。
         *
         * <p>分岐: tech_auth/password_reset.md §25 #1
         */
        @Test
        void test_tokenが指定されていればサービスへ渡る() throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmBody(RAW_TOKEN, NEW_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));

            verify(authService).resetPassword(RAW_TOKEN, NEW_PASSWORD);
        }

        /**
         * 必須違反は 422 で止め、サービスを呼ばない（§24 手順1）。未指定と空文字は同じ分岐。
         *
         * <p>分岐: tech_auth/password_reset.md §25 #2
         */
        @ParameterizedTest(name = "body={0}")
        @ValueSource(strings = {
            "{\"newPassword\":\"newsecurepass456\"}",                  // 未指定
            "{\"token\":\"\",\"newPassword\":\"newsecurepass456\"}"    // 空文字
        })
        void test_tokenが未指定または空文字なら422を返す(String body) throws Exception {
            mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).resetPassword(any(), any());
        }

        /**
         * 許容範囲の両端（8文字・128文字ちょうど）は受け付ける（tech_auth.md §1「パスワード要件」、
         * 長さの正は account.md §9「入力長」）。
         *
         * <p>分岐: tech_auth/password_reset.md §25 #3
         */
        @ParameterizedTest(name = "length={0}")
        @ValueSource(ints = {8, 128})
        void test_8文字以上128文字以下の新パスワードは受け付ける(int length) throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmBody(RAW_TOKEN, "a".repeat(length))))
                    .andExpect(status().isOk());
        }

        /**
         * 範囲外は 422。空文字（必須違反）と上限超過（129文字）を同じ分岐に含む。
         *
         * <p>分岐: tech_auth/password_reset.md §25 #4
         */
        @ParameterizedTest(name = "length={0}")
        @ValueSource(ints = {
            0,    // 空文字（必須違反）
            7,    // 下限の1つ手前
            129   // 上限の1つ外側
        })
        void test_7文字以下または129文字以上の新パスワードは422を返す(int length) throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmBody(RAW_TOKEN, "a".repeat(length))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            verify(authService, never()).resetPassword(any(), any());
        }
    }
}
