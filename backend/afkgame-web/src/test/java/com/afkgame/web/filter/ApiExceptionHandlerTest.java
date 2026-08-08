package com.afkgame.web.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.afkgame.domain.exception.AppException;
import com.afkgame.web.resource.RefreshResource;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * {@link ApiExceptionHandler} の単体テスト。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「統一エラーレスポンス形式」「グローバル例外ハンドラ」、
 * docs/tech/basic/tech_api_common.md「HTTPステータスコードの使い分け」。
 *
 * <p>分岐観点: {@code AppException}（コードとステータスをそのまま返す）/
 * Bean Validation 違反（422）/ 本文が読めない・未知フィールド（422）/
 * Spring MVC の標準例外（{@code HTTP_<status>}）/ 未捕捉例外（500）。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class ApiExceptionHandlerTest {

    /** 例外の種類ごとに応答を確かめるためのスタブ。テスト専用。 */
    @RestController
    static class StubApi {

        @PostMapping(path = "/stub/app-error")
        String appError() {
            throw new AppException("SHOP_INSUFFICIENT_GOLD", "ゴールドが足りません", 400);
        }

        @PostMapping(path = "/stub/unexpected")
        String unexpected() {
            throw new IllegalStateException("想定外");
        }

        @PostMapping(path = "/stub/validated")
        String validated(@Valid @RequestBody RefreshResource body) {
            return body.refreshToken();
        }

        @GetMapping(path = "/stub/required-param")
        String requiredParam(@RequestParam String name) {
            return name;
        }

        /** 標準例外のうち 500 で {@code handleExceptionInternal} を通るもの。 */
        @PostMapping(path = "/stub/not-writable")
        String notWritable() {
            throw new HttpMessageNotWritableException(
                    "Could not write JSON: com.afkgame.domain.model.Player");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubApi())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        MDC.put(RequestLogFilter.MDC_REQUEST_ID, "test-request-id");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("AppException")
    class TestAppException {

        @Test
        void test_コードとステータスをそのまま返す() throws Exception {
            mockMvc.perform(post("/stub/app-error"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SHOP_INSUFFICIENT_GOLD"))
                    .andExpect(jsonPath("$.error.message").value("ゴールドが足りません"))
                    .andExpect(jsonPath("$.error.requestId").value("test-request-id"));
        }
    }

    @Nested
    @DisplayName("バリデーション")
    class TestValidation {

        @Test
        void test_制約違反は422とVALIDATION_ERRORを返す() throws Exception {
            mockMvc.perform(post("/stub/validated")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        void test_本文が読めない場合も422を返す() throws Exception {
            mockMvc.perform(post("/stub/validated")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.requestId").value("test-request-id"));
        }
    }

    @Nested
    @DisplayName("Spring MVC の標準例外")
    class TestSpringMvcException {

        @Test
        void test_必須パラメータ不足はHTTP_400を返す() throws Exception {
            mockMvc.perform(get("/stub/required-param"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("HTTP_400"));
        }

        /**
         * 5xx は内部の型名・変換先を含みうるため、例外メッセージを応答へ載せない
         * （規約 §4-4「応答に内部情報を載せない」）。
         */
        @Test
        void test_5xxは例外メッセージを返さず定型文にする() throws Exception {
            mockMvc.perform(post("/stub/not-writable"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("INTERNAL_UNEXPECTED_ERROR"))
                    .andExpect(jsonPath("$.error.message").value("サーバー内部エラーが発生しました"));
        }
    }

    @Nested
    @DisplayName("未捕捉例外")
    class TestUnexpected {

        @Test
        void test_500とINTERNAL_UNEXPECTED_ERRORを返す() throws Exception {
            mockMvc.perform(post("/stub/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("INTERNAL_UNEXPECTED_ERROR"))
                    // スタックトレースはクライアントへ返さない（tech_logging.md）
                    .andExpect(jsonPath("$.error.message").value("サーバー内部エラーが発生しました"));
        }
    }
}
