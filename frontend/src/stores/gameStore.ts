import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { EnemyInfo, GameState, Settings, TowerClearInfo } from '@/types/game'

export const useGameStore = defineStore('game', () => {
  const gold = ref(0)
  const currentTowerId = ref<string | null>(null)
  const currentFloor = ref<number | null>(null)
  const targetFloor = ref<number | null>(null)
  const towerMode = ref<string>('auto_repeat')
  const hpThreshold = ref(0.3)
  const highestFloor = ref(0)
  const currentEnemy = ref<EnemyInfo | null>(null)
  const settings = ref<Settings>({
    potionThreshold: 0.5,
    battleLogCount: 50,
    toastEnabled: true,
    autoSellRarity: null,
  })
  const towersCleared = ref<Record<string, TowerClearInfo>>({})
  const isConnected = ref(true)
  const isLoading = ref(false)
  const errorMessage = ref<string | null>(null)

  function loadFromState(state: GameState) {
    gold.value = state.player.gold
    currentTowerId.value = state.player.currentTowerId
    currentFloor.value = state.player.currentFloor
    targetFloor.value = state.player.targetFloor
    towerMode.value = state.player.towerMode
    hpThreshold.value = state.player.hpThreshold
    highestFloor.value = state.player.highestFloor ?? 0
    currentEnemy.value = state.currentEnemy ?? null
    settings.value = state.settings
    towersCleared.value = state.towersCleared
  }

  function setConnectionError(message: string) {
    isConnected.value = false
    errorMessage.value = message
  }

  function clearError() {
    isConnected.value = true
    errorMessage.value = null
  }

  return {
    gold, currentTowerId, currentFloor, targetFloor, towerMode, hpThreshold,
    highestFloor, currentEnemy,
    settings, towersCleared, isConnected, isLoading, errorMessage,
    loadFromState, setConnectionError, clearError,
  }
})
