/** E2E 実行環境の固定値（playwright.config.ts とテストで共有する） */

/** E2E 専用バックエンドのポート（開発用の 8000 とは分ける） */
export const E2E_BACKEND_PORT = 8100

/** E2E 専用フロントのポート（開発用の 5173 とは分ける） */
export const E2E_FRONTEND_PORT = 5174

/** E2E 専用DBのファイル名（`backend/` 直下に作る。`*.db` は .gitignore 済み） */
export const E2E_DB_FILENAME = 'e2e.db'

/** tick間隔（秒）。バックエンドの `config.TICK_INTERVAL_SECONDS` と一致させる */
export const TICK_INTERVAL_SECONDS = 60
