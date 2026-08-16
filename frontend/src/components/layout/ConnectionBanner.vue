<script setup lang="ts">
import BaseIcon from '@/components/ui/BaseIcon.vue'
import { useGameStore } from '@/stores/gameStore'

/**
 * 通信エラーの告知。
 * レイアウトの流れから外して（position: fixed）表示する。
 * フローに置くと、表示された瞬間に本体が縦にずれる・PC ではカラムの一部として
 * 横並びになるなど、レイアウトを壊すため。
 */
const gameStore = useGameStore()
</script>

<template>
  <transition name="banner">
    <div v-if="!gameStore.isConnected" class="conn-banner px-safe pt-safe" role="alert">
      <BaseIcon name="alert" :size="16" />
      <span>{{ gameStore.errorMessage }}</span>
    </div>
  </transition>
</template>

<style scoped>
.conn-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.625rem 1rem;
  background-color: var(--color-danger);
  color: var(--color-content-strong);
  font-size: var(--text-label);
  font-weight: 500;
  text-align: center;
  box-shadow: var(--shadow-raised);
}

.banner-enter-active,
.banner-leave-active {
  transition:
    transform var(--duration-base) var(--ease-out-quart),
    opacity var(--duration-base);
}

.banner-enter-from,
.banner-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}
</style>
