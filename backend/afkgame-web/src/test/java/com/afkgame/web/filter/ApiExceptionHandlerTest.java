package com.afkgame.web.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.terasoluna.gfw.common.exception.BusinessException;
import org.terasoluna.gfw.common.exception.ResourceNotFoundException;
import org.terasoluna.gfw.common.exception.SystemException;
import org.terasoluna.gfw.common.message.ResultMessages;

import com.afkgame.env.logging.LogKey;
import com.afkgame.web.resource.RefreshResource;
import com.afkgame.web.resource.RegisterResource;

import jakarta.validation.Valid;

/**
 * {@link ApiExceptionHandler} の単体テスト。
 *
 * <p>仕様: docs/tech/basic/tech_error_handling.md「統一エラーレスポンス形式」
 * 「入力チェック違反の `details`」「グローバル例外ハンドラ」、
 * docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」、
 * 変換規約は docs/process/coding_standards_backend/exception.md §4。
 *
 * <p>分岐観点: 業務例外（対応表のコード / 未登録のコード / {@code ResourceNotFoundException} の 404）/
 * システム例外（500）/ 未捕捉例外（500）/ Bean Validation 違反（422 + {@code details}）/
 * 本文が JSON として壊れている（400）/ 解析はできたが型が合わない（422）/
 * Spring MVC の標準例外の 4xx（{@code HTTP_<status>}）と 5xx（定型文）。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class ApiExceptionHandlerTest {

    /** 例外の種類ごとに応答を確かめるためのスタブ。テスト専用。 */
    @RestController
    static class StubApi {

        @PostMapping(path = "/stub/business")
        String business() {
            throw new BusinessException(ResultMessages.error().add("AUTH_EMAIL_TAKEN"));
        }

        /** 対応表に無いコード。既定の 422 と定型文へ倒れることを確かめる。 */
        @PostMapping(path = "/stub/unknown-code")
        String unknownCode() {
            throw new BusinessException(ResultMessages.error().add("SHOP_INSUFFICIENT_GOLD"));
        }

        @PostMapping(path = "/stub/not-found")
        String notFound() {
            throw new ResourceNotFoundException(ResultMessages.error().add("AUTH_PLAYER_NOT_FOUND"));
        }

        @PostMapping(path = "/stub/system-error")
        String systemError() {
            throw new SystemException("INTERNAL_MASTER_DATA_MISSING", "not found item entity. item code [10-123456].");
        }

        @PostMapping(path = "/stub/unexpected")
        String unexpected() {
            throw new IllegalStateException("想定外");
        }

        @PostMapping(path = "/stub/validated")
        String validated(@Valid @RequestBody RefreshResource body) {
            return body.refreshToken();
        }

        @PostMapping(path = "/stub/register")
        String register(@Valid @RequestBody RegisterResource body) {
            return body.email();
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
        MDC.put(LogKey.REQUEST_ID.field(), "test-request-id");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("業務例外")
    class TestBusinessException {

        /** ステータスと文言は例外ではなく対応表が決める（規約 §4 #4）。 */
        @Test
        void test_対応表のステータスと文言を返す() throws Exception {
            mockMvc.perform(post("/stub/business"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_TAKEN"))
                    .andExpect(jsonPath("$.error.message").value("このメールアドレスは既に使用されています"))
                    .andExpect(jsonPath("$.error.requestId").value("test-request-id"))
                    // details は VALIDATION_ERROR のときだけ添える（tech_error_handling.md）
                    .andExpect(jsonPath("$.error.details").doesNotExist());
        }

        /** 例外の getMessage() は ResultMessages の toString() であり、応答へ写さない（規約 §2.1）。 */
        @Test
        void test_例外のメッセージを応答へ載せない() throws Exception {
            mockMvc.perform(post("/stub/business"))
                    .andExpect(jsonPath("$.error.message", Matchers.not(Matchers.containsString("ResultMessage"))));
        }

        @Test
        void test_対応表に無いコードは422と定型文になる() throws Exception {
            mockMvc.perform(post("/stub/unknown-code"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("SHOP_INSUFFICIENT_GOLD"))
                    .andExpect(jsonPath("$.error.message").value("リクエストを処理できませんでした"));
        }

        @Test
        void test_ResourceNotFoundExceptionは404を返す() throws Exception {
            mockMvc.perform(post("/stub/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("AUTH_PLAYER_NOT_FOUND"))
                    .andExpect(jsonPath("$.error.message").value("指定されたプレイヤーが見つかりません"));
        }
    }

    @Nested
    @DisplayName("システム例外")
    class TestSystemException {

        /** 分類2は分類3と同じ応答にそろえ、切り分けはログだけが持つ（規約 §1）。 */
        @Test
        void test_500と定型文を返し内部情報を漏らさない() throws Exception {
            mockMvc.perform(post("/stub/system-error"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("INTERNAL_UNEXPECTED_ERROR"))
                    .andExpect(jsonPath("$.error.message").value("サーバー内部エラーが発生しました"));
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
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].target").value("refreshToken"))
                    .andExpect(jsonPath("$.error.details[0].code").value("NotBlank"));
        }

        /**
         * 制約が複数ある API では、どの項目が落ちたかを {@code details} で判別できる（規約 §4 #9）。
         * 入力値そのもの（{@code rejectedValue}）は載せない。
         */
        @Test
        void test_違反した項目ごとにdetailsを返す() throws Exception {
            mockMvc.perform(post("/stub/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details", Matchers.hasSize(2)))
                    .andExpect(jsonPath("$.error.details[?(@.target == 'email')].code").value("Email"))
                    .andExpect(jsonPath("$.error.details[?(@.target == 'password')].code").value("Size"))
                    .andExpect(jsonPath("$..rejectedValue").doesNotExist())
                    .andExpect(jsonPath("$.error.message").value("リクエストの入力値が不正です"));
        }
    }

    @Nested
    @DisplayName("本文が読めない場合の切り分け")
    class TestNotReadable {

        /** JSON として解析できない構文破損は 400（送信処理のバグ。規約 §4 #10）。 */
        @Test
        void test_構文が壊れていれば400とHTTP_400を返す() throws Exception {
            mockMvc.perform(post("/stub/validated")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("HTTP_400"))
                    .andExpect(jsonPath("$.error.message").value("リクエストを処理できませんでした"))
                    .andExpect(jsonPath("$.error.requestId").value("test-request-id"));
        }

        /** 解析はできたがスキーマに合わないものは 422（入力のやり直し。規約 §4 #10）。 */
        @Test
        void test_型が合わなければ422とVALIDATION_ERRORを返す() throws Exception {
            mockMvc.perform(post("/stub/validated")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":{\"nested\":1}}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("Spring MVC の標準例外")
    class TestSpringMvcException {

        /**
         * 4xx も例外メッセージを応答へ載せない（規約 §4 #1）。
         * {@code MethodArgumentTypeMismatchException} 等が変換先のクラス名を含むため。
         */
        @Test
        void test_必須パラメータ不足はHTTP_400と定型文を返す() throws Exception {
            mockMvc.perform(get("/stub/required-param"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("HTTP_400"))
                    .andExpect(jsonPath("$.error.message").value("リクエストを処理できませんでした"));
        }

        /**
         * 5xx は内部の型名・変換先を含みうるため、例外メッセージを応答へ載せない
         * （規約 §4 #1「応答に内部情報を載せない」）。
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
                    // スタックトレースはクライアントへ返さない（tech_error_handling.md）
                    .andExpect(jsonPath("$.error.message").value("サーバー内部エラーが発生しました"));
        }
    }
}
