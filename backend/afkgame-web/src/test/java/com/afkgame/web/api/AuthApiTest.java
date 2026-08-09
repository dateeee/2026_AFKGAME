package com.afkgame.web.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.afkgame.domain.model.User;
import com.afkgame.domain.service.AuthResult;
import com.afkgame.domain.service.AuthService;

/**
 * {@link AuthApi} の単体テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §5（リクエスト/レスポンス例）、
 * docs/tech/basic/tech_api_common.md §5.0（ボディのキーは camelCase）。
 *
 * <p>分岐観点: ゲスト作成 / リフレッシュ（いずれも同じ応答形式）。エラー系はサービス層が
 * {@code AppException} を投げ、{@code ApiExceptionHandler} が応答へ変換する。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AuthApiTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthApi(authService)).build();
    }

    private static AuthResult authResult() {
        User user = new User();
        user.setId("guest_550e8400");
        user.setDisplayName("冒険者");
        user.setGuest(true);
        user.setEmailVerified(false);
        return new AuthResult(user, "access-token", "refresh-token");
    }

    @Nested
    @DisplayName("POST /api/auth/guest")
    class TestCreateGuest {

        @Test
        void test_トークンペアとユーザー情報を返す() throws Exception {
            when(authService.createGuest()).thenReturn(authResult());

            mockMvc.perform(post("/api/auth/guest"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.user.id").value("guest_550e8400"))
                    .andExpect(jsonPath("$.user.displayName").value("冒険者"))
                    .andExpect(jsonPath("$.user.isGuest").value(true))
                    .andExpect(jsonPath("$.user.emailVerified").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class TestRefresh {

        @Test
        void test_受け取ったトークンでローテーションする() throws Exception {
            when(authService.refresh(any())).thenReturn(authResult());

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"old-refresh-token\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

            verify(authService).refresh("old-refresh-token");
        }
    }
}
