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
    <div
      v-for="slot in SLOTS"
      :key="slot"
      class="slot-item"
      :class="{ filled: equipmentStore.equippedItems[slot] }"
      @click="$emit('selectSlot', slot)"
    >
      <div class="slot-label">{{ SLOT_LABELS[slot] }}</div>
      <div v-if="equipmentStore.equippedItems[slot]" class="slot-equip">
        <span
          class="equip-name"
          :style="{ color: RARITY_COLORS[equipmentStore.equippedItems[slot]!.rarity] }"
        >
          {{ BASE_NAMES[equipmentStore.equippedItems[slot]!.baseId] ?? equipmentStore.equippedItems[slot]!.baseId }}
        </span>
      </div>
      <div v-else class="slot-empty">---</div>
    </div>
  </div>
</template>

<style scoped>
.slot-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
}
.slot-item {
  border: 1px solid var(--color-border);
  border-radius: 4px;
  padding: 0.5rem;
  text-align: center;
  cursor: pointer;
  background: var(--color-bg-secondary);
  transition: background 0.15s;
}
.slot-item:hover {
  background: var(--color-bg-hover, #2a2a2a);
}
.slot-item.filled {
  border-color: var(--color-primary);
}
.slot-label {
  font-size: 0.6875rem;
  color: var(--color-text-muted);
  margin-bottom: 0.25rem;
}
.equip-name {
  font-size: 0.8125rem;
  font-weight: bold;
}
.slot-empty {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}
</style>
