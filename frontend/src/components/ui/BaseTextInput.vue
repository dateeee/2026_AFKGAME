<script setup lang="ts">
/**
 * 全画面共通のテキスト入力。
 * 文字サイズは 16px 固定（下回ると iOS Safari がタップ時に画面を拡大する）。
 */
withDefaults(
  defineProps<{
    modelValue: string
    id?: string
    type?: 'text' | 'email' | 'password'
    placeholder?: string
    required?: boolean
    minlength?: number
    autocomplete?: string
    invalid?: boolean
  }>(),
  {
    type: 'text',
    id: undefined,
    placeholder: undefined,
    minlength: undefined,
    autocomplete: undefined,
  },
)

defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <input
    :id="id"
    class="text-input"
    :class="{ 'is-invalid': invalid }"
    :type="type"
    :value="modelValue"
    :placeholder="placeholder"
    :required="required"
    :minlength="minlength"
    :autocomplete="autocomplete"
    @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
  />
</template>

<style scoped>
.text-input {
  width: 100%;
  min-height: var(--size-tap-min);
  padding: 0 0.875rem;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background-color: var(--color-surface-inset);
  color: var(--color-content-strong);
  font-size: var(--text-input);
  transition: border-color var(--duration-fast) ease;
}

@media (hover: hover) {
  .text-input:hover {
    border-color: var(--color-line-strong);
  }
}

.text-input::placeholder {
  color: var(--color-content-faint);
}

.text-input.is-invalid {
  border-color: var(--color-danger);
}
</style>
