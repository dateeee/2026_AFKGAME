import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { EnemyInfo, GameState, Settings, TowerClearInfo, TowerInfo } from '@/types/game'
import {
  getGameState,
  getTowerList,
  postTowerRetire,
  postTowerSelect,
  putTowerMode,
} from '@/api/client'
import { ApiError, errorMessage as errorMessage_ } from '@/api/errors'
import { usePlayerStore } from '@/stores/playerStore'

/** 設定の既定値（正はサーバー: backend/app/config.py の DEFAULT_* / ui.md §設定画面） */
const DEFAULT_SETTINGS: Settings = {
  potionThreshold: 0.3,
  battleLogCount: 50,
  toastEnabled: true,
  autoSellRarity: null,
}

export const useGameStore = defineStore('game', () => {
  const gold = ref(0)
  const currentTowerId = ref<string | null>(null)
  const currentFloor = ref<number | null>(null)
  const targetFloor = ref<number | null>(null)
  const towerMode = ref<string>('auto_repeat')
  const hpThreshold = ref(0.3)
  const highestFloor = ref(0)
  const currentEnemy = ref<EnemyInfo | null>(null)
  const settings = ref<Settings>({ ...DEFAULT_SETTINGS })
  const towersCleared = ref<Record<string, TowerClearInfo>>({})
  const towers = ref<TowerInfo[]>([])
  const isConnected = ref(true)
  const isLoading = ref(false)
  // 通信断の告知（ConnectionBanner に固定表示される）
  const errorMessage = ref<string | null>(null)
  // 操作が拒否された業務エラー（ゴールド不足・不正な目標階など）。通信断とは分けて出す
  const actionError = ref<string | null>(null)

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

  async function loadTowers() {
    towers.value = await getTowerList()
  }

  /**
   * 塔操作の結果をゲーム状態から取り直して反映する。
   * 塔の出入りはプレイヤー側（HP・所持金）も動くため playerStore も同じ応答で更新する。
   */
  async function reloadState() {
    const state = await getGameState()
    loadFromState(state)
    usePlayerStore().loadFromState(state)
  }

  /** 塔へ入る（目標階を指定して攻略を開始する） */
  async function enterTower(towerId: string, floor: number) {
    try {
      await postTowerSelect(towerId, floor)
      await reloadState()
    } catch (e) {
      reportActionFailure(e)
    }
  }

  /** 攻略中の塔から撤退する */
  async function retireFromTower() {
    try {
      await postTowerRetire()
      await reloadState()
    } catch (e) {
      reportActionFailure(e)
    }
  }

  /** 周回モードを反転させる（auto_repeat ⇄ stop_on_clear） */
  async function toggleTowerMode() {
    const newMode = towerMode.value === 'auto_repeat' ? 'stop_on_clear' : 'auto_repeat'
    try {
      await putTowerMode(newMode)
      towerMode.value = newMode
    } catch (e) {
      reportActionFailure(e)
    }
  }

  function setConnectionError(message: string) {
    isConnected.value = false
    errorMessage.value = message
  }

  function clearError() {
    isConnected.value = true
    errorMessage.value = null
  }

  function setActionError(message: string) {
    actionError.value = message
  }

  function clearActionError() {
    actionError.value = null
  }

  /**
   * 操作の失敗を種別に応じて振り分ける。
   * 通信断は ConnectionBanner、業務エラーはインライン表示（actionError）へ。
   */
  function reportActionFailure(error: unknown) {
    if (error instanceof ApiError && !error.isNetworkError) {
      setActionError(error.message)
      return
    }
    setConnectionError(errorMessage_(error))
  }

  /** ログアウト時に前アカウントの状態を残さないためのリセット */
  function reset() {
    gold.value = 0
    currentTowerId.value = null
    currentFloor.value = null
    targetFloor.value = null
    towerMode.value = 'auto_repeat'
    hpThreshold.value = 0.3
    highestFloor.value = 0
    currentEnemy.value = null
    settings.value = { ...DEFAULT_SETTINGS }
    towersCleared.value = {}
    towers.value = []
    isLoading.value = false
    clearError()
    clearActionError()
  }

  return {
    gold,
    currentTowerId,
    currentFloor,
    targetFloor,
    towerMode,
    hpThreshold,
    highestFloor,
    currentEnemy,
    settings,
    towersCleared,
    towers,
    isConnected,
    isLoading,
    errorMessage,
    actionError,
    loadFromState,
    loadTowers,
    enterTower,
    retireFromTower,
    toggleTowerMode,
    setConnectionError,
    clearError,
    setActionError,
    clearActionError,
    reportActionFailure,
    reset,
  }
})
