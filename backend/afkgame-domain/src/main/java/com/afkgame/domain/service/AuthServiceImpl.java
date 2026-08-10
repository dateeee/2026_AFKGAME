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
import org.springframework.util.StringUtils;
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
    private final boolean googleConfigured;

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
        // 未設定は null と空文字の両方があるため（プロパティの既定値は空文字）、判定を1か所で済ませる
        this.googleConfigured = StringUtils.hasText(authSettings.googleClientId());
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

        String rawVerificationToken = saveVerificationToken(user.getId(), now);

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
     * {@inheritDoc}
     *
     * <p>ユーザー更新からトークン発行までを本メソッドの境界で1つにまとめる
     * （tech_auth/link.md §18 手順11）。確認メールの送信要求だけは境界の外へ出さず、
     * {@link #register} と同じく送信側が「コミット後・トランザクションの外」を担保する（§16.1）。
     */
    @Override
    @Transactional
    public AuthResult linkAccount(User user, String email, String rawPassword, String googleAuthCode) {
        boolean emailLink = email != null && rawPassword != null;
        boolean googleLink = googleAuthCode != null;
        // ちょうど一方でなければ連携先が決まらない（§18 手順2）。両方あるときも同じ扱いにする
        if (emailLink == googleLink) {
            logger.warn("移行失敗").reason(LogReason.LINK_PAYLOAD_INVALID)
                    .with(LogKey.USER_ID, user.getId()).log();
            throw authError("AUTH_LINK_PAYLOAD_INVALID");
        }

        if (googleLink) {
            throw googleUnavailable(user.getId());
        }

        // ゲスト扱いの判定はトークンではなくユーザーの現在値を見る（§18 末尾・手順4）
        if (!user.isGuest()) {
            logger.warn("移行失敗").reason(LogReason.ALREADY_REGISTERED)
                    .with(LogKey.USER_ID, user.getId()).log();
            throw authError("AUTH_ALREADY_REGISTERED");
        }

        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmail(normalizedEmail) != null) {
            logger.warn("移行失敗").reason(LogReason.EMAIL_TAKEN)
                    .with(LogKey.EMAIL, normalizedEmail).log();
            throw emailTaken();
        }

        Instant now = clock.instant();

        // id・display_name・created_at は触らない。更新する5列だけを差し替える（手順8）
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setGuest(false);
        user.setEmailVerified(false);
        user.setLastLoginAt(now);
        try {
            userRepository.updateLinkedAccount(user);
        } catch (DuplicateKeyException e) {
            // 手順6の通過後に同時移行で uq_users_email 違反が起きた場合（§19 #19）。
            // google_id は Phase 2 では設定しない（手順3 で 501）ため、制約名を見る必要が無い
            logger.warn("移行失敗").reason(LogReason.EMAIL_TAKEN_CONFLICT)
                    .with(LogKey.EMAIL, normalizedEmail).log();
            throw emailTaken();
        }

        String rawVerificationToken = saveVerificationToken(user.getId(), now);

        // 既存のリフレッシュトークンは失効させない（ユーザーIDも権限も変わらないため。手順10）
        AuthResult result = issueTokens(user);
        logger.info("アカウント移行").with(LogKey.USER_ID, user.getId()).log();
        verificationMailSender.send(user, rawVerificationToken);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>確認状態とトークンの使用済み更新を本メソッドの境界で1つにまとめる
     * （tech_auth/verify.md §20 手順9）。
     */
    @Override
    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken =
                emailVerificationTokenRepository.findByTokenHash(hashToken(token));
        if (verificationToken == null) {
            logger.warn("メール確認失敗").reason(LogReason.VERIFICATION_NOT_FOUND).log();
            throw verificationInvalid();
        }

        // 再設定トークンの流用を防ぐ（§20 手順3）
        if (!VERIFY_EMAIL_PURPOSE.equals(verificationToken.getPurpose())) {
            logger.warn("メール確認失敗").reason(LogReason.VERIFICATION_PURPOSE_MISMATCH)
                    .with(LogKey.USER_ID, verificationToken.getUserId()).log();
            throw verificationInvalid();
        }

        if (verificationToken.isUsed()) {
            // メール内リンクの再クリックは正常操作（§20 手順4。ログアウトの二重実行と同じ扱い）
            return;
        }

        // 現在時刻ちょうども「以前」に含む（§21 #10）
        if (!verificationToken.getExpiresAt().isAfter(clock.instant())) {
            logger.warn("メール確認失敗").reason(LogReason.VERIFICATION_EXPIRED)
                    .with(LogKey.USER_ID, verificationToken.getUserId()).log();
            throw verificationInvalid();
        }

        User user = userRepository.findById(verificationToken.getUserId());
        if (user == null) {
            logger.warn("メール確認失敗").reason(LogReason.USER_NOT_FOUND)
                    .with(LogKey.USER_ID, verificationToken.getUserId()).log();
            throw verificationInvalid();
        }

        // 既に true でも同じ値を書くだけで変化しない（§20 手順7・§21 #14）
        userRepository.updateEmailVerified(user.getId(), true);
        // 同じユーザーの他の確認トークンは変更しない（手順8）
        emailVerificationTokenRepository.updateUsedById(verificationToken.getId());

        logger.info("メール確認").with(LogKey.USER_ID, user.getId()).log();
    }

    /**
     * 確認トークンを1件作成して保存し、メール本文へ載せる生値を返す。
     *
     * <p>登録（account.md §10 手順6）と移行（link.md §18 手順9）で同じ形の行を作る。
     * 生値は保存せず、SHA-256 だけを残す。
     *
     * @param userId 宛先ユーザーのID
     * @param now 発行時刻（{@code created_at} と有効期限の起点。呼び出し側が1回だけ取った値）
     * @return メールへ載せる生トークン
     */
    private String saveVerificationToken(String userId, Instant now) {
        String rawToken = generateRawToken();

        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUserId(userId);
        verificationToken.setTokenHash(hashToken(rawToken));
        verificationToken.setPurpose(VERIFY_EMAIL_PURPOSE);
        verificationToken.setExpiresAt(now.plus(verificationTokenExpire));
        verificationToken.setUsed(false);
        verificationToken.setCreatedAt(now);
        emailVerificationTokenRepository.save(verificationToken);

        return rawToken;
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
     *
     * <p>メッセージに制約名が含まれる前提は
     * {@code UserRepositoryTest#同じメールアドレスでは2人目を作れない} が実DBで固定する
     * （本メソッドの単体テストはメッセージをモックで自作するため、そこでは退行を検出できない）。
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
     * Google連携が成立しないことを表す例外を作る（link.md §18 手順3）。
     *
     * <p>設定の有無でコードを分ける（運用者が「設定漏れ」と「Phase 2 では未対応」を切り分けられるように）。
     * クライアントへの文言は対応表がどちらも同じにしており、区別させない。
     */
    private BusinessException googleUnavailable(String userId) {
        if (googleConfigured) {
            logger.warn("移行失敗").reason(LogReason.GOOGLE_NOT_IMPLEMENTED)
                    .with(LogKey.USER_ID, userId).log();
            return authError("AUTH_GOOGLE_NOT_IMPLEMENTED");
        }
        logger.warn("移行失敗").reason(LogReason.GOOGLE_NOT_CONFIGURED)
                .with(LogKey.USER_ID, userId).log();
        return authError("AUTH_GOOGLE_NOT_CONFIGURED");
    }

    /**
     * メール確認失敗の例外を作る。
     *
     * <p>該当なし・用途違い・期限切れ・退会済みのどれであるかを**クライアントには**区別させない
     * （§20 手順2）。存在するトークンを総当たりで探る手がかりを与えないため。
     * 内部の切り分けはログの {@code reason} が持つ。
     */
    private static BusinessException verificationInvalid() {
        return authError("AUTH_VERIFICATION_INVALID");
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
