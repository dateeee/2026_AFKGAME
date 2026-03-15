import { ref } from 'vue'
import { getGameState, USE_API } from '@/api/client'
import { useGameStore } from '@/stores/gameStore'
import { usePlayerStore } from '@/stores/playerStore'
import { useEquipmentStore } from '@/stores/equipmentStore'
import { usePolling } from './usePolling'

export function useGameLoop() {
  const isInitialized = ref(false)
  const isLoading = ref(true)

  const { start: startPolling, tick } = usePolling()

  /**
   * ゲーム初期化（認証済み前提で呼ばれる）
   * 認証フローはrouter guardとauthStoreが担当
   */
  async function initialize() {
    if (!USE_API) {
      isLoading.value = false
      isInitialized.value = true
      return
    }

    const gameStore = useGameStore()
    gameStore.isLoading = true

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
