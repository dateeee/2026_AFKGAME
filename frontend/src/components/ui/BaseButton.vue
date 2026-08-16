<script setup lang="ts">
import BaseIcon from './BaseIcon.vue'
import type { IconName } from './icons'

/**
 * 全画面共通のボタン。
 * 各画面で個別に padding / 色 / 角丸を書かないこと。
 * md 以上は最小高さ 44px（タップ領域の下限）を満たす。
 */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
    size?: 'sm' | 'md' | 'lg'
    type?: 'button' | 'submit'
    block?: boolean
    disabled?: boolean
    icon?: IconName
    /** アイコンのみのボタン。ラベルは aria-label で渡すこと */
    iconOnly?: boolean
  }>(),
  {
    variant: 'secondary',
    size: 'md',
    type: 'button',
    icon: undefined,
  },
)

defineEmits<{ click: [MouseEvent] }>()
</script>

<template>
  <button
    :type="type"
    :disabled="disabled"
    class="btn"
    :class="[`btn-${variant}`, `btn-${size}`, { 'btn-block': block, 'btn-icon-only': iconOnly }]"
    @click="$emit('click', $event)"
  >
    <BaseIcon v-if="icon" :name="icon" :size="size === 'sm' ? 16 : 18" />
    <span v-if="!iconOnly" class="btn-label"><slot /></span>
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-weight: 600;
  letter-spacing: 0.02em;
  line-height: 1;
  cursor: pointer;
  white-space: nowrap;
  transition:
    background-color var(--duration-fast) ease,
    border-color var(--duration-fast) ease,
    color var(--duration-fast) ease,
    transform var(--duration-fast) ease;
}

/* --- サイズ（高さはタップ領域の下限に従う） --- */
.btn-sm {
  min-height: 2.5rem;
  padding: 0 0.75rem;
  font-size: var(--text-label);
}
.btn-md {
  min-height: var(--size-tap-min);
  padding: 0 1.125rem;
  font-size: var(--text-body);
}
.btn-lg {
  min-height: 3.25rem;
  padding: 0 1.5rem;
  font-size: var(--text-input);
}

.btn-block {
  display: flex;
  width: 100%;
}

.btn-icon-only {
  padding: 0;
  aspect-ratio: 1;
}

/* 押下は縮小ではなく沈み込みで表現する（拡大縮小はチープに見えやすい） */
.btn:active:not(:disabled) {
  transform: translateY(1px);
}

.btn:disabled {
  opacity: 0.38;
  cursor: not-allowed;
}

/* --- 主要導線: 古金。1画面に1つを原則とする --- */
.btn-primary {
  background: linear-gradient(180deg, var(--color-accent-bright), var(--color-accent));
  border-color: var(--color-accent-dim);
  color: var(--color-content-inverse);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.28);
}
@media (hover: hover) {
  .btn-primary:hover:not(:disabled) {
    background: linear-gradient(180deg, var(--color-accent-pale), var(--color-accent-bright));
  }
}

.btn-secondary {
  background-color: var(--color-surface-2);
  border-color: var(--color-line);
  color: var(--color-content);
}
@media (hover: hover) {
  .btn-secondary:hover:not(:disabled) {
    background-color: var(--color-surface-3);
    border-color: var(--color-line-strong);
  }
}

.btn-ghost {
  background-color: transparent;
  color: var(--color-content-muted);
}
@media (hover: hover) {
  .btn-ghost:hover:not(:disabled) {
    background-color: var(--color-surface-3);
    color: var(--color-content);
  }
}

.btn-danger {
  background-color: var(--color-danger);
  border-color: var(--color-danger-deep);
  color: var(--color-content-strong);
}
@media (hover: hover) {
  .btn-danger:hover:not(:disabled) {
    background-color: var(--color-danger-bright);
    color: var(--color-content-inverse);
  }
}
</style>
