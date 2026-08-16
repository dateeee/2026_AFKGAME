<script setup lang="ts">
import { computed } from 'vue'

/**
 * HP / EXP / 敵HP のゲージ。
 * ラベル・バー・数値の三点セットで、幅と桁揃えを固定する。
 * ※ E2E がバー要素の「次の兄弟 span」を数値として参照するため、
 *   バー要素と数値 span の隣接関係を崩さないこと。
 */
const props = withDefaults(
  defineProps<{
    label: string
    value: number
    max: number
    tone?: 'hp' | 'exp' | 'enemy'
    /** HPが閾値以下のとき色を変える（危険域の可視化） */
    lowThreshold?: number
  }>(),
  { tone: 'hp', lowThreshold: undefined },
)

const percent = computed(() => {
  if (props.max <= 0) return 0
  return Math.min(100, Math.max(0, (props.value / props.max) * 100))
})

const isLow = computed(
  () => props.lowThreshold !== undefined && percent.value <= props.lowThreshold * 100,
)
</script>

<template>
  <div class="statbar">
    <span class="statbar-label">{{ label }}</span>
    <div class="stat-bar" :class="[`stat-bar-${tone}`, { 'is-low': isLow }]">
      <div class="stat-bar-fill" :style="{ width: `${percent}%` }"></div>
    </div>
    <span class="statbar-value num">{{ value }} / {{ max }}</span>
  </div>
</template>

<style scoped>
.statbar {
  display: flex;
  align-items: center;
  gap: 0.625rem;
}

.statbar-label {
  width: 2.25rem;
  flex: none;
  font-size: var(--text-caption);
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--color-content-muted);
}

.stat-bar {
  position: relative;
  flex: 1;
  min-width: 0;
  height: 10px;
  border-radius: 999px;
  background-color: var(--color-surface-inset);
  box-shadow: var(--shadow-inset-track);
  overflow: hidden;
}

.stat-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width var(--duration-slow) var(--ease-out-quart);
  /* 上端だけわずかに明るくして、平坦な塗りを避ける */
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.22);
}

.stat-bar-hp .stat-bar-fill {
  background-color: var(--color-hp);
}

.stat-bar-hp.is-low .stat-bar-fill {
  background-color: var(--color-danger-bright);
}

.stat-bar-exp .stat-bar-fill {
  background-color: var(--color-exp);
}

.stat-bar-enemy .stat-bar-fill {
  background-color: var(--color-danger);
}

.statbar-value {
  flex: none;
  min-width: 6.25rem;
  text-align: right;
  font-size: var(--text-label);
  color: var(--color-content);
}
</style>
