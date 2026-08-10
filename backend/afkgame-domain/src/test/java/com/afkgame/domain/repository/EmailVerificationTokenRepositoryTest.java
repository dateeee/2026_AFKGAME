package com.afkgame.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.afkgame.domain.model.EmailVerificationToken;

/**
 * {@link EmailVerificationTokenRepository} のテスト。
 *
 * <p>列・NULL 可否・一意制約の正は docs/tech/basic/tech_db/auth.md §1
 * （{@code email_verification_tokens}）、使用済み化の正は
 * docs/tech/detail/tech_auth/verify.md §20 手順8・docs/tech/detail/tech_auth/password_reset.md
 * §22 手順4・§24 手順7。
 *
 * <p>観点: 全列往復・用途で絞らない検索・1件の使用済み化を未使用/使用済みの両側で・用途で絞った一括
 * 使用済み化を件数 0/1/2 で・確認トークンと他ユーザーを巻き込まないこと。
 * {@code updateUsedByUserIdAndPurpose} は<b>更新件数を返さない</b>（呼び出し側が件数で経路を分けない）
 * ため、件数ごとの振る舞いはここでしか検証されない。
 */
class EmailVerificationTokenRepositoryTest extends RepositoryTestSupport {

    private static final String VERIFY_EMAIL = "verify_email";

    private static final String PASSWORD_RESET = "password_reset";

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    /** {@code email_verification_tokens} へ1行作り、そのトークンハッシュを返す。 */
    private String givenToken(String userId, String purpose, boolean used) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setTokenHash(uuid("hash"));
        token.setPurpose(purpose);
        token.setExpiresAt(FIXED_NOW.plus(Duration.ofHours(1)));
        token.setUsed(used);
        token.setCreatedAt(FIXED_NOW);
        emailVerificationTokenRepository.save(token);
        return token.getTokenHash();
    }

    /** 指定ハッシュの行が使用済みか。 */
    private boolean used(String tokenHash) {
        return emailVerificationTokenRepository.findByTokenHash(tokenHash).isUsed();
    }

    /** 指定ハッシュの行のID。 */
    private Integer idOf(String tokenHash) {
        return emailVerificationTokenRepository.findByTokenHash(tokenHash).getId();
    }

    @Nested
    class Test登録 {

        @Test
        void 保存した行はすべての列が往復する() {
            String userId = givenUser();

            String tokenHash = givenToken(userId, PASSWORD_RESET, false);

            EmailVerificationToken actual = emailVerificationTokenRepository
                    .findByTokenHash(tokenHash);
            assertThat(actual.getUserId()).isEqualTo(userId);
            assertThat(actual.getTokenHash()).isEqualTo(tokenHash);
            assertThat(actual.getPurpose()).isEqualTo(PASSWORD_RESET);
            assertThat(actual.getExpiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofHours(1)));
            assertThat(actual.isUsed()).isFalse();
            assertThat(actual.getCreatedAt()).isEqualTo(FIXED_NOW);
        }

        /** 用途で絞らずに引く（用途違いは verify.md §21 #6 が「該当なし」と別扱いにする）。 */
        @Test
        void 用途が違っても同じハッシュで引ける() {
            String tokenHash = givenToken(givenUser(), VERIFY_EMAIL, false);

            assertThat(emailVerificationTokenRepository.findByTokenHash(tokenHash).getPurpose())
                    .isEqualTo(VERIFY_EMAIL);
        }

        @Test
        void 未登録のハッシュで引くとnullを返す() {
            assertThat(emailVerificationTokenRepository.findByTokenHash(uuid("hash"))).isNull();
        }
    }

    @Nested
    class Test1件の使用済み化 {

        /**
         * 未使用の1件を使用済みにする。更新件数1は再設定の続行条件になる（§24 手順7）。
         *
         * <p>分岐: tech_auth/password_reset.md §25 #20
         */
        @Test
        void 未使用の1件を使用済みにして更新件数1を返す() {
            String tokenHash = givenToken(givenUser(), PASSWORD_RESET, false);

            assertThat(emailVerificationTokenRepository.updateUsedById(idOf(tokenHash)))
                    .isEqualTo(1);
            assertThat(used(tokenHash)).isTrue();
        }

        /**
         * {@code AND used = FALSE} により、使用済みの行は更新できない。呼び出し側はこの0件を
         * 「並走した他のリクエストが先に使い切った」として扱う（§24 手順7）。
         *
         * <p>分岐: tech_auth/password_reset.md §25 #21
         */
        @Test
        void 使用済みの1件は更新件数0を返す() {
            String tokenHash = givenToken(givenUser(), PASSWORD_RESET, true);

            assertThat(emailVerificationTokenRepository.updateUsedById(idOf(tokenHash))).isZero();
        }

        /** 同じユーザーの他のトークンは変更しない（verify.md §20 手順8）。 */
        @Test
        void 同じユーザーの他のトークンは変更しない() {
            String userId = givenUser();
            String target = givenToken(userId, VERIFY_EMAIL, false);
            String other = givenToken(userId, VERIFY_EMAIL, false);

            emailVerificationTokenRepository.updateUsedById(idOf(target));

            assertThat(used(other)).isFalse();
        }
    }

    @Nested
    class Test用途で絞った一括使用済み化 {

        /**
         * 未使用の再設定トークンが無くても例外にせず、そのまま正常終了する（呼び出し側は0件でも
         * 新しい1件を作る）。
         *
         * <p>分岐: tech_auth/password_reset.md §23 #10
         */
        @Test
        void 未使用が無ければ何も変えずに正常終了する() {
            String userId = givenUser();
            String alreadyUsed = givenToken(userId, PASSWORD_RESET, true);

            assertThatCode(() -> emailVerificationTokenRepository
                    .updateUsedByUserIdAndPurpose(userId, PASSWORD_RESET))
                    .doesNotThrowAnyException();

            assertThat(used(alreadyUsed)).isTrue();
        }

        /**
         * 未使用が1件ならそれを使用済みにする。
         *
         * <p>分岐: tech_auth/password_reset.md §23 #11
         */
        @Test
        void 未使用が1件ならそれを使用済みにする() {
            String userId = givenUser();
            String unused = givenToken(userId, PASSWORD_RESET, false);

            emailVerificationTokenRepository.updateUsedByUserIdAndPurpose(userId, PASSWORD_RESET);

            assertThat(used(unused)).isTrue();
        }

        /**
         * 未使用が2件以上ならすべて使用済みにする（有効な再設定トークンを常に最新の1本だけに保つ。
         * §22 手順4）。
         *
         * <p>分岐: tech_auth/password_reset.md §23 #12
         */
        @Test
        void 未使用が2件以上ならすべて使用済みにする() {
            String userId = givenUser();
            String first = givenToken(userId, PASSWORD_RESET, false);
            String second = givenToken(userId, PASSWORD_RESET, false);

            emailVerificationTokenRepository.updateUsedByUserIdAndPurpose(userId, PASSWORD_RESET);

            assertThat(used(first)).isTrue();
            assertThat(used(second)).isTrue();
        }

        /** {@code AND purpose} により、確認トークンは巻き込まない（§22 手順4）。 */
        @Test
        void 確認トークンは巻き込まない() {
            String userId = givenUser();
            String verification = givenToken(userId, VERIFY_EMAIL, false);

            emailVerificationTokenRepository.updateUsedByUserIdAndPurpose(userId, PASSWORD_RESET);

            assertThat(used(verification)).isFalse();
        }

        /** {@code WHERE user_id} により、他ユーザーの再設定トークンは巻き込まない。 */
        @Test
        void 他ユーザーの再設定トークンは巻き込まない() {
            String userId = givenUser();
            String otherUserId = givenUser();
            givenToken(userId, PASSWORD_RESET, false);
            String othersToken = givenToken(otherUserId, PASSWORD_RESET, false);

            emailVerificationTokenRepository.updateUsedByUserIdAndPurpose(userId, PASSWORD_RESET);

            assertThat(used(othersToken)).isFalse();
        }
    }
}
