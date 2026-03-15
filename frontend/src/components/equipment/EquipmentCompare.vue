<script setup lang="ts">
import type { Equipment } from '@/types/game'
import { RARITY_COLORS, RARITY_LABELS, BASE_NAMES } from '@/stores/equipmentStore'

const props = defineProps<{
  current: Equipment | null
  preview: Equipment
}>()

defineEmits<{
  equip: []
  cancel: []
}>()

function diff(newVal: number | null, oldVal: number | null): { text: string; cls: string } | null {
  const n = newVal ?? 0
  const o = oldVal ?? 0
  if (n === o) return null
  const d = n - o
  return {
    text: d > 0 ? `+${d}` : `${d}`,
    cls: d > 0 ? 'positive' : 'negative',
  }
}

const stats = ['ATK', 'DEF', 'HP', 'SPD'] as const
const statKeys = ['statAtk', 'statDef', 'statHp', 'statSpd'] as const
</script>

<template>
  <transition name="modal-fade">
    <div class="modal-overlay" @click.self="$emit('cancel')">
      <div class="modal-panel">
        <h3 class="font-display text-base font-semibold text-accent mb-3">装備比較</h3>

        <!-- Comparison columns -->
        <div class="grid grid-cols-2 gap-3 mb-3">
          <div>
            <div class="text-xs text-text-muted mb-1">現在</div>
            <div v-if="current" class="flex flex-col">
              <span class="font-semibold text-sm" :style="{ color: RARITY_COLORS[current.rarity] }">
                {{ BASE_NAMES[current.baseId] ?? current.baseId }}
              </span>
              <span class="text-xs text-text-muted">{{ RARITY_LABELS[current.rarity] }} Lv.{{ current.level }}</span>
            </div>
            <div v-else class="text-text-muted text-sm">なし</div>
          </div>

          <div>
            <div class="text-xs text-text-muted mb-1">新規</div>
            <div class="flex flex-col">
              <span class="font-semibold text-sm" :style="{ color: RARITY_COLORS[preview.rarity] }">
                {{ BASE_NAMES[preview.baseId] ?? preview.baseId }}
              </span>
              <span class="text-xs text-text-muted">{{ RARITY_LABELS[preview.rarity] }} Lv.{{ preview.level }}</span>
            </div>
          </div>
        </div>

        <!-- Stat Table -->
        <table class="stat-table">
          <thead>
            <tr>
              <th></th>
              <th>現在</th>
              <th>新規</th>
              <th>差分</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(stat, i) in stats" :key="stat">
              <td class="font-semibold">{{ stat }}</td>
              <td>{{ current?.[statKeys[i]] ?? '-' }}</td>
              <td>{{ preview[statKeys[i]] ?? '-' }}</td>
              <td :class="diff(preview[statKeys[i]], current?.[statKeys[i]] ?? null)?.cls">
                {{ diff(preview[statKeys[i]], current?.[statKeys[i]] ?? null)?.text ?? '-' }}
              </td>
            </tr>
            <tr v-if="preview.lifesteal || current?.lifesteal">
              <td class="font-semibold">吸収</td>
              <td>{{ current?.lifesteal ? (current.lifesteal * 100).toFixed(1) + '%' : '-' }}</td>
              <td>{{ preview.lifesteal ? (preview.lifesteal * 100).toFixed(1) + '%' : '-' }}</td>
              <td></td>
            </tr>
          </tbody>
        </table>

        <!-- Actions -->
        <div class="flex gap-2">
          <button class="btn btn-primary flex-1" @click="$emit('equip')">装備する</button>
          <button class="btn btn-secondary flex-1" @click="$emit('cancel')">キャンセル</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.stat-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
  margin-bottom: 0.75rem;
}

.stat-table th,
.stat-table td {
  padding: 0.375rem 0.5rem;
  text-align: center;
  border-bottom: 1px solid var(--color-border);
}

.stat-table th {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  font-weight: 500;
}

.positive {
  color: var(--color-success);
  font-weight: 700;
}

.negative {
  color: var(--color-danger);
  font-weight: 700;
}
</style>
