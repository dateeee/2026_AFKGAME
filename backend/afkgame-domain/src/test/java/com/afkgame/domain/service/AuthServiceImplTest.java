package com.afkgame.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.terasoluna.gfw.common.exception.BusinessException;

import com.afkgame.domain.model.EmailVerificationToken;
import com.afkgame.domain.model.RefreshToken;
import com.afkgame.domain.model.User;
import com.afkgame.domain.repository.EmailVerificationTokenRepository;
import com.afkgame.domain.repository.RefreshTokenRepository;
import com.afkgame.domain.repository.UserRepository;
import com.afkgame.env.config.AuthSettings;

/**
 * {@link AuthServiceImpl} の単体テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §1（期限・ローテーション）・§3「ゲストプレイ」・
 * §4「リフレッシュトークン」（再利用検知で全トークン失効）、
 * docs/tech/basic/tech_error_handling.md「AUTH_ コード一覧」。
 *
 * <p>分岐観点: リフレッシュの 正常 / 該当なし / revoked済み（再利用検知）/ 期限切れ /
 * ユーザー不在、および認証ユーザー取得の 存在する / しない。
 * これらは骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 *
 * <p>ゲスト作成は tech_auth.md §8.2「処理フロー」のトランザクション境界（手順1・7・8）を担うため、
 * §8.3 の #11・#12 に対応するテストだけマーカーを持つ。手順2〜6（#1・#2・#5・#7〜#9）は
 * {@link PlayerInitializationService} 側の責務で、{@code PlayerInitializationServiceImplTest} が持つ。
 *
 * <p><b>登録・ログイン・ログアウト（移行 STEP 3-A-2）</b>: 仕様は
 * docs/tech/detail/tech_auth/account.md §9〜§15。分岐一覧 §11（登録）・§13（ログイン）・
 * §15（ログアウト）のうち、サービス層が決める分岐を本クラスが持つ。Bean Validation と
 * HTTP ステータスは {@code AuthApiTest}、認証必須の拒否（§15 #2）は
 * {@code AuthApiIntegrationTest} が持つ。
 *
 * <p>分岐観点（追加分）: 登録の メール重複なし / 重複あり / 挿入時のUNIQUE違反 / 全手順成功 /
 * 途中失敗 / 確認メール送信の成否、ログインの ユーザー有無・ゲスト / パスワード設定の有無 /
 * 照合の一致・不一致 / メール確認状態、ログアウトの トークンの存在 / 持ち主 / 失効状態 / 有効期限。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code AuthService#register(String email, String rawPassword)} → {@link AuthResult}</li>
 *   <li>{@code AuthService#login(String email, String rawPassword)} → {@link AuthResult}</li>
 *   <li>{@code AuthService#logout(String userId, String rawRefreshToken)} → {@code void}</li>
 *   <li>コンストラクタへ {@link PasswordEncoder}・{@link EmailVerificationTokenRepository}・
 *       {@code VerificationMailSender} を追加（いずれもコンストラクタ注入）</li>
 *   <li>{@code UserRepository#findByEmail(String)} → {@link User}（不在は null）、
 *       {@code UserRepository#updateLastLoginAt(String id, Instant lastLoginAt)}</li>
 *   <li>{@code EmailVerificationToken}（{@code id}・{@code userId}・{@code tokenHash}・
 *       {@code purpose}・{@code expiresAt}・{@code used}・{@code createdAt}。列定義は
 *       docs/tech/basic/tech_db/auth.md §3）と {@code EmailVerificationTokenRepository#save}</li>
 *   <li>{@code VerificationMailSender#send(User user, String rawToken)}: 確認メールの送信境界。
 *       「コミット後・トランザクションの外」（§10 手順9）を満たす仕組みも、送信失敗を WARN に
 *       とどめる責務も実装側が持つ（ISSUE-702 の是正で {@code AuthService} から移した）。
 *       送信手段（SMTP設定・本文・再送）は verify-email の詳細設計（STEP 3-A-3）で確定する</li>
 * </ul>
 *
 * <p>登録・ログインが受け取るメールは §9「メールの正規化」に従って前後の空白除去と小文字化を
 * 経てから検索・保存される。正規化そのものの分岐は §11 #15・#16 と §13 #14・#15 が持つ。
 *
 * <p><b>アカウント移行・メール確認（移行 STEP 3-A-3）</b>: 仕様は
 * docs/tech/detail/tech_auth/link.md §18・docs/tech/detail/tech_auth/verify.md §20。
 * 分岐一覧 link.md §19（移行）・verify.md §21（確認）のうち、サービス層が決める分岐を本クラスが持つ。
 * Bean Validation と HTTP ステータスは {@code AuthApiTest}、認証必須の拒否（§19 #2）と実DBへの
 * 反映（§19 #20・§21 #15）は {@code AuthApiIntegrationTest} が持つ。
 *
 * <p>分岐観点（追加分）: 移行の ペイロードの形（メール連携 / Google連携 / 欠落 / 両方）/
 * Google設定の有無 / アカウント種別 / メール重複（事前確認・更新時の制約違反）/ 全手順成功 /
 * 途中失敗 / 確認メール送信、メール確認の トークンの存在 / 用途 / 使用状態 / 有効期限 /
 * 対象ユーザーの有無 / 確認状態 / 全手順成功 / 途中失敗。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code AuthService#linkAccount(User user, String email, String rawPassword,
 *       String googleAuthCode)} → {@link AuthResult}。手順1で特定済みの認証ユーザー
 *       <b>そのもの</b>を受け取る（{@code is_guest} はトークンではなくユーザーの現在値を見る・§18 末尾。
 *       {@code userId} だけを受けて引き直すと、テストの無い「ユーザー不在」分岐が増える）</li>
 *   <li>{@code AuthService#verifyEmail(String rawToken)} → {@code void}（成功は例外を投げないこと。
 *       応答 {@code {"status": "ok"}} は Web 層が組む）</li>
 *   <li>{@code UserRepository#updateLinkedAccount(User user)}: {@code email}・
 *       {@code password_hash}・{@code is_guest}・{@code email_verified}・{@code last_login_at} を
 *       更新する（{@code id}・{@code display_name}・{@code created_at} は変えない・§18 手順8）</li>
 *   <li>{@code UserRepository#updateEmailVerified(String id, boolean emailVerified)}（§20 手順7）</li>
 *   <li>{@code EmailVerificationTokenRepository#findByTokenHash(String tokenHash)} →
 *       {@link EmailVerificationToken}（不在は null）、
 *       {@code EmailVerificationTokenRepository#updateUsedById(Integer id)}（§20 手順2・8）</li>
 *   <li>{@link AuthSettings} へ {@code String googleClientId} を追加（末尾のコンポーネント。
 *       未設定は null または空文字）。プロパティキーは {@code afkgame.auth.google.client.id}、
 *       環境変数は {@code GOOGLE_CLIENT_ID}（tech_operations.md §12.2）。
 *       {@code AfkgameSettingsConfig}・{@code afkgame.properties}・{@code JwtServiceImplTest} の
 *       組み立ても同時に直す</li>
 * </ul>
 *
 * <p>移行は<b>ゲームデータを作り直さない</b>（tech_auth.md §3）。{@link PlayerInitializationService}
 * を呼ばないことを §19 #20 のテストで固定する。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final AuthSettings AUTH_SETTINGS = new AuthSettings(
            "afkgame-test-secret-value-32bytes-or-longer",
            Duration.ofMinutes(30),
            Duration.ofDays(30),
            12, 8, 128,
            Duration.ofDays(90),
            Duration.ofHours(24),
            Duration.ofHours(1),
            null);

    /** {@code GOOGLE_CLIENT_ID} が設定済みの構成（link.md §19 #8）。ほかの値は既定と同じ。 */
    private static final AuthSettings AUTH_SETTINGS_WITH_GOOGLE = new AuthSettings(
            "afkgame-test-secret-value-32bytes-or-longer",
            Duration.ofMinutes(30),
            Duration.ofDays(30),
            12, 8, 128,
            Duration.ofDays(90),
            Duration.ofHours(24),
            Duration.ofHours(1),
            "afkgame.apps.googleusercontent.com");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PlayerInitializationService playerInitializationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private VerificationMailSender verificationMailSender;

    private AuthService authService;

    /**
     * 時刻の供給元。JJWT の期限判定は実時間で行われるため、実時間のクロックを渡す。
     * 発行時刻を1回だけ取ることの検証は、同一レコードの2列の関係（下記 expires_at − created_at）で行う。
     */
    private static final Clock CLOCK = Clock.systemUTC();

    private AuthService authService() {
        if (authService == null) {
            authService = authService(AUTH_SETTINGS, CLOCK);
        }
        return authService;
    }

    /**
     * 設定・時刻を指定してサービスを組み立てる。
     *
     * <p>Google設定の有無（link.md §19 #7・#8）と、期限の境界（verify.md §21 #9・#10。
     * {@code expires_at} が現在時刻ちょうどの経路は実時間のクロックでは作れない）で使う。
     */
    private AuthService authService(AuthSettings settings, Clock clock) {
        return new AuthServiceImpl(userRepository, refreshTokenRepository,
                new JwtServiceImpl(settings, clock), settings,
                playerInitializationService, clock, passwordEncoder,
                emailVerificationTokenRepository, verificationMailSender);
    }

    /**
     * 業務例外が載せたエラーコードを取り出す。
     *
     * <p>HTTP ステータスは例外が持たず Web 層の対応表が決めるため、サービス層の検証はコードだけを見る
     * （規約 exception.md §4 #4）。ステータスとの対応は {@code ErrorCatalogTest} と
     * {@code scripts/check_error_codes.py} が担保する。
     */
    private static String codeOf(Throwable e) {
        return ((BusinessException) e).getResultMessages().getList().get(0).getCode();
    }

    /** 有効なリフレッシュトークンのレコードを組み立てる。 */
    private static RefreshToken storedToken(String tokenHash) {
        RefreshToken token = new RefreshToken();
        token.setId(1);
        token.setUserId("guest_001");
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        token.setRevoked(false);
        token.setCreatedAt(Instant.now());
        return token;
    }

    private static User storedUser() {
        User user = new User();
        user.setId("guest_001");
        user.setDisplayName("冒険者");
        user.setGuest(true);
        return user;
    }

    @Nested
    @DisplayName("ゲストアカウント作成")
    class TestCreateGuest {

        @Test
        void test_ゲストユーザーとトークンペアを発行する() {
            AuthResult result = authService().createGuest();

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            // ID は guest_<UUID>（tech_auth.md §2）
            assertThat(saved.getValue().getId()).startsWith("guest_");
            assertThat(saved.getValue().isGuest()).isTrue();
            assertThat(saved.getValue().isEmailVerified()).isFalse();
            assertThat(saved.getValue().getEmail()).isNull();
            assertThat(saved.getValue().getCreatedAt()).isNotNull();
            assertThat(saved.getValue().getLastLoginAt()).isNotNull();

            assertThat(result.user().getId()).isEqualTo(saved.getValue().getId());
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
        }

        /**
         * 手順1〜7がすべて成功する経路。手順2〜6の中身は
         * {@code PlayerInitializationServiceImplTest} が持ち、ここでは順序と委譲だけを見る。
         *
         * <p>分岐: tech_auth.md #11
         */
        @Test
        void test_ユーザー作成後にプレイヤー初期化を行いトークンペアを返す() {
            AuthResult result = authService().createGuest();

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());

            // 手順1（ユーザー）→ 手順2〜6（初期化）→ 手順7（トークン）の順で進む
            InOrder inOrder = inOrder(userRepository, playerInitializationService, refreshTokenRepository);
            inOrder.verify(userRepository).save(any(User.class));
            inOrder.verify(playerInitializationService).initialize(saved.getValue().getId());
            inOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));

            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
        }

        /**
         * 途中で失敗した場合。ロールバック自体は {@code @Transactional} が行うため、
         * ここでは「例外を握りつぶさずに伝播させる（＝ロールバックが起きる）」ことと、
         * トークンを発行しないことを見る。DBへ何も残らないことの検証は統合テストが持つ。
         *
         * <p>分岐: tech_auth.md #12
         */
        @Test
        void test_初期化に失敗したらトークンを発行せず例外を伝播する() {
            doThrow(new DuplicateKeyException("uq_players_user_id"))
                    .when(playerInitializationService).initialize(any());

            assertThatThrownBy(() -> authService().createGuest())
                    .isInstanceOf(DuplicateKeyException.class);

            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void test_リフレッシュトークンは生値を保存せずハッシュを保存する() {
            AuthResult result = authService().createGuest();

            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(saved.capture());
            assertThat(saved.getValue().getTokenHash())
                    .isNotEqualTo(result.refreshToken())
                    .hasSize(64);
            // 生値は48バイトを Base64URL（パディングなし）で表した64文字（§9）。
            // ハッシュと桁数が同じため、生値側も明示して取り違えを防ぐ
            assertThat(result.refreshToken()).hasSize(64);
            assertThat(saved.getValue().isRevoked()).isFalse();
            // 有効期限は30日（tech_auth.md §1）。時刻を2回取ると誤差が乗るため、
            // expires_at − created_at はちょうど有効期限であること
            assertThat(Duration.between(saved.getValue().getCreatedAt(),
                    saved.getValue().getExpiresAt())).isEqualTo(Duration.ofDays(30));
        }
    }

    @Nested
    @DisplayName("リフレッシュ")
    class TestRefresh {

        /** 生トークンを発行し、それに対応する保存済みレコードを Repository に仕込む。 */
        private String issueAndStore() {
            AuthResult issued = authService().createGuest();
            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(saved.capture());
            when(refreshTokenRepository.findByTokenHash(saved.getValue().getTokenHash()))
                    .thenReturn(storedToken(saved.getValue().getTokenHash()));
            return issued.refreshToken();
        }

        @Test
        void test_正常時は旧トークンを失効させ新しいペアを返す() {
            String rawToken = issueAndStore();
            when(refreshTokenRepository.updateRevokedById(1)).thenReturn(1);
            when(userRepository.findById("guest_001")).thenReturn(storedUser());

            AuthResult result = authService().refresh(rawToken);

            verify(refreshTokenRepository).updateRevokedById(1);
            assertThat(result.refreshToken()).isNotEqualTo(rawToken);
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.user().getId()).isEqualTo("guest_001");
        }

        @Test
        void test_該当レコードが無ければAUTH_REFRESH_INVALIDになる() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(null);

            assertThatThrownBy(() -> authService().refresh("unknown-token"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_REFRESH_INVALID");
        }

        @Test
        void test_revoked済みの再利用は全トークンを失効させる() {
            RefreshToken revoked = storedToken("dummy-hash");
            revoked.setRevoked(true);
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(revoked);

            assertThatThrownBy(() -> authService().refresh("reused-token"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_REFRESH_INVALID");

            verify(refreshTokenRepository).updateRevokedByUserId("guest_001");
            verify(refreshTokenRepository, never()).updateRevokedById(any());
        }

        /**
         * 同じ生トークンで2本のリクエストが並走した場合。読んだ時点では未失効でも、失効更新が
         * 0件なら他方が先にローテーションを済ませている＝再利用であり、revoked済みを読んだ場合
         * （上のテスト）と同じ全失効経路へ寄せる（tech_auth.md §4「不正検知」）。
         *
         * <p>READ COMMITTED では両方が失効判定を通過するため、判定ではなく更新件数で勝者を決める。
         */
        @Test
        void test_失効更新が0件なら再利用として全トークンを失効させる() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(storedToken("dummy-hash"));
            when(refreshTokenRepository.updateRevokedById(1)).thenReturn(0);

            assertThatThrownBy(() -> authService().refresh("raced-token"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_REFRESH_INVALID");

            verify(refreshTokenRepository).updateRevokedByUserId("guest_001");
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void test_期限切れはAUTH_REFRESH_INVALIDになる() {
            RefreshToken expired = storedToken("dummy-hash");
            expired.setExpiresAt(Instant.now().minus(Duration.ofSeconds(1)));
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(expired);

            assertThatThrownBy(() -> authService().refresh("expired-token"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_REFRESH_INVALID");

            verify(refreshTokenRepository, never()).updateRevokedByUserId(any());
        }

        @Test
        void test_トークンは正当でもユーザーが居なければAUTH_REFRESH_INVALIDになる() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(storedToken("dummy-hash"));
            when(refreshTokenRepository.updateRevokedById(1)).thenReturn(1);
            when(userRepository.findById("guest_001")).thenReturn(null);

            assertThatThrownBy(() -> authService().refresh("orphan-token"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_REFRESH_INVALID");
        }
    }

    @Nested
    @DisplayName("認証ユーザーの取得")
    class TestFindAuthenticatedUser {

        @Test
        void test_存在すればユーザーを返す() {
            when(userRepository.findById("guest_001")).thenReturn(storedUser());

            assertThat(authService().findAuthenticatedUser("guest_001").getId()).isEqualTo("guest_001");
        }

        @Test
        void test_存在しなければAUTH_USER_NOT_FOUNDになる() {
            when(userRepository.findById("guest_404")).thenReturn(null);

            assertThatThrownBy(() -> authService().findAuthenticatedUser("guest_404"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_USER_NOT_FOUND");
        }
    }

    /** 登録・ログインで使う値。生のパスワードとハッシュはテスト全体で使い回す。 */
    private static final String EMAIL = "user@example.com";

    private static final String PASSWORD = "securepass123";

    private static final String HASHED = "$2a$12$dummyhashvalueforunittestonly";

    @Nested
    @DisplayName("アカウント登録")
    class TestRegister {

        /** 既に同じメールを持つ行（ゲスト・Google連携のみでも扱いは同じ）。 */
        private User existing() {
            User user = new User();
            user.setId("user_existing");
            user.setEmail(EMAIL);
            return user;
        }

        /**
         * 手順2でメールが空いていれば、ユーザー作成（手順3〜4）へ進む。
         *
         * <p>分岐: tech_auth/account.md §11 #7
         */
        @Test
        void test_同じメールのユーザーが無ければ登録を続行する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);

            AuthResult result = authService().register(EMAIL, PASSWORD);

            verify(userRepository).save(any(User.class));
            assertThat(result.user().getEmail()).isEqualTo(EMAIL);
        }

        /**
         * 相手がゲスト・Google連携のみのアカウントでも同じ扱いで、行を1つも作らない。
         *
         * <p>分岐: tech_auth/account.md §11 #8
         */
        @Test
        void test_メールが登録済みならAUTH_EMAIL_TAKENで何も作らない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(existing());

            assertThatThrownBy(() -> authService().register(EMAIL, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_EMAIL_TAKEN");

            verify(userRepository, never()).save(any());
            verify(playerInitializationService, never()).initialize(any());
            verify(emailVerificationTokenRepository, never()).save(any());
            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * 手順2を通過した後に同時登録で {@code uq_users_email} 違反が起きる経路。
         * ロールバック自体は {@code @Transactional} が行うため、ここでは 409 への変換と、
         * 後続のトークン発行を行わないことを見る。
         *
         * <p>分岐: tech_auth/account.md §11 #9
         */
        @Test
        void test_挿入時のメール重複違反もAUTH_EMAIL_TAKENになる() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);
            doThrow(new DuplicateKeyException("uq_users_email")).when(userRepository).save(any());

            assertThatThrownBy(() -> authService().register(EMAIL, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_EMAIL_TAKEN");

            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * {@code uq_users_email} 以外の一意制約違反は業務例外へ写像しない。§11 #9 が扱うのは
         * メール重複だけで、それ以外は「予期しないエラー」としてそのまま伝播させる
         * （coding_standards_backend/exception.md の3分類 ③）。原因と表示が食い違うと
         * 切り分けができなくなるため、link-account が {@code google_id} を設定するより前に絞る。
         */
        @Test
        void test_メール以外の一意制約違反は変換せず伝播する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);
            doThrow(new DuplicateKeyException("uq_users_google_id")).when(userRepository).save(any());

            assertThatThrownBy(() -> authService().register(EMAIL, PASSWORD))
                    .isInstanceOf(DuplicateKeyException.class);

            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * 受け取った表記の大小・前後の空白を落とした値で重複を確認する（§9「メールの正規化」）。
         *
         * <p>分岐: tech_auth/account.md §11 #15
         */
        @Test
        void test_大小だけが異なる表記は正規化後に一致して409になる() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(existing());

            assertThatThrownBy(() -> authService().register("  User@Example.COM  ", PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_EMAIL_TAKEN");

            // 検索キーは受け取った表記ではなく正規化後の値
            verify(userRepository).findByEmail(EMAIL);
            verify(userRepository, never()).save(any());
        }

        /**
         * 正規化しても既存と一致しなければ登録を続行する。DBへ渡るのは正規化後の値なので、
         * {@code uq_users_email} がそのまま大小違いの重複を捕まえる（§9）。
         *
         * <p>分岐: tech_auth/account.md §11 #16
         */
        @Test
        void test_一致しなければ正規化後の値で登録する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);

            AuthResult result = authService().register("  User@Example.COM  ", PASSWORD);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
            assertThat(result.user().getEmail()).isEqualTo(EMAIL);
        }

        /**
         * 手順3〜7がすべて成功する経路。手順5（初期化）の中身は
         * {@code PlayerInitializationServiceImplTest} が持ち、ここでは順序と委譲だけを見る。
         *
         * <p>分岐: tech_auth/account.md §11 #10
         */
        @Test
        void test_手順3から7を順に実行しトークンペアを返す() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);

            AuthResult result = authService().register(EMAIL, PASSWORD);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            // ID は user_<UUID>。表示名は列の既定値に任せ、google_id は NULL（§10 手順4）。
            // 列の既定値はアプリ側（Entity フィールドの初期値）で付与する（tech_db.md §4-2）ため、
            // register が設定しない結果は null ではなく tech_db/auth.md §1 の既定値になる
            assertThat(saved.getValue().getId()).startsWith("user_");
            assertThat(saved.getValue().isGuest()).isFalse();
            assertThat(saved.getValue().isEmailVerified()).isFalse();
            assertThat(saved.getValue().getDisplayName()).isEqualTo("冒険者");
            assertThat(saved.getValue().getGoogleId()).isNull();
            assertThat(saved.getValue().getCreatedAt()).isNotNull();
            assertThat(saved.getValue().getLastLoginAt()).isNotNull();

            InOrder inOrder = inOrder(userRepository, playerInitializationService,
                    emailVerificationTokenRepository, refreshTokenRepository);
            inOrder.verify(userRepository).save(any(User.class));
            inOrder.verify(playerInitializationService).initialize(saved.getValue().getId());
            inOrder.verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
            inOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));

            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
        }

        /**
         * 同じ成功経路が残す値。手順3（bcrypt）と手順6（確認トークン）はいずれも生値を保存しない。
         *
         * <p>分岐: tech_auth/account.md §11 #10
         */
        @Test
        void test_パスワードと確認トークンはハッシュだけを保存する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED);

            authService().register(EMAIL, PASSWORD);

            ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(user.capture());
            assertThat(user.getValue().getPasswordHash()).isEqualTo(HASHED);

            ArgumentCaptor<EmailVerificationToken> token =
                    ArgumentCaptor.forClass(EmailVerificationToken.class);
            verify(emailVerificationTokenRepository).save(token.capture());
            assertThat(token.getValue().getPurpose()).isEqualTo("verify_email");
            assertThat(token.getValue().isUsed()).isFalse();
            // SHA-256（16進小文字）だけを保存する（§9）
            assertThat(token.getValue().getTokenHash()).hasSize(64);
            // 有効期限は現在時刻 + 24時間（§10 手順6）。時刻を2回取ると誤差が乗るため差で見る
            assertThat(Duration.between(token.getValue().getCreatedAt(),
                    token.getValue().getExpiresAt())).isEqualTo(Duration.ofHours(24));
        }

        /**
         * 手順3〜7の途中で失敗する経路。ロールバックは {@code @Transactional} が行うため、
         * ここでは例外を握りつぶさず伝播すること（＝ロールバックが起きる）と、
         * 確認トークン・リフレッシュトークンを残さないことを見る。
         * DBへ何も残らないことの検証は統合テストが持つ。
         *
         * <p>分岐: tech_auth/account.md §11 #11
         */
        @Test
        void test_途中で失敗したら例外を伝播しトークンを発行しない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);
            doThrow(new DuplicateKeyException("uq_players_user_id"))
                    .when(playerInitializationService).initialize(any());

            assertThatThrownBy(() -> authService().register(EMAIL, PASSWORD))
                    .isInstanceOf(DuplicateKeyException.class);

            verify(emailVerificationTokenRepository, never()).save(any());
            verify(refreshTokenRepository, never()).save(any());
            verify(verificationMailSender, never()).send(any(), any());
        }

        /**
         * 送信の成否は応答へ反映しない（§10 手順9）。成功時は登録結果をそのまま返す。
         *
         * <p>分岐: tech_auth/account.md §11 #12
         */
        @Test
        void test_確認メールの送信に成功しても応答は変わらない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);

            AuthResult result = authService().register(EMAIL, PASSWORD);

            verify(verificationMailSender).send(any(User.class), any(String.class));
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("ログイン")
    class TestLogin {

        /** メール登録済み（パスワードあり）のユーザー。 */
        private User registered(boolean emailVerified) {
            User user = new User();
            user.setId("user_001");
            user.setEmail(EMAIL);
            user.setPasswordHash(HASHED);
            user.setGuest(false);
            user.setEmailVerified(emailVerified);
            return user;
        }

        /**
         * 手順2でメールが一致すれば、パスワード設定の確認（手順3）へ進む。
         *
         * <p>分岐: tech_auth/account.md §13 #5
         */
        @Test
        void test_該当ユーザーが居ればパスワード照合へ進む() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(registered(true));
            when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(true);

            AuthResult result = authService().login(EMAIL, PASSWORD);

            // 検索キーはボディのメールそのもの。手順3以降へ進んだ証拠として結果を返す
            verify(userRepository).findByEmail(EMAIL);
            assertThat(result.user().getId()).isEqualTo("user_001");
        }

        /**
         * どちらが誤りかを区別させないため、パスワード不一致と同じコードを返す（§12 末尾）。
         *
         * <p>#7（ゲストアカウントでのログイン）も {@code findByEmail} が null を返す同じ経路に落ちる。
         * サービス層で見分けられるのは「行が引けたか」だけで、ゲスト行が引けないこと自体は
         * {@code email} が NULL である {@code users} と SQL の性質のため、{@code UserRepositoryTest}
         * が実DBで持つ（Repository をモックしたままでは検証できない）。
         *
         * <p>分岐: tech_auth/account.md §13 #6
         */
        @Test
        void test_未登録のメールはAUTH_INVALID_CREDENTIALSになる() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(null);

            assertThatThrownBy(() -> authService().login("unknown@example.com", PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_INVALID_CREDENTIALS");

            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * 手順4は保存済みハッシュとの照合であり、生値どうしを比べない。
         *
         * <p>分岐: tech_auth/account.md §13 #8
         */
        @Test
        void test_パスワードハッシュを持つなら保存済みハッシュと照合する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(registered(true));
            when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(true);

            authService().login(EMAIL, PASSWORD);

            verify(passwordEncoder).matches(PASSWORD, HASHED);
        }

        /**
         * Google連携のみのアカウント。bcrypt 照合そのものを行わない（§12 手順3）。
         *
         * <p>分岐: tech_auth/account.md §13 #9
         */
        @Test
        void test_パスワード未設定のアカウントは照合せず401になる() {
            User googleOnly = registered(true);
            googleOnly.setPasswordHash(null);
            when(userRepository.findByEmail(EMAIL)).thenReturn(googleOnly);

            assertThatThrownBy(() -> authService().login(EMAIL, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_INVALID_CREDENTIALS");

            verify(passwordEncoder, never()).matches(any(), any());
        }

        /**
         * 照合が一致した後の手順6〜7。既存のリフレッシュトークンは失効させない（§12 手順7）。
         *
         * <p>分岐: tech_auth/account.md §13 #10
         */
        @Test
        void test_照合が一致すれば最終ログイン時刻を更新しトークンペアを返す() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(registered(true));
            when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(true);

            AuthResult result = authService().login(EMAIL, PASSWORD);

            verify(userRepository).updateLastLoginAt(eq("user_001"), any(Instant.class));
            verify(refreshTokenRepository).save(any(RefreshToken.class));
            verify(refreshTokenRepository, never()).updateRevokedByUserId(any());
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
        }

        /**
         * 不一致は 401 で止め、手順6（最終ログイン時刻の更新）まで進まない。
         *
         * <p>分岐: tech_auth/account.md §13 #11
         */
        @Test
        void test_照合が不一致なら401で最終ログイン時刻を更新しない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(registered(true));
            when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(false);

            assertThatThrownBy(() -> authService().login(EMAIL, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_INVALID_CREDENTIALS");

            verify(userRepository, never()).updateLastLoginAt(any(), any());
            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * <p>分岐: tech_auth/account.md §13 #12
         */
        @Test
        void test_メール確認済みならログインできる() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(registered(true));
            when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(true);

            AuthResult result = authService().login(EMAIL, PASSWORD);

            assertThat(result.user().isEmailVerified()).isTrue();
            assertThat(result.accessToken()).isNotBlank();
        }

        /**
         * 未確認でもログイン・プレイは可能（§12 手順5）。確認済みと応答の形も変えない。
         *
         * <p>分岐: tech_auth/account.md §13 #13
         */
        @Test
        void test_メール未確認でも同じ応答でログインできる() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(registered(false));
            when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(true);

            AuthResult result = authService().login(EMAIL, PASSWORD);

            assertThat(result.user().isEmailVerified()).isFalse();
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
        }

        /**
         * 登録時と大小が異なる表記でも、正規化後の値で検索するため同じ行に一致する（§9）。
         *
         * <p>分岐: tech_auth/account.md §13 #14
         */
        @Test
        void test_大小が異なる表記でも正規化後に一致して認証を続行する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(registered(true));
            when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(true);

            AuthResult result = authService().login("  User@Example.COM  ", PASSWORD);

            // 検索キーは受け取った表記ではなく正規化後の値
            verify(userRepository).findByEmail(EMAIL);
            assertThat(result.user().getId()).isEqualTo("user_001");
        }

        /**
         * 正規化しても一致する行が無ければ、未登録と同じ 401 で止める（§12 末尾）。
         *
         * <p>分岐: tech_auth/account.md §13 #15
         */
        @Test
        void test_正規化しても一致しなければ401になる() {
            when(userRepository.findByEmail("other@example.com")).thenReturn(null);

            assertThatThrownBy(() -> authService().login("  Other@Example.COM  ", PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_INVALID_CREDENTIALS");

            verify(passwordEncoder, never()).matches(any(), any());
        }
    }

    @Nested
    @DisplayName("ログアウト")
    class TestLogout {

        private static final String USER_ID = "user_001";

        private static final String RAW_TOKEN = "raw-refresh-token";

        /** 認証ユーザー自身の、有効なリフレッシュトークン。 */
        private RefreshToken ownedToken() {
            RefreshToken token = new RefreshToken();
            token.setId(7);
            token.setUserId(USER_ID);
            token.setTokenHash("dummy-hash");
            token.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
            token.setRevoked(false);
            token.setCreatedAt(Instant.now());
            return token;
        }

        /**
         * 手順3の検索は生値ではなく SHA-256 で行う（§9・§14 手順3）。
         *
         * <p>分岐: tech_auth/account.md §15 #5
         */
        @Test
        void test_ハッシュが一致する行があれば持ち主の確認へ進む() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(ownedToken());

            authService().logout(USER_ID, RAW_TOKEN);

            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            verify(refreshTokenRepository).findByTokenHash(hash.capture());
            assertThat(hash.getValue()).isNotEqualTo(RAW_TOKEN).hasSize(64);
        }

        /**
         * <p>分岐: tech_auth/account.md §15 #6
         */
        @Test
        void test_一致する行が無ければAUTH_REFRESH_INVALIDになる() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(null);

            assertThatThrownBy(() -> authService().logout(USER_ID, RAW_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_REFRESH_INVALID");

            verify(refreshTokenRepository, never()).updateRevokedById(any());
        }

        /**
         * 手順4の持ち主判定。認証ユーザーとトークンの {@code user_id} が一致すれば手順5へ進む。
         *
         * <p>分岐: tech_auth/account.md §15 #7
         */
        @Test
        void test_自分のトークンなら失効状態の確認へ進む() {
            RefreshToken owned = ownedToken();
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(owned);

            authService().logout(owned.getUserId(), RAW_TOKEN);

            verify(refreshTokenRepository).updateRevokedById(7);
        }

        /**
         * 他人のトークンを失効させない（§14 手順4）。
         *
         * <p>分岐: tech_auth/account.md §15 #8
         */
        @Test
        void test_他人のトークンは失効させず401になる() {
            RefreshToken others = ownedToken();
            others.setUserId("user_999");
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(others);

            assertThatThrownBy(() -> authService().logout(USER_ID, RAW_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_REFRESH_INVALID");

            verify(refreshTokenRepository, never()).updateRevokedById(any());
        }

        /**
         * 手順5の失効状態判定。未失効なら {@code revoked} を true へ更新する。
         *
         * <p>分岐: tech_auth/account.md §15 #9
         */
        @Test
        void test_未失効のトークンを失効させる() {
            RefreshToken active = ownedToken();
            active.setRevoked(false);
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(active);

            authService().logout(USER_ID, RAW_TOKEN);

            verify(refreshTokenRepository).updateRevokedById(7);
        }

        /**
         * 二重ログアウトは再送信・複数タブで起きる正常操作なので、再利用検知（全端末の切断）を
         * 行わない（§14 手順5）。
         *
         * <p>分岐: tech_auth/account.md §15 #10
         */
        @Test
        void test_失効済みのトークンは更新せず冪等に成功する() {
            RefreshToken revoked = ownedToken();
            revoked.setRevoked(true);
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(revoked);

            authService().logout(USER_ID, RAW_TOKEN);

            verify(refreshTokenRepository, never()).updateRevokedById(any());
            verify(refreshTokenRepository, never()).updateRevokedByUserId(any());
        }

        /**
         * <p>分岐: tech_auth/account.md §15 #11
         */
        @Test
        void test_期限内のトークンを失効させる() {
            RefreshToken valid = ownedToken();
            valid.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(valid);

            authService().logout(USER_ID, RAW_TOKEN);

            verify(refreshTokenRepository).updateRevokedById(7);
        }

        /**
         * 期限切れと失効は両立するため、期限内と同じく失効させる（§14 手順6）。
         * リフレッシュ（{@code AUTH_REFRESH_INVALID}）との違いに注意する。
         *
         * <p>分岐: tech_auth/account.md §15 #12
         */
        @Test
        void test_期限切れのトークンも失効させる() {
            RefreshToken expired = ownedToken();
            expired.setExpiresAt(Instant.now().minus(Duration.ofSeconds(1)));
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(expired);

            authService().logout(USER_ID, RAW_TOKEN);

            verify(refreshTokenRepository).updateRevokedById(7);
        }
    }

    /** Google連携で受け取る認可コード（Phase 2 では未対応。link.md §18 手順3）。 */
    private static final String GOOGLE_AUTH_CODE = "4/0AWtgzh-google-auth-code";

    @Nested
    @DisplayName("アカウント移行")
    class TestLinkAccount {

        /** 移行前の作成時刻。{@code created_at} を変えないことの照合に使う。 */
        private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

        /** 手順1で特定済みの認証ユーザー（ゲスト）。principal がそのままサービスへ渡る。 */
        private User guest() {
            User user = new User();
            user.setId("guest_001");
            user.setDisplayName("冒険者");
            user.setGuest(true);
            user.setEmailVerified(false);
            user.setCreatedAt(CREATED_AT);
            return user;
        }

        /** 既に同じメールを持つ行（相手が本登録済みでもゲストでも扱いは同じ）。 */
        private User existing() {
            User user = new User();
            user.setId("user_existing");
            user.setEmail(EMAIL);
            return user;
        }

        /**
         * {@code email} と {@code password} だけがあればメール連携として扱い、Google連携の 501 では
         * なくアカウント種別の判定（手順4）へ進む。
         *
         * <p>分岐: tech_auth/link.md §19 #3
         */
        @Test
        void test_メールとパスワードだけならメール連携として続行する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);

            AuthResult result = authService().linkAccount(guest(), EMAIL, PASSWORD, null);

            // 正規化後の値で重複を確認する経路（手順6）まで到達している
            verify(userRepository).findByEmail(EMAIL);
            assertThat(result.accessToken()).isNotBlank();
        }

        /**
         * {@code googleAuthCode} だけがあれば Google連携として扱い、メール連携の経路
         * （重複確認・更新）へは入らない。設定の有無による出し分けは #7・#8 が持つ。
         *
         * <p>分岐: tech_auth/link.md §19 #4
         */
        @Test
        void test_googleAuthCodeだけならメール連携の経路へ入らない() {
            assertThatThrownBy(() -> authService().linkAccount(guest(), null, null, GOOGLE_AUTH_CODE))
                    .isInstanceOf(BusinessException.class);

            verify(userRepository, never()).findByEmail(any());
            verify(userRepository, never()).updateLinkedAccount(any());
        }

        /**
         * 連携先が決まらないため 400 で止め、何も変更しない（手順2）。
         *
         * <p>メール連携が成立するのは {@code email} と {@code password} が<b>揃っている</b>ときだけで、
         * 片側だけの指定は「どちらも無い」と同じ扱いになる。{@code LinkAccountResource} はどちらも
         * 必須にしない（Google連携のボディを 422 で落とさないため）ので、片側だけのボディは
         * Bean Validation を通過してサービス層へ届く。
         *
         * <p>分岐: tech_auth/link.md §19 #5
         */
        @ParameterizedTest(name = "email={0}, password={1}")
        @CsvSource(nullValues = "NULL", value = {
            "NULL, NULL",                     // どちらの指定も無い
            "user@example.com, NULL",         // メール連携がパスワードを欠く
            "NULL, securepass123"             // メール連携がメールを欠く
        })
        void test_メール連携が揃わずGoogleも無ければAUTH_LINK_PAYLOAD_INVALIDになる(
                String email, String password) {
            assertThatThrownBy(() -> authService().linkAccount(guest(), email, password, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_LINK_PAYLOAD_INVALID");

            verify(userRepository, never()).updateLinkedAccount(any());
            verify(emailVerificationTokenRepository, never()).save(any());
        }

        /**
         * 両方あるときも連携先が一意に決まらないため同じ扱いにする（手順2）。
         *
         * <p>分岐: tech_auth/link.md §19 #6
         */
        @Test
        void test_両方の指定があればAUTH_LINK_PAYLOAD_INVALIDになる() {
            assertThatThrownBy(
                    () -> authService().linkAccount(guest(), EMAIL, PASSWORD, GOOGLE_AUTH_CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_LINK_PAYLOAD_INVALID");

            verify(userRepository, never()).updateLinkedAccount(any());
            verify(emailVerificationTokenRepository, never()).save(any());
        }

        /**
         * {@code GOOGLE_CLIENT_ID} が未設定なら「設定が無い」ことを示すコードで 501 を返す（手順3）。
         *
         * <p>分岐: tech_auth/link.md §19 #7
         */
        @Test
        void test_GOOGLE_CLIENT_IDが未設定ならAUTH_GOOGLE_NOT_CONFIGUREDになる() {
            assertThatThrownBy(() -> authService().linkAccount(guest(), null, null, GOOGLE_AUTH_CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_GOOGLE_NOT_CONFIGURED");
        }

        /**
         * 設定済みでも Phase 2 では実装しない（手順3）。クライアントへは 501 の文言を
         * {@code AUTH_GOOGLE_NOT_CONFIGURED} とそろえるが、コードは区別する。
         *
         * <p>分岐: tech_auth/link.md §19 #8
         */
        @Test
        void test_GOOGLE_CLIENT_IDが設定済みならAUTH_GOOGLE_NOT_IMPLEMENTEDになる() {
            AuthService service = authService(AUTH_SETTINGS_WITH_GOOGLE, CLOCK);

            assertThatThrownBy(() -> service.linkAccount(guest(), null, null, GOOGLE_AUTH_CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_GOOGLE_NOT_IMPLEMENTED");
        }

        /**
         * ゲストなら入力検証（手順5）へ進み、本登録化まで到達する。
         *
         * <p>分岐: tech_auth/link.md §19 #9
         */
        @Test
        void test_ゲストユーザーなら移行を続行する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);

            authService().linkAccount(guest(), EMAIL, PASSWORD, null);

            ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
            verify(userRepository).updateLinkedAccount(updated.capture());
            assertThat(updated.getValue().isGuest()).isFalse();
        }

        /**
         * 本登録済みのユーザーは二重に移行できない（手順4）。行を1つも変えない。
         *
         * <p>分岐: tech_auth/link.md §19 #10
         */
        @Test
        void test_本登録済みならAUTH_ALREADY_REGISTEREDで何も変更しない() {
            User registered = guest();
            registered.setGuest(false);

            assertThatThrownBy(() -> authService().linkAccount(registered, EMAIL, PASSWORD, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_ALREADY_REGISTERED");

            verify(userRepository, never()).findByEmail(any());
            verify(userRepository, never()).updateLinkedAccount(any());
            verify(emailVerificationTokenRepository, never()).save(any());
            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * 正規化後の値に一致する行が無ければ移行を続行する（手順6）。
         *
         * <p>分岐: tech_auth/link.md §19 #17
         */
        @Test
        void test_同じメールのユーザーが無ければ本登録化する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);

            AuthResult result = authService().linkAccount(guest(), EMAIL, PASSWORD, null);

            verify(userRepository).updateLinkedAccount(any(User.class));
            assertThat(result.user().getEmail()).isEqualTo(EMAIL);
        }

        /**
         * 相手が誰であっても同じ扱いで、移行そのものを行わない（手順6）。
         *
         * <p>分岐: tech_auth/link.md §19 #18
         */
        @Test
        void test_メールが登録済みならAUTH_EMAIL_TAKENで何も変更しない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(existing());

            assertThatThrownBy(() -> authService().linkAccount(guest(), EMAIL, PASSWORD, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_EMAIL_TAKEN");

            verify(userRepository, never()).updateLinkedAccount(any());
            verify(emailVerificationTokenRepository, never()).save(any());
            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * 手順6を通過した後に同時移行で {@code uq_users_email} 違反が起きる経路。ロールバック自体は
         * {@code @Transactional} が行うため、ここでは 409 への変換と、後続を行わないことを見る。
         *
         * <p>分岐: tech_auth/link.md §19 #19
         */
        @Test
        void test_更新時のメール重複違反もAUTH_EMAIL_TAKENになる() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);
            doThrow(new DuplicateKeyException("uq_users_email"))
                    .when(userRepository).updateLinkedAccount(any());

            assertThatThrownBy(() -> authService().linkAccount(guest(), EMAIL, PASSWORD, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_EMAIL_TAKEN");

            verify(emailVerificationTokenRepository, never()).save(any());
            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * 手順7〜10がすべて成功する経路。**ゲームデータは作り直さず**（tech_auth.md §3）、
         * {@code id}・{@code display_name}・{@code created_at} も変えない（手順8）。
         * 既存のリフレッシュトークンも失効させない（手順10）。
         *
         * <p>分岐: tech_auth/link.md §19 #20
         */
        @Test
        void test_手順7から10を順に実行しトークンペアを返す() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED);

            AuthResult result = authService().linkAccount(guest(), EMAIL, PASSWORD, null);

            ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
            verify(userRepository).updateLinkedAccount(updated.capture());
            assertThat(updated.getValue().getId()).isEqualTo("guest_001");
            assertThat(updated.getValue().getDisplayName()).isEqualTo("冒険者");
            assertThat(updated.getValue().getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(updated.getValue().getEmail()).isEqualTo(EMAIL);
            assertThat(updated.getValue().getPasswordHash()).isEqualTo(HASHED);
            assertThat(updated.getValue().isGuest()).isFalse();
            assertThat(updated.getValue().isEmailVerified()).isFalse();
            assertThat(updated.getValue().getLastLoginAt()).isNotNull();

            // 移行はゲームデータを作り直さない
            verify(playerInitializationService, never()).initialize(any());
            // 既存のリフレッシュトークンは失効させない（ユーザーIDも権限も変わらないため）
            verify(refreshTokenRepository, never()).updateRevokedByUserId(any());

            InOrder inOrder = inOrder(userRepository, emailVerificationTokenRepository,
                    refreshTokenRepository);
            inOrder.verify(userRepository).updateLinkedAccount(any(User.class));
            inOrder.verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
            inOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));

            assertThat(result.user().getId()).isEqualTo("guest_001");
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
        }

        /**
         * 同じ成功経路が残す値。手順7（bcrypt）と手順9（確認トークン）はいずれも生値を保存しない。
         *
         * <p>分岐: tech_auth/link.md §19 #20
         */
        @Test
        void test_パスワードと確認トークンはハッシュだけを保存する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED);

            authService().linkAccount(guest(), EMAIL, PASSWORD, null);

            ArgumentCaptor<EmailVerificationToken> token =
                    ArgumentCaptor.forClass(EmailVerificationToken.class);
            verify(emailVerificationTokenRepository).save(token.capture());
            assertThat(token.getValue().getUserId()).isEqualTo("guest_001");
            assertThat(token.getValue().getPurpose()).isEqualTo("verify_email");
            assertThat(token.getValue().isUsed()).isFalse();
            // SHA-256（16進小文字）だけを保存する（§9）
            assertThat(token.getValue().getTokenHash()).hasSize(64);
            // 有効期限は現在時刻 + 24時間（手順9）。時刻を2回取ると誤差が乗るため差で見る
            assertThat(Duration.between(token.getValue().getCreatedAt(),
                    token.getValue().getExpiresAt())).isEqualTo(Duration.ofHours(24));
        }

        /**
         * 手順7〜10の途中で失敗する経路。ロールバックは {@code @Transactional} が行うため、ここでは
         * 例外を握りつぶさず伝播すること（＝ロールバックが起きる）と、リフレッシュトークンを
         * 発行せず確認メールも要求しないことを見る。DBへ何も残らないことの検証は統合テストが持つ。
         *
         * <p>分岐: tech_auth/link.md §19 #21
         */
        @Test
        void test_途中で失敗したら例外を伝播しトークンを発行しない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);
            doThrow(new DataAccessResourceFailureException("DB error"))
                    .when(emailVerificationTokenRepository).save(any());

            assertThatThrownBy(() -> authService().linkAccount(guest(), EMAIL, PASSWORD, null))
                    .isInstanceOf(DataAccessResourceFailureException.class);

            verify(refreshTokenRepository, never()).save(any());
            verify(verificationMailSender, never()).send(any(), any());
        }

        /**
         * 送信の成否は応答へ反映しない（手順12）。成功時は移行結果をそのまま返す。
         *
         * <p>分岐: tech_auth/link.md §19 #22
         */
        @Test
        void test_確認メールの送信に成功しても応答は変わらない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);

            AuthResult result = authService().linkAccount(guest(), EMAIL, PASSWORD, null);

            verify(verificationMailSender).send(any(User.class), any(String.class));
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
        }

        /**
         * 送信に失敗しても移行は成功として扱い、確認トークンの行を消さない。
         *
         * <p>失敗そのものを握るのは送信側の責務で（{@link VerificationMailSender} は例外を投げない
         * 契約。実行はコミット後）、その分岐は {@code VerificationMailSenderImplTest} が持つ。
         * 呼び出し元である本メソッドに求められるのは、<b>送信要求を永続化のすべてより後に置き、
         * 送信後の後始末（確認トークンの取り消し）を持たない</b>ことなので、そこを固定する。
         *
         * <p>分岐: tech_auth/link.md §19 #23
         */
        @Test
        void test_確認メールの送信要求は永続化を終えてから出し取り消さない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);

            authService().linkAccount(guest(), EMAIL, PASSWORD, null);

            InOrder inOrder = inOrder(emailVerificationTokenRepository, refreshTokenRepository,
                    verificationMailSender);
            inOrder.verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
            inOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));
            inOrder.verify(verificationMailSender).send(any(User.class), any(String.class));

            // 確認トークンへの操作は save 1回だけ（送信後に消す・使用済みにする後始末を持たない）
            verifyNoMoreInteractions(emailVerificationTokenRepository);
        }
    }

    @Nested
    @DisplayName("メール確認")
    class TestVerifyEmail {

        /**
         * 期限判定の基準時刻。{@code expires_at} が現在時刻ちょうどの経路（§21 #10）は
         * 実時間のクロックでは作れないため、この節だけ固定クロックでサービスを組む。
         */
        private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

        /** メール本文のリンクに載る生値。DBにはこの SHA-256 だけが入る。 */
        private static final String RAW_TOKEN = "raw-verification-token";

        /** 対象ユーザーのID。確認トークンの {@code user_id} と対応する。 */
        private static final String USER_ID = "user_001";

        private AuthService verifyService() {
            return authService(AUTH_SETTINGS, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        /** 有効な確認トークンの行（用途 verify_email・未使用・期限内）。 */
        private EmailVerificationToken token() {
            EmailVerificationToken token = new EmailVerificationToken();
            token.setId(1);
            token.setUserId(USER_ID);
            token.setTokenHash("0".repeat(64));
            token.setPurpose("verify_email");
            token.setExpiresAt(NOW.plus(Duration.ofHours(1)));
            token.setUsed(false);
            token.setCreatedAt(NOW.minus(Duration.ofHours(23)));
            return token;
        }

        /** 確認前のユーザー。 */
        private User unverified() {
            User user = new User();
            user.setId(USER_ID);
            user.setEmail(EMAIL);
            user.setGuest(false);
            user.setEmailVerified(false);
            return user;
        }

        /**
         * 生値ではなく SHA-256（16進小文字）で検索し、行があれば用途の確認（手順3）へ進む（§9）。
         *
         * <p>分岐: tech_auth/verify.md §21 #3
         */
        @Test
        void test_ハッシュが一致する行があれば用途の確認へ進む() {
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(token());
            when(userRepository.findById(USER_ID)).thenReturn(unverified());

            verifyService().verifyEmail(RAW_TOKEN);

            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            verify(emailVerificationTokenRepository).findByTokenHash(hash.capture());
            assertThat(hash.getValue()).isNotEqualTo(RAW_TOKEN).matches("[0-9a-f]{64}");
        }

        /**
         * 存在しないトークンは 400。理由（不正・期限切れ・退会済み）を出し分けない（§20 手順2）。
         *
         * <p>分岐: tech_auth/verify.md §21 #4
         */
        @Test
        void test_一致する行が無ければAUTH_VERIFICATION_INVALIDになる() {
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(null);

            assertThatThrownBy(() -> verifyService().verifyEmail(RAW_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_VERIFICATION_INVALID");

            verifyNoInteractions(userRepository);
        }

        /**
         * 用途が確認メールなら使用状態の確認（手順4）へ進む。
         *
         * <p>分岐: tech_auth/verify.md §21 #5
         */
        @Test
        void test_用途がverify_emailなら使用状態の確認へ進む() {
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(token());
            when(userRepository.findById(USER_ID)).thenReturn(unverified());

            verifyService().verifyEmail(RAW_TOKEN);

            verify(emailVerificationTokenRepository).updateUsedById(1);
        }

        /**
         * 再設定トークンの流用を防ぐ（§20 手順3）。{@code email_verified} を変えない。
         *
         * <p>分岐: tech_auth/verify.md §21 #6
         */
        @Test
        void test_用途がpassword_resetならAUTH_VERIFICATION_INVALIDになる() {
            EmailVerificationToken reset = token();
            reset.setPurpose("password_reset");
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(reset);

            assertThatThrownBy(() -> verifyService().verifyEmail(RAW_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_VERIFICATION_INVALID");

            verifyNoInteractions(userRepository);
            verify(emailVerificationTokenRepository, never()).updateUsedById(any());
        }

        /**
         * 未使用なら有効期限の確認（手順5）へ進む。
         *
         * <p>分岐: tech_auth/verify.md §21 #7
         */
        @Test
        void test_未使用のトークンは有効期限の確認へ進む() {
            EmailVerificationToken expired = token();
            expired.setUsed(false);
            expired.setExpiresAt(NOW.minus(Duration.ofSeconds(1)));
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(expired);

            // 使用済みなら 200 で素通りする（#8）ため、期限判定へ進んだことが 400 で分かる
            assertThatThrownBy(() -> verifyService().verifyEmail(RAW_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_VERIFICATION_INVALID");
        }

        /**
         * メール内リンクの再クリックは正常操作なので、何も更新せず成功させる
         * （ログアウトの二重実行と同じ扱い。§20 手順4）。
         *
         * <p>分岐: tech_auth/verify.md §21 #8
         */
        @Test
        void test_使用済みのトークンは何も更新せず成功する() {
            EmailVerificationToken used = token();
            used.setUsed(true);
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(used);

            assertThatCode(() -> verifyService().verifyEmail(RAW_TOKEN)).doesNotThrowAnyException();

            verifyNoInteractions(userRepository);
            verify(emailVerificationTokenRepository, never()).updateUsedById(any());
        }

        /**
         * 期限内なら対象ユーザーの検索（手順6）へ進む。
         *
         * <p>分岐: tech_auth/verify.md §21 #9
         */
        @Test
        void test_有効期限が現在時刻より後ならユーザーの検索へ進む() {
            EmailVerificationToken valid = token();
            // 境界の1つ内側（現在時刻の1秒後）
            valid.setExpiresAt(NOW.plus(Duration.ofSeconds(1)));
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(valid);
            when(userRepository.findById(USER_ID)).thenReturn(unverified());

            verifyService().verifyEmail(RAW_TOKEN);

            verify(userRepository).findById(USER_ID);
        }

        /**
         * 期限切れは 400。再送APIを設けないため、未確認のまま据え置く（§20 手順5）。
         * <b>現在時刻ちょうども「以前」に含む</b>（{@code <=} であることを境界で固定する）。
         *
         * <p>分岐: tech_auth/verify.md §21 #10
         */
        @ParameterizedTest(name = "expiresAt = 現在時刻{0}秒")
        @ValueSource(longs = {
            0,   // 境界ちょうど（現在時刻と等しい）
            -1   // 境界の1つ外側
        })
        void test_有効期限が現在時刻以前ならAUTH_VERIFICATION_INVALIDになる(long offsetSeconds) {
            EmailVerificationToken expired = token();
            expired.setExpiresAt(NOW.plusSeconds(offsetSeconds));
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(expired);

            assertThatThrownBy(() -> verifyService().verifyEmail(RAW_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_VERIFICATION_INVALID");

            verifyNoInteractions(userRepository);
            verify(emailVerificationTokenRepository, never()).updateUsedById(any());
        }

        /**
         * ユーザーが居れば確認状態の更新（手順7）へ進む。
         *
         * <p>分岐: tech_auth/verify.md §21 #11
         */
        @Test
        void test_対象ユーザーが存在すれば確認状態の更新へ進む() {
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(token());
            when(userRepository.findById(USER_ID)).thenReturn(unverified());

            verifyService().verifyEmail(RAW_TOKEN);

            verify(userRepository).updateEmailVerified(USER_ID, true);
        }

        /**
         * 退会済みのユーザーへ宛てたトークンは無効として扱う（§20 手順6）。
         *
         * <p>分岐: tech_auth/verify.md §21 #12
         */
        @Test
        void test_対象ユーザーが存在しなければAUTH_VERIFICATION_INVALIDになる() {
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(token());
            when(userRepository.findById(USER_ID)).thenReturn(null);

            assertThatThrownBy(() -> verifyService().verifyEmail(RAW_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AuthServiceImplTest::codeOf)
                    .isEqualTo("AUTH_VERIFICATION_INVALID");

            verify(userRepository, never()).updateEmailVerified(any(), anyBoolean());
            verify(emailVerificationTokenRepository, never()).updateUsedById(any());
        }

        /**
         * 未確認なら {@code email_verified} を true にし、使ったトークンを使用済みにする（手順7・8）。
         *
         * <p>分岐: tech_auth/verify.md §21 #13
         */
        @Test
        void test_未確認ならemail_verifiedをtrueにしトークンを使用済みにする() {
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(token());
            when(userRepository.findById(USER_ID)).thenReturn(unverified());

            verifyService().verifyEmail(RAW_TOKEN);

            verify(userRepository).updateEmailVerified(USER_ID, true);
            verify(emailVerificationTokenRepository).updateUsedById(1);
        }

        /**
         * 別トークンで確認済みなら値は true のままで、使ったトークンだけ使用済みにする（手順7・8）。
         * 同じユーザーの他の確認トークンは変更しない。
         *
         * <p>分岐: tech_auth/verify.md §21 #14
         */
        @Test
        void test_確認済みならトークンだけを使用済みにする() {
            User verified = unverified();
            verified.setEmailVerified(true);
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(token());
            when(userRepository.findById(USER_ID)).thenReturn(verified);

            verifyService().verifyEmail(RAW_TOKEN);

            assertThat(verified.isEmailVerified()).isTrue();
            verify(userRepository, never()).updateEmailVerified(USER_ID, false);
            verify(emailVerificationTokenRepository).updateUsedById(1);
        }

        /**
         * 手順7・8がともに成功する経路。コミットは {@code @Transactional} が行うため、ここでは
         * 2つの更新が順に呼ばれることだけを見る。DBへ反映されることの検証は統合テストが持つ。
         *
         * <p>分岐: tech_auth/verify.md §21 #15
         */
        @Test
        void test_確認状態とトークンの更新を順に実行して成功する() {
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(token());
            when(userRepository.findById(USER_ID)).thenReturn(unverified());

            assertThatCode(() -> verifyService().verifyEmail(RAW_TOKEN)).doesNotThrowAnyException();

            InOrder inOrder = inOrder(userRepository, emailVerificationTokenRepository);
            inOrder.verify(userRepository).updateEmailVerified(USER_ID, true);
            inOrder.verify(emailVerificationTokenRepository).updateUsedById(1);
        }

        /**
         * 手順7・8の途中で失敗する経路。ロールバックは {@code @Transactional} が行うため、ここでは
         * 例外を握りつぶさず伝播すること（＝ロールバックが起きる）と、トークンを使用済みに
         * しないことを見る。
         *
         * <p>分岐: tech_auth/verify.md §21 #16
         */
        @Test
        void test_途中で失敗したら例外を伝播しトークンを使用済みにしない() {
            when(emailVerificationTokenRepository.findByTokenHash(any())).thenReturn(token());
            when(userRepository.findById(USER_ID)).thenReturn(unverified());
            doThrow(new DataAccessResourceFailureException("DB error"))
                    .when(userRepository).updateEmailVerified(any(), anyBoolean());

            assertThatThrownBy(() -> verifyService().verifyEmail(RAW_TOKEN))
                    .isInstanceOf(DataAccessResourceFailureException.class);

            verify(emailVerificationTokenRepository, never()).updateUsedById(any());
        }
    }
}
