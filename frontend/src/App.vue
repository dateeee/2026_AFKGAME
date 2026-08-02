<script setup lang="ts">
import { watch } from 'vue'
import { useGameLoop } from '@/composables/useGameLoop'
import { useAuthStore } from '@/stores/authStore'
import { useGameStore } from '@/stores/gameStore'

const gameStore = useGameStore()
const authStore = useAuthStore()
const { initialize, isLoading } = useGameLoop()

// ゲスト開始・ログインでは App が再マウントされないため、onMounted だけでは
// 認証直後のゲーム状態が読み込まれない。認証状態の変化を起点に初期化する。
watch(() => authStore.isAuthenticated, () => initialize(), { immediate: true })
</script>

<template>
  <div class="flex flex-col min-h-screen min-h-dvh md:flex-row">
    <!-- Error Banner -->
    <div
      v-if="!gameStore.isConnected"
      class="bg-danger text-white px-4 py-2 text-center text-sm"
    >
      {{ gameStore.errorMessage }}
    </div>

    <!-- Sidebar Nav (desktop) -->
    <nav class="hidden md:flex flex-col border-r border-border bg-bg w-20 order-first">
      <router-link
        v-for="item in [
          { to: '/', label: 'ホーム', icon: '\u2694' },
          { to: '/equipment', label: '装備', icon: '\uD83D\uDEE1' },
          { to: '/shop', label: 'ショップ', icon: '\uD83D\uDCB0' },
          { to: '/settings', label: '設定', icon: '\u2699' },
        ]"
        :key="item.to"
        :to="item.to"
        class="nav-link"
        active-class="nav-link-active"
      >
        <span class="text-lg">{{ item.icon }}</span>
        <span class="text-xs">{{ item.label }}</span>
      </router-link>
    </nav>

    <!-- Main Content -->
    <main class="flex-1 p-4 overflow-y-auto">
      <div v-if="isLoading" class="flex items-center justify-center h-full">
        <span class="text-xl text-text-muted animate-pulse">読み込み中...</span>
      </div>
      <router-view v-else v-slot="{ Component }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- Bottom Nav (mobile) -->
    <nav class="flex md:hidden border-t border-border bg-bg">
      <router-link
        v-for="item in [
          { to: '/', label: 'ホーム', icon: '\u2694' },
          { to: '/equipment', label: '装備', icon: '\uD83D\uDEE1' },
          { to: '/shop', label: 'ショップ', icon: '\uD83D\uDCB0' },
          { to: '/settings', label: '設定', icon: '\u2699' },
        ]"
        :key="item.to"
        :to="item.to"
        class="nav-link flex-1"
        active-class="nav-link-active"
      >
        <span class="text-lg">{{ item.icon }}</span>
        <span class="text-xs">{{ item.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.nav-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.125rem;
  padding: 0.625rem 0.5rem;
  color: var(--color-text-muted);
  text-decoration: none;
  transition: color 150ms, background-color 150ms;
  position: relative;
}

.nav-link:hover {
  color: var(--color-text);
  background-color: var(--color-bg-elevated);
}

.nav-link-active {
  color: var(--color-primary) !important;
}

.nav-link-active::after {
  content: '';
  position: absolute;
  background-color: var(--color-primary);
}

/* Mobile: top accent bar */
@media (max-width: 767px) {
  .nav-link-active::after {
    top: 0;
    left: 25%;
    right: 25%;
    height: 2px;
    border-radius: 0 0 2px 2px;
  }
}

/* Desktop: left accent bar */
@media (min-width: 768px) {
  .nav-link-active::after {
    left: 0;
    top: 25%;
    bottom: 25%;
    width: 2px;
    border-radius: 0 2px 2px 0;
  }
}
</style>
