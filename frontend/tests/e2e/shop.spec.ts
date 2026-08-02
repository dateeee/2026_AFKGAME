/**
 * E2E シナリオ#6: 常設ショップ購入 → 所持金・在庫の反映
 *
 * 検証対象は画面遷移図「ショップ画面（常設タブ）」と systems/economy.md の購入導線。
 * 価格・スタック上限の境界は L1（`backend/tests/integration/test_shop_flow.py`）が担当する。
 */

import { expect, test, type Page } from '@playwright/test'

import {
  advanceUntil,
  ensureInTower,
  enterTower,
  readGold,
  readPotionCount,
  startAsGuest,
} from './support/harness'

/** 常設タブの HPポーション の商品パネル */
function potionCard(page: Page) {
  return page.locator('.panel').filter({ hasText: 'HPポーション' })
}

async function openShop(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'ショップ' }).first().click()
  await expect(page.getByRole('heading', { name: 'ショップ' })).toBeVisible()
}

test.describe('シナリオ#6 常設ショップでの購入', () => {
  test('所持金が足りないと購入ボタンが押せない', async ({ page }) => {
    await startAsGuest(page)
    await openShop(page)

    await expect(page.getByText('ゴールド: 0')).toBeVisible()
    await expect(potionCard(page).getByText('所持: 5 / 99')).toBeVisible()
    await expect(potionCard(page).getByRole('button', { name: /^購入/ })).toBeDisabled()
  })

  test('戦闘で稼いだゴールドで購入すると所持金と所持数に反映される', async ({ page }) => {
    await startAsGuest(page)
    await enterTower(page)

    // ゴールドが丸め表記（10,000以上で `12.3K`）に入らない範囲で稼ぐ
    await advanceUntil(page, async () => (await readGold(page)) >= 50, {
      ticksPerRound: 5,
      maxRounds: 30,
      // ポーション切れで自動撤退したら、回復を待ってから入り直す
      prepare: () => ensureInTower(page),
    })
    const goldBefore = await readGold(page)
    const potionsBefore = await readPotionCount(page)

    await openShop(page)
    const card = potionCard(page)
    const price = Number((await card.getByText(/\d+G$/).first().innerText()).replace('G', ''))
    await expect(card.getByText(`所持: ${potionsBefore} / 99`)).toBeVisible()

    await card.getByRole('button', { name: /^購入/ }).click()

    // 購入結果がショップ画面に反映される
    await expect(page.getByText('1個購入しました！')).toBeVisible()
    await expect(card.getByText(`所持: ${potionsBefore + 1} / 99`)).toBeVisible()
    expect(await readGold(page)).toBe(goldBefore - price)

    // ホームへ戻っても所持数・所持金が保たれている
    await page.getByRole('link', { name: 'ホーム' }).first().click()
    expect(await readPotionCount(page)).toBe(potionsBefore + 1)
    expect(await readGold(page)).toBe(goldBefore - price)
  })
})
