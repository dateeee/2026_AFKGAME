package com.afkgame.web.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.afkgame.env.config.AuthSettings;
import com.afkgame.web.resource.auth.RegisterResource;

import jakarta.validation.constraints.Size;

/**
 * 設定値と Bean Validation の制約が一致していることの統合テスト。
 *
 * <p>仕様: 入力長の**数値の正**は docs/tech/detail/tech_auth/account.md §9「入力長」
 * （要求値は docs/tech/nonfunctional/tech_security.md §11.3）、設定キーの正は
 * docs/tech/basic/tech_backend.md §4.2。
 *
 * <p>観点: パスワード長は {@code @Size} がコンパイル時定数しか取れないため、
 * {@link RegisterResource} のリテラルと {@code afkgame.properties} の2か所に現れる。実際に効くのは
 * 前者だけで、後者を変えても挙動は動かない。ここで組み上がった {@link AuthSettings} と突き合わせ、
 * **片方だけを直した変更を赤くする**（キーの取り違えも {@code AfkgameSettingsConfig} 経由で捕まる）。
 *
 * <p>コンテキストと DB の起こし方は {@link WebIntegrationTestSupport}。
 */
class AuthSettingsConsistencyIntegrationTest extends WebIntegrationTestSupport {

    @Autowired
    private AuthSettings authSettings;

    /** レコードコンポーネントに付けた制約は、{@code @Size} が METHOD を対象に含むためアクセサへ伝播する。 */
    private static Size sizeOf(String component) throws NoSuchMethodException {
        return RegisterResource.class.getMethod(component).getAnnotation(Size.class);
    }

    @Test
    @DisplayName("パスワード長の @Size が afkgame.auth.password.{min,max}.length と一致する")
    void test_パスワード長の制約は設定値と一致する() throws NoSuchMethodException {
        Size password = sizeOf("password");

        assertThat(password.min()).isEqualTo(authSettings.passwordMinLength());
        assertThat(password.max()).isEqualTo(authSettings.passwordMaxLength());
    }
}
