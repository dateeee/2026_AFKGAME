/**
 * E2E 用バックエンドの起動スクリプト（Playwright の webServer から呼ばれる）
 *
 * 開発用DB（`afkgame`）と開発用 Tomcat のどちらも汚さないよう、専用データベースを毎回
 * 作り直し、リポジトリ配下に専用の CATALINA_BASE を組み立てて war を配備する。
 * スキーマは Flyway が起動時に適用する（`afkgame-env` の `AfkgameEnvConfig#flyway`）。
 *
 * 前提（いずれも満たされていなければ理由つきで即座に失敗する）:
 *   1. `docker compose up -d` で PostgreSQL が起動している
 *   2. `mvn -DskipTests package` で `afkgame-web` の war がビルド済み
 *   3. Tomcat 11.0 があり `CATALINA_HOME`（または `E2E_CATALINA_HOME`）が指している
 *
 * ポートとDB名は playwright.config.ts が `webServer.env` で渡す
 * （素の Node で動かすため、TypeScript の定数を直接は読めない）。
 */

import { spawn, spawnSync } from 'node:child_process'
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const port = process.env.E2E_BACKEND_PORT ?? '8100'
const dbName = process.env.E2E_DB_NAME ?? 'afkgame_e2e'
const dbContainer = process.env.E2E_DB_CONTAINER ?? 'afkgame-postgres'
const dbUser = process.env.E2E_DB_USER ?? 'afkgame'

// Tomcat の停止ポート。待受ポートと衝突せず、開発用 Tomcat（既定 8005）とも重ならない値にする
const shutdownPort = String(Number(port) + 1)

// frontend/tests/e2e/support → リポジトリルート
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..')
// この E2E 専用の Tomcat インスタンス（.gitignore 済み）
const catalinaBase = join(repoRoot, 'frontend', 'tests', 'e2e', '.tomcat')

function fail(message) {
  console.error(`[serve-backend] ${message}`)
  process.exit(1)
}

/** `java` の実体。PATH に無い環境が多いため JAVA_HOME からも探す */
function javaBin() {
  if (process.env.E2E_JAVA) return process.env.E2E_JAVA
  if (process.env.JAVA_HOME) {
    const candidate = join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    if (existsSync(candidate)) return candidate
  }
  return 'java'
}

/** Tomcat のインストール先。bootstrap.jar の実在まで確かめる */
function catalinaHome() {
  const home = process.env.E2E_CATALINA_HOME ?? process.env.CATALINA_HOME
  if (!home) {
    fail(
      'Tomcat の場所が分からない。CATALINA_HOME（または E2E_CATALINA_HOME）に Tomcat 11.0 の\n' +
        '  展開先を設定すること（tech_operations.md §12.1）',
    )
  }
  if (!existsSync(join(home, 'bin', 'bootstrap.jar'))) {
    fail(`CATALINA_HOME=${home} に bin/bootstrap.jar が無い。Tomcat 11.0 の展開先を指しているか確認すること`)
  }
  return home
}

/** maven-war-plugin が出力した war（finalName は親POMで artifactId 固定） */
function findWar() {
  const war = join(repoRoot, 'backend', 'afkgame-web', 'target', 'afkgame-web.war')
  if (!existsSync(war)) {
    fail(`war が見つからない（${war}）。先に mvn -DskipTests package を実行すること`)
  }
  return war
}

/**
 * 専用の CATALINA_BASE を組み立てる。
 * conf は丸ごと複製してから server.xml のポートだけ差し替える
 * （catalina.properties・web.xml・logging.properties も Tomcat の起動に要るため）。
 */
function buildCatalinaBase(home, war) {
  rmSync(catalinaBase, { recursive: true, force: true })
  for (const dir of ['conf', 'webapps', 'logs', 'temp', 'work']) {
    mkdirSync(join(catalinaBase, dir), { recursive: true })
  }
  cpSync(join(home, 'conf'), join(catalinaBase, 'conf'), { recursive: true })

  const serverXml = join(catalinaBase, 'conf', 'server.xml')
  const patched = readFileSync(serverXml, 'utf-8')
    .replace('port="8005"', `port="${shutdownPort}"`)
    .replace('port="8080"', `port="${port}"`)
  writeFileSync(serverXml, patched)

  // ROOT コンテキストへ配備する。/health・/api/** を仕様どおりの絶対パスで受けるため
  // （コンテキストパスを付けない。tech_operations.md §12.1）
  cpSync(war, join(catalinaBase, 'webapps', 'ROOT.war'))
}

/**
 * PostgreSQL のコンテナに対して psql を1回実行する。
 *
 * `docker compose exec` ではなく `docker exec` + コンテナ名を使う（理由は config.ts）。
 */
function psql(database, sql) {
  return spawnSync(
    'docker',
    ['exec', '-i', dbContainer, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', dbUser, '-d', database, '-c', sql],
    { cwd: repoRoot, encoding: 'utf-8' },
  )
}

const home = catalinaHome()
const war = findWar()

// 前回の実行結果を持ち越さない（毎回クリーンな状態から始める）。
// FORCE は残った接続を切って DROP する（PostgreSQL 13 以降）
const dropped = psql('postgres', `DROP DATABASE IF EXISTS ${dbName} WITH (FORCE)`)
if (dropped.status !== 0) {
  fail(
    `E2E 用DBを作り直せない。docker compose up -d で PostgreSQL（${dbContainer}）を起動しているか確認すること\n${dropped.stderr ?? ''}`,
  )
}
const created = psql('postgres', `CREATE DATABASE ${dbName} OWNER ${dbUser}`)
if (created.status !== 0) fail(`CREATE DATABASE ${dbName} に失敗した\n${created.stderr ?? ''}`)

buildCatalinaBase(home, war)

// catalina.bat / catalina.sh を経由せず Bootstrap を直接起動する。
// スクリプト経由だと java が孫プロセスになり、Playwright の teardown で
// 停止しそこねて次回の起動がポート衝突で落ちるため。
const classpath = [join(home, 'bin', 'bootstrap.jar'), join(home, 'bin', 'tomcat-juli.jar')].join(
  process.platform === 'win32' ? ';' : ':',
)

const child = spawn(
  javaBin(),
  [
    `-Dcatalina.home=${home}`,
    `-Dcatalina.base=${catalinaBase}`,
    `-Djava.io.tmpdir=${join(catalinaBase, 'temp')}`,
    '-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',
    `-Djava.util.logging.config.file=${join(catalinaBase, 'conf', 'logging.properties')}`,
    '-classpath',
    classpath,
    'org.apache.catalina.startup.Bootstrap',
    'start',
  ],
  {
    cwd: repoRoot,
    stdio: 'inherit',
    env: {
      ...process.env,
      // 未設定だと AfkgameSettingsConfig が起動時に落とす（tech_operations.md §12.2）
      SPRING_PROFILES_ACTIVE: 'local',
      DATABASE_URL: `jdbc:postgresql://localhost:5432/${dbName}`,
    },
  },
)

child.on('exit', (code, signal) => process.exit(signal ? 1 : (code ?? 0)))
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal))
}
