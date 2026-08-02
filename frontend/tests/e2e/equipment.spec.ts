/**
 * E2E シナリオ#5: 装備ドロップ → 装備変更 → ステータス反映
 *
 * 検証対象は画面遷移図「装備画面」と systems/equipment.md の装備変更導線。
 * ドロップ率・レアリティ抽選・オートセルの分岐は
 * L1（`backend/tests/integration/test_equipment_flow.py`）が担当する。
 */

import { expect, test, type Page } from '@playwright/test'

import {
  advanceUntil,
  enterTower,
  ensureInTower,
  readHp,
  reloadHome,
  startAsGuest,
} from './support/harness'

/** 所持装備のうち最大HPを上げるもの（ホーム画面で反映を確認できる唯一のステータス） */
function hpEquipmentCards(page: Page) {
  return page
    .locator('.equipment-card')
    .filter({ has: page.locator('.stat-chip', { hasText: /^HP \+\d+$/ }) })
}

async function openEquipment(page: Page): Promise<void> {
  await page.getByRole('link', { name: '装備' }).first().click()
  await expect(page.getByRole('heading', { name: '所持装備' })).toBeVisible()
}

async function openHome(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'ホーム' }).first().click()
  await expect(page.getByRole('heading', { name: 'キャラクター' })).toBeVisible()
}

test.describe('シナリオ#5 装備ドロップから装備変更とステータス反映', () => {
  test('ドロップした装備を装着すると最大HPに反映され、外すと戻る', async ({ page }) => {
    await startAsGuest(page)
    await enterTower(page)

    // 最大HPを上げる装備が落ちるまで放置を重ねる（ドロップは乱数のため回数を決め打ちしない）
    await advanceUntil(
      page,
      async () => {
        await openEquipment(page)
        return (await hpEquipmentCards(page).count()) > 0
      },
      { ticksPerRound: 20, maxRounds: 15, prepare: () => ensureInTower(page) },
    )

    // 装着前の最大HPを控える（この後 tick は進まないので基準として使える）
    await openHome(page)
    const before = await readHp(page)

    await openEquipment(page)
    const card = hpEquipmentCards(page).first()
    const cardName = await card.locator('span.font-semibold').first().innerText()
    const hpBonus = Number(
      (await card.locator('.stat-chip', { hasText: /^HP \+\d+$/ }).innerText()).replace(/\D/g, ''),
    )

    // 装備比較ポップアップ経由で装着する（画面遷移図「装備一覧 → 装備比較ポップアップ」）
    await card.click()
    const compare = page.locator('.modal-overlay').filter({ hasText: '装備比較' })
    await expect(compare.getByText('なし')).toBeVisible() // 現在の装備は空
    await compare.getByRole('button', { name: '装備する' }).click()
    await expect(compare).toHaveCount(0)

    // 装備中スロットへ移り、所持装備一覧からは消える
    await expect(page.locator('.slot-item.filled')).toHaveCount(1)
    await expect(page.locator('.slot-item.filled')).toContainText(cardName)

    // サーバーが返す実効ステータスに反映される（再読み込みで取り直す。tickは進めない）
    await reloadHome(page)
    expect(await readHp(page)).toMatchObject({ max: before.max + hpBonus })

    // 外すと元に戻る
    await openEquipment(page)
    await page.locator('.slot-item.filled').click()
    await page.getByRole('button', { name: /スロットの装備を外す$/ }).click()
    await expect(page.locator('.slot-item.filled')).toHaveCount(0)

    await reloadHome(page)
    expect(await readHp(page)).toMatchObject({ max: before.max })
  })
})
