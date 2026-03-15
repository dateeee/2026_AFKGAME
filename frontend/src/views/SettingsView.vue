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
  <div class="mx-auto max-w-[480px]">
    <h1 class="font-display text-xl font-bold text-accent mb-6">設定</h1>

    <div class="space-y-5">
      <div class="setting-group">
        <label class="block text-sm text-text mb-1">ポーション自動使用HP閾値</label>
        <select
          class="settings-select"
          :value="gameStore.settings.potionThreshold"
          @change="updatePotionThreshold(Number(($event.target as HTMLSelectElement).value))"
        >
          <option :value="0.1">10%</option>
          <option :value="0.2">20%</option>
          <option :value="0.3">30%</option>
          <option :value="0.4">40%</option>
          <option :value="0.5">50%</option>
        </select>
      </div>

      <div class="setting-group">
        <label class="block text-sm text-text mb-1">戦闘ログ表示数</label>
        <select
          class="settings-select"
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
        <label class="flex items-center gap-2 text-sm cursor-pointer">
          <input
            type="checkbox"
            class="settings-checkbox"
            :checked="gameStore.settings.toastEnabled"
            @change="updateToastEnabled(($event.target as HTMLInputElement).checked)"
          />
          通知表示
        </label>
      </div>

      <div class="setting-group">
        <label class="block text-sm text-text mb-1">自動売却レアリティ</label>
        <select
          class="settings-select"
          :value="gameStore.settings.autoSellRarity ?? ''"
          @change="updateAutoSellRarity(($event.target as HTMLSelectElement).value)"
        >
          <option value="">オフ</option>
          <option value="common">コモン以下</option>
          <option value="uncommon">アンコモン以下</option>
        </select>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-select {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 0.375rem;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 0.875rem;
  font-family: var(--font-body);
  transition: border-color 150ms;
}

.settings-select:focus {
  border-color: var(--color-primary);
  outline: none;
}

.settings-checkbox {
  width: 1rem;
  height: 1rem;
  accent-color: var(--color-primary);
}
</style>
