package com.afkgame.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.afkgame.domain.model.RefreshToken;

/**
 * {@link RefreshTokenRepository} のテスト。
 *
 * <p>列・NULL 可否・一意制約の正は docs/tech/basic/tech_db/auth.md §1（{@code refresh_tokens}）、
 * ローテーションと不正検知の正は docs/tech/detail/tech_auth.md §4、パスワード変更時の全端末切断の正は
 * docs/tech/detail/tech_auth/password_reset.md §24 手順9。
 *
 * <p>観点: 全列往復・失効済みの行を二重に更新しないこと・ユーザー単位の一括失効を件数 0/1/2 で・
 * 他ユーザーの行を巻き込まないこと。{@code updateRevokedByUserId} は<b>更新件数を返さない</b>
 * （呼び出し側が件数で経路を分けない）ため、件数ごとの振る舞いはここでしか検証されない。
 * {@code AuthServiceImplTest} は Repository をモックするため、マッピングXMLの列名と SQL の条件も同様。
 */
class RefreshTokenRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    /** {@code refresh_tokens} へ1行作り、そのトークンハッシュを返す。 */
    private String givenToken(String userId, boolean revoked) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(uuid("hash"));
        token.setExpiresAt(FIXED_NOW.plus(Duration.ofDays(30)));
        token.setRevoked(revoked);
        token.setCreatedAt(FIXED_NOW);
        refreshTokenRepository.save(token);
        return token.getTokenHash();
    }

    /** 指定ハッシュの行が失効しているか。 */
    private boolean revoked(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash).isRevoked();
    }

    /** 指定ユーザーの未失効の行数。 */
    private int 未失効件数(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked = FALSE",
                Integer.class, userId);
    }

    @Nested
    class Test登録 {

        @Test
        void 保存した行はすべての列が往復する() {
            String userId = givenUser();

            String tokenHash = givenToken(userId, false);

            RefreshToken actual = refreshTokenRepository.findByTokenHash(tokenHash);
            assertThat(actual.getUserId()).isEqualTo(userId);
            assertThat(actual.getTokenHash()).isEqualTo(tokenHash);
            assertThat(actual.getExpiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofDays(30)));
            assertThat(actual.isRevoked()).isFalse();
            assertThat(actual.getCreatedAt()).isEqualTo(FIXED_NOW);
        }

        @Test
        void 未登録のハッシュで引くとnullを返す() {
            assertThat(refreshTokenRepository.findByTokenHash(uuid("hash"))).isNull();
        }
    }

    @Nested
    class Test1件の失効 {

        /** ローテーションで旧トークンを失効させる（tech_auth.md §4）。 */
        @Test
        void 未失効の1件を失効させて更新件数1を返す() {
            String tokenHash = givenToken(givenUser(), false);
            Integer id = refreshTokenRepository.findByTokenHash(tokenHash).getId();

            assertThat(refreshTokenRepository.updateRevokedById(id)).isEqualTo(1);
            assertThat(revoked(tokenHash)).isTrue();
        }

        /**
         * {@code AND revoked = FALSE} により、失効済みの行は更新できない。呼び出し側はこの0件を
         * 「並走した他のリクエストが先に失効させた＝再利用」として扱う（tech_auth.md §4）。
         */
        @Test
        void 失効済みの1件は更新件数0を返す() {
            String tokenHash = givenToken(givenUser(), true);
            Integer id = refreshTokenRepository.findByTokenHash(tokenHash).getId();

            assertThat(refreshTokenRepository.updateRevokedById(id)).isZero();
        }
    }

    @Nested
    class Testユーザー単位の一括失効 {

        /**
         * 未失効が無くても例外にせず、失効対象0件のまま正常終了する（呼び出し側は件数で経路を
         * 分けない）。
         *
         * <p>分岐: tech_auth/password_reset.md §25 #15
         */
        @Test
        void 未失効が無ければ何も変えずに正常終了する() {
            String userId = givenUser();
            String alreadyRevoked = givenToken(userId, true);

            assertThatCode(() -> refreshTokenRepository.updateRevokedByUserId(userId))
                    .doesNotThrowAnyException();

            assertThat(未失効件数(userId)).isZero();
            assertThat(revoked(alreadyRevoked)).isTrue();
        }

        /**
         * 未失効が1件ならその1件を失効させる。
         *
         * <p>分岐: tech_auth/password_reset.md §25 #16
         */
        @Test
        void 未失効が1件ならその1件を失効させる() {
            String userId = givenUser();
            String active = givenToken(userId, false);

            refreshTokenRepository.updateRevokedByUserId(userId);

            assertThat(revoked(active)).isTrue();
            assertThat(未失効件数(userId)).isZero();
        }

        /**
         * 未失効が2件以上ならすべて失効させる（パスワード変更で全端末を切断する。§24 手順9）。
         *
         * <p>分岐: tech_auth/password_reset.md §25 #17
         */
        @Test
        void 未失効が2件以上ならすべて失効させる() {
            String userId = givenUser();
            String first = givenToken(userId, false);
            String second = givenToken(userId, false);

            refreshTokenRepository.updateRevokedByUserId(userId);

            assertThat(revoked(first)).isTrue();
            assertThat(revoked(second)).isTrue();
            assertThat(未失効件数(userId)).isZero();
        }

        /** {@code WHERE user_id} により、他ユーザーの未失効トークンは巻き込まない。 */
        @Test
        void 他ユーザーの未失効トークンは巻き込まない() {
            String userId = givenUser();
            String otherUserId = givenUser();
            givenToken(userId, false);
            String othersToken = givenToken(otherUserId, false);

            refreshTokenRepository.updateRevokedByUserId(userId);

            assertThat(revoked(othersToken)).isFalse();
            assertThat(未失効件数(otherUserId)).isEqualTo(1);
        }
    }
}
