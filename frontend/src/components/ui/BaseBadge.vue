<script setup lang="ts">
import BaseIcon from './BaseIcon.vue'
import type { IconName } from './icons'

/**
 * 状態を示す小さなラベル。
 * 色だけに意味を持たせず、必ず文言（必要ならアイコン）を伴わせる。
 */
withDefaults(
  defineProps<{
    tone?: 'neutral' | 'gold' | 'danger' | 'success' | 'info'
    icon?: IconName
  }>(),
  { tone: 'neutral', icon: undefined },
)
</script>

<template>
  <span class="badge" :class="`badge-${tone}`">
    <BaseIcon v-if="icon" :name="icon" :size="12" />
    <slot />
  </span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.1875rem 0.5rem;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: var(--text-caption);
  font-weight: 600;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.badge-neutral {
  border-color: var(--color-line);
  color: var(--color-content-muted);
}

.badge-gold {
  border-color: var(--color-accent-dim);
  color: var(--color-accent-bright);
  background-color: color-mix(in srgb, var(--color-accent) 10%, transparent);
}

.badge-danger {
  border-color: var(--color-danger-deep);
  color: var(--color-danger-bright);
  background-color: color-mix(in srgb, var(--color-danger) 12%, transparent);
}

.badge-success {
  border-color: color-mix(in srgb, var(--color-success) 45%, transparent);
  color: var(--color-hp-bright);
  background-color: color-mix(in srgb, var(--color-success) 10%, transparent);
}

.badge-info {
  border-color: color-mix(in srgb, var(--color-exp) 45%, transparent);
  color: var(--color-exp-bright);
  background-color: color-mix(in srgb, var(--color-exp) 10%, transparent);
}
</style>
