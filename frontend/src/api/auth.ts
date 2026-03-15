/**
 * 認証API（Phase 2）
 * トークン管理はauthStoreと連携
 */

import type { AuthResponse } from '@/types/game'

const BASE = '/api/auth'

async function authFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  }
  const response = await fetch(url, { ...options, headers })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.detail || `HTTP ${response.status}`)
  }
  return await response.json() as T
}

/** ゲストアカウント作成 */
export async function createGuest(): Promise<AuthResponse> {
  return authFetch<AuthResponse>(`${BASE}/guest`, { method: 'POST' })
}

/** メール+パスワード登録 */
export async function register(email: string, password: string, displayName?: string): Promise<AuthResponse> {
  return authFetch<AuthResponse>(`${BASE}/register`, {
    method: 'POST',
    body: JSON.stringify({ email, password, displayName }),
  })
}

/** メール+パスワードログイン */
export async function login(email: string, password: string): Promise<AuthResponse> {
  return authFetch<AuthResponse>(`${BASE}/login`, {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

/** リフレッシュトークンで新しいトークンペアを取得 */
export async function refreshToken(refreshToken: string): Promise<AuthResponse> {
  return authFetch<AuthResponse>(`${BASE}/refresh`, {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  })
}

/** ログアウト */
export async function logout(refreshTokenValue: string): Promise<void> {
  await authFetch(`${BASE}/logout`, {
    method: 'POST',
    body: JSON.stringify({ refreshToken: refreshTokenValue }),
  })
}

/** ゲスト→本登録移行（メール） */
export async function linkAccount(
  accessToken: string,
  email: string,
  password: string,
): Promise<AuthResponse> {
  return authFetch<AuthResponse>(`${BASE}/link-account`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
    body: JSON.stringify({ email, password }),
  })
}
