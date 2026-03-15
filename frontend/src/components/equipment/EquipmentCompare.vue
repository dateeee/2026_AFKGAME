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
  <div class="compare-overlay" @click.self="$emit('cancel')">
    <div class="compare-panel">
      <h3>装備比較</h3>

      <div class="compare-grid">
        <div class="compare-col">
          <div class="col-title">現在</div>
          <div v-if="current" class="col-equip">
            <span :style="{ color: RARITY_COLORS[current.rarity] }">
              {{ BASE_NAMES[current.baseId] ?? current.baseId }}
            </span>
            <span class="rarity-label">{{ RARITY_LABELS[current.rarity] }} Lv.{{ current.level }}</span>
          </div>
          <div v-else class="col-empty">なし</div>
        </div>

        <div class="compare-col">
          <div class="col-title">新規</div>
          <div class="col-equip">
            <span :style="{ color: RARITY_COLORS[preview.rarity] }">
              {{ BASE_NAMES[preview.baseId] ?? preview.baseId }}
            </span>
            <span class="rarity-label">{{ RARITY_LABELS[preview.rarity] }} Lv.{{ preview.level }}</span>
          </div>
        </div>
      </div>

      <table class="stat-table">
        <thead>
          <tr><th></th><th>現在</th><th>新規</th><th>差分</th></tr>
        </thead>
        <tbody>
          <tr v-for="(stat, i) in stats" :key="stat">
            <td>{{ stat }}</td>
            <td>{{ current?.[statKeys[i]] ?? '-' }}</td>
            <td>{{ preview[statKeys[i]] ?? '-' }}</td>
            <td :class="diff(preview[statKeys[i]], current?.[statKeys[i]] ?? null)?.cls">
              {{ diff(preview[statKeys[i]], current?.[statKeys[i]] ?? null)?.text ?? '-' }}
            </td>
          </tr>
          <tr v-if="preview.lifesteal || current?.lifesteal">
            <td>吸収</td>
            <td>{{ current?.lifesteal ? (current.lifesteal * 100).toFixed(1) + '%' : '-' }}</td>
            <td>{{ preview.lifesteal ? (preview.lifesteal * 100).toFixed(1) + '%' : '-' }}</td>
            <td></td>
          </tr>
        </tbody>
      </table>

      <div class="compare-actions">
        <button class="btn-equip" @click="$emit('equip')">装備する</button>
        <button class="btn-cancel" @click="$emit('cancel')">キャンセル</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.compare-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.compare-panel {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 1.25rem;
  max-width: 400px;
  width: 90%;
}
.compare-panel h3 {
  margin: 0 0 0.75rem;
  font-size: 1rem;
}
.compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}
.col-title {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-bottom: 0.25rem;
}
.col-equip {
  display: flex;
  flex-direction: column;
}
.rarity-label {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}
.col-empty {
  color: var(--color-text-muted);
}
.stat-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
  margin-bottom: 0.75rem;
}
.stat-table th, .stat-table td {
  padding: 0.25rem 0.5rem;
  text-align: center;
  border-bottom: 1px solid var(--color-border);
}
.stat-table th {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}
.positive { color: #4caf50; font-weight: bold; }
.negative { color: #e74c3c; font-weight: bold; }
.compare-actions {
  display: flex;
  gap: 0.5rem;
}
.btn-equip {
  flex: 1;
  padding: 0.5rem;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.btn-cancel {
  flex: 1;
  padding: 0.5rem;
  background: var(--color-bg-secondary);
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  cursor: pointer;
}
</style>
