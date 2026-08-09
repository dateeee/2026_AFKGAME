package com.afkgame.env.config;

import java.util.List;

/**
 * CORS の設定値を保持する。
 *
 * <p>値の正は docs/tech/nonfunctional/tech_security.md §11.2。許可オリジンは環境変数
 * {@code CORS_ORIGINS}（カンマ区切り）で明示し、本番でワイルドカードは使わない。
 * 組み立ては {@link com.afkgame.env.config.app.AfkgameSettingsConfig} が1か所で行う。
 *
 * @param origins 許可オリジン
 */
public record CorsSettings(List<String> origins) {
}
