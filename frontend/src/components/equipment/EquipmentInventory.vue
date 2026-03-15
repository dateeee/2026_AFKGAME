<script setup lang="ts">
import { ref, computed } from 'vue'
import { useEquipmentStore, RARITY_LABELS, SLOT_LABELS } from '@/stores/equipmentStore'
import { useGameStore } from '@/stores/gameStore'
import { formatGold } from '@/utils/format'
import EquipmentCard from './EquipmentCard.vue'
import type { Equipment, EquipmentSlot, Rarity } from '@/types/game'

const props = defineProps<{
  filterSlot?: EquipmentSlot | null
}>()

const emit = defineEmits<{
  select: [item: Equipment]
}>()

const equipmentStore = useEquipmentStore()
const gameStore = useGameStore()

const filterRarity = ref<Rarity | ''>('')
const sortKey = ref<'level' | 'rarity'>('level')
const selectedIds = ref<Set<string>>(new Set())
const selectionMode = ref(false)

const RARITY_ORDER: Rarity[] = ['common', 'uncommon', 'rare', 'epic', 'legendary']

const filteredItems = computed(() => {
  let list = equipmentStore.items.filter(e =>
    !Object.values(equipmentStore.equipped).includes(e.id)
  )
  if (props.filterSlot) {
    list = list.filter(e => e.slot === props.filterSlot)
  }
  if (filterRarity.value) {
    list = list.filter(e => e.rarity === filterRarity.value)
  }
  list.sort((a, b) => {
    if (sortKey.value === 'rarity') {
      return RARITY_ORDER.indexOf(b.rarity as Rarity) - RARITY_ORDER.indexOf(a.rarity as Rarity)
    }
    return b.level - a.level
  })
  return list
})

function toggleSelect(id: string) {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
}

async function sellSelected() {
  const ids = Array.from(selectedIds.value)
  if (ids.length === 0) return
  const result = await equipmentStore.sellItems(ids)
  gameStore.gold += result.goldEarned
  selectedIds.value.clear()
  selectionMode.value = false
}

function handleClick(item: Equipment) {
  if (selectionMode.value) {
    if (!item.locked) {
      toggleSelect(item.id)
    }
  } else {
    emit('select', item)
  }
}
</script>

<template>
  <div class="inventory">
    <div class="inv-controls">
      <select v-model="filterRarity" class="inv-filter">
        <option value="">全レアリティ</option>
        <option v-for="r in RARITY_ORDER" :key="r" :value="r">{{ RARITY_LABELS[r] }}</option>
      </select>
      <select v-model="sortKey" class="inv-filter">
        <option value="level">Lv順</option>
        <option value="rarity">レアリティ順</option>
      </select>
      <button
        class="btn-sell-mode"
        :class="{ active: selectionMode }"
        @click="selectionMode = !selectionMode; selectedIds.clear()"
      >
        {{ selectionMode ? 'キャンセル' : '売却' }}
      </button>
    </div>

    <div v-if="selectionMode && selectedIds.size > 0" class="sell-bar">
      <span>{{ selectedIds.size }}件選択中</span>
      <button class="btn-confirm-sell" @click="sellSelected">売却する</button>
    </div>

    <div class="inv-grid">
      <EquipmentCard
        v-for="item in filteredItems"
        :key="item.id"
        :item="item"
        :selected="selectedIds.has(item.id)"
        @click="handleClick(item)"
      />
    </div>

    <div v-if="filteredItems.length === 0" class="inv-empty">
      装備がありません
    </div>
  </div>
</template>

<style scoped>
.inv-controls {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
  flex-wrap: wrap;
}
.inv-filter {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-secondary);
  color: var(--color-text);
  font-size: 0.8125rem;
}
.btn-sell-mode {
  margin-left: auto;
  padding: 0.25rem 0.75rem;
  border-radius: 4px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-secondary);
  color: var(--color-text);
  cursor: pointer;
  font-size: 0.8125rem;
}
.btn-sell-mode.active {
  background: var(--color-danger);
  color: white;
  border-color: var(--color-danger);
}
.sell-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem;
  background: var(--color-bg-secondary);
  border-radius: 4px;
  margin-bottom: 0.5rem;
  font-size: 0.875rem;
}
.btn-confirm-sell {
  padding: 0.25rem 0.75rem;
  background: var(--color-danger);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.inv-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 0.5rem;
}
.inv-empty {
  text-align: center;
  color: var(--color-text-muted);
  padding: 2rem;
}
</style>
