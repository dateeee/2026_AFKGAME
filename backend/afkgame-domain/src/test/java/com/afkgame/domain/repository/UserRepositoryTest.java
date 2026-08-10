package com.afkgame.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.afkgame.domain.model.User;

/**
 * {@link UserRepository} のテスト。
 *
 * <p>列・NULL 可否・一意制約の正は docs/tech/basic/tech_db/auth.md §1（{@code users}）、
 * メールでの検索と最終ログイン時刻の更新の正は
 * docs/tech/detail/tech_auth/account.md §12 手順2・手順6。
 *
 * <p>観点: 全列往復・NULL 許容列（{@code email} / {@code password_hash} / {@code google_id}）の両側・
 * {@code uq_users_email}・未登録の経路。{@code AuthServiceImplTest} は Repository をモックするため、
 * マッピングXMLの列名と SQL の条件はここでしか検証されない。
 */
class UserRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private UserRepository userRepository;

    /** メール登録済みユーザー（NULL 許容列にすべて値が入る）。 */
    private User メール登録済みユーザー(String email) {
        User user = new User();
        user.setId(uuid("user"));
        user.setEmail(email);
        user.setPasswordHash("$2a$12$abcdefghijklmnopqrstuv");
        user.setGoogleId(uuid("google"));
        user.setDisplayName("テストユーザー");
        user.setGuest(false);
        user.setEmailVerified(true);
        user.setCreatedAt(FIXED_NOW);
        user.setLastLoginAt(FIXED_NOW);
        return user;
    }

    /** ゲスト（NULL 許容列がすべて NULL）。 */
    private User ゲスト() {
        User user = new User();
        user.setId(uuid("guest"));
        user.setGuest(true);
        user.setEmailVerified(false);
        user.setCreatedAt(FIXED_NOW);
        user.setLastLoginAt(FIXED_NOW);
        return user;
    }

    /** 一意なメールアドレスを作る（{@code uq_users_email} の巻き添えを避ける）。 */
    private static String email() {
        return uuid("user") + "@example.com";
    }

    @Nested
    class Test登録 {

        @Test
        void メール登録済みユーザーはすべての列が往復する() {
            User expected = メール登録済みユーザー(email());

            userRepository.save(expected);

            assertThat(userRepository.findById(expected.getId()))
                    .usingRecursiveComparison().isEqualTo(expected);
        }

        /** 表示名の既定値はアプリ側で付与する（{@code User} のフィールド初期値。tech_db.md §4-2）。 */
        @Test
        void ゲストはメールとパスワードとGoogleIDがNULLのまま往復する() {
            User expected = ゲスト();

            userRepository.save(expected);

            User actual = userRepository.findById(expected.getId());
            assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
            assertThat(actual.getEmail()).isNull();
            assertThat(actual.getPasswordHash()).isNull();
            assertThat(actual.getGoogleId()).isNull();
            assertThat(actual.getDisplayName()).isEqualTo("冒険者");
        }

        /**
         * {@code AuthServiceImpl#isEmailConstraintViolation} は例外メッセージに制約名が含まれる前提で
         * メール重複と Google連携の重複を判別するため、**実DBが返す文字列に {@code uq_users_email} が
         * 含まれること**まで固定する。単体テストはメッセージをモックで自作するため、ドライバ更新で
         * 制約名が落ちてもそちらは赤くならない。
         */
        @Test
        void 同じメールアドレスでは2人目を作れない() {
            String email = email();
            userRepository.save(メール登録済みユーザー(email));

            assertThatThrownBy(() -> userRepository.save(メール登録済みユーザー(email)))
                    .isInstanceOf(DuplicateKeyException.class)
                    .extracting(e -> ((DuplicateKeyException) e).getMostSpecificCause().getMessage())
                    .asString().contains("uq_users_email");
        }

        @Test
        void 未登録のIDで引くとnullを返す() {
            assertThat(userRepository.findById("user_not_exists")).isNull();
        }
    }

    /** メールでの検索（{@code account.md} §12 手順2）。 */
    @Nested
    class Testメール検索 {

        @Test
        void メールアドレスで登録済みユーザーを引ける() {
            User expected = メール登録済みユーザー(email());
            userRepository.save(expected);

            assertThat(userRepository.findByEmail(expected.getEmail()))
                    .usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void 未登録のメールで引くとnullを返す() {
            assertThat(userRepository.findByEmail("unknown@example.com")).isNull();
        }

        /**
         * ゲスト行は {@code email} が NULL のため、どのメールで引いても一致しない（§12 末尾）。
         * 検索条件が {@code email IS NULL} を拾うように壊れれば、ここだけが赤くなる。
         *
         * <p>分岐: tech_auth/account.md §13 #7
         */
        @Test
        void ゲスト行はどのメールで引いても一致しない() {
            userRepository.save(ゲスト());

            assertThat(userRepository.findByEmail("guest@example.com")).isNull();
        }
    }

    /** 最終ログイン時刻の更新（{@code account.md} §12 手順6）。 */
    @Nested
    class Test最終ログイン時刻 {

        @Test
        void 最終ログイン時刻だけを更新する() {
            User registered = メール登録済みユーザー(email());
            userRepository.save(registered);
            Instant loggedInAt = FIXED_NOW.plus(Duration.ofDays(3));

            userRepository.updateLastLoginAt(registered.getId(), loggedInAt);

            User actual = userRepository.findById(registered.getId());
            assertThat(actual.getLastLoginAt()).isEqualTo(loggedInAt);
            // 作成時刻は更新の対象外（SET 句に混ざっていないこと）
            assertThat(actual.getCreatedAt()).isEqualTo(FIXED_NOW);
        }

        @Test
        void 未登録のIDを更新しても何も起きない() {
            User registered = メール登録済みユーザー(email());
            userRepository.save(registered);

            userRepository.updateLastLoginAt("user_not_exists", FIXED_NOW.plus(Duration.ofDays(3)));

            assertThat(userRepository.findById(registered.getId()).getLastLoginAt())
                    .isEqualTo(FIXED_NOW);
        }
    }

    /** ゲストの本登録化（{@code link.md} §18 手順8）。 */
    @Nested
    class Test本登録化 {

        /** 更新後の値を持つユーザー（{@code id} と {@code created_at} は元のまま）。 */
        private User 本登録化後(User guest, String email) {
            User updated = new User();
            updated.setId(guest.getId());
            updated.setEmail(email);
            updated.setPasswordHash("$2a$12$abcdefghijklmnopqrstuv");
            updated.setGuest(false);
            updated.setEmailVerified(false);
            updated.setLastLoginAt(FIXED_NOW.plus(Duration.ofDays(3)));
            return updated;
        }

        /**
         * ゲストのままなら5列だけを更新し、更新件数1を返す。{@code id}・{@code display_name}・
         * {@code created_at} は SET 句に無いため変わらない（手順8）。
         *
         * <p>分岐: tech_auth/link.md §19 #24
         */
        @Test
        void ゲストなら5列を更新して更新件数1を返す() {
            User guest = ゲスト();
            guest.setDisplayName("冒険者");
            userRepository.save(guest);
            String email = email();

            assertThat(userRepository.updateLinkedAccount(本登録化後(guest, email))).isEqualTo(1);

            User actual = userRepository.findById(guest.getId());
            assertThat(actual.getEmail()).isEqualTo(email);
            assertThat(actual.isGuest()).isFalse();
            assertThat(actual.isEmailVerified()).isFalse();
            assertThat(actual.getLastLoginAt()).isEqualTo(FIXED_NOW.plus(Duration.ofDays(3)));
            // 更新の対象外（SET 句に混ざっていないこと）
            assertThat(actual.getDisplayName()).isEqualTo("冒険者");
            assertThat(actual.getCreatedAt()).isEqualTo(FIXED_NOW);
        }

        /**
         * {@code AND is_guest = TRUE} により、本登録済みの行は更新できない。呼び出し側はこの0件を
         * 「同時移行で他のリクエストが先に本登録化した」として扱う（手順8）。
         *
         * <p>分岐: tech_auth/link.md §19 #25
         */
        @Test
        void 本登録済みなら更新件数0を返し何も変えない() {
            User registered = メール登録済みユーザー(email());
            userRepository.save(registered);

            assertThat(userRepository.updateLinkedAccount(本登録化後(registered, email()))).isZero();

            User actual = userRepository.findById(registered.getId());
            assertThat(actual.getEmail()).isEqualTo(registered.getEmail());
            assertThat(actual.isEmailVerified()).isTrue();
        }
    }
}
