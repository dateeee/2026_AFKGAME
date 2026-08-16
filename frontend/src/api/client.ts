/**
 * API通信クライアント（Phase 2: JWT認証対応）
 * - アクセストークンは本ファイルのモジュール変数でメモリ保持（tech_auth.md §7）
 * - 401時にリフレッシュトークンで自動再取得
 * - USE_API=false でバックエンド未起動時のフォールバック
 * - リトライはネットワークエラー時のみ・冪等なリクエストに限る（下記 fetchWithRetry）
 */

import type {
  Equipment,
  GameState,
  Settings,
  ShopLineupResponse,
  TickResponse,
  TowerInfo,
} from '@/types/game'
import { refreshToken as refreshTokenApi } from '@/api/auth'
import { ApiError, SESSION_EXPIRED_CODE, networkError, toApiError } from '@/api/errors'

const USE_API = import.meta.env.VITE_USE_API !== 'false'
const MAX_RETRIES = 3
const BASE_DELAY_MS = 1000

/** 再送しても副作用が増えないメソッド。これ以外は自動リトライしない */
const IDEMPOTENT_METHODS = new Set(['GET', 'HEAD'])

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

/** 送信時のヘッダを組み立てる（アクセストークンは毎回最新を読む） */
function buildHeaders(options: RequestInit): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  }
  const token = getAccessToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return headers
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/** 1回送信する。401 ならリフレッシュして1度だけ再送する（リトライ回数とは別枠） */
async function sendWithAuth(url: string, options: RequestInit): Promise<Response> {
  const response = await fetch(url, { ...options, headers: buildHeaders(options) })
  if (response.status !== 401) return response

  if (!(await tryRefresh())) {
    // リフレッシュ不可＝再送しても結果は変わらないので即時失敗させる
    throw new ApiError('認証に失敗しました', SESSION_EXPIRED_CODE, 401)
  }
  return await fetch(url, { ...options, headers: buildHeaders(options) })
}

/**
 * APIを呼び出す。
 *
 * リトライはネットワークエラー（サーバーに届かなかった場合）に限る。
 * 4xx/5xx はサーバーが応答を確定させているので再送せず即座に ApiError を投げる
 * （業務エラーの表示が遅れないようにするため）。
 * また非冪等な POST/PUT は、応答だけ喪失したケースで二重購入・二重売却になりうるため
 * ネットワークエラーでも再送しない。
 */
async function fetchWithRetry<T>(url: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  const maxAttempts = IDEMPOTENT_METHODS.has(method) ? MAX_RETRIES : 1

  let lastCause: unknown = null
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    if (attempt > 0) {
      await delay(BASE_DELAY_MS * Math.pow(2, attempt - 1))
    }

    let response: Response
    try {
      response = await sendWithAuth(url, options)
    } catch (error) {
      // ApiError（認証失敗）は確定した結果なので再送しない
      if (error instanceof ApiError) throw error
      lastCause = error
      continue
    }

    if (!response.ok) throw await toApiError(response)
    return (await response.json()) as T
  }
  throw networkError(lastCause)
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
export async function postTowerSelect(
  towerId: string,
  targetFloor: number,
  mode: string = 'auto_repeat',
) {
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
export async function getShopLineup(): Promise<ShopLineupResponse> {
  return fetchWithRetry<ShopLineupResponse>('/api/shop/lineup')
}

/** ショップ購入（常設商品） */
export async function postShopBuy(itemId: string, quantity: number) {
  return fetchWithRetry<{ status: string; gold: number; itemId: string; quantity: number }>(
    '/api/shop/buy',
    {
      method: 'POST',
      body: JSON.stringify({ itemId, quantity }),
    },
  )
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
