<script setup lang="ts">
import type { Equipment } from '@/types/game'
import { RARITY_COLORS, RARITY_LABELS, BASE_NAMES } from '@/stores/equipmentStore'
import BaseModal from '@/components/ui/BaseModal.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

const props = defineProps<{
  current: Equipment | null
  preview: Equipment
}>()

defineEmits<{
  equip: []
  cancel: []
}>()

const STATS = [
  { label: 'ATK', key: 'statAtk' },
  { label: 'DEF', key: 'statDef' },
  { label: 'HP', key: 'statHp' },
  { label: 'SPD', key: 'statSpd' },
] as const

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
</script>

<template>
  <BaseModal open title="装備比較" @close="$emit('cancel')">
    <!-- 対象の識別。名前はレアリティ色で示す -->
    <div class="compare-head">
      <div class="compare-side">
        <span class="label-caps">現在</span>
        <template v-if="current">
          <span class="item-name" :style="{ color: RARITY_COLORS[current.rarity] }">
            {{ BASE_NAMES[current.baseId] ?? current.baseId }}
          </span>
          <span class="item-meta num">{{ RARITY_LABELS[current.rarity] }} Lv.{{ current.level }}</span>
        </template>
        <span v-else class="item-none">なし</span>
      </div>

      <div class="compare-side">
        <span class="label-caps">新規</span>
        <span class="item-name" :style="{ color: RARITY_COLORS[preview.rarity] }">
          {{ BASE_NAMES[preview.baseId] ?? preview.baseId }}
        </span>
        <span class="item-meta num">{{ RARITY_LABELS[preview.rarity] }} Lv.{{ preview.level }}</span>
      </div>
    </div>

    <table class="stat-table">
      <thead>
        <tr>
          <th scope="col"><span class="sr-only">項目</span></th>
          <th scope="col">現在</th>
          <th scope="col">新規</th>
          <th scope="col">差分</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="stat in STATS" :key="stat.label">
          <th scope="row">{{ stat.label }}</th>
          <td class="num">{{ current?.[stat.key] ?? '-' }}</td>
          <td class="num">{{ preview[stat.key] ?? '-' }}</td>
          <td class="num" :class="diff(preview[stat.key], current?.[stat.key] ?? null)?.cls">
            {{ diff(preview[stat.key], current?.[stat.key] ?? null)?.text ?? '-' }}
          </td>
        </tr>
        <tr v-if="preview.lifesteal || current?.lifesteal">
          <th scope="row">吸収</th>
          <td class="num">{{ current?.lifesteal ? (current.lifesteal * 100).toFixed(1) + '%' : '-' }}</td>
          <td class="num">{{ preview.lifesteal ? (preview.lifesteal * 100).toFixed(1) + '%' : '-' }}</td>
          <td>-</td>
        </tr>
      </tbody>
    </table>

    <template #footer>
      <BaseButton variant="secondary" block @click="$emit('cancel')">キャンセル</BaseButton>
      <BaseButton variant="primary" block @click="$emit('equip')">装備する</BaseButton>
    </template>
  </BaseModal>
</template>

<style scoped>
.compare-head {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.compare-side {
  display: flex;
  flex-direction: column;
  gap: 0.1875rem;
  padding: 0.625rem;
  background-color: var(--color-surface-2);
  border-radius: var(--radius-md);
}

.item-name {
  font-size: var(--text-body);
  font-weight: 600;
}

.item-meta {
  font-size: var(--text-caption);
  color: var(--color-content-muted);
}

.item-none {
  font-size: var(--text-body);
  color: var(--color-content-faint);
}

.stat-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-label);
}

.stat-table th,
.stat-table td {
  padding: 0.5rem;
  text-align: center;
  border-bottom: 1px solid var(--color-line-soft);
}

.stat-table thead th {
  font-size: var(--text-caption);
  font-weight: 500;
  color: var(--color-content-muted);
}

.stat-table tbody th {
  text-align: left;
  font-weight: 600;
  color: var(--color-content-muted);
}

.stat-table tbody td {
  color: var(--color-content);
}

/* 差分は色だけでなく符号（+/-）も併記されるため、色覚に依存しない */
.positive {
  color: var(--color-hp-bright);
  font-weight: 700;
}

.negative {
  color: var(--color-danger-bright);
  font-weight: 700;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
