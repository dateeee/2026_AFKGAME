/**
 * E2E 用バックエンドの起動スクリプト（Playwright の webServer から呼ばれる）
 *
 * 開発用DB（`afkgame`）を汚さないよう、専用データベースを毎回作り直してから
 * 実行可能 jar を起動する。スキーマは Flyway が起動時に適用する（application.yml）。
 *
 * 前提（どちらも満たされていなければ理由つきで即座に失敗する）:
 *   1. `docker compose up -d` で PostgreSQL が起動している
 *   2. `mvn -DskipTests package` で `afkgame-web` の jar がビルド済み
 *
 * ポートとDB名は playwright.config.ts が `webServer.env` で渡す
 * （素の Node で動かすため、TypeScript の定数を直接は読めない）。
 */

import { spawn, spawnSync } from 'node:child_process'
import { existsSync, readdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const port = process.env.E2E_BACKEND_PORT ?? '8100'
const dbName = process.env.E2E_DB_NAME ?? 'afkgame_e2e'
const dbService = process.env.E2E_DB_SERVICE ?? 'postgres'
const dbUser = process.env.E2E_DB_USER ?? 'afkgame'

// frontend/tests/e2e/support → リポジトリルート
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..')

/** `java` の実体。PATH に無い環境が多いため JAVA_HOME からも探す */
function javaBin() {
  if (process.env.E2E_JAVA) return process.env.E2E_JAVA
  if (process.env.JAVA_HOME) {
    const candidate = join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    if (existsSync(candidate)) return candidate
  }
  return 'java'
}

/** spring-boot-maven-plugin が repackage した実行可能 jar を探す */
function findJar() {
  const target = join(repoRoot, 'backend', 'afkgame-web', 'target')
  if (!existsSync(target)) {
    fail(`jar が見つからない（${target} が無い）。先に mvn -DskipTests package を実行すること`)
  }
  // `*.jar.original`（repackage 前）は拾わない
  const jars = readdirSync(target).filter((f) => f.startsWith('afkgame-web-') && f.endsWith('.jar'))
  if (jars.length === 0) {
    fail(`${target} に afkgame-web-*.jar が無い。先に mvn -DskipTests package を実行すること`)
  }
  return join(target, jars[0])
}

function fail(message) {
  console.error(`[serve-backend] ${message}`)
  process.exit(1)
}

/** docker compose の PostgreSQL に対して psql を1回実行する */
function psql(database, sql) {
  return spawnSync(
    'docker',
    ['compose', 'exec', '-T', dbService, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', dbUser, '-d', database, '-c', sql],
    { cwd: repoRoot, encoding: 'utf-8' },
  )
}

// 前回の実行結果を持ち越さない（毎回クリーンな状態から始める）。
// FORCE は残った接続を切って DROP する（PostgreSQL 13 以降）
const dropped = psql('postgres', `DROP DATABASE IF EXISTS ${dbName} WITH (FORCE)`)
if (dropped.status !== 0) {
  fail(
    `E2E 用DBを作り直せない。docker compose up -d で PostgreSQL を起動しているか確認すること\n${dropped.stderr ?? ''}`,
  )
}
const created = psql('postgres', `CREATE DATABASE ${dbName} OWNER ${dbUser}`)
if (created.status !== 0) fail(`CREATE DATABASE ${dbName} に失敗した\n${created.stderr ?? ''}`)

const child = spawn(javaBin(), ['-jar', findJar()], {
  cwd: repoRoot,
  stdio: 'inherit',
  env: {
    ...process.env,
    APP_ENV: 'local',
    SERVER_PORT: port,
    DATABASE_URL: `jdbc:postgresql://localhost:5432/${dbName}`,
  },
})

child.on('exit', (code, signal) => process.exit(signal ? 1 : (code ?? 0)))
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal))
}
