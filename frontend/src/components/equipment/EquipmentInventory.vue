<script setup lang="ts">
import { ref, computed } from 'vue'
import { useEquipmentStore, RARITY_LABELS } from '@/stores/equipmentStore'
import { useGameStore } from '@/stores/gameStore'
import EquipmentCard from './EquipmentCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import { errorMessage } from '@/api/errors'
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
const sellError = ref('')

const RARITY_ORDER: Rarity[] = ['common', 'uncommon', 'rare', 'epic', 'legendary']

const RARITY_OPTIONS: Array<{ value: Rarity | ''; label: string }> = [
  { value: '', label: '全レアリティ' },
  ...RARITY_ORDER.map(r => ({ value: r, label: RARITY_LABELS[r] ?? r })),
]

const SORT_OPTIONS: Array<{ value: 'level' | 'rarity'; label: string }> = [
  { value: 'level', label: 'Lv順' },
  { value: 'rarity', label: 'レアリティ順' },
]

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

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  selectedIds.value.clear()
  sellError.value = ''
}

async function sellSelected() {
  const ids = Array.from(selectedIds.value)
  if (ids.length === 0) return

  sellError.value = ''
  try {
    const result = await equipmentStore.sellItems(ids)
    gameStore.gold += result.goldEarned
    selectedIds.value.clear()
    selectionMode.value = false
  } catch (e) {
    // 失敗時は選択状態を保持したままエラーを出す（選び直しの手間を増やさない）
    sellError.value = errorMessage(e)
  }
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
    <div class="controls">
      <BaseSelect
        v-model="filterRarity"
        :options="RARITY_OPTIONS"
        aria-label="レアリティで絞り込む"
      />
      <BaseSelect
        v-model="sortKey"
        :options="SORT_OPTIONS"
        aria-label="並び替え"
      />
      <BaseButton
        :variant="selectionMode ? 'danger' : 'secondary'"
        size="sm"
        class="controls-sell"
        @click="toggleSelectionMode"
      >
        {{ selectionMode ? 'キャンセル' : '売却' }}
      </BaseButton>
    </div>

    <p v-if="sellError" class="sell-error" role="alert">{{ sellError }}</p>

    <!-- 選択中の操作バー。ナビの上に固定して、一覧を下までたどっても押せるようにする -->
    <div v-if="selectionMode && selectedIds.size > 0" class="sell-bar">
      <span class="sell-count num">{{ selectedIds.size }}件選択中</span>
      <BaseButton variant="danger" size="sm" @click="sellSelected">売却する</BaseButton>
    </div>

    <div v-if="filteredItems.length > 0" class="card-grid">
      <EquipmentCard
        v-for="item in filteredItems"
        :key="item.id"
        :item="item"
        :selected="selectedIds.has(item.id)"
        @click="handleClick(item)"
      />
    </div>

    <p v-else class="empty">装備がありません</p>
  </div>
</template>

<style scoped>
.inventory {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.controls-sell {
  margin-left: auto;
}

/* 売却失敗の業務エラー。通信断は ConnectionBanner が担当する */
.sell-error {
  margin: 0;
  padding: 0.625rem 0.875rem;
  background-color: var(--color-surface-2);
  border: 1px solid var(--color-line-soft);
  border-left: 3px solid var(--color-danger);
  border-radius: var(--radius-md);
  font-size: var(--text-label);
  color: var(--color-content);
}

.sell-bar {
  position: sticky;
  bottom: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  background-color: var(--color-surface-3);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-raised);
}

.sell-count {
  font-size: var(--text-label);
  color: var(--color-content-strong);
}

.card-grid {
  display: grid;
  /* 320px 幅でも 2 列を確保できる下限 */
  grid-template-columns: repeat(auto-fill, minmax(8.5rem, 1fr));
  gap: 0.5rem;
}

.empty {
  padding: 2.5rem 0;
  text-align: center;
  font-size: var(--text-label);
  color: var(--color-content-faint);
}
</style>
