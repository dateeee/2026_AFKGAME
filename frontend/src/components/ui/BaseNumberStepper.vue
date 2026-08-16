<script setup lang="ts">
import BaseIcon from './BaseIcon.vue'

/**
 * 数値入力（−/＋ 付き）。
 * スマホでは数字キーボードを出すより ± の連打の方が速く確実なため、
 * 44px のボタンを両脇に置く。入力欄自体も残して直接入力もできるようにする。
 */
const props = withDefaults(
  defineProps<{
    modelValue: number
    min?: number
    max?: number
    step?: number
    id?: string
    ariaLabel?: string
  }>(),
  { min: 1, step: 1, max: undefined, id: undefined, ariaLabel: undefined },
)

const emit = defineEmits<{ 'update:modelValue': [number] }>()

function clamp(v: number): number {
  const lo = props.min
  const hi = props.max ?? Number.MAX_SAFE_INTEGER
  if (Number.isNaN(v)) return lo
  return Math.min(hi, Math.max(lo, v))
}

function shift(delta: number) {
  emit('update:modelValue', clamp(props.modelValue + delta))
}

function onInput(e: Event) {
  emit('update:modelValue', clamp(Number((e.target as HTMLInputElement).value)))
}
</script>

<template>
  <div class="stepper">
    <button
      type="button"
      class="stepper-btn"
      :disabled="modelValue <= min"
      aria-label="1減らす"
      @click="shift(-step)"
    >
      <BaseIcon name="minus" :size="16" />
    </button>
    <input
      :id="id"
      class="stepper-input num"
      type="number"
      inputmode="numeric"
      :aria-label="ariaLabel"
      :value="modelValue"
      :min="min"
      :max="max"
      @input="onInput"
    />
    <button
      type="button"
      class="stepper-btn"
      :disabled="max !== undefined && modelValue >= max"
      aria-label="1増やす"
      @click="shift(step)"
    >
      <BaseIcon name="plus" :size="16" />
    </button>
  </div>
</template>

<style scoped>
.stepper {
  display: inline-flex;
  align-items: stretch;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background-color: var(--color-surface-inset);
  overflow: hidden;
}

.stepper-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--size-tap-min);
  min-height: var(--size-tap-min);
  border: none;
  background: transparent;
  color: var(--color-content-muted);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}

@media (hover: hover) {
  .stepper-btn:hover:not(:disabled) {
    background-color: var(--color-surface-3);
    color: var(--color-content-strong);
  }
}

.stepper-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.stepper-input {
  width: 3.5rem;
  border: none;
  border-inline: 1px solid var(--color-line);
  background: transparent;
  color: var(--color-content-strong);
  font-size: var(--text-input);
  font-weight: 600;
  text-align: center;
}

.stepper-input:focus {
  outline: none;
  background-color: var(--color-surface-2);
}

/* ブラウザ既定のスピナーは 44px を確保できないため隠す */
.stepper-input::-webkit-outer-spin-button,
.stepper-input::-webkit-inner-spin-button {
  appearance: none;
  margin: 0;
}
.stepper-input {
  appearance: textfield;
}
</style>
