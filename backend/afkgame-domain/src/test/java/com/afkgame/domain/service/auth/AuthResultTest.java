package com.afkgame.domain.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.afkgame.domain.model.User;

/**
 * {@link AuthResult} の単体テスト。
 *
 * <p>仕様: docs/process/coding_standards_backend/logging/application.md §3.1 規約2
 * 「戻り値のフィールドをtoString()から外すことで担保する」。AOP境界ログの {@code result} は
 * 本レコードの {@code toString()} をそのまま出力するため、生トークンを含めない。
 *
 * <p>骨格構築の横断基盤であり詳細設計の分岐一覧を持たないため、分岐マーカーは付けない。
 */
@Tag("unit")
class AuthResultTest {

    @Test
    @DisplayName("toString はアクセストークン・リフレッシュトークンを伏せる")
    void test_トークンを伏せる() {
        User user = new User();
        user.setId("user_001");
        AuthResult result = new AuthResult(user, "raw-access-token", "raw-refresh-token");

        String text = result.toString();

        assertThat(text).doesNotContain("raw-access-token", "raw-refresh-token");
        assertThat(text).contains("accessToken=****", "refreshToken=****");
    }
}
