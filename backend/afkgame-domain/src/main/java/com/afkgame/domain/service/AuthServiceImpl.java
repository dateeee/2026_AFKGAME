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
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.terasoluna.gfw.common.exception.BusinessException;
import org.terasoluna.gfw.common.message.ResultMessages;

import com.afkgame.domain.model.EmailVerificationToken;
import com.afkgame.domain.model.RefreshToken;
import com.afkgame.domain.model.User;
import com.afkgame.domain.repository.EmailVerificationTokenRepository;
import com.afkgame.domain.repository.RefreshTokenRepository;
import com.afkgame.domain.repository.UserRepository;
import com.afkgame.env.config.AuthSettings;
import com.afkgame.env.logging.AppLogger;
import com.afkgame.env.logging.LogKey;
import com.afkgame.env.logging.LogReason;
import com.afkgame.env.logging.LoggerName;

/**
 * {@link AuthService} の実装。
 *
 * <p>仕様・各メソッドの契約はインタフェース側が持つ。本クラスはトークンの生成方式（乱数・ハッシュ）と
 * トランザクション境界の宣言を担う。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final AppLogger logger = AppLogger.of(LoggerName.AUTH);

    /** 生トークンの乱数バイト数（Base64URL パディングなしで64文字になる）。 */
    private static final int RAW_TOKEN_BYTES = 48;

    /** メールの一意制約名（V1__initial_schema.sql）。重複の判別を制約名で行うため定数で持つ。 */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";

    /** 確認トークンの用途（tech_db/auth.md §3）。用途をまたいだ流用を防ぐため発行時に固定する。 */
    private static final String VERIFY_EMAIL_PURPOSE = "verify_email";

    /** トークン生成用の暗号乱数。ゲーム乱数（{@code RandomFactory}）とは用途が異なり、共有してよい。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final Duration refreshTokenExpire;
    private final Duration verificationTokenExpire;
    private final PlayerInitializationService playerInitializationService;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final VerificationMailSender verificationMailSender;

    public AuthServiceImpl(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService, AuthSettings authSettings,
            PlayerInitializationService playerInitializationService, Clock clock,
            PasswordEncoder passwordEncoder,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            VerificationMailSender verificationMailSender) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshTokenExpire = authSettings.refreshTokenExpire();
        this.verificationTokenExpire = authSettings.verificationTokenExpire();
        this.playerInitializationService = playerInitializationService;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.verificationMailSender = verificationMailSender;
    }

    /**
     * {@inheritDoc}
     *
     * <p>ユーザー作成・初期化・トークン発行を本メソッドの境界で1つにまとめる（tech_auth.md §8.2 手順8）。
     */
    @Override
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

        logger.info("ゲストアカウント作成").with(LogKey.USER_ID, user.getId()).log();
        return issueTokens(user);
    }

    /**
     * {@inheritDoc}
     *
     * <p>不正検知による全トークン失効は 401 を返す場合でも確定させる必要があるため、
     * {@link BusinessException} ではロールバックしない（tech_auth.md §4「不正検知」）。
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hashToken(refreshToken));
        if (stored == null) {
            logger.warn("リフレッシュ失敗").reason(LogReason.REFRESH_NOT_FOUND).log();
            throw refreshInvalid();
        }

        if (stored.isRevoked()) {
            throw detectReuse(stored.getUserId());
        }

        if (stored.getExpiresAt().isBefore(clock.instant())) {
            logger.warn("リフレッシュ失敗").reason(LogReason.REFRESH_EXPIRED)
                    .with(LogKey.USER_ID, stored.getUserId()).log();
            throw refreshInvalid();
        }

        // 失効させられるのは未失効の1本だけ。0件なら同じトークンで並走した他のリクエストが
        // 先にローテーションを済ませており、READ COMMITTED では上の失効判定を通り抜けている
        if (refreshTokenRepository.updateRevokedById(stored.getId()) == 0) {
            throw detectReuse(stored.getUserId());
        }

        User user = userRepository.findById(stored.getUserId());
        if (user == null) {
            logger.warn("リフレッシュ失敗").reason(LogReason.USER_NOT_FOUND)
                    .with(LogKey.USER_ID, stored.getUserId()).log();
            throw refreshInvalid();
        }
        return issueTokens(user);
    }

    /**
     * {@inheritDoc}
     *
     * <p>参照のみで更新を伴わないため、トランザクション境界を持たない。
     */
    @Override
    public User findAuthenticatedUser(String userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            logger.warn("認証失敗").reason(LogReason.USER_NOT_FOUND).with(LogKey.USER_ID, userId).log();
            throw authError("AUTH_USER_NOT_FOUND");
        }
        return user;
    }

    /**
     * {@inheritDoc}
     *
     * <p>重複確認からトークン発行までを本メソッドの境界で1つにまとめる
     * （tech_auth/account.md §10 手順8）。確認メールの送信要求だけは境界の外へ出さず、
     * 送信側（{@link VerificationMailSender}）が「コミット後・トランザクションの外」で送る担保と、
     * 失敗を WARN ログにとどめる責務を持つ（mail.md §16.1）。本メソッドは送信結果を待たない。
     */
    @Override
    @Transactional
    public AuthResult register(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmail(normalizedEmail) != null) {
            logger.warn("登録失敗").reason(LogReason.EMAIL_TAKEN)
                    .with(LogKey.EMAIL, normalizedEmail).log();
            throw emailTaken();
        }

        Instant now = clock.instant();

        User user = new User();
        user.setId("user_" + UUID.randomUUID());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setGuest(false);
        user.setEmailVerified(false);
        user.setCreatedAt(now);
        user.setLastLoginAt(now);
        try {
            userRepository.save(user);
        } catch (DuplicateKeyException e) {
            if (!isEmailConstraintViolation(e)) {
                // §11 #9 が扱うのはメール重複だけ。ほかの一意制約違反を 409 へ写像すると
                // 原因と表示が食い違うため、予期しないエラーとしてそのまま伝播させる
                throw e;
            }
            // 手順2の通過後に同時登録で uq_users_email 違反が起きた場合も重複として扱う（§11 #9）
            logger.warn("登録失敗").reason(LogReason.EMAIL_TAKEN_CONFLICT)
                    .with(LogKey.EMAIL, normalizedEmail).log();
            throw emailTaken();
        }

        playerInitializationService.initialize(user.getId());

        String rawVerificationToken = generateRawToken();
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUserId(user.getId());
        verificationToken.setTokenHash(hashToken(rawVerificationToken));
        verificationToken.setPurpose(VERIFY_EMAIL_PURPOSE);
        verificationToken.setExpiresAt(now.plus(verificationTokenExpire));
        verificationToken.setUsed(false);
        verificationToken.setCreatedAt(now);
        emailVerificationTokenRepository.save(verificationToken);

        AuthResult result = issueTokens(user);
        logger.info("アカウント登録").with(LogKey.USER_ID, user.getId()).log();
        verificationMailSender.send(user, rawVerificationToken);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>最終ログイン時刻の更新を含むため境界を持つ（tech_auth/account.md §12 手順5）。
     */
    @Override
    @Transactional
    public AuthResult login(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            logger.warn("ログイン失敗").reason(LogReason.EMAIL_NOT_FOUND)
                    .with(LogKey.EMAIL, normalizedEmail).log();
            throw invalidCredentials();
        }

        // Google連携のみのアカウントは bcrypt 照合そのものを行わない（§12 手順3）
        if (user.getPasswordHash() == null) {
            logger.warn("ログイン失敗").reason(LogReason.PASSWORD_NOT_SET).with(LogKey.USER_ID, user.getId()).log();
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            logger.warn("ログイン失敗").reason(LogReason.PASSWORD_MISMATCH).with(LogKey.USER_ID, user.getId()).log();
            throw invalidCredentials();
        }

        // email_verified の値でログインを止めない（§12 手順5）
        userRepository.updateLastLoginAt(user.getId(), clock.instant());

        logger.info("ログイン").with(LogKey.USER_ID, user.getId()).log();
        return issueTokens(user);
    }

    /**
     * {@inheritDoc}
     *
     * <p>失効の更新を伴うため境界を持つ（tech_auth/account.md §14）。
     */
    @Override
    @Transactional
    public void logout(String userId, String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hashToken(refreshToken));
        if (stored == null) {
            logger.warn("ログアウト失敗").reason(LogReason.REFRESH_NOT_FOUND)
                    .with(LogKey.USER_ID, userId).log();
            throw refreshInvalid();
        }

        if (!stored.getUserId().equals(userId)) {
            logger.warn("他ユーザーのリフレッシュトークンでログアウト")
                    .reason(LogReason.REFRESH_OWNER_MISMATCH).with(LogKey.USER_ID, userId).log();
            throw refreshInvalid();
        }

        if (stored.isRevoked()) {
            // 二重ログアウトは再送信・複数タブで起きる正常操作（§14 手順5）
            return;
        }

        refreshTokenRepository.updateRevokedById(stored.getId());
        logger.info("ログアウト").with(LogKey.USER_ID, userId).log();
    }

    /**
     * メールアドレスを正規化する。
     *
     * <p>前後の空白を除いて小文字化する（tech_auth/account.md §9「メールの正規化」）。
     * DBへ渡る値を常に正規化済みにすることで、{@code uq_users_email} がそのまま
     * 大小違いの重複を捕まえる。ロケール依存の変換を避けるため {@link Locale#ROOT} を使う。
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * メールの一意制約違反かどうかを判定する。
     *
     * <p>{@code users} は {@code uq_users_email} と {@code uq_users_google_id} の2本を持つため、
     * 制約名を見ずに重複と決めつけると、Google連携の重複が「メールが既に使われています」として
     * 返る（link-account で {@code google_id} を設定するようになった時点で顕在化する）。
     */
    private static boolean isEmailConstraintViolation(DuplicateKeyException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        return message.contains(EMAIL_UNIQUE_CONSTRAINT);
    }

    /**
     * 再利用を検知し、当該ユーザーのトークンを全失効させたうえで返す例外を作る。
     *
     * <p>失効済みの行を読んだ場合と、失効更新に負けた場合（同時実行）の両方から呼ぶ
     * （tech_auth.md §4「不正検知」）。{@code @Transactional(noRollbackFor = BusinessException.class)}
     * により、401 を返す経路でも全失効は確定する。
     */
    private BusinessException detectReuse(String userId) {
        logger.warn("不正リフレッシュトークン検知").reason(LogReason.REFRESH_REUSED)
                .with(LogKey.USER_ID, userId).log();
        refreshTokenRepository.updateRevokedByUserId(userId);
        return refreshInvalid();
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
     * 同じ方式を使う（tech_auth/account.md §9）。
     */
    private static String generateRawToken() {
        byte[] raw = new byte[RAW_TOKEN_BYTES];
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
     * 内部の切り分けは送出元が出すログの {@code reason} が持つ。
     */
    private static BusinessException refreshInvalid() {
        return authError("AUTH_REFRESH_INVALID");
    }

    /**
     * メール重複の例外を作る。
     *
     * <p>相手がゲスト・Google連携のみのアカウントでも同じ扱いにする（§10 手順2）。
     * メールアドレスは応答には載せない。ログへは {@link LogKey#EMAIL} がマスクした形でだけ残す（§9）。
     */
    private static BusinessException emailTaken() {
        return authError("AUTH_EMAIL_TAKEN");
    }

    /**
     * ログイン失敗の例外を作る。
     *
     * <p>未登録・パスワード未設定・不一致のどれであるかを**クライアントには**区別させない
     * （§12 末尾）。内部の切り分けはログの {@code reason} が持つ。
     */
    private static BusinessException invalidCredentials() {
        return authError("AUTH_INVALID_CREDENTIALS");
    }

    /**
     * 認証系のビジネス例外を作る。
     *
     * <p>載せるのはコードだけで、文言もステータスも持たせない（規約 exception.md §3 #5・§4 #4）。
     * 文言はフロントエンドがコードから組み立て、ステータスは Web 層の対応表が決める。
     */
    private static BusinessException authError(String code) {
        return new BusinessException(ResultMessages.error().add(code));
    }
}
