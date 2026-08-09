package com.afkgame.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afkgame.domain.exception.AppException;
import com.afkgame.domain.model.EmailVerificationToken;
import com.afkgame.domain.model.RefreshToken;
import com.afkgame.domain.model.User;
import com.afkgame.domain.repository.EmailVerificationTokenRepository;
import com.afkgame.domain.repository.RefreshTokenRepository;
import com.afkgame.domain.repository.UserRepository;
import com.afkgame.env.config.AuthSettings;

/**
 * 認証のドメインサービス。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §2「ゲストプレイ」・§3「認証フロー」・
 * §4「リフレッシュトークン」と docs/tech/detail/tech_auth_account.md §9〜§15
 * （登録・ログイン・ログアウト）、エラーコードは docs/tech/basic/tech_logging.md「AUTH_ コード一覧」。
 *
 * <p>ゲスト作成・リフレッシュ・登録・ログイン・ログアウトと、ユーザー作成時のプレイヤー初期化
 * （tech_auth.md §8.2。実体は {@link PlayerInitializationService}）を持つ。
 * アカウント移行・確認メール・パスワード再設定は STEP 3-A-3 以降で追加する
 * （docs/backlog/java_migration.md）。
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger("afkgame.auth");

    /** リフレッシュトークンの生値のバイト数（Base64URL で44文字になる）。 */
    private static final int REFRESH_TOKEN_BYTES = 48;

    /** 確認トークンの用途（tech_db/auth.md §3）。用途をまたいだ流用を防ぐため発行時に固定する。 */
    private static final String VERIFY_EMAIL_PURPOSE = "verify_email";

    /** 確認トークンの有効期間（tech_auth_account.md §10 手順6）。 */
    private static final Duration VERIFICATION_TOKEN_EXPIRE = Duration.ofHours(24);

    /** トークン生成用の暗号乱数。ゲーム乱数（{@code RandomFactory}）とは用途が異なり、共有してよい。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final Duration refreshTokenExpire;
    private final PlayerInitializationService playerInitializationService;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final VerificationMailSender verificationMailSender;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService, AuthSettings authSettings,
            PlayerInitializationService playerInitializationService, Clock clock,
            PasswordEncoder passwordEncoder,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            VerificationMailSender verificationMailSender) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshTokenExpire = authSettings.refreshTokenExpire();
        this.playerInitializationService = playerInitializationService;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.verificationMailSender = verificationMailSender;
    }

    /**
     * ゲストアカウントを作成し、プレイ可能な初期状態を組み立ててトークンペアを発行する。
     *
     * <p>tech_auth.md §8.2 の手順1（ユーザー作成）→ 手順2〜6（初期化）→ 手順7（トークン発行）を
     * このメソッドのトランザクション境界で1つにまとめる（手順8）。途中で失敗した場合は
     * ユーザーを含めて何も残さない。
     *
     * @return 作成したユーザーとトークンペア
     */
    @Transactional
    public AuthResult createGuest() {
        Instant now = clock.instant();

        User user = new User();
        user.setId("guest_" + UUID.randomUUID());
        user.setGuest(true);
        user.setEmailVerified(false);
        user.setCreatedAt(now);
        user.setLastLoginAt(now);
        userRepository.save(user);

        playerInitializationService.initialize(user.getId());

        logger.info("ゲストアカウント作成 user_id={}", user.getId());
        return issueTokens(user);
    }

    /**
     * リフレッシュトークンを検証し、ローテーションして新しいトークンペアを発行する。
     *
     * <p>revoked 済みのトークンが使われた場合は窃取とみなし、そのユーザーの全トークンを失効させる
     * （tech_auth.md §4「不正検知」）。この失効は 401 を返す場合でも確定させる必要があるため、
     * {@link AppException} ではロールバックしない。
     *
     * @param rawRefreshToken クライアントが持つ生のリフレッシュトークン
     * @return 新しいトークンペア
     * @throws AppException {@code AUTH_REFRESH_INVALID}（不正・再利用・期限切れ）
     */
    @Transactional(noRollbackFor = AppException.class)
    public AuthResult refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken));
        if (stored == null) {
            throw refreshInvalid("Invalid refresh token");
        }

        if (stored.isRevoked()) {
            logger.warn("不正リフレッシュトークン検知 user_id={}", stored.getUserId());
            refreshTokenRepository.updateRevokedByUserId(stored.getUserId());
            throw refreshInvalid("Refresh token reuse detected");
        }

        if (stored.getExpiresAt().isBefore(clock.instant())) {
            throw refreshInvalid("Refresh token expired");
        }

        refreshTokenRepository.updateRevokedById(stored.getId());

        User user = userRepository.findById(stored.getUserId());
        if (user == null) {
            throw refreshInvalid("User not found");
        }
        return issueTokens(user);
    }

    /**
     * アクセストークンのユーザーIDから認証ユーザーを取得する。
     *
     * @param userId トークンの {@code sub}
     * @return 該当ユーザー
     * @throws AppException {@code AUTH_USER_NOT_FOUND}（トークンは正当だがユーザーが存在しない）
     */
    public User findAuthenticatedUser(String userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            logger.warn("認証失敗 reason=user_not_found user_id={}", userId);
            throw new AppException("AUTH_USER_NOT_FOUND", "User not found", 401);
        }
        return user;
    }

    /**
     * メールアドレスとパスワードでアカウントを登録し、トークンペアを発行する。
     *
     * <p>tech_auth_account.md §10 の手順2（重複確認）→ 手順3〜7（ユーザー・初期化・確認トークン・
     * トークンペア）をこのメソッドのトランザクション境界で1つにまとめる（手順8）。途中で失敗した場合は
     * ユーザーを含めて何も残さない。
     *
     * <p>表示名は設定しない。既定値 {@code 冒険者} は {@link User} のフィールド初期値が持つ
     * （tech_db.md §4-2・§10 手順4）。
     *
     * @param email 登録するメールアドレス
     * @param rawPassword 生のパスワード
     * @return 作成したユーザーとトークンペア
     * @throws AppException {@code AUTH_EMAIL_TAKEN}（メールが使用済み）
     */
    @Transactional
    public AuthResult register(String email, String rawPassword) {
        if (userRepository.findByEmail(email) != null) {
            logger.warn("登録失敗 reason=email_taken");
            throw emailTaken();
        }

        Instant now = clock.instant();

        User user = new User();
        user.setId("user_" + UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setGuest(false);
        user.setEmailVerified(false);
        user.setCreatedAt(now);
        user.setLastLoginAt(now);
        try {
            userRepository.save(user);
        } catch (DuplicateKeyException e) {
            // 手順2の通過後に同時登録で uq_users_email 違反が起きた場合も重複として扱う（§11 #9）
            logger.warn("登録失敗 reason=email_taken_conflict");
            throw emailTaken();
        }

        playerInitializationService.initialize(user.getId());

        String rawVerificationToken = generateRawToken();
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUserId(user.getId());
        verificationToken.setTokenHash(hashToken(rawVerificationToken));
        verificationToken.setPurpose(VERIFY_EMAIL_PURPOSE);
        verificationToken.setExpiresAt(now.plus(VERIFICATION_TOKEN_EXPIRE));
        verificationToken.setUsed(false);
        verificationToken.setCreatedAt(now);
        emailVerificationTokenRepository.save(verificationToken);

        AuthResult result = issueTokens(user);
        logger.info("アカウント登録 user_id={}", user.getId());
        sendVerificationMail(user, rawVerificationToken);
        return result;
    }

    /**
     * メールアドレスとパスワードで認証し、トークンペアを発行する。
     *
     * <p>tech_auth_account.md §12。手順2〜4 の失敗はいずれも同じコードを返し、メールとパスワードの
     * どちらが誤りかを区別させない。既存のリフレッシュトークンは失効させない（手順7）。
     *
     * @param email メールアドレス
     * @param rawPassword 生のパスワード
     * @return 認証したユーザーとトークンペア
     * @throws AppException {@code AUTH_INVALID_CREDENTIALS}（未登録・パスワード未設定・不一致）
     */
    @Transactional
    public AuthResult login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            logger.warn("ログイン失敗 reason=email_not_found");
            throw invalidCredentials();
        }

        // Google連携のみのアカウントは bcrypt 照合そのものを行わない（§12 手順3）
        if (user.getPasswordHash() == null) {
            logger.warn("ログイン失敗 reason=password_not_set user_id={}", user.getId());
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            logger.warn("ログイン失敗 reason=password_mismatch user_id={}", user.getId());
            throw invalidCredentials();
        }

        // email_verified の値でログインを止めない（§12 手順5）
        userRepository.updateLastLoginAt(user.getId(), clock.instant());

        logger.info("ログイン user_id={}", user.getId());
        return issueTokens(user);
    }

    /**
     * リフレッシュトークンを失効させてログアウトする。
     *
     * <p>tech_auth_account.md §14。失効済みのトークンでも 200 を返す冪等な操作であり、
     * §4 の再利用検知（ユーザーの全トークン失効）は行わない（手順5）。期限切れのトークンも
     * 期限内と同じく失効させる（手順6）。
     *
     * @param userId 手順1で特定した認証ユーザーのID
     * @param rawRefreshToken クライアントが持つ生のリフレッシュトークン
     * @throws AppException {@code AUTH_REFRESH_INVALID}（該当なし・他人のトークン）
     */
    @Transactional
    public void logout(String userId, String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken));
        if (stored == null) {
            throw refreshInvalid("Refresh token not found");
        }

        if (!stored.getUserId().equals(userId)) {
            logger.warn("他ユーザーのリフレッシュトークンでログアウト user_id={}", userId);
            throw refreshInvalid("Refresh token owner mismatch");
        }

        if (stored.isRevoked()) {
            // 二重ログアウトは再送信・複数タブで起きる正常操作（§14 手順5）
            return;
        }

        refreshTokenRepository.updateRevokedById(stored.getId());
        logger.info("ログアウト user_id={}", userId);
    }

    /**
     * 確認メールの送信を要求する。
     *
     * <p>送信の成否は登録の成否へ反映せず、失敗は WARN ログだけを残す（§10 手順9・§11 #13）。
     * 「コミット後・トランザクションの外」で送るための仕組みは {@link VerificationMailSender} 側が持つ。
     */
    private void sendVerificationMail(User user, String rawVerificationToken) {
        try {
            verificationMailSender.send(user, rawVerificationToken);
        } catch (RuntimeException e) {
            logger.warn("確認メールの送信に失敗 user_id={}", user.getId(), e);
        }
    }

    /** アクセストークンを発行し、リフレッシュトークンを新規保存する。 */
    private AuthResult issueTokens(User user) {
        // expires_at と created_at の差を有効期限そのものにするため、時刻は1回だけ取る
        Instant now = clock.instant();

        String rawRefreshToken = generateRawToken();

        RefreshToken record = new RefreshToken();
        record.setUserId(user.getId());
        record.setTokenHash(hashToken(rawRefreshToken));
        record.setExpiresAt(now.plus(refreshTokenExpire));
        record.setRevoked(false);
        record.setCreatedAt(now);
        refreshTokenRepository.save(record);

        return new AuthResult(user, jwtService.createAccessToken(user.getId(), user.isGuest()), rawRefreshToken);
    }

    /**
     * クライアントへ渡す生トークンを作る。
     *
     * <p>48バイトの乱数を Base64URL（パディングなし）で表す。リフレッシュトークンと確認トークンで
     * 同じ方式を使う（tech_auth_account.md §9）。
     */
    private static String generateRawToken() {
        byte[] raw = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** 生トークンの SHA-256 ハッシュ（16進小文字）。DBには生値を保存しない。 */
    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 は Java 標準で必須のため到達しない
            throw new IllegalStateException(e);
        }
    }

    /**
     * リフレッシュ失敗の例外を作る。
     *
     * <p>失敗理由（不正・再利用・期限切れ）はクライアントへ出し分けない。
     * どれも「再ログインが必要」であり、区別はトークン探索の手がかりになるため。
     */
    private static AppException refreshInvalid(String reason) {
        return new AppException("AUTH_REFRESH_INVALID", reason, 401);
    }

    /**
     * メール重複の例外を作る。
     *
     * <p>相手がゲスト・Google連携のみのアカウントでも同じ扱いにする（§10 手順2）。
     * メールアドレスは応答にもログにも載せない（§9）。
     */
    private static AppException emailTaken() {
        return new AppException("AUTH_EMAIL_TAKEN", "Email already registered", 409);
    }

    /**
     * ログイン失敗の例外を作る。
     *
     * <p>未登録・パスワード未設定・不一致のどれであるかを**クライアントには**区別させない
     * （§12 末尾）。内部の切り分けはログの {@code reason} が持つ。
     */
    private static AppException invalidCredentials() {
        return new AppException("AUTH_INVALID_CREDENTIALS", "Invalid email or password", 401);
    }
}
