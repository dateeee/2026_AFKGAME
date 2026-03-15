<script setup lang="ts">
import { ref } from 'vue'
import { useEquipmentStore } from '@/stores/equipmentStore'
import { usePlayerStore } from '@/stores/playerStore'
import { useGameStore } from '@/stores/gameStore'
import EquipmentSlotGrid from '@/components/equipment/EquipmentSlotGrid.vue'
import EquipmentInventory from '@/components/equipment/EquipmentInventory.vue'
import EquipmentCompare from '@/components/equipment/EquipmentCompare.vue'
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
    // スロットが選択されていないか、スロットが一致しない場合は自動でスロット選択
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
    gameStore.setConnectionError((e as Error).message)
  }
}

async function onUnequip(slot: EquipmentSlot) {
  const character = playerStore.characters[0]
  if (!character) return
  try {
    await equipmentStore.equipItem(character.id, slot, null)
  } catch (e) {
    gameStore.setConnectionError((e as Error).message)
  }
}
</script>

<template>
  <div class="equipment-view">
    <h2 class="page-title">装備</h2>

    <section class="section">
      <h3 class="section-title">装備中</h3>
      <EquipmentSlotGrid @select-slot="onSelectSlot" />
      <div v-if="selectedSlot && equipmentStore.equippedItems[selectedSlot]" class="unequip-area">
        <button class="btn-unequip" @click="onUnequip(selectedSlot!)">
          {{ selectedSlot }}スロットの装備を外す
        </button>
      </div>
    </section>

    <section class="section">
      <h3 class="section-title">
        所持装備
        <span v-if="selectedSlot" class="filter-badge">{{ selectedSlot }}のみ表示中
          <button class="btn-clear-filter" @click="selectedSlot = null">&times;</button>
        </span>
      </h3>
      <EquipmentInventory
        :filter-slot="selectedSlot"
        @select="onSelectInventoryItem"
      />
    </section>

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
  max-width: 600px;
  margin: 0 auto;
}
.page-title {
  font-size: 1.25rem;
  margin-bottom: 1rem;
}
.section {
  margin-bottom: 1.5rem;
}
.section-title {
  font-size: 1rem;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.filter-badge {
  font-size: 0.75rem;
  padding: 0.1rem 0.5rem;
  background: var(--color-primary);
  color: white;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}
.btn-clear-filter {
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  font-size: 0.875rem;
  padding: 0;
  line-height: 1;
}
.unequip-area {
  margin-top: 0.5rem;
}
.btn-unequip {
  padding: 0.25rem 0.75rem;
  border-radius: 4px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-secondary);
  color: var(--color-text);
  cursor: pointer;
  font-size: 0.8125rem;
}
</style>
