/**
 * E2E シナリオ#1: 認証 → ゲーム状態取得
 *
 * 検証対象は**基本設計**（画面遷移図の「認証・エントリーフロー」と
 * メインナビゲーション構造、tech_api.md のゲーム状態取得）。
 * トークン発行やゲスト移行の分岐は L1（`backend/tests/integration/test_auth_flow.py`）が担当する。
 */

import { expect, test } from '@playwright/test'

import { startAsGuest, waitForHome } from './support/harness'

test.describe('シナリオ#1 認証からゲーム状態取得', () => {
  test('未認証でホームを開くとログイン画面へ送られる', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { name: 'AFK GAME' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'ゲストで始める' })).toBeEnabled()
  })

  test('ゲストで始めるとホームに初期状態が表示される', async ({ page }) => {
    await startAsGuest(page)

    // 初期キャラクター（config.INITIAL_CHARACTER）
    await expect(page.getByText('勇者')).toBeVisible()
    await expect(page.getByText('LV 1')).toBeVisible()
    await expect(
      page.locator('.stat-bar-hp').locator('xpath=following-sibling::span[1]'),
    ).toHaveText('100 / 100')

    // 初期所持品（config.INITIAL_POTIONS）と初期ゴールド
    await expect(page.getByText('HPポーション: 5')).toBeVisible()
    await expect(page.getByText(/^ゴールド: 0$/)).toBeVisible()

    // 塔へ入る前なので戦闘ログは空
    await expect(page.getByText('まだ戦闘ログがありません', { exact: false })).toBeVisible()
  })

  test('再読み込みしてもセッションとゲーム状態が保たれる', async ({ page }) => {
    await startAsGuest(page)
    await expect(page.getByText('HPポーション: 5')).toBeVisible()

    // アクセストークンはメモリ保持のため、復元はリフレッシュトークン経由になる
    await page.reload()

    await expect(page).toHaveURL(/\/$/)
    await waitForHome(page)
    await expect(page.getByText('勇者')).toBeVisible()
    await expect(page.getByText('HPポーション: 5')).toBeVisible()
  })

  test('ログアウト相当のトークン破棄後はログイン画面へ戻る', async ({ page }) => {
    await startAsGuest(page)

    await page.evaluate(() => window.localStorage.clear())
    await page.goto('/')

    await expect(page).toHaveURL(/\/login$/)
  })
})

test.describe('画面遷移（Phase 2 のタブ構成）', () => {
  test('/register は登録フォームが開いた状態のログイン画面になる', async ({ page }) => {
    // 画面遷移図の「アカウント登録画面」は LoginView の登録タブへ統合されている
    await page.goto('/register')

    await expect(page).toHaveURL(/\/login\?mode=register$/)
    await expect(page.getByLabel('表示名')).toBeVisible()
    await expect(page.getByRole('button', { name: '登録', exact: true })).toBeVisible()
  })

  test('ホームから各タブへ行き来できる', async ({ page }) => {
    await startAsGuest(page)

    await page.getByRole('link', { name: '装備' }).first().click()
    await expect(page).toHaveURL(/\/equipment$/)
    await expect(page.getByRole('heading', { name: '装備', exact: true })).toBeVisible()

    await page.getByRole('link', { name: 'ショップ' }).first().click()
    await expect(page).toHaveURL(/\/shop$/)
    await expect(page.getByRole('heading', { name: 'ショップ' })).toBeVisible()

    await page.getByRole('link', { name: '設定' }).first().click()
    await expect(page).toHaveURL(/\/settings$/)

    await page.getByRole('link', { name: 'ホーム' }).first().click()
    await expect(page).toHaveURL(/\/$/)
    await waitForHome(page)
  })
})
