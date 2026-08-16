<script setup lang="ts" generic="T extends string | number">
import { computed } from 'vue'
import BaseIcon from './BaseIcon.vue'

/**
 * 全画面共通のセレクト。
 * 選択肢は options で渡す（DOM 経由で文字列化されるのを避け、元の型のまま返すため）。
 * 文字サイズは 16px 固定（下回ると iOS Safari がタップ時に画面を拡大する）。
 */
const props = withDefaults(
  defineProps<{
    modelValue: T
    options: ReadonlyArray<{ value: T; label: string }>
    id?: string
    ariaLabel?: string
    block?: boolean
  }>(),
  { block: false, id: undefined, ariaLabel: undefined },
)

const emit = defineEmits<{ 'update:modelValue': [T] }>()

// value を DOM に渡すと文字列化されるため、添字で往復させる
const selectedIndex = computed(() => props.options.findIndex((o) => o.value === props.modelValue))

function onChange(e: Event) {
  const option = props.options[Number((e.target as HTMLSelectElement).value)]
  if (option) emit('update:modelValue', option.value)
}
</script>

<template>
  <div class="select-wrap" :class="{ 'select-block': block }">
    <select
      :id="id"
      class="select-control"
      :aria-label="ariaLabel"
      :value="selectedIndex"
      @change="onChange"
    >
      <option v-for="(option, i) in options" :key="option.value" :value="i">
        {{ option.label }}
      </option>
    </select>
    <BaseIcon name="chevron-down" :size="16" class="select-chevron" />
  </div>
</template>

<style scoped>
.select-wrap {
  position: relative;
  display: inline-flex;
}

/* flex 行の中で幅0まで潰され、選択肢の文字がシェブロンに重なるのを防ぐ */
.select-wrap:not(.select-block) {
  flex: none;
}

.select-block {
  display: flex;
  width: 100%;
}

.select-control {
  appearance: none;
  width: 100%;
  min-height: var(--size-tap-min);
  padding: 0 2.25rem 0 0.75rem;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background-color: var(--color-surface-inset);
  color: var(--color-content);
  font-size: var(--text-input);
  cursor: pointer;
  transition: border-color var(--duration-fast) ease;
}

@media (hover: hover) {
  .select-control:hover {
    border-color: var(--color-line-strong);
  }
}

.select-chevron {
  position: absolute;
  right: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-content-muted);
  pointer-events: none;
}

/* 選択肢のポップアップは OS が描画するため、背景色だけ合わせる */
.select-control option {
  background-color: var(--color-surface-2);
  color: var(--color-content);
}
</style>
