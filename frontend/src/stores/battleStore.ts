import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { BattleLogEntry, OfflineSummary } from '@/types/game'
import { useGameStore } from '@/stores/gameStore'

/** 保持できるログの上限（tick群単位）。DB保存件数と揃える（ui.md §設定画面） */
const MAX_FRONTEND_LOGS = 100

export const useBattleStore = defineStore('battle', () => {
  const battleLogs = ref<BattleLogEntry[][]>([])
  const offlineSummary = ref<OfflineSummary | null>(null)

  /**
   * 設定「戦闘ログ表示件数」（20/50/100）を超えた古いログを破棄する
   * （tech_polling.md §5）。単位は tick 群で、DB保存件数100件と揃える。
   */
  function addBattleLogs(logs: BattleLogEntry[][]) {
    const limit = Math.min(useGameStore().settings.battleLogCount, MAX_FRONTEND_LOGS)
    battleLogs.value.push(...logs)
    if (battleLogs.value.length > limit) {
      battleLogs.value = battleLogs.value.slice(-limit)
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
