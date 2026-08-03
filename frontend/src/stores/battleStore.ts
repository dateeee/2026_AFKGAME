import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { BattleLogEntry, OfflineSummary } from '@/types/game'

export const useBattleStore = defineStore('battle', () => {
  const MAX_FRONTEND_LOGS = 100
  const battleLogs = ref<BattleLogEntry[][]>([])
  const offlineSummary = ref<OfflineSummary | null>(null)

  function addBattleLogs(logs: BattleLogEntry[][]) {
    battleLogs.value.push(...logs)
    if (battleLogs.value.length > MAX_FRONTEND_LOGS) {
      battleLogs.value = battleLogs.value.slice(-MAX_FRONTEND_LOGS)
    }
  }

  function clearOfflineSummary() {
    offlineSummary.value = null
  }

  function setOfflineSummary(summary: OfflineSummary | null) {
    offlineSummary.value = summary
  }

  /** ログアウト時に前アカウントの状態を残さないためのリセット */
  function reset() {
    battleLogs.value = []
    offlineSummary.value = null
  }

  return {
    battleLogs, offlineSummary,
    addBattleLogs, clearOfflineSummary, setOfflineSummary, reset,
  }
})
