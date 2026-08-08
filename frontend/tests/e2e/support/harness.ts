/**
 * E2E 共通ヘルパー（L2: Playwright）
 *
 * 画面操作は必ず UI から行い、DB を直接触るのは **時刻の巻き戻しだけ** に限る。
 * tick は 60 秒間隔（固定）のため、実時間を待つとテストが成立しない。
 * L1 の `rewind` フィクスチャと同じ考え方で、`last_tick_at` を過去へずらして
 * 「その時間だけ放置した」状態を作る。
 */

import { spawnSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { expect, type Locator, type Page } from '@playwright/test'

import { E2E_DB_NAME, E2E_DB_SERVICE, E2E_DB_USER, TICK_INTERVAL_SECONDS } from './config'

// frontend/tests/e2e/support → リポジトリルート（docker compose の実行位置）
const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..')

/**
 * 全プレイヤーの `last_tick_at` を `seconds` 秒前へ戻す。
 *
 * テストは直列実行でプレイヤーもテストごとに作られるため、対象を絞らず一括で更新する。
 * DB は docker compose の PostgreSQL にあるため、コンテナ内の psql から更新する
 * （E2E 実行中はバックエンドが同じDBを掴んでいるが、行ロックは短時間で解ける）。
 */
export function rewindLastTick(seconds: number): void {
  const result = spawnSync(
    'docker',
    [
      'compose', 'exec', '-T', E2E_DB_SERVICE,
      'psql', '-v', 'ON_ERROR_STOP=1', '-U', E2E_DB_USER, '-d', E2E_DB_NAME,
      '-c', `UPDATE players SET last_tick_at = now() - interval '${seconds} seconds'`,
    ],
    { cwd: REPO_ROOT, encoding: 'utf-8' },
  )
  if (result.status !== 0) {
    throw new Error(`last_tick_at を巻き戻せなかった: ${result.stderr ?? result.error?.message}`)
  }
}

/** ホーム画面が読み込み終わるまで待つ（`isLoading` が下りて tick 完了済み） */
export async function waitForHome(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: 'キャラクター' })).toBeVisible()
}

/**
 * オフライン報酬モーダルが出ていたら閉じる。
 *
 * 放置ぶんの tick を処理すると必ず前面に出るため、閉じないと後続の操作を遮る。
 */
export async function dismissOfflineSummary(page: Page): Promise<void> {
  const modal = page
    .locator('.modal-overlay')
    .filter({ has: page.getByRole('heading', { name: 'オフライン報酬' }) })
  if ((await modal.count()) === 0) return
  await modal.getByRole('button', { name: 'OK' }).click()
  await expect(modal).toHaveCount(0)
}

/** ログイン画面から「ゲストで始める」でホームまで進む */
export async function startAsGuest(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByRole('button', { name: 'ゲストで始める' }).click()
  await expect(page).toHaveURL(/\/$/)
  await waitForHome(page)
}

/**
 * ホームを開き直してサーバーの最新状態を取り込む。
 *
 * 全画面読み込みで `initialize()` が走り `GET /api/game/state` を取り直す。
 * `last_tick_at` を戻していないので tick は進まず、戦闘の乱数が混ざらない。
 */
export async function reloadHome(page: Page): Promise<void> {
  await page.goto('/')
  await waitForHome(page)
}

/**
 * `ticks` 回ぶん放置した状態にしてホームを開き直す。
 *
 * 再読み込みで `useGameLoop.initialize()` が走り、その中の `POST /api/battle/tick` が
 * 経過時間ぶんをまとめて処理する。tick を起こすのはあくまでアプリ側。
 */
export async function advanceTicks(page: Page, ticks: number): Promise<void> {
  rewindLastTick(ticks * TICK_INTERVAL_SECONDS + 1)
  await page.goto('/')
  await waitForHome(page)
  await dismissOfflineSummary(page)
}

/**
 * 条件が満たされるまで放置と再読み込みを繰り返す。
 *
 * ドロップやゴールドは乱数を含むため、固定回数の tick では確実に得られない。
 * 固定スリープではなく「条件が満たされるまで進める」形にして安定させる。
 */
export async function advanceUntil(
  page: Page,
  condition: () => Promise<boolean>,
  {
    ticksPerRound = 30,
    maxRounds = 6,
    prepare,
  }: { ticksPerRound?: number; maxRounds?: number; prepare?: () => Promise<void> } = {},
): Promise<void> {
  for (let round = 0; round < maxRounds; round += 1) {
    if (prepare) await prepare()
    await advanceTicks(page, ticksPerRound)
    if (await condition()) return
  }
  throw new Error(
    `${maxRounds} 回（各 ${ticksPerRound} tick）進めても条件を満たさなかった。` +
      'ドロップ率・報酬量の変更、または進行が止まっている可能性がある',
  )
}

/**
 * ゴールド表示（ホーム・ショップ共通）から数値を読む。
 *
 * `formatGold` は 10,000 以上を `12.3K` のように丸めるため、その桁では 25G の増減を
 * 読み取れない。丸め表記に入ったら黙って誤判定せず、その場で失敗させる。
 */
export async function readGold(page: Page): Promise<number> {
  const text = (await page.getByText(/^ゴールド: /).first().innerText()).replace('ゴールド:', '').trim()
  if (/[KMB]$/.test(text)) {
    throw new Error(`ゴールドが丸め表記（${text}）で正確に読めない。tick数を減らして再試行すること`)
  }
  return Number(text.replace(/,/g, ''))
}

/**
 * ホーム画面のHPポーション所持数を読む。
 *
 * 表示要素は前後に改行を含むため、空白を正規化する文字列マッチで引く
 * （正規表現マッチだと空白が正規化されず一致しない）。
 */
export async function readPotionCount(page: Page): Promise<number> {
  const text = await page.getByText('HPポーション:').innerText()
  return Number(text.replace(/[^0-9]/g, ''))
}

/** ホーム画面のHP表示（`現在HP / 最大HP`）を読む */
export async function readHp(page: Page): Promise<{ current: number; max: number }> {
  const text = await hpValue(page).innerText()
  const [current, max] = text.split('/').map((v) => Number(v.trim()))
  return { current: current!, max: max! }
}

/** ホーム画面のHP数値表示のロケーター（HPバーの直後のスパン） */
export function hpValue(page: Page): Locator {
  return page.locator('.stat-bar-hp').locator('xpath=following-sibling::span[1]')
}

/** ホーム画面の塔パネル（入塔前は塔一覧、入塔後は進行状況） */
export function towerPanel(page: Page): Locator {
  return page
    .locator('section.panel')
    .filter({ has: page.getByRole('heading', { name: '塔', exact: true }) })
}

/**
 * 塔一覧のカード。
 *
 * 未解放の塔は解放条件として別の塔の名前を載せるため、塔名だけで絞ると重複する。
 * カード見出し（`p.font-display`）に名前があるものだけを対象にする。
 */
export function towerCard(page: Page, towerName: string): Locator {
  return towerPanel(page)
    .locator('.tower-card')
    .filter({ has: page.locator('p.font-display', { hasText: towerName }) })
}

/**
 * 入塔中の進行状況（`フロア 現在 / 目標`）を読む。
 * 塔にいない（全滅・撤退・退出）ときは null。
 */
export async function readTowerProgress(
  page: Page,
): Promise<{ current: number; target: number } | null> {
  const label = towerPanel(page).getByText(/^フロア \d+ \/ \d+$/)
  if ((await label.count()) === 0) return null
  const matched = /フロア (\d+) \/ (\d+)/.exec(await label.innerText())
  if (!matched) return null
  return { current: Number(matched[1]), target: Number(matched[2]) }
}

/** ホーム画面から塔へ入る */
export async function enterTower(page: Page, towerName = 'ゴブリンの塔'): Promise<void> {
  const panel = towerPanel(page)
  await towerCard(page, towerName).click()
  await panel.getByRole('button', { name: '塔に入る' }).click()
  await expect(panel.getByText(/^フロア \d+ \/ \d+$/)).toBeVisible()
}

/**
 * 塔にいなければ入り直す。
 *
 * ポーション切れやHP閾値で自動撤退するのは仕様どおりの挙動なので、
 * 長く放置する検証では入り直しながら進める。
 * ただし低HPのまま入り直すと全滅を繰り返して進行が止まるため、
 * 塔の外での自然回復を待ってから入る。
 */
export async function ensureInTower(
  page: Page,
  { towerName = 'ゴブリンの塔', minHpRatio = 0.8 } = {},
): Promise<void> {
  // 条件判定などでホーム以外へ移っていることがあるため、塔パネルの有無で判断して戻す
  if ((await towerPanel(page).count()) === 0) await reloadHome(page)
  if ((await readTowerProgress(page)) !== null) return

  const hp = await readHp(page)
  if (hp.current < hp.max * minHpRatio) return // 回復を待つ（次のラウンドで再判定）
  await enterTower(page, towerName)
}
