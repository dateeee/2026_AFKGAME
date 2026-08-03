import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Character, GameState } from '@/types/game'

export const usePlayerStore = defineStore('player', () => {
  const characters = ref<Character[]>([])
  const potions = ref<Record<string, number>>({})

  function loadFromState(state: GameState) {
    characters.value = state.characters
    potions.value = state.potions
  }

  function updateCharacter(updated: Character) {
    const index = characters.value.findIndex(c => c.id === updated.id)
    if (index !== -1) {
      characters.value[index] = updated
    }
  }

  /** ログアウト時に前アカウントの状態を残さないためのリセット */
  function reset() {
    characters.value = []
    potions.value = {}
  }

  return {
    characters, potions,
    loadFromState, updateCharacter, reset,
  }
})
