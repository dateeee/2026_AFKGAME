<script setup lang="ts">
import type { Equipment } from '@/types/game'
import { RARITY_COLORS, RARITY_LABELS, SLOT_LABELS, BASE_NAMES } from '@/stores/equipmentStore'

const props = defineProps<{
  item: Equipment
  selected?: boolean
}>()

defineEmits<{
  click: []
}>()

function statText(label: string, value: number | null): string | null {
  if (value === null) return null
  return `${label} +${value}`
}
</script>

<template>
  <div
    class="equipment-card"
    :class="{ selected, locked: item.locked }"
    :style="{ borderColor: RARITY_COLORS[item.rarity] }"
    @click="$emit('click')"
  >
    <div class="card-header">
      <span class="card-name" :style="{ color: RARITY_COLORS[item.rarity] }">
        {{ BASE_NAMES[item.baseId] ?? item.baseId }}
      </span>
      <span v-if="item.locked" class="lock-icon" title="ロック中">&#128274;</span>
    </div>
    <div class="card-rarity">{{ RARITY_LABELS[item.rarity] }} Lv.{{ item.level }}</div>
    <div class="card-slot">{{ SLOT_LABELS[item.slot] }}{{ item.isTwoHanded ? '(両手)' : '' }}</div>
    <div class="card-stats">
      <span v-if="item.statAtk" class="stat">ATK +{{ item.statAtk }}</span>
      <span v-if="item.statDef" class="stat">DEF +{{ item.statDef }}</span>
      <span v-if="item.statHp" class="stat">HP +{{ item.statHp }}</span>
      <span v-if="item.statSpd" class="stat">SPD +{{ item.statSpd }}</span>
      <span v-if="item.lifesteal" class="stat lifesteal">吸収 {{ (item.lifesteal * 100).toFixed(1) }}%</span>
    </div>
  </div>
</template>

<style scoped>
.equipment-card {
  border: 2px solid #555;
  border-radius: 6px;
  padding: 0.5rem;
  cursor: pointer;
  background: var(--color-bg-secondary);
  transition: background 0.15s;
  font-size: 0.8125rem;
}
.equipment-card:hover {
  background: var(--color-bg-hover, #2a2a2a);
}
.equipment-card.selected {
  background: var(--color-bg-active, #333);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-name {
  font-weight: bold;
}
.lock-icon {
  font-size: 0.75rem;
}
.card-rarity {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}
.card-slot {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}
.card-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  margin-top: 0.25rem;
}
.stat {
  font-size: 0.75rem;
  padding: 0.1rem 0.3rem;
  background: var(--color-bg, #1a1a1a);
  border-radius: 3px;
}
.stat.lifesteal {
  color: var(--color-hp, #e74c3c);
}
</style>
