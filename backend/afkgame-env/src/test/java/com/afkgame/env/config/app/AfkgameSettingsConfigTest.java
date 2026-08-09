package com.afkgame.env.config.app;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;

/**
 * {@link AfkgameSettingsConfig} の単体テスト。
 *
 * <p>仕様: docs/tech/nonfunctional/tech_operations.md §12.1（環境識別は {@code SPRING_PROFILES_ACTIVE}
 * の {@code local} / {@code production}）・§12.2（既定値を置かず、未設定なら起動時バリデーションで
 * 落とす。設定漏れが local へのフォールバックとして黙って成立するのを防ぐため）。
 *
 * <p>分岐観点: アクティブプロファイルの件数（1件 / 0件 / 複数）と、その値が許可リストに含まれるか。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class AfkgameSettingsConfigTest {

    /**
     * 指定したアクティブプロファイルを返す実行環境を作る。
     *
     * <p>実環境の {@code SPRING_PROFILES_ACTIVE} を拾わないようモックで固定する
     * （{@code StandardEnvironment} は未設定時にシステム環境変数へフォールバックする）。
     *
     * @param activeProfiles アクティブプロファイル
     * @return モックした {@link Environment}
     */
    private static Environment environmentWith(String... activeProfiles) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(activeProfiles);
        return environment;
    }

    @Nested
    @DisplayName("プロファイル検査")
    class TestProfileValidation {

        /** 許可された値がちょうど1つ ＝ 環境が一意に定まる。 */
        @ParameterizedTest
        @ValueSource(strings = {"local", "production"})
        void test_許可されたプロファイルが1件なら生成できる(String profile) {
            assertThatCode(() -> new AfkgameSettingsConfig(environmentWith(profile)))
                    .doesNotThrowAnyException();
        }

        /** 未設定。素の Spring は default プロファイルで起動するため、ここで落とす。 */
        @Test
        void test_プロファイルが未設定なら起動を中止する() {
            assertThatThrownBy(() -> new AfkgameSettingsConfig(environmentWith()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be exactly one of [local, production]")
                    .hasMessageContaining("but was []");
        }

        /** 複数指定。どちらの環境として動くかが曖昧になるため許容しない。 */
        @Test
        void test_プロファイルが複数なら起動を中止する() {
            assertThatThrownBy(() -> new AfkgameSettingsConfig(environmentWith("local", "production")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("but was [local, production]");
        }

        /** 想定外の値。綴り違い・未定義環境をフォールバックさせない。 */
        @Test
        void test_許可されていないプロファイルなら起動を中止する() {
            assertThatThrownBy(() -> new AfkgameSettingsConfig(environmentWith("staging")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("but was [staging]");
        }
    }
}
