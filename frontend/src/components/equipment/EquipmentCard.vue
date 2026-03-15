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
    :class="{ selected, locked: item.locked, legendary: item.rarity === 'legendary' }"
    :style="{
      '--card-rarity-color': RARITY_COLORS[item.rarity],
      borderColor: RARITY_COLORS[item.rarity],
    }"
    @click="$emit('click')"
  >
    <div class="flex justify-between items-center">
      <span class="font-semibold text-sm" :style="{ color: RARITY_COLORS[item.rarity] }">
        {{ BASE_NAMES[item.baseId] ?? item.baseId }}
      </span>
      <span v-if="item.locked" class="text-xs" title="ロック中">&#128274;</span>
    </div>
    <div class="text-xs text-text-muted">{{ RARITY_LABELS[item.rarity] }} Lv.{{ item.level }}</div>
    <div class="text-xs text-text-muted">{{ SLOT_LABELS[item.slot] }}{{ item.isTwoHanded ? '(両手)' : '' }}</div>
    <div class="flex flex-wrap gap-1 mt-1">
      <span v-if="item.statAtk" class="stat-chip">ATK +{{ item.statAtk }}</span>
      <span v-if="item.statDef" class="stat-chip">DEF +{{ item.statDef }}</span>
      <span v-if="item.statHp" class="stat-chip">HP +{{ item.statHp }}</span>
      <span v-if="item.statSpd" class="stat-chip">SPD +{{ item.statSpd }}</span>
      <span v-if="item.lifesteal" class="stat-chip text-hp">吸収 {{ (item.lifesteal * 100).toFixed(1) }}%</span>
    </div>
  </div>
</template>

<style scoped>
.equipment-card {
  border: 2px solid var(--color-border);
  border-radius: 0.5rem;
  padding: 0.5rem;
  cursor: pointer;
  background: linear-gradient(135deg, var(--color-bg-secondary) 0%, color-mix(in srgb, var(--card-rarity-color, #555) 5%, var(--color-bg-secondary) 95%) 100%);
  transition: transform 150ms, box-shadow 150ms, background 150ms;
  font-size: 0.8125rem;
}

.equipment-card:hover {
  background-color: var(--color-bg-elevated);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.equipment-card.selected {
  background-color: var(--color-bg-elevated);
  box-shadow: 0 0 0 1px var(--card-rarity-color), 0 4px 12px rgba(0, 0, 0, 0.3);
  transform: scale(1.02);
}

.equipment-card.legendary {
  animation: var(--animate-rarity-glow);
  --glow-color: var(--color-rarity-legendary);
}

.stat-chip {
  font-size: 0.6875rem;
  font-weight: 500;
  padding: 0.1rem 0.375rem;
  background: var(--color-bg-darkest);
  border-radius: 0.25rem;
  color: var(--color-text-bright);
}
</style>
