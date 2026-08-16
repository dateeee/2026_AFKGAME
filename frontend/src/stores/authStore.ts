/**
 * 認証ストア（Phase 2）
 * - ユーザー情報管理
 * - トークン管理（client.tsと連携）
 * - ログイン/ログアウト/ゲスト開始
 */

import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { UserInfo } from '@/types/game'
import * as authApi from '@/api/auth'
import { setTokens, clearTokens, getRefreshToken } from '@/api/client'
import { errorMessage } from '@/api/errors'
import { stopActivePolling } from '@/composables/usePolling'
import { useGameStore } from '@/stores/gameStore'
import { usePlayerStore } from '@/stores/playerStore'
import { useBattleStore } from '@/stores/battleStore'
import { useEquipmentStore } from '@/stores/equipmentStore'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const isAuthenticated = computed(() => !!user.value)
  const isGuest = computed(() => user.value?.isGuest ?? true)

  function _handleAuthResponse(data: {
    accessToken: string
    refreshToken: string
    user: UserInfo
  }) {
    setTokens(data.accessToken, data.refreshToken)
    user.value = data.user
    error.value = null
  }

  /** 起動時: リフレッシュトークンがあればセッション復元 */
  async function restoreSession(): Promise<boolean> {
    const rt = getRefreshToken()
    if (!rt) return false

    loading.value = true
    try {
      const data = await authApi.refreshToken(rt)
      _handleAuthResponse(data)
      return true
    } catch {
      clearTokens()
      return false
    } finally {
      loading.value = false
    }
  }

  /** ゲストプレイ開始 */
  async function startAsGuest(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const data = await authApi.createGuest()
      _handleAuthResponse(data)
    } catch (e) {
      error.value = errorMessage(e)
      throw e
    } finally {
      loading.value = false
    }
  }

  /** メール+パスワード登録 */
  async function register(email: string, password: string, displayName?: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const data = await authApi.register(email, password, displayName)
      _handleAuthResponse(data)
    } catch (e) {
      error.value = errorMessage(e)
      throw e
    } finally {
      loading.value = false
    }
  }

  /** メール+パスワードログイン */
  async function loginWithEmail(email: string, password: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const data = await authApi.login(email, password)
      _handleAuthResponse(data)
    } catch (e) {
      error.value = errorMessage(e)
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * ログアウト。
   * トークン破棄だけでは (1) ポーリングが動き続けて401バナーが出る、
   * (2) 前アカウントのゲーム状態が画面に残る、ため両方まとめて後始末する。
   */
  async function logout(): Promise<void> {
    const rt = getRefreshToken()
    if (rt) {
      try {
        await authApi.logout(rt)
      } catch {
        // ログアウトAPIが失敗してもローカルはクリア
      }
    }

    stopActivePolling()
    clearTokens()
    user.value = null
    error.value = null

    useGameStore().reset()
    usePlayerStore().reset()
    useBattleStore().reset()
    useEquipmentStore().reset()
  }

  /**
   * セッション失効（リフレッシュ不能）の後始末。
   * ログアウトAPIは呼べない（トークンが無効）ため、ローカルだけを片付けて
   * ログイン画面へ送る。放置すると `user` が残ったままガードを通過し、
   * ポーリングが60秒ごとに失敗して赤バナーが出続ける。
   */
  async function expireSession(): Promise<void> {
    stopActivePolling()
    clearTokens()
    user.value = null
    error.value = null

    useGameStore().reset()
    usePlayerStore().reset()
    useBattleStore().reset()
    useEquipmentStore().reset()

    const { default: router } = await import('@/router')
    if (router.currentRoute.value.name !== 'login') {
      await router.push({ name: 'login' })
    }
  }

  /** ゲスト→本登録移行 */
  async function linkAccount(email: string, password: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const { getAccessToken } = await import('@/api/client')
      const at = getAccessToken()
      if (!at) throw new Error('Not authenticated')
      const data = await authApi.linkAccount(at, email, password)
      _handleAuthResponse(data)
    } catch (e) {
      error.value = errorMessage(e)
      throw e
    } finally {
      loading.value = false
    }
  }

  return {
    user,
    loading,
    error,
    isAuthenticated,
    isGuest,
    restoreSession,
    startAsGuest,
    register,
    loginWithEmail,
    logout,
    expireSession,
    linkAccount,
  }
})
