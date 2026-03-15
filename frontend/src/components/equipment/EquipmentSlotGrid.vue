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
  <div class="grid grid-cols-3 gap-2">
    <div
      v-for="slot in SLOTS"
      :key="slot"
      class="slot-item"
      :class="{ filled: equipmentStore.equippedItems[slot] }"
      :style="equipmentStore.equippedItems[slot] ? { borderColor: RARITY_COLORS[equipmentStore.equippedItems[slot]!.rarity] } : {}"
      @click="$emit('selectSlot', slot)"
    >
      <div class="text-[0.6875rem] text-text-muted mb-1">{{ SLOT_LABELS[slot] }}</div>
      <div v-if="equipmentStore.equippedItems[slot]">
        <span
          class="text-sm font-semibold"
          :style="{ color: RARITY_COLORS[equipmentStore.equippedItems[slot]!.rarity] }"
        >
          {{ BASE_NAMES[equipmentStore.equippedItems[slot]!.baseId] ?? equipmentStore.equippedItems[slot]!.baseId }}
        </span>
      </div>
      <div v-else class="text-xs text-text-muted">---</div>
    </div>
  </div>
</template>

<style scoped>
.slot-item {
  border: 2px dashed var(--color-border);
  border-radius: 0.5rem;
  padding: 0.5rem;
  text-align: center;
  cursor: pointer;
  background: var(--color-bg-secondary);
  transition: all 150ms;
}

.slot-item:hover {
  background: var(--color-bg-elevated);
  border-color: var(--color-border-glow);
}

.slot-item.filled {
  border-style: solid;
  box-shadow: 0 0 8px color-mix(in srgb, var(--color-primary) 20%, transparent);
}
</style>
