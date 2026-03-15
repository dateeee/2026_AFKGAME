<script setup lang="ts">
import { onMounted } from 'vue'
import { useGameLoop } from '@/composables/useGameLoop'
import { useGameStore } from '@/stores/gameStore'

const gameStore = useGameStore()
const { initialize, isLoading } = useGameLoop()

onMounted(() => {
  initialize()
})
</script>

<template>
  <div class="app">
    <div v-if="!gameStore.isConnected" class="error-banner">
      {{ gameStore.errorMessage }}
    </div>

    <main class="app-main">
      <div v-if="isLoading" class="loading">Loading...</div>
      <router-view v-else />
    </main>

    <nav class="bottom-nav">
      <router-link to="/" class="nav-item" active-class="active">Home</router-link>
      <router-link to="/equipment" class="nav-item" active-class="active">Equip</router-link>
      <router-link to="/shop" class="nav-item" active-class="active">Shop</router-link>
      <router-link to="/settings" class="nav-item" active-class="active">Settings</router-link>
    </nav>
  </div>
</template>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
}

.error-banner {
  background-color: var(--color-danger);
  color: white;
  padding: 0.5rem 1rem;
  text-align: center;
  font-size: 0.875rem;
}

.app-main {
  flex: 1;
  padding: 1rem;
  overflow-y: auto;
}

.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 1.25rem;
  color: var(--color-text-muted);
}

.bottom-nav {
  display: flex;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-secondary);
}

.nav-item {
  flex: 1;
  text-align: center;
  padding: 0.75rem;
  text-decoration: none;
  color: var(--color-text-muted);
  font-size: 0.875rem;
  transition: color 0.2s;
}

.nav-item.active {
  color: var(--color-primary);
  font-weight: bold;
}

@media (min-width: 768px) {
  .app {
    flex-direction: row;
  }

  .bottom-nav {
    flex-direction: column;
    border-top: none;
    border-right: 1px solid var(--color-border);
    order: -1;
    width: 80px;
  }

  .nav-item {
    padding: 1rem 0.5rem;
  }
}
</style>
