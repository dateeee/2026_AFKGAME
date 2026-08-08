package com.afkgame.web.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * 骨格（DataSource → Flyway → MyBatis → @RestController）が連結して動くことの統合テスト。
 *
 * <p>仕様: docs/tech/nonfunctional/tech_operations.md §12.3（ヘルスチェックの契約）、
 * §12.4（起動時に Flyway が自動適用）。スキーマ定義の正は docs/tech/basic/tech_db.md。
 *
 * <p>DB は埋め込み PostgreSQL（zonky）を使う。Docker に依存させないための選択で、
 * 経緯は docs/backlog/java_migration.md §2。
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class HealthApiIntegrationTest {

    /** V1 初期スキーマが作るテーブル（tech_db.md §1 の実装済み16件）。 */
    private static final List<String> V1_TABLES = List.of(
            "users", "refresh_tokens", "email_verification_tokens",
            "players", "player_settings", "tower_clear_records", "characters",
            "party_members", "learned_skills", "active_skill_slots",
            "equipment", "character_equip_slots", "inventory_items",
            "shop_daily_states", "shop_daily_slots",
            "battle_logs");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("GET /health は実DBへ疎通して 200 と db:ok を返す")
    void test_ヘルスチェックが実DBで200を返す() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.version").isNotEmpty())
                .andExpect(jsonPath("$.db").value("ok"));
    }

    @Test
    @DisplayName("起動時に Flyway の V1 が適用され、定義書どおりの16テーブルが揃う")
    void test_Flywayの初期スキーマが適用される() {
        List<String> actual = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
                        + " AND table_name <> 'flyway_schema_history'",
                String.class);

        assertThat(actual).containsExactlyInAnyOrderElementsOf(V1_TABLES);
    }

    @Test
    @DisplayName("一意制約名が定義書の命名規約どおりに作られる（known_issues #17）")
    void test_一意制約名が命名規約に従う() {
        List<String> actual = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints"
                        + " WHERE table_schema = 'public' AND constraint_type = 'UNIQUE'",
                String.class);

        assertThat(actual)
                .contains("uq_shop_daily_slots_state_slot")
                .doesNotContain("uq_shop_daily_slot_index")
                .allMatch(name -> name.startsWith("uq_"));
    }
}
