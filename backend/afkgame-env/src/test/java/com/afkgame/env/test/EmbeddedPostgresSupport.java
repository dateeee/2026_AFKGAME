package com.afkgame.env.test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * 統合テスト用の埋め込み PostgreSQL。
 *
 * <p>Docker に依存させないための選択で、方式の確定は docs/backlog/java_migration/steps.md §4
 * 「2R-0 の確定結果」。Spring Boot 前提の {@code embedded-database-spring-test}（zonky の
 * {@code @AutoConfigureEmbeddedDatabase}）は使わず、{@code EmbeddedPostgres} を直接起動する。
 *
 * <p>サーバーは JVM（surefire / failsafe のフォーク）に1つだけ起動し、データベースは
 * {@link #createDatabase()} でテスト用コンテキストごとに払い出す。Spring TestContext は
 * 設定が同じテストクラスでコンテキストを共有するため、払い出しの単位も自然とコンテキスト単位になる
 * （コミットする統合テストどうしが同じ行を見ないようにするための分離）。
 *
 * <p>使い方は {@code @DynamicPropertySource} で接続情報を差し込む形にそろえる。こうすると
 * {@code AfkgameEnvConfig} の {@code DataSource} 定義をテスト側で上書きせずに済む。
 *
 * <pre>{@code
 * @DynamicPropertySource
 * static void embeddedDatabase(DynamicPropertyRegistry registry) {
 *     String jdbcUrl = EmbeddedPostgresSupport.createDatabase();
 *     registry.add("database.url", () -> jdbcUrl);
 *     registry.add("database.user", EmbeddedPostgresSupport::username);
 *     registry.add("database.password", EmbeddedPostgresSupport::password);
 * }
 * }</pre>
 *
 * <p>スキーマは各コンテキストの起動時に Flyway（{@code AfkgameEnvConfig#flyway}）が
 * {@code V1__initial_schema.sql} を適用する。
 */
public final class EmbeddedPostgresSupport {

    /** 埋め込み PostgreSQL のスーパーユーザー（zonky の既定）。 */
    private static final String USER = "postgres";

    /** ローカル接続のみで trust 認証のため、値そのものは検証されない。 */
    private static final String PASSWORD = "postgres";

    /** JVM に1つだけ起動するサーバー。 */
    private static final EmbeddedPostgres SERVER = start();

    /** 払い出したデータベースの連番（名前の衝突を避ける）。 */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private EmbeddedPostgresSupport() {
    }

    private static EmbeddedPostgres start() {
        try {
            EmbeddedPostgres server = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> close(server)));
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("埋め込み PostgreSQL の起動に失敗した", e);
        }
    }

    private static void close(EmbeddedPostgres server) {
        try {
            server.close();
        } catch (IOException e) {
            throw new IllegalStateException("埋め込み PostgreSQL の停止に失敗した", e);
        }
    }

    /**
     * 空のデータベースを1つ払い出し、その JDBC URL を返す。
     *
     * @return 払い出したデータベースへの JDBC URL
     */
    public static String createDatabase() {
        String name = "afkgame_test_" + SEQUENCE.incrementAndGet();
        try (Connection connection = SERVER.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("テスト用データベースの作成に失敗した (" + name + ")", e);
        }
        return SERVER.getJdbcUrl(USER, name);
    }

    /**
     * @return 接続ユーザー名
     */
    public static String username() {
        return USER;
    }

    /**
     * @return 接続パスワード
     */
    public static String password() {
        return PASSWORD;
    }
}
