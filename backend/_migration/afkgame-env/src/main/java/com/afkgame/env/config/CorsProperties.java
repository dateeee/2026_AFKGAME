package com.afkgame.env.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS の設定値（{@code afkgame.cors.*}）。
 *
 * <p>値の正は docs/tech/nonfunctional/tech_security.md §11.2。
 * 許可オリジンは環境変数 {@code CORS_ORIGINS}（カンマ区切り）で明示し、本番でワイルドカードは使わない。
 *
 * @param origins 許可オリジン
 */
@ConfigurationProperties(prefix = "afkgame.cors")
public record CorsProperties(List<String> origins) {
}
