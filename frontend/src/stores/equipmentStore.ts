import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Equipment, EquipmentSlot, GameState } from '@/types/game'
import { postEquip, postEquipmentSell, postEquipmentLock } from '@/api/client'

/** レアリティ表示名 */
export const RARITY_LABELS: Record<string, string> = {
  common: 'コモン',
  uncommon: 'アンコモン',
  rare: 'レア',
  epic: 'エピック',
  legendary: 'レジェンダリー',
}

/**
 * レアリティカラー。
 * 実際の色は assets/styles/tokens.css で定義する。
 * ここに16進数を直接書かないこと（テーマ変更が2箇所に分かれるため）。
 */
export const RARITY_COLORS: Record<string, string> = {
  common: 'var(--color-rarity-common)',
  uncommon: 'var(--color-rarity-uncommon)',
  rare: 'var(--color-rarity-rare)',
  epic: 'var(--color-rarity-epic)',
  legendary: 'var(--color-rarity-legendary)',
}

/** スロット表示名 */
export const SLOT_LABELS: Record<string, string> = {
  weapon: '武器',
  shield: '盾',
  head: '頭',
  body: '胴体',
  arms: '腕',
  waist: '腰',
  legs: '足',
  ears: '耳',
  ring: '指輪',
}

/** ベースID→表示名 */
export const BASE_NAMES: Record<string, string> = {
  sword: '剣', dagger: '短剣', mace: 'メイス',
  greatsword: '大剣', spear: '槍', bow: '弓', staff: '杖',
  shield: '盾', helm: '兜', chest_armor: '鎧',
  gauntlets: '小手', belt: '帯', greaves: '脛当て',
  earring: '耳飾り', ring: '指輪',
}

export const useEquipmentStore = defineStore('equipment', () => {
  const items = ref<Equipment[]>([])
  const equipped = ref<Record<string, string | null>>({})

  function loadFromState(state: GameState) {
    items.value = state.equipment ?? []
    equipped.value = state.equipped ?? {}
  }

  function addDrops(drops: Equipment[]) {
    items.value.push(...drops)
  }

  const equippedItems = computed(() => {
    const map: Record<string, Equipment | null> = {}
    for (const [slot, eqId] of Object.entries(equipped.value)) {
      map[slot] = eqId ? items.value.find(e => e.id === eqId) ?? null : null
    }
    return map
  })

  const inventory = computed(() =>
    items.value.filter(e => !Object.values(equipped.value).includes(e.id))
  )

  async function equipItem(characterId: string, slot: EquipmentSlot, equipmentId: string | null) {
    await postEquip(characterId, slot, equipmentId)
    equipped.value[slot] = equipmentId
  }

  async function sellItems(equipmentIds: string[]) {
    const result = await postEquipmentSell(equipmentIds)
    items.value = items.value.filter(e => !equipmentIds.includes(e.id))
    return result
  }

  async function toggleLock(equipmentId: string) {
    const result = await postEquipmentLock(equipmentId)
    const item = items.value.find(e => e.id === equipmentId)
    if (item) {
      item.locked = result.locked
    }
    return result.locked
  }

  return {
    items, equipped,
    equippedItems, inventory,
    loadFromState, addDrops, equipItem, sellItems, toggleLock,
  }
})
