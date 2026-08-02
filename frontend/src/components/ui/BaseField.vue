<script setup lang="ts">
import { useId } from 'vue'

/**
 * ラベル + 入力部品 + 補足/エラー の共通レイアウト。
 * ラベルと入力の紐付け（for / id）をここで一元化し、
 * 各画面で id を手書きしないようにする。
 */
defineProps<{
  label: string
  hint?: string
  error?: string
}>()

const fieldId = useId()
</script>

<template>
  <div class="field">
    <label class="field-label" :for="fieldId">{{ label }}</label>
    <slot :id="fieldId" />
    <p v-if="error" class="field-error">{{ error }}</p>
    <p v-else-if="hint" class="field-hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.field-label {
  font-size: var(--text-label);
  font-weight: 500;
  color: var(--color-content-muted);
}

.field-hint {
  font-size: var(--text-caption);
  color: var(--color-content-faint);
}

.field-error {
  font-size: var(--text-caption);
  color: var(--color-danger-bright);
}
</style>
