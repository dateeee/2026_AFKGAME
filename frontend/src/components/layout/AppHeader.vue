<script setup lang="ts">
import AppIcon from '@/components/ui/AppIcon.vue'
import { useGameStore } from '@/stores/gameStore'
import { formatGold } from '@/utils/format'

/**
 * 全画面共通のヘッダ。
 * ゴールドの表示場所はここ1箇所に固定する（画面ごとに置くと居場所が定まらない）。
 */
const gameStore = useGameStore()
</script>

<template>
  <header class="app-header px-safe pt-safe">
    <router-link to="/" class="brand" aria-label="ホームへ戻る">AFK GAME</router-link>

    <div class="wallet">
      <AppIcon name="coin" :size="16" />
      <span class="wallet-value num">ゴールド: {{ formatGold(gameStore.gold) }}</span>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  min-height: var(--size-header-h);
  padding-inline: 1rem;
  background-color: var(--color-surface-1);
  border-bottom: 1px solid var(--color-line-soft);
}

.brand {
  font-family: var(--font-display);
  font-size: var(--text-heading);
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--color-content-strong);
}

@media (hover: hover) {
  .brand:hover {
    color: var(--color-accent-pale);
  }
}

.wallet {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  color: var(--color-gold);
}

.wallet-value {
  font-size: var(--text-label);
  font-weight: 600;
  letter-spacing: 0.02em;
}
</style>
