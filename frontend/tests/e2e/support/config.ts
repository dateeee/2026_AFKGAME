/** E2E 実行環境の固定値（playwright.config.ts とテストで共有する） */

/** E2E 専用バックエンドのポート（開発用の 8080 とは分ける） */
export const E2E_BACKEND_PORT = 8100

/** E2E 専用フロントのポート（開発用の 5173 とは分ける） */
export const E2E_FRONTEND_PORT = 5174

/**
 * E2E 専用データベース名。
 *
 * docker-compose.yml の PostgreSQL 内に作る（開発用の `afkgame` とは別データベース）。
 * 毎回 DROP → CREATE してから起動し、スキーマは Flyway が適用する。
 */
export const E2E_DB_NAME = 'afkgame_e2e'

/** PostgreSQL を動かしている docker compose のサービス名（docker-compose.yml） */
export const E2E_DB_SERVICE = 'postgres'

/** DB ユーザー（docker-compose.yml の `POSTGRES_USER`） */
export const E2E_DB_USER = 'afkgame'

/** tick間隔（秒）。バックエンドの `afkgame.tick-interval-seconds` と一致させる */
export const TICK_INTERVAL_SECONDS = 60
