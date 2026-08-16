<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'

/**
 * 全画面共通のモーダル。
 * スマホ縦持ちでは下端に寄せ、セーフエリアと親指の届く位置を確保する。
 */
const props = defineProps<{
  open: boolean
  title?: string
  /** 背面タップ・Esc で閉じさせない（重要な確認ダイアログ用） */
  persistent?: boolean
}>()

const emit = defineEmits<{ close: [] }>()

function requestClose() {
  if (!props.persistent) emit('close')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') requestClose()
}

// 背面のスクロール（body）を止める。モーダル内部は独自にスクロールさせる
watch(
  () => props.open,
  (open) => {
    document.documentElement.style.overflow = open ? 'hidden' : ''
    if (open) window.addEventListener('keydown', onKeydown)
    else window.removeEventListener('keydown', onKeydown)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.documentElement.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <transition name="modal-fade">
      <div v-if="open" class="modal-overlay" @click.self="requestClose">
        <div class="modal-panel" role="dialog" aria-modal="true">
          <header v-if="title || $slots.title" class="modal-head">
            <h2 class="modal-title">
              <slot name="title">{{ title }}</slot>
            </h2>
          </header>
          <div class="modal-body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="modal-footer">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  background-color: var(--color-surface-overlay);
  backdrop-filter: blur(2px);
  display: flex;
  /* スマホは下寄せ（親指が届く）、PC は中央 */
  align-items: flex-end;
  justify-content: center;
  padding: 1rem;
  padding-bottom: calc(1rem + env(safe-area-inset-bottom, 0px));
}

.modal-panel {
  width: 100%;
  max-width: 28rem;
  max-height: calc(100dvh - 4rem);
  display: flex;
  flex-direction: column;
  background-color: var(--color-surface-1);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-xl);
  box-shadow:
    var(--shadow-overlay),
    inset 0 1px 0 rgba(242, 239, 228, 0.06);
}

.modal-head {
  padding: 1.125rem 1.25rem 0.75rem;
  border-bottom: 1px solid var(--color-line-soft);
}

.modal-title {
  font-family: var(--font-body);
  font-size: var(--text-heading);
  font-weight: 600;
  color: var(--color-content-strong);
  letter-spacing: 0.02em;
}

.modal-body {
  padding: 1.125rem 1.25rem;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.modal-footer {
  display: flex;
  gap: 0.625rem;
  padding: 0 1.25rem 1.25rem;
}

@media (min-width: 768px) {
  .modal-overlay {
    align-items: center;
  }
}
</style>
