<script setup lang="ts">
import type { Equipment } from '@/types/game'
import { RARITY_COLORS, RARITY_LABELS, SLOT_LABELS, BASE_NAMES } from '@/stores/equipmentStore'
import AppIcon from '@/components/ui/AppIcon.vue'

defineProps<{
  item: Equipment
  selected?: boolean
}>()

defineEmits<{
  click: []
}>()
</script>

<template>
  <div
    class="equipment-card"
    :class="{ selected, locked: item.locked, legendary: item.rarity === 'legendary' }"
    :style="{ '--card-rarity-color': RARITY_COLORS[item.rarity] }"
    role="button"
    tabindex="0"
    @click="$emit('click')"
    @keydown.enter.prevent="$emit('click')"
    @keydown.space.prevent="$emit('click')"
  >
    <div class="card-head">
      <span class="font-semibold card-name">
        {{ BASE_NAMES[item.baseId] ?? item.baseId }}
      </span>
      <AppIcon v-if="item.locked" name="lock" :size="14" class="card-lock" />
    </div>

    <p class="card-meta num">
      {{ RARITY_LABELS[item.rarity] }} Lv.{{ item.level }}
    </p>
    <p class="card-meta">
      {{ SLOT_LABELS[item.slot] }}{{ item.isTwoHanded ? '（両手）' : '' }}
    </p>

    <div class="chips">
      <span v-if="item.statAtk" class="stat-chip num">ATK +{{ item.statAtk }}</span>
      <span v-if="item.statDef" class="stat-chip num">DEF +{{ item.statDef }}</span>
      <span v-if="item.statHp" class="stat-chip num">HP +{{ item.statHp }}</span>
      <span v-if="item.statSpd" class="stat-chip num">SPD +{{ item.statSpd }}</span>
      <span v-if="item.lifesteal" class="stat-chip stat-chip-life num">
        吸収 {{ (item.lifesteal * 100).toFixed(1) }}%
      </span>
    </div>
  </div>
</template>

<style scoped>
.equipment-card {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  /* レアリティは上端の細い帯で示す。枠線全体を塗ると一覧が色で埋まる */
  padding: 0.625rem;
  background-color: var(--color-surface-2);
  border: 1px solid var(--color-line-soft);
  border-top: 2px solid var(--card-rarity-color, var(--color-line));
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--duration-fast) ease, box-shadow var(--duration-fast) ease;
}

@media (hover: hover) {
  .equipment-card:hover {
    background-color: var(--color-surface-3);
  }
}

.equipment-card.selected {
  background-color: var(--color-surface-3);
  box-shadow: 0 0 0 2px var(--card-rarity-color);
}

.equipment-card.legendary {
  --glow-color: var(--color-rarity-legendary);
  animation: var(--animate-rarity-glow);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.375rem;
}

.card-name {
  font-size: var(--text-label);
  font-weight: 600;
  color: var(--card-rarity-color, var(--color-content-strong));
}

.card-lock {
  color: var(--color-content-faint);
}

.card-meta {
  font-size: var(--text-caption);
  color: var(--color-content-muted);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  margin-top: 0.375rem;
}

.stat-chip {
  padding: 0.125rem 0.375rem;
  background-color: var(--color-surface-inset);
  border-radius: var(--radius-sm);
  font-size: var(--text-caption);
  font-weight: 600;
  color: var(--color-content-strong);
}

.stat-chip-life {
  color: var(--color-hp-bright);
}
</style>
