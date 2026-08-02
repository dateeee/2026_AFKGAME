import { ref } from 'vue'
import { getGameState, USE_API } from '@/api/client'
import { useAuthStore } from '@/stores/authStore'
import { useGameStore } from '@/stores/gameStore'
import { usePlayerStore } from '@/stores/playerStore'
import { useEquipmentStore } from '@/stores/equipmentStore'
import { usePolling } from './usePolling'

export function useGameLoop() {
  const isInitialized = ref(false)
  const isLoading = ref(true)

  const { start: startPolling, tick } = usePolling()

  /**
   * ゲーム初期化
   * 認証フローはrouter guardとauthStoreが担当。
   * ログイン画面では未認証のまま呼ばれるため、その場合は何も読まずに戻る
   * （401を叩いてエラーバナーを出さないため）。
   */
  async function initialize() {
    if (!USE_API) {
      isLoading.value = false
      isInitialized.value = true
      return
    }

    if (!useAuthStore().isAuthenticated) {
      isLoading.value = false
      return
    }

    const gameStore = useGameStore()
    gameStore.isLoading = true
    isLoading.value = true

    try {
      const state = await getGameState()
      gameStore.loadFromState(state)
      usePlayerStore().loadFromState(state)
      useEquipmentStore().loadFromState(state)

      await tick()
      startPolling()

      gameStore.clearError()
    } catch (error) {
      gameStore.setConnectionError((error as Error).message)
    } finally {
      gameStore.isLoading = false
      isLoading.value = false
      isInitialized.value = true
    }
  }

  return { initialize, isInitialized, isLoading }
}
