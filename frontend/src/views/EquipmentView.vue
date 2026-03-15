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
  <div class="mx-auto max-w-[600px]">
    <h1 class="font-display text-xl font-bold text-accent mb-4">装備</h1>

    <!-- Equipped Section -->
    <section class="panel mb-4">
      <h2 class="panel-title">装備中</h2>
      <EquipmentSlotGrid @select-slot="onSelectSlot" />
      <div v-if="selectedSlot && equipmentStore.equippedItems[selectedSlot]" class="mt-2">
        <button class="btn btn-secondary text-sm" @click="onUnequip(selectedSlot!)">
          {{ selectedSlot }}スロットの装備を外す
        </button>
      </div>
    </section>

    <!-- Inventory Section -->
    <section class="panel">
      <h2 class="panel-title flex items-center gap-2">
        所持装備
        <span v-if="selectedSlot" class="badge bg-primary text-white">
          {{ selectedSlot }}のみ表示中
          <button class="ml-1 hover:opacity-70" @click="selectedSlot = null">&times;</button>
        </span>
      </h2>
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
