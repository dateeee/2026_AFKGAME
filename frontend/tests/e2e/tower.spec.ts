/**
 * E2E シナリオ#2: 塔選択 → 目標階設定
 *
 * 検証対象は画面遷移図「塔画面」（Phase 2 時点はホーム画面内セクション）と
 * systems/battle.md の目標階設定。塔別クリア記録の独立などデータ側の不変条件は
 * L1（`backend/tests/integration/test_tower_flow.py`）が担当する。
 */

import { expect, test } from '@playwright/test'

import {
  advanceUntil,
  enterTower,
  readTowerProgress,
  startAsGuest,
  towerCard,
  towerPanel,
} from './support/harness'

test.describe('シナリオ#2 塔選択から目標階設定', () => {
  test('解放済みの塔を選んで目標階を決めると入塔できる', async ({ page }) => {
    await startAsGuest(page)
    const panel = towerPanel(page)

    // 初回は最高到達 0F のため、選択できる目標階の上限は 1
    await expect(towerCard(page, 'ゴブリンの塔')).toBeVisible()
    await expect(panel.getByText('/ 1（選択上限）')).toBeVisible()
    await expect(panel.getByRole('spinbutton')).toHaveValue('1')

    await towerCard(page, 'ゴブリンの塔').click()
    await panel.getByRole('button', { name: '塔に入る' }).click()

    // 入塔後は塔一覧から進行状況表示へ切り替わる
    await expect(panel.getByText('フロア 1 / 1')).toBeVisible()
    await expect(panel.getByText('モード: 自動周回')).toBeVisible()
    await expect(panel.getByRole('button', { name: '塔に入る' })).toHaveCount(0)
  })

  test('未解放の塔は選べず解放条件が表示される', async ({ page }) => {
    await startAsGuest(page)

    const locked = towerCard(page, '森の塔')
    await expect(locked.getByText('未解放')).toBeVisible()
    await expect(locked.getByText('ゴブリンの塔のボス討伐で解放')).toBeVisible()

    // 選択肢として無効。ポインタでもキーボード（Tab）でも到達できない
    await expect(locked).toHaveAttribute('aria-disabled', 'true')
    await expect(locked).toHaveAttribute('tabindex', '-1')

    // クリックが届いた場合も選択は移らない（ゴブリンの塔が選ばれたまま）。
    // 無効な要素への通常クリックは actionability チェックで待たされるため、
    // force で発火させてハンドラ側のガードだけを見る
    await locked.click({ force: true })
    await expect(locked).not.toHaveClass(/tower-card-selected/)
    await expect(towerCard(page, 'ゴブリンの塔')).toHaveClass(/tower-card-selected/)
  })

  test('階をクリアすると目標階の上限が追従して上がる', async ({ page }) => {
    await startAsGuest(page)
    const panel = towerPanel(page)
    await enterTower(page)
    expect(await readTowerProgress(page)).toEqual({ current: 1, target: 1 })

    // 目標階が上限に張り付いている間、階をクリアすると上限が自動で +1 される
    // （systems/battle.md 目標階設定の上限追従）。1tickで複数階進むこともあるため、
    // 特定の階を待たず「上限が上がったか」で判定する
    await advanceUntil(page, async () => ((await readTowerProgress(page))?.target ?? 0) >= 2, {
      ticksPerRound: 1,
      maxRounds: 15,
    })
    const progress = await readTowerProgress(page)
    expect(progress).not.toBeNull()

    await panel.getByRole('button', { name: '撤退' }).click()

    // 到達記録が塔カードに出て、選択できる目標階の上限が「最高到達 + 1」になる
    await expect(
      towerCard(page, 'ゴブリンの塔').getByText(`最高到達: ${progress!.target - 1}F`),
    ).toBeVisible()
    await expect(panel.getByText(`/ ${progress!.target}（選択上限）`)).toBeVisible()
  })

  test('撤退すると塔選択に戻る', async ({ page }) => {
    await startAsGuest(page)
    const panel = towerPanel(page)
    await enterTower(page)

    await panel.getByRole('button', { name: '撤退' }).click()

    await expect(panel.getByRole('button', { name: '塔に入る' })).toBeVisible()
    await expect(panel.getByText('フロア 1 / 1')).toHaveCount(0)
  })
})
