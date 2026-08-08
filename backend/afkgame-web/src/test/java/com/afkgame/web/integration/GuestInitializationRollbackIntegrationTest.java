package com.afkgame.web.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.afkgame.domain.repository.InventoryItemMapper;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * ゲスト作成の初期化が途中で失敗したとき、単一トランザクションが全体をロールバックすることの統合テスト。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §8.2 手順8（手順1〜7を単一トランザクションでコミット）。
 *
 * <p>失敗を DB 制約で作れない（公開APIからは一意制約違反へ到達できない）ため、
 * 手順6（初期所持アイテムの付与）の Mapper をモックへ差し替えて例外を強制する。
 * ロールバックの確認には「他のテストが作った行が無い」ことが要るため、
 * {@link AuthApiIntegrationTest} と同居させず専用クラスに置く。
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class GuestInitializationRollbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private InventoryItemMapper inventoryItemMapper;

    private int countOf(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    /**
     * 分岐: tech_auth.md #12
     */
    @Test
    @DisplayName("初期化の途中で失敗したら、ユーザーを含めて何も残らない")
    void test_初期化の途中で失敗したら全体をロールバックする() throws Exception {
        doThrow(new DataIntegrityViolationException("初期アイテムの付与に失敗"))
                .when(inventoryItemMapper).insert(any());

        // 業務例外へ写さないため、統一エラー形式の 500 になる（tech_logging.md「グローバル例外ハンドラ」）
        mockMvc.perform(post("/api/auth/guest"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_UNEXPECTED_ERROR"));

        assertThat(countOf("users")).isZero();
        assertThat(countOf("players")).isZero();
        assertThat(countOf("player_settings")).isZero();
        assertThat(countOf("characters")).isZero();
        assertThat(countOf("character_equip_slots")).isZero();
        assertThat(countOf("inventory_items")).isZero();
        assertThat(countOf("refresh_tokens")).isZero();
    }
}
