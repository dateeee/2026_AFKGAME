import { defineConfig, devices } from '@playwright/test'

import {
  E2E_BACKEND_PORT,
  E2E_DB_CONTAINER,
  E2E_DB_NAME,
  E2E_DB_USER,
  E2E_FRONTEND_PORT,
} from './tests/e2e/support/config'

const FRONTEND_URL = `http://localhost:${E2E_FRONTEND_PORT}`
const BACKEND_URL = `http://localhost:${E2E_BACKEND_PORT}`

export default defineConfig({
  testDir: './tests/e2e',

  // 全テストが1つの E2E 用DBを共有するため直列実行する
  // （テスト間の独立性は、各テストが自分のゲストプレイヤーを作ることで担保する）
  fullyParallel: false,
  workers: 1,

  forbidOnly: !!process.env.CI,
  // 不安定なテストをリトライで隠さない（integration-test スキル §4）
  retries: 0,
  timeout: 90_000,
  reporter: [['list']],

  use: {
    baseURL: FRONTEND_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // 既定は無制限。誤った画面で操作を待ち続けてテスト全体のタイムアウトになると
    // 原因が読めないため、操作単位で先に失敗させる
    actionTimeout: 15_000,
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  webServer: [
    {
      // 起動確認は /health（tech_operations.md §12.3）。DB疎通まで通って初めて ready とみなす
      command: 'node tests/e2e/support/serve-backend.mjs',
      url: `${BACKEND_URL}/health`,
      // 開発用バックエンド（本物のDBを掴んでいる）を再利用しないよう必ず新規起動する
      reuseExistingServer: false,
      timeout: 60_000,
      env: {
        E2E_BACKEND_PORT: String(E2E_BACKEND_PORT),
        E2E_DB_NAME,
        E2E_DB_CONTAINER,
        E2E_DB_USER,
      },
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: `npm run dev -- --port ${E2E_FRONTEND_PORT} --strictPort`,
      url: FRONTEND_URL,
      reuseExistingServer: false,
      timeout: 60_000,
      env: { VITE_API_PROXY_TARGET: BACKEND_URL },
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
})
