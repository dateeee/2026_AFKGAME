package com.afkgame.web.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.afkgame.domain.service.health.HealthService;

/**
 * {@link HealthApi} の単体テスト。
 *
 * <p>仕様: docs/tech/nonfunctional/tech_operations.md §12.3。
 * 正常時は {@code 200 / {"status":"ok","version":"x.y.z","db":"ok"}}、
 * 異常時は {@code 503 / {"status":"degraded","db":"error"}} を返す。
 *
 * <p>分岐観点: DB疎通あり（200）/ DB疎通なし（503）。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class HealthApiTest {

    @Mock
    private HealthService healthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // バージョンは Maven のリソースフィルタが埋めた application.version を注入する形（2R-D）
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthApi(healthService, "0.1.0")).build();
    }

    @Nested
    @DisplayName("GET /health")
    class TestHealth {

        @Test
        void test_DB疎通できるとき200とokを返す() throws Exception {
            when(healthService.isDatabaseReachable()).thenReturn(true);

            mockMvc.perform(get("/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"))
                    .andExpect(jsonPath("$.version").value("0.1.0"))
                    .andExpect(jsonPath("$.db").value("ok"));
        }

        @Test
        void test_DB疎通できないとき503とdegradedを返す() throws Exception {
            when(healthService.isDatabaseReachable()).thenReturn(false);

            mockMvc.perform(get("/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("degraded"))
                    .andExpect(jsonPath("$.db").value("error"))
                    // 異常時の応答に version は含めない（§12.3）
                    .andExpect(jsonPath("$.version").doesNotExist());
        }
    }
}
