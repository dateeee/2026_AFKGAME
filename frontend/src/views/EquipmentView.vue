<script setup lang="ts">
import { ref } from 'vue'
import { useEquipmentStore, SLOT_LABELS } from '@/stores/equipmentStore'
import { usePlayerStore } from '@/stores/playerStore'
import { useGameStore } from '@/stores/gameStore'
import EquipmentSlotGrid from '@/components/equipment/EquipmentSlotGrid.vue'
import EquipmentInventory from '@/components/equipment/EquipmentInventory.vue'
import EquipmentCompare from '@/components/equipment/EquipmentCompare.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseIcon from '@/components/ui/BaseIcon.vue'
import type { Equipment, EquipmentSlot } from '@/types/game'

const equipmentStore = useEquipmentStore()
const playerStore = usePlayerStore()
const gameStore = useGameStore()

const selectedSlot = ref<EquipmentSlot | null>(null)
const compareItem = ref<Equipment | null>(null)

function onSelectSlot(slot: EquipmentSlot) {
  selectedSlot.value = slot
  compareItem.value = null
}

function onSelectInventoryItem(item: Equipment) {
  if (selectedSlot.value && item.slot === selectedSlot.value) {
    compareItem.value = item
  } else {
    selectedSlot.value = item.slot
    compareItem.value = item
  }
}

async function onEquip() {
  if (!compareItem.value || !selectedSlot.value) return
  const character = playerStore.characters[0]
  if (!character) return
  try {
    await equipmentStore.equipItem(character.id, selectedSlot.value, compareItem.value.id)
    compareItem.value = null
  } catch (e) {
    gameStore.reportActionFailure(e)
  }
}

async function onUnequip(slot: EquipmentSlot) {
  const character = playerStore.characters[0]
  if (!character) return
  try {
    await equipmentStore.equipItem(character.id, slot, null)
  } catch (e) {
    gameStore.reportActionFailure(e)
  }
}
</script>

<template>
  <div class="equipment-view">
    <h1>装備</h1>

    <p v-if="gameStore.actionError" class="action-error" role="alert">
      {{ gameStore.actionError }}
    </p>

    <BaseCard title="装備中">
      <EquipmentSlotGrid @select-slot="onSelectSlot" />
      <BaseButton
        v-if="selectedSlot && equipmentStore.equippedItems[selectedSlot]"
        variant="secondary"
        size="sm"
        class="unequip-btn"
        @click="onUnequip(selectedSlot!)"
      >
        {{ SLOT_LABELS[selectedSlot] ?? selectedSlot }}スロットの装備を外す
      </BaseButton>
    </BaseCard>

    <BaseCard>
      <template #title>所持装備</template>
      <template v-if="selectedSlot" #actions>
        <!-- 絞り込み中であることと、その解除を1箇所にまとめる -->
        <button type="button" class="filter-chip" @click="selectedSlot = null">
          {{ SLOT_LABELS[selectedSlot] ?? selectedSlot }}のみ表示中
          <BaseIcon name="close" :size="12" />
        </button>
      </template>

      <EquipmentInventory :filter-slot="selectedSlot" @select="onSelectInventoryItem" />
    </BaseCard>

    <EquipmentCompare
      v-if="compareItem && selectedSlot"
      :current="equipmentStore.equippedItems[selectedSlot] ?? null"
      :preview="compareItem"
      @equip="onEquip"
      @cancel="compareItem = null"
    />
  </div>
</template>

<style scoped>
.equipment-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* 業務エラー（装着拒否など）。通信断は ConnectionBanner が担当する */
.action-error {
  margin: 0;
  padding: 0.625rem 0.875rem;
  background-color: var(--color-surface-2);
  border: 1px solid var(--color-line-soft);
  border-left: 3px solid var(--color-danger);
  border-radius: var(--radius-md);
  font-size: var(--text-label);
  color: var(--color-content);
}

.unequip-btn {
  margin-top: 0.75rem;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  min-height: 1.875rem;
  padding: 0 0.5rem;
  border: 1px solid var(--color-accent-dim);
  border-radius: 999px;
  background-color: color-mix(in srgb, var(--color-accent) 10%, transparent);
  color: var(--color-accent-bright);
  font-size: var(--text-caption);
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}

@media (hover: hover) {
  .filter-chip:hover {
    background-color: color-mix(in srgb, var(--color-accent) 20%, transparent);
  }
}
</style>
