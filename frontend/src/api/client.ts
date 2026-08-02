/**
 * API通信クライアント（Phase 2: JWT認証対応）
 * - アクセストークンはメモリ保持（authStore経由）
 * - 401時にリフレッシュトークンで自動再取得
 * - USE_API=false でバックエンド未起動時のフォールバック
 * - 指数バックオフリトライ（最大3回）
 */

import type { Equipment, GameState, Settings, ShopDailyItem, TickResponse, TowerInfo } from '@/types/game'
import { refreshToken as refreshTokenApi } from '@/api/auth'

const USE_API = import.meta.env.VITE_USE_API !== 'false'
const MAX_RETRIES = 3
const BASE_DELAY_MS = 1000

// ── トークン管理（メモリ） ──

let _accessToken: string | null = null
let _refreshing: Promise<void> | null = null

function getAccessToken(): string | null {
  return _accessToken
}

function setAccessToken(token: string | null): void {
  _accessToken = token
}

function getRefreshToken(): string | null {
  return localStorage.getItem('refresh_token')
}

function setRefreshToken(token: string | null): void {
  if (token) {
    localStorage.setItem('refresh_token', token)
  } else {
    localStorage.removeItem('refresh_token')
  }
}

/** トークンペアを保存 */
function setTokens(accessToken: string, refreshToken: string): void {
  setAccessToken(accessToken)
  setRefreshToken(refreshToken)
}

/** トークンをクリア */
function clearTokens(): void {
  setAccessToken(null)
  setRefreshToken(null)
  // Phase 1互換のゲストトークンも削除
  localStorage.removeItem('guest_token')
}

/** リフレッシュトークンでアクセストークンを再取得（重複防止） */
async function tryRefresh(): Promise<boolean> {
  const rt = getRefreshToken()
  if (!rt) return false

  if (_refreshing) {
    await _refreshing
    return !!_accessToken
  }

  _refreshing = (async () => {
    try {
      const data = await refreshTokenApi(rt)
      setTokens(data.accessToken, data.refreshToken)
    } catch {
      clearTokens()
    } finally {
      _refreshing = null
    }
  })()

  await _refreshing
  return !!_accessToken
}

// ── API呼び出し ──

async function fetchWithRetry<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  }

  let lastError: Error | null = null
  for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
    // 最新のアクセストークンを毎回取得
    const token = getAccessToken()
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    try {
      const response = await fetch(url, { ...options, headers })

      // 401 → リフレッシュ試行して1回だけリトライ
      if (response.status === 401 && attempt === 0) {
        const refreshed = await tryRefresh()
        if (refreshed) continue
        throw new Error('認証に失敗しました')
      }

      if (!response.ok) {
        throw new Error(`サーバーエラー (${response.status})`)
      }
      return await response.json() as T
    } catch (error) {
      lastError = error as Error
      if (attempt < MAX_RETRIES - 1) {
        await new Promise(resolve => setTimeout(resolve, BASE_DELAY_MS * Math.pow(2, attempt)))
      }
    }
  }
  throw lastError!
}

// ── 公開API関数 ──

/** ゲーム状態取得 */
export async function getGameState(): Promise<GameState> {
  return fetchWithRetry<GameState>('/api/game/state')
}

/** tick処理 */
export async function postTick(): Promise<TickResponse> {
  return fetchWithRetry<TickResponse>('/api/battle/tick', { method: 'POST' })
}

/** 設定更新 */
export async function putSettings(settings: Partial<Settings>): Promise<Settings> {
  return fetchWithRetry<Settings>('/api/game/settings', {
    method: 'PUT',
    body: JSON.stringify(settings),
  })
}

/** 塔一覧取得 */
export async function getTowerList(): Promise<TowerInfo[]> {
  return fetchWithRetry<TowerInfo[]>('/api/tower/list')
}

/** 塔選択 */
export async function postTowerSelect(towerId: string, targetFloor: number, mode: string = 'auto_repeat') {
  return fetchWithRetry<{ status: string }>('/api/tower/select', {
    method: 'POST',
    body: JSON.stringify({ towerId, targetFloor, mode }),
  })
}

/** 塔退却 */
export async function postTowerRetire() {
  return fetchWithRetry<{ status: string }>('/api/tower/retire', {
    method: 'POST',
  })
}

/** 塔モード変更 */
export async function putTowerMode(mode: string) {
  return fetchWithRetry<{ status: string }>('/api/tower/mode', {
    method: 'PUT',
    body: JSON.stringify({ mode }),
  })
}

/** 退却条件変更 */
export async function putRetreatConditions(hpThreshold: number) {
  return fetchWithRetry<{ status: string }>('/api/tower/retreat-conditions', {
    method: 'PUT',
    body: JSON.stringify({ hpThreshold }),
  })
}

/** ショップ商品一覧（常設 + 日替わり） */
export async function getShopLineup() {
  return fetchWithRetry<{
    lineup: Array<{
      itemId: string
      name: string
      price: number
      healRatio: number
      quantityOwned: number
      stackLimit: number
    }>
    daily: ShopDailyItem[]
    dailyResetAt: string
  }>('/api/shop/lineup')
}

/** ショップ購入（常設商品） */
export async function postShopBuy(itemId: string, quantity: number) {
  return fetchWithRetry<{ status: string; gold: number; itemId: string; quantity: number }>('/api/shop/buy', {
    method: 'POST',
    body: JSON.stringify({ itemId, quantity }),
  })
}

/** ショップ購入（日替わり装備） */
export async function postShopBuyDaily(dailySlotIndex: number) {
  return fetchWithRetry<{ status: string; gold: number; equipment: Equipment }>('/api/shop/buy', {
    method: 'POST',
    body: JSON.stringify({ dailySlotIndex }),
  })
}

/** 装備一覧取得 */
export async function getEquipmentList(): Promise<Equipment[]> {
  return fetchWithRetry<Equipment[]>('/api/equipment/list')
}

/** 装備の装着/解除 */
export async function postEquip(characterId: string, slot: string, equipmentId: string | null) {
  return fetchWithRetry<{ status: string }>('/api/equipment/equip', {
    method: 'POST',
    body: JSON.stringify({ characterId, slot, equipmentId }),
  })
}

/** 装備売却 */
export async function postEquipmentSell(equipmentIds: string[]) {
  return fetchWithRetry<{ goldEarned: number; itemsSold: number }>('/api/equipment/sell', {
    method: 'POST',
    body: JSON.stringify({ equipmentIds }),
  })
}

/** 装備ロック切替 */
export async function postEquipmentLock(equipmentId: string) {
  return fetchWithRetry<{ locked: boolean }>('/api/equipment/lock', {
    method: 'POST',
    body: JSON.stringify({ equipmentId }),
  })
}

export {
  USE_API,
  getAccessToken,
  setAccessToken,
  getRefreshToken,
  setRefreshToken,
  setTokens,
  clearTokens,
  tryRefresh,
}
