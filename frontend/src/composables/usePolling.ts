import { ref, onUnmounted } from 'vue'
import { postTick } from '@/api/client'
import { useGameStore } from '@/stores/gameStore'
import { useBattleStore } from '@/stores/battleStore'
import { usePlayerStore } from '@/stores/playerStore'
import { useEquipmentStore } from '@/stores/equipmentStore'

const TICK_INTERVAL_MS = 60_000

export function usePolling() {
  const isActive = ref(false)
  let timerId: ReturnType<typeof setInterval> | null = null

  async function tick() {
    const gameStore = useGameStore()
    try {
      const response = await postTick()
      gameStore.loadFromState(response.updatedState)
      usePlayerStore().loadFromState(response.updatedState)
      useEquipmentStore().loadFromState(response.updatedState)

      const battleStore = useBattleStore()
      if (response.battleLogs.length > 0) {
        battleStore.addBattleLogs(response.battleLogs)
      }
      if (response.offlineSummary) {
        battleStore.setOfflineSummary(response.offlineSummary)
      }

      gameStore.clearError()
    } catch (error) {
      gameStore.setConnectionError((error as Error).message)
    }
  }

  function start() {
    if (isActive.value) return
    isActive.value = true
    timerId = setInterval(tick, TICK_INTERVAL_MS)
  }

  function stop() {
    if (timerId !== null) {
      clearInterval(timerId)
      timerId = null
    }
    isActive.value = false
  }

  function handleVisibilityChange() {
    if (document.hidden) {
      stop()
    } else {
      tick()
      start()
    }
  }

  document.addEventListener('visibilitychange', handleVisibilityChange)

  onUnmounted(() => {
    stop()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })

  return { start, stop, isActive, tick }
}
