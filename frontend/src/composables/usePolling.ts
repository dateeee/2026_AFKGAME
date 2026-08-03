import { ref, onUnmounted } from 'vue'
import { postTick } from '@/api/client'
import { errorMessage } from '@/api/errors'
import { useAuthStore } from '@/stores/authStore'
import { useGameStore } from '@/stores/gameStore'
import { useBattleStore } from '@/stores/battleStore'
import { usePlayerStore } from '@/stores/playerStore'
import { useEquipmentStore } from '@/stores/equipmentStore'

const TICK_INTERVAL_MS = 60_000

/**
 * 稼働中のポーリングを止める関数。
 * ログアウト（authStore）からポーリングを停止できるようにするための登録口。
 * usePolling は App.vue で1度だけインスタンス化される想定。
 */
let activeStop: (() => void) | null = null

/** ログアウト時にポーリングを止める（未起動なら何もしない） */
export function stopActivePolling(): void {
  activeStop?.()
}

export function usePolling() {
  const isActive = ref(false)
  let timerId: ReturnType<typeof setInterval> | null = null
  // 非表示になる直前に稼働していたか。復帰時の再開判定に使う
  let wasActiveBeforeHidden = false

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
      gameStore.setConnectionError(errorMessage(error))
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
    // ログアウト後にタブ復帰しても再開しないよう、保存した稼働状態も落とす
    wasActiveBeforeHidden = false
  }

  /**
   * タブの表示・非表示に追従する。
   * 復帰時は「非表示になる前に稼働していた」かつ「認証済み」のときだけ再開する。
   * usePolling は App.vue から常時インスタンス化されるため、ガードがないと
   * ログイン画面でタブを切り替えて戻るだけで tick が走り 401 になる。
   */
  function handleVisibilityChange() {
    if (document.hidden) {
      wasActiveBeforeHidden = isActive.value
      stop()
      return
    }
    if (!wasActiveBeforeHidden) return
    if (!useAuthStore().isAuthenticated) return
    tick()
    start()
  }

  document.addEventListener('visibilitychange', handleVisibilityChange)
  activeStop = stop

  onUnmounted(() => {
    stop()
    activeStop = null
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })

  return { start, stop, isActive, tick }
}
