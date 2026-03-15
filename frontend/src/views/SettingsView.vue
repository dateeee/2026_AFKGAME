<script setup lang="ts">
import { useGameStore } from '@/stores/gameStore'
import { putSettings, USE_API } from '@/api/client'

const gameStore = useGameStore()

async function updatePotionThreshold(value: number) {
  gameStore.settings.potionThreshold = value
  if (USE_API) {
    await putSettings({ potionThreshold: value })
  }
}

async function updateBattleLogCount(value: number) {
  gameStore.settings.battleLogCount = value
  if (USE_API) {
    await putSettings({ battleLogCount: value })
  }
}

async function updateToastEnabled(value: boolean) {
  gameStore.settings.toastEnabled = value
  if (USE_API) {
    await putSettings({ toastEnabled: value })
  }
}

async function updateAutoSellRarity(value: string) {
  const rarity = value || null
  gameStore.settings.autoSellRarity = rarity
  if (USE_API) {
    await putSettings({ autoSellRarity: value || '' })
  }
}
</script>

<template>
  <div class="settings-view">
    <h1>Settings</h1>

    <div class="setting-group">
      <label>Potion Auto-Use Threshold</label>
      <select
        :value="gameStore.settings.potionThreshold"
        @change="updatePotionThreshold(Number(($event.target as HTMLSelectElement).value))"
      >
        <option :value="0.3">30%</option>
        <option :value="0.5">50%</option>
        <option :value="0.7">70%</option>
      </select>
    </div>

    <div class="setting-group">
      <label>Battle Log Count</label>
      <select
        :value="gameStore.settings.battleLogCount"
        @change="updateBattleLogCount(Number(($event.target as HTMLSelectElement).value))"
      >
        <option :value="20">20</option>
        <option :value="50">50</option>
        <option :value="100">100</option>
        <option :value="200">200</option>
      </select>
    </div>

    <div class="setting-group">
      <label>
        <input
          type="checkbox"
          :checked="gameStore.settings.toastEnabled"
          @change="updateToastEnabled(($event.target as HTMLInputElement).checked)"
        />
        Toast Notifications
      </label>
    </div>

    <div class="setting-group">
      <label>Auto-Sell Rarity</label>
      <select
        :value="gameStore.settings.autoSellRarity ?? ''"
        @change="updateAutoSellRarity(($event.target as HTMLSelectElement).value)"
      >
        <option value="">Off</option>
        <option value="common">Common and below</option>
        <option value="uncommon">Uncommon and below</option>
      </select>
    </div>
  </div>
</template>

<style scoped>
.settings-view {
  max-width: 480px;
}

h1 {
  color: var(--color-primary);
  margin-bottom: 1.5rem;
}

.setting-group {
  margin-bottom: 1.25rem;
}

.setting-group label {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.875rem;
}

.setting-group select {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 0.875rem;
}
</style>
