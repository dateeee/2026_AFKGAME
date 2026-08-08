package com.afkgame.domain;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Mapper 統合テスト用の最小 Spring コンテキスト。
 *
 * <p>{@code afkgame-domain} には起動クラスが無いため、テスト側にコンテキストの起点を置く。
 * {@code @ComponentScan} を持たせず Service を読み込まないことで、DataSource・Flyway・MyBatis だけを
 * 立ち上げる（本番の起動クラスは {@code afkgame-web} の {@code AfkgameApplication}）。
 *
 * <p>スキーマは {@code afkgame-initdb} の Flyway マイグレーションが埋め込み PostgreSQL へ適用する。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan("com.afkgame.domain.repository")
public class MapperTestApplication {
}
