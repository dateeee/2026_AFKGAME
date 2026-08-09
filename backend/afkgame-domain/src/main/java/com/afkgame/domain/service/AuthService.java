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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afkgame.domain.exception.AppException;
import com.afkgame.domain.model.RefreshToken;
import com.afkgame.domain.model.User;
import com.afkgame.domain.repository.RefreshTokenRepository;
import com.afkgame.domain.repository.UserRepository;
import com.afkgame.env.config.AuthSettings;

/**
 * 認証のドメインサービス。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §2「ゲストプレイ」・§3「認証フロー」・
 * §4「リフレッシュトークン」、エラーコードは docs/tech/basic/tech_logging.md「AUTH_ コード一覧」。
 *
 * <p>ゲスト作成・リフレッシュと、ゲスト作成時のプレイヤー初期化（tech_auth.md §8.2。実体は
 * {@link PlayerInitializationService}）を持つ。メール登録・ログイン・アカウント移行は
 * STEP 3-A-2 以降で追加する（docs/backlog/java_migration.md）。
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger("afkgame.auth");

    /** ゲストアカウントの既定表示名。本登録時にユーザーが変更する。 */
    private static final String DEFAULT_DISPLAY_NAME = "冒険者";

    /** リフレッシュトークンの生値のバイト数（Base64URL で44文字になる）。 */
    private static final int REFRESH_TOKEN_BYTES = 48;

    /** トークン生成用の暗号乱数。ゲーム乱数（{@code RandomFactory}）とは用途が異なり、共有してよい。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final Duration refreshTokenExpire;
    private final PlayerInitializationService playerInitializationService;
    private final Clock clock;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService, AuthSettings authSettings,
            PlayerInitializationService playerInitializationService, Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshTokenExpire = authSettings.refreshTokenExpire();
        this.playerInitializationService = playerInitializationService;
        this.clock = clock;
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
        user.setDisplayName(DEFAULT_DISPLAY_NAME);
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

    /** アクセストークンを発行し、リフレッシュトークンを新規保存する。 */
    private AuthResult issueTokens(User user) {
        // expires_at と created_at の差を有効期限そのものにするため、時刻は1回だけ取る
        Instant now = clock.instant();

        byte[] raw = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken record = new RefreshToken();
        record.setUserId(user.getId());
        record.setTokenHash(hashToken(rawRefreshToken));
        record.setExpiresAt(now.plus(refreshTokenExpire));
        record.setRevoked(false);
        record.setCreatedAt(now);
        refreshTokenRepository.save(record);

        return new AuthResult(user, jwtService.createAccessToken(user.getId(), user.isGuest()), rawRefreshToken);
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
}
