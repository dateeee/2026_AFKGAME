<script setup lang="ts">
import BaseIcon from '@/components/ui/BaseIcon.vue'
import { NAV_ITEMS } from './navItems'
</script>

<template>
  <nav class="app-nav px-safe pb-safe" aria-label="メインナビゲーション">
    <router-link
      v-for="item in NAV_ITEMS"
      :key="item.to"
      :to="item.to"
      class="nav-link"
      active-class="nav-link-active"
    >
      <BaseIcon :name="item.icon" :size="22" />
      <span class="nav-label">{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<style scoped>
.app-nav {
  display: flex;
  background-color: var(--color-surface-1);
  border-top: 1px solid var(--color-line-soft);
}

.nav-link {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.1875rem;
  /* ボトムナビは親指の可動域。高さは 56px を確保する */
  min-height: var(--size-nav-h);
  padding: 0.5rem 0.25rem;
  color: var(--color-content-faint);
  transition:
    color var(--duration-fast) ease,
    background-color var(--duration-fast) ease;
}

@media (hover: hover) {
  .nav-link:hover {
    color: var(--color-content);
    background-color: var(--color-surface-2);
  }
}

.nav-label {
  font-size: var(--text-caption);
  font-weight: 500;
  letter-spacing: 0.02em;
}

/* 選択状態は色だけに頼らず、目印の線を併用する */
.nav-link-active {
  color: var(--color-accent-bright);
}

.nav-link-active::before {
  content: '';
  position: absolute;
  background-color: var(--color-accent);
  border-radius: 999px;
}

/* モバイル: 上端に目印 */
.nav-link-active::before {
  top: 0;
  left: 30%;
  right: 30%;
  height: 2px;
}

@media (min-width: 768px) {
  .app-nav {
    flex-direction: column;
    border-top: none;
    border-right: 1px solid var(--color-line-soft);
    padding-top: 0.5rem;
  }

  .nav-link {
    flex: none;
    min-height: 4rem;
  }

  /* PC: 左端に目印 */
  .nav-link-active::before {
    top: 22%;
    bottom: 22%;
    left: 0;
    right: auto;
    width: 2px;
    height: auto;
  }
}
</style>
