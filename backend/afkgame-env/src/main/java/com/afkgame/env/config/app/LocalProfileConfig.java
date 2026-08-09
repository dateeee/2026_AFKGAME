package com.afkgame.env.config.app;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

/**
 * local プロファイル専用の既定値を読み込む。
 *
 * <p>素の Spring には Spring Boot の {@code application-{profile}} が無いため、環境差分の
 * 読み分けを本クラスが担う（docs/tech/nonfunctional/tech_operations.md §12.1・§12.2）。
 * production では読み込まれないので、{@code DATABASE_PASSWORD}・{@code JWT_SECRET} が未設定なら
 * プレースホルダを解決できず起動を中止する＝設定漏れのまま本番稼働することを防ぐ。
 *
 * <p>読み込み先は {@code META-INF/spring} の**サブディレクトリ**に置く。
 * 同ディレクトリ直下に置くと {@code classpath*:/META-INF/spring/*.properties} のグロブに入り、
 * プロファイルに関係なく読まれてしまうため。
 */
@Configuration
@Profile("local")
@PropertySource("classpath:/META-INF/spring/local/afkgame-local.properties")
public class LocalProfileConfig {
}
