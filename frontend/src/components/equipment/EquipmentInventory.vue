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
  <div>
    <!-- Controls -->
    <div class="flex flex-wrap gap-2 mb-3">
      <select v-model="filterRarity" class="inv-select">
        <option value="">全レアリティ</option>
        <option v-for="r in RARITY_ORDER" :key="r" :value="r">{{ RARITY_LABELS[r] }}</option>
      </select>
      <select v-model="sortKey" class="inv-select">
        <option value="level">Lv順</option>
        <option value="rarity">レアリティ順</option>
      </select>
      <button
        class="btn ml-auto"
        :class="selectionMode ? 'btn-danger' : 'btn-secondary'"
        @click="selectionMode = !selectionMode; selectedIds.clear()"
      >
        {{ selectionMode ? 'キャンセル' : '売却' }}
      </button>
    </div>

    <!-- Sell Bar -->
    <div
      v-if="selectionMode && selectedIds.size > 0"
      class="flex justify-between items-center p-2 bg-bg-secondary rounded-lg border border-border mb-2 text-sm"
    >
      <span>{{ selectedIds.size }}件選択中</span>
      <button class="btn btn-danger text-sm" @click="sellSelected">売却する</button>
    </div>

    <!-- Grid -->
    <div class="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-2">
      <EquipmentCard
        v-for="item in filteredItems"
        :key="item.id"
        :item="item"
        :selected="selectedIds.has(item.id)"
        @click="handleClick(item)"
      />
    </div>

    <div v-if="filteredItems.length === 0" class="text-center text-text-muted py-8">
      装備がありません
    </div>
  </div>
</template>

<style scoped>
.inv-select {
  padding: 0.25rem 0.5rem;
  border-radius: 0.375rem;
  border: 1px solid var(--color-border);
  background: var(--color-bg-secondary);
  color: var(--color-text);
  font-size: 0.8125rem;
  font-family: var(--font-body);
  transition: border-color 150ms;
}

.inv-select:focus {
  border-color: var(--color-primary);
  outline: none;
}
</style>
