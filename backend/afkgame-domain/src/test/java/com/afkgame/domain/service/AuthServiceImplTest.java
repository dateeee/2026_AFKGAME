package com.afkgame.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.afkgame.domain.exception.AppException;
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
 * docs/tech/basic/tech_logging.md「AUTH_ コード一覧」。
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
 * docs/tech/detail/tech_auth_account.md §9〜§15。分岐一覧 §11（登録）・§13（ログイン）・
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
 *       「コミット後・トランザクションの外」（§10 手順9）を満たす仕組みは実装側が持ち、
 *       {@code AuthService} は送信例外を握って WARN ログにとどめる。送信手段（SMTP設定・本文・
 *       再送）は verify-email の詳細設計（STEP 3-A-3）で確定する</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final AuthSettings AUTH_SETTINGS = new AuthSettings(
            "afkgame-test-secret-value-32bytes-or-longer",
            Duration.ofMinutes(30),
            Duration.ofDays(30),
            12, 8, 128,
            Duration.ofDays(90));

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
            authService = new AuthServiceImpl(userRepository, refreshTokenRepository,
                    new JwtServiceImpl(AUTH_SETTINGS, CLOCK), AUTH_SETTINGS,
                    playerInitializationService, CLOCK, passwordEncoder,
                    emailVerificationTokenRepository, verificationMailSender);
        }
        return authService;
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
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_REFRESH_INVALID", 401);
        }

        @Test
        void test_revoked済みの再利用は全トークンを失効させる() {
            RefreshToken revoked = storedToken("dummy-hash");
            revoked.setRevoked(true);
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(revoked);

            assertThatThrownBy(() -> authService().refresh("reused-token"))
                    .isInstanceOf(AppException.class)
                    .extracting("code")
                    .isEqualTo("AUTH_REFRESH_INVALID");

            verify(refreshTokenRepository).updateRevokedByUserId("guest_001");
            verify(refreshTokenRepository, never()).updateRevokedById(any());
        }

        @Test
        void test_期限切れはAUTH_REFRESH_INVALIDになる() {
            RefreshToken expired = storedToken("dummy-hash");
            expired.setExpiresAt(Instant.now().minus(Duration.ofSeconds(1)));
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(expired);

            assertThatThrownBy(() -> authService().refresh("expired-token"))
                    .isInstanceOf(AppException.class)
                    .extracting("code")
                    .isEqualTo("AUTH_REFRESH_INVALID");

            verify(refreshTokenRepository, never()).updateRevokedByUserId(any());
        }

        @Test
        void test_トークンは正当でもユーザーが居なければAUTH_REFRESH_INVALIDになる() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(storedToken("dummy-hash"));
            when(userRepository.findById("guest_001")).thenReturn(null);

            assertThatThrownBy(() -> authService().refresh("orphan-token"))
                    .isInstanceOf(AppException.class)
                    .extracting("code")
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
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_USER_NOT_FOUND", 401);
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
         * <p>分岐: tech_auth_account.md §11 #7
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
         * <p>分岐: tech_auth_account.md §11 #8
         */
        @Test
        void test_メールが登録済みならAUTH_EMAIL_TAKENで何も作らない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(existing());

            assertThatThrownBy(() -> authService().register(EMAIL, PASSWORD))
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_EMAIL_TAKEN", 409);

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
         * <p>分岐: tech_auth_account.md §11 #9
         */
        @Test
        void test_挿入時のメール重複違反もAUTH_EMAIL_TAKENになる() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);
            doThrow(new DuplicateKeyException("uq_users_email")).when(userRepository).save(any());

            assertThatThrownBy(() -> authService().register(EMAIL, PASSWORD))
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_EMAIL_TAKEN", 409);

            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * 手順3〜7がすべて成功する経路。手順5（初期化）の中身は
         * {@code PlayerInitializationServiceImplTest} が持ち、ここでは順序と委譲だけを見る。
         *
         * <p>分岐: tech_auth_account.md §11 #10
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
         * <p>分岐: tech_auth_account.md §11 #10
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
         * <p>分岐: tech_auth_account.md §11 #11
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
         * <p>分岐: tech_auth_account.md §11 #12
         */
        @Test
        void test_確認メールの送信に成功しても応答は変わらない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);

            AuthResult result = authService().register(EMAIL, PASSWORD);

            verify(verificationMailSender).send(any(User.class), any(String.class));
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
        }

        /**
         * 送信失敗は WARN ログだけを残し、登録は成功として扱う。確認トークンの行も消さない
         * （§10 手順9）。
         *
         * <p>分岐: tech_auth_account.md §11 #13
         */
        @Test
        void test_確認メールの送信に失敗しても登録は成功する() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(null);
            doThrow(new IllegalStateException("SMTP unreachable"))
                    .when(verificationMailSender).send(any(), any());

            AuthResult result = authService().register(EMAIL, PASSWORD);

            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
            verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
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
         * <p>分岐: tech_auth_account.md §13 #5
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
         * <p>分岐: tech_auth_account.md §13 #6
         */
        @Test
        void test_未登録のメールはAUTH_INVALID_CREDENTIALSになる() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(null);

            assertThatThrownBy(() -> authService().login("unknown@example.com", PASSWORD))
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_INVALID_CREDENTIALS", 401);

            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * ゲスト行は {@code email} が NULL のため、どのメールで引いても手順2に一致しない
         * （§12 末尾）。メールログインの対象外であることが未登録と同じ応答になる。
         *
         * <p>分岐: tech_auth_account.md §13 #7
         */
        @Test
        void test_ゲストアカウントはメールログインの対象外で401になる() {
            when(userRepository.findByEmail("guest@example.com")).thenReturn(null);

            assertThatThrownBy(() -> authService().login("guest@example.com", PASSWORD))
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_INVALID_CREDENTIALS", 401);

            verify(passwordEncoder, never()).matches(any(), any());
        }

        /**
         * 手順4は保存済みハッシュとの照合であり、生値どうしを比べない。
         *
         * <p>分岐: tech_auth_account.md §13 #8
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
         * <p>分岐: tech_auth_account.md §13 #9
         */
        @Test
        void test_パスワード未設定のアカウントは照合せず401になる() {
            User googleOnly = registered(true);
            googleOnly.setPasswordHash(null);
            when(userRepository.findByEmail(EMAIL)).thenReturn(googleOnly);

            assertThatThrownBy(() -> authService().login(EMAIL, PASSWORD))
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_INVALID_CREDENTIALS", 401);

            verify(passwordEncoder, never()).matches(any(), any());
        }

        /**
         * 照合が一致した後の手順6〜7。既存のリフレッシュトークンは失効させない（§12 手順7）。
         *
         * <p>分岐: tech_auth_account.md §13 #10
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
         * <p>分岐: tech_auth_account.md §13 #11
         */
        @Test
        void test_照合が不一致なら401で最終ログイン時刻を更新しない() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(registered(true));
            when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(false);

            assertThatThrownBy(() -> authService().login(EMAIL, PASSWORD))
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_INVALID_CREDENTIALS", 401);

            verify(userRepository, never()).updateLastLoginAt(any(), any());
            verify(refreshTokenRepository, never()).save(any());
        }

        /**
         * <p>分岐: tech_auth_account.md §13 #12
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
         * <p>分岐: tech_auth_account.md §13 #13
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
         * <p>分岐: tech_auth_account.md §15 #5
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
         * <p>分岐: tech_auth_account.md §15 #6
         */
        @Test
        void test_一致する行が無ければAUTH_REFRESH_INVALIDになる() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(null);

            assertThatThrownBy(() -> authService().logout(USER_ID, RAW_TOKEN))
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_REFRESH_INVALID", 401);

            verify(refreshTokenRepository, never()).updateRevokedById(any());
        }

        /**
         * 手順4の持ち主判定。認証ユーザーとトークンの {@code user_id} が一致すれば手順5へ進む。
         *
         * <p>分岐: tech_auth_account.md §15 #7
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
         * <p>分岐: tech_auth_account.md §15 #8
         */
        @Test
        void test_他人のトークンは失効させず401になる() {
            RefreshToken others = ownedToken();
            others.setUserId("user_999");
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(others);

            assertThatThrownBy(() -> authService().logout(USER_ID, RAW_TOKEN))
                    .isInstanceOf(AppException.class)
                    .extracting("code", "status")
                    .containsExactly("AUTH_REFRESH_INVALID", 401);

            verify(refreshTokenRepository, never()).updateRevokedById(any());
        }

        /**
         * 手順5の失効状態判定。未失効なら {@code revoked} を true へ更新する。
         *
         * <p>分岐: tech_auth_account.md §15 #9
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
         * <p>分岐: tech_auth_account.md §15 #10
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
         * <p>分岐: tech_auth_account.md §15 #11
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
         * <p>分岐: tech_auth_account.md §15 #12
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
}
