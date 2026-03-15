import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { BattleLogEntry, OfflineSummary } from '@/types/game'

export const useBattleStore = defineStore('battle', () => {
  const battleLogs = ref<BattleLogEntry[][]>([])
  const offlineSummary = ref<OfflineSummary | null>(null)

  function addBattleLogs(logs: BattleLogEntry[][]) {
    battleLogs.value.push(...logs)
  }

  function clearOfflineSummary() {
    offlineSummary.value = null
  }

  function setOfflineSummary(summary: OfflineSummary | null) {
    offlineSummary.value = summary
  }

  return {
    battleLogs, offlineSummary,
    addBattleLogs, clearOfflineSummary, setOfflineSummary,
  }
})
