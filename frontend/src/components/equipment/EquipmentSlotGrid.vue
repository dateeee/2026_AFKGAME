<script setup lang="ts">
import { useEquipmentStore, SLOT_LABELS, RARITY_COLORS, BASE_NAMES } from '@/stores/equipmentStore'
import type { EquipmentSlot } from '@/types/game'

const equipmentStore = useEquipmentStore()

const SLOTS: EquipmentSlot[] = ['weapon', 'shield', 'head', 'body', 'arms', 'waist', 'legs', 'ears', 'ring']

defineEmits<{
  selectSlot: [slot: EquipmentSlot]
}>()
</script>

<template>
  <div class="slot-grid">
    <button
      v-for="slot in SLOTS"
      :key="slot"
      type="button"
      class="slot-item"
      :class="{ filled: equipmentStore.equippedItems[slot] }"
      :style="equipmentStore.equippedItems[slot]
        ? { '--slot-rarity-color': RARITY_COLORS[equipmentStore.equippedItems[slot]!.rarity] }
        : {}"
      @click="$emit('selectSlot', slot)"
    >
      <span class="slot-label">{{ SLOT_LABELS[slot] }}</span>
      <span v-if="equipmentStore.equippedItems[slot]" class="slot-name">
        {{ BASE_NAMES[equipmentStore.equippedItems[slot]!.baseId] ?? equipmentStore.equippedItems[slot]!.baseId }}
      </span>
      <span v-else class="slot-empty" aria-label="未装備">—</span>
    </button>
  </div>
</template>

<style scoped>
.slot-grid {
  display: grid;
  /* 320px 幅でも 3 列が破綻しないよう、最小幅を指定して自動で列数を決める */
  grid-template-columns: repeat(auto-fill, minmax(5.5rem, 1fr));
  gap: 0.5rem;
}

.slot-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.1875rem;
  /* 空スロットも指で押せる高さを確保する */
  min-height: 3.75rem;
  padding: 0.5rem 0.375rem;
  background-color: var(--color-surface-2);
  border: 1px dashed var(--color-line);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--duration-fast) ease, border-color var(--duration-fast) ease;
}

@media (hover: hover) {
  .slot-item:hover {
    background-color: var(--color-surface-3);
    border-color: var(--color-line-strong);
  }
}

/* 装備済みは実線 + レアリティ色。空きは破線のままにして一目で区別できるようにする */
.slot-item.filled {
  border-style: solid;
  border-color: var(--slot-rarity-color, var(--color-line-strong));
  background-color: var(--color-surface-3);
}

.slot-label {
  font-size: var(--text-caption);
  color: var(--color-content-muted);
}

.slot-name {
  font-size: var(--text-label);
  font-weight: 600;
  color: var(--slot-rarity-color, var(--color-content-strong));
  text-align: center;
  line-height: 1.25;
}

.slot-empty {
  font-size: var(--text-label);
  color: var(--color-content-faint);
}
</style>
