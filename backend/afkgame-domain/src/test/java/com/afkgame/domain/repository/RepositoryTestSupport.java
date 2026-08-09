package com.afkgame.domain.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import com.afkgame.domain.RepositoryTestConfig;
import com.afkgame.env.test.EmbeddedPostgresSupport;

/**
 * Repository 統合テストの共通基盤。スキーマ定義の正は docs/tech/basic/tech_db/（実体は
 * afkgame-initdb の V1__initial_schema.sql）。
 *
 * <p>コンテキストは素の Spring（{@code @ExtendWith(SpringExtension)} +
 * {@code @ContextConfiguration}）で起こす。Spring Boot のテスト支援は使わない
 * （docs/process/coding_standards_backend/test.md §5）。
 *
 * <p>DB は埋め込み PostgreSQL。Docker を必要としない（docs/backlog/java_migration/steps.md §4
 * 「2R-0 の確定結果」）。接続先は {@link EmbeddedPostgresSupport} が払い出したデータベースへ
 * {@code @DynamicPropertySource} で差し替え、スキーマはコンテキスト起動時に Flyway が適用する。
 * 各テストは {@code @Transactional} でロールバックするため、テスト間で状態を共有しない。
 *
 * <p>親レコード（users → players → characters → equipment）の作成は、検証対象の Repository へ
 * 依存させないため {@link JdbcTemplate} で直接行う。
 */
@Tag("integration")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RepositoryTestConfig.class)
@ActiveProfiles("local")
@Transactional
abstract class RepositoryTestSupport {

    /** 時刻はすべてこの固定値から作る（実行のたびに結果が変わらないようにする）。 */
    protected static final Instant FIXED_NOW = Instant.parse("2026-08-08T12:00:00Z");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 埋め込み PostgreSQL へ接続先を差し替える。
     *
     * @param registry テストコンテキストのプロパティ
     */
    @DynamicPropertySource
    static void embeddedDatabase(DynamicPropertyRegistry registry) {
        String jdbcUrl = EmbeddedPostgresSupport.createDatabase();
        registry.add("database.url", () -> jdbcUrl);
        registry.add("database.user", EmbeddedPostgresSupport::username);
        registry.add("database.password", EmbeddedPostgresSupport::password);
    }

    /**
     * 一意な ID を作る（テスト間の衝突を避ける）。
     *
     * <p>{@code players.id} など大半の PK は {@code varchar(36)} で、UUID を丸ごと使うと接頭辞の分だけ
     * 溢れる。接頭辞は失敗時の判別に要るため、UUID 側を先頭8桁に切って収める。
     */
    protected static String uuid(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** {@code users} へ1行作り、その ID を返す。 */
    protected String givenUser() {
        String id = uuid("user");
        jdbcTemplate.update("""
                INSERT INTO users (id, display_name, is_guest, email_verified, created_at, last_login_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, "テストユーザー", true, false, Timestamp.from(FIXED_NOW), Timestamp.from(FIXED_NOW));
        return id;
    }

    /** {@code players} へ1行作り、その ID を返す（塔外・初期値相当）。 */
    protected String givenPlayer(String userId) {
        String id = uuid("player");
        jdbcTemplate.update("""
                INSERT INTO players (id, user_id, gold, tower_mode, hp_threshold,
                                     run_gold, highest_floor, last_tick_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, userId, 0L, "auto_repeat", 0.3, 0L, 0, Timestamp.from(FIXED_NOW),
                Timestamp.from(FIXED_NOW));
        return id;
    }

    /** {@code users} と {@code players} をまとめて作り、プレイヤー ID を返す。 */
    protected String givenPlayer() {
        return givenPlayer(givenUser());
    }

    /** {@code characters} へ1行作り、その ID を返す。 */
    protected String givenCharacter(String playerId) {
        String id = uuid("character");
        jdbcTemplate.update("""
                INSERT INTO characters (id, player_id, name, type, level, exp, hp, max_hp,
                                        base_atk, base_def, base_spd, limit_break, skill_points, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, playerId, "冒険者", "melee", 1, 0L, 100, 100, 10, 5, 8, 0, 0,
                Timestamp.from(FIXED_NOW));
        return id;
    }

    /** {@code equipment} へ1行作り、その ID を返す（装備済みスロットの検証用）。 */
    protected String givenEquipment(String playerId) {
        String id = uuid("equipment");
        jdbcTemplate.update("""
                INSERT INTO equipment (id, player_id, base_id, slot, rarity, level, enhance_level,
                                       is_two_handed, locked, acquired_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, playerId, "sword_001", "weapon", "common", 1, 0, false, false,
                Timestamp.from(FIXED_NOW));
        return id;
    }
}
