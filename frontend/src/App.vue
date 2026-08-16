<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useGameLoop } from '@/composables/useGameLoop'
import { useAuthStore } from '@/stores/authStore'
import AppShell from '@/components/layout/AppShell.vue'
import ConnectionBanner from '@/components/layout/ConnectionBanner.vue'
import GuestUpgradeBanner from '@/components/layout/GuestUpgradeBanner.vue'

const route = useRoute()
const authStore = useAuthStore()
const { initialize, isLoading } = useGameLoop()

// ゲスト開始・ログインでは App が再マウントされないため、onMounted だけでは
// 認証直後のゲーム状態が読み込まれない。認証状態の変化を起点に初期化する。
watch(
  () => authStore.isAuthenticated,
  () => initialize(),
  { immediate: true },
)

// 認証画面はヘッダ・ナビを出さず、1枚の画面として見せる
const isPublic = computed(() => route.meta.public === true)

// ホームだけ PC で2カラムに広げる。他は読みやすさ優先で1カラム幅に収める
const shellWidth = computed(() => (route.name === 'game' ? 'wide' : 'content'))
</script>

<template>
  <ConnectionBanner />

  <main v-if="isPublic" class="auth-main px-safe">
    <router-view />
  </main>

  <AppShell v-else :width="shellWidth">
    <GuestUpgradeBanner />
    <div v-if="isLoading" class="loading">
      <span class="loading-text">読み込み中...</span>
    </div>
    <router-view v-else v-slot="{ Component }">
      <transition name="page-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </AppShell>
</template>

<style scoped>
.auth-main {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 1rem calc(2rem + env(safe-area-inset-bottom, 0px));
}

.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4rem 0;
}

.loading-text {
  font-size: var(--text-label);
  letter-spacing: 0.1em;
  color: var(--color-content-faint);
  animation: loading-pulse 1.4s ease-in-out infinite;
}

@keyframes loading-pulse {
  0%,
  100% {
    opacity: 0.4;
  }
  50% {
    opacity: 1;
  }
}
</style>
