/**
 * E2E 用バックエンドの起動スクリプト（Playwright の webServer から呼ばれる）
 *
 * 開発用DB（afkgame.db）を汚さないよう、専用の e2e.db を毎回作り直してから uvicorn を起動する。
 * DB の切り替えは `DATABASE_URL`（tech_operations.md §12.2）で行う。
 *
 * ポートとDBファイル名は playwright.config.ts が `webServer.env` で渡す
 * （素の Node で動かすため、TypeScript の定数を直接は読めない）。
 */

import { spawn } from 'node:child_process'
import { rmSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const port = process.env.E2E_BACKEND_PORT ?? '8100'
const dbFilename = process.env.E2E_DB_FILENAME ?? 'e2e.db'

// frontend/tests/e2e/support → リポジトリルート → backend
const backendDir = resolve(dirname(fileURLToPath(import.meta.url)), '../../../../backend')

// 前回の実行結果を持ち越さない（毎回クリーンな状態から始める）
for (const suffix of ['', '-journal', '-wal', '-shm']) {
  rmSync(resolve(backendDir, `${dbFilename}${suffix}`), { force: true })
}

const python = process.env.E2E_PYTHON ?? (process.platform === 'win32' ? 'python' : 'python3')

const child = spawn(python, ['-m', 'uvicorn', 'app.main:app', '--port', port], {
  cwd: backendDir,
  stdio: 'inherit',
  env: { ...process.env, DATABASE_URL: `sqlite:///./${dbFilename}` },
})

child.on('exit', (code, signal) => process.exit(signal ? 1 : (code ?? 0)))
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal))
}
