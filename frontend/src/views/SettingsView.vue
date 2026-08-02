<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { putSettings, USE_API } from '@/api/client'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseField from '@/components/ui/BaseField.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'

const gameStore = useGameStore()

const POTION_THRESHOLD_OPTIONS: Array<{ value: number; label: string }> = [
  { value: 0.1, label: '10%' },
  { value: 0.2, label: '20%' },
  { value: 0.3, label: '30%' },
  { value: 0.4, label: '40%' },
  { value: 0.5, label: '50%' },
]

const LOG_COUNT_OPTIONS: Array<{ value: number; label: string }> = [
  { value: 20, label: '20件' },
  { value: 50, label: '50件' },
  { value: 100, label: '100件' },
  { value: 200, label: '200件' },
]

const AUTO_SELL_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'オフ' },
  { value: 'common', label: 'コモン以下' },
  { value: 'uncommon', label: 'アンコモン以下' },
]

/** 自動売却はサーバー側が空文字を「オフ」として扱うため、null と往復させる */
const autoSellRarity = computed({
  get: () => gameStore.settings.autoSellRarity ?? '',
  set: (value: string) => updateAutoSellRarity(value),
})

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
  gameStore.settings.autoSellRarity = value || null
  if (USE_API) {
    await putSettings({ autoSellRarity: value })
  }
}
</script>

<template>
  <div class="settings">
    <h1>設定</h1>

    <BaseCard title="戦闘">
      <div class="setting-list">
        <BaseField label="ポーション自動使用HP閾値" hint="HPがこの割合を下回ると自動でポーションを使います">
          <template #default="{ id }">
            <BaseSelect
              :id="id"
              block
              :model-value="gameStore.settings.potionThreshold"
              :options="POTION_THRESHOLD_OPTIONS"
              @update:model-value="updatePotionThreshold"
            />
          </template>
        </BaseField>

        <BaseField label="戦闘ログ表示数">
          <template #default="{ id }">
            <BaseSelect
              :id="id"
              block
              :model-value="gameStore.settings.battleLogCount"
              :options="LOG_COUNT_OPTIONS"
              @update:model-value="updateBattleLogCount"
            />
          </template>
        </BaseField>
      </div>
    </BaseCard>

    <BaseCard title="装備">
      <BaseField label="自動売却レアリティ" hint="指定したレアリティ以下の装備を、拾った時点で売却します">
        <template #default="{ id }">
          <BaseSelect
            :id="id"
            v-model="autoSellRarity"
            block
            :options="AUTO_SELL_OPTIONS"
          />
        </template>
      </BaseField>
    </BaseCard>

    <BaseCard title="通知">
      <!-- スイッチは行全体をタップ対象にする（小さな四角を狙わせない） -->
      <label class="switch-row">
        <span class="switch-text">
          <span class="switch-title">通知表示</span>
          <span class="switch-hint">レベルアップや装備獲得をトーストで知らせます</span>
        </span>
        <input
          type="checkbox"
          class="switch-input"
          :checked="gameStore.settings.toastEnabled"
          @change="updateToastEnabled(($event.target as HTMLInputElement).checked)"
        />
        <span class="switch-track" aria-hidden="true"><span class="switch-thumb"></span></span>
      </label>
    </BaseCard>
  </div>
</template>

<style scoped>
.settings {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.setting-list {
  display: flex;
  flex-direction: column;
  gap: 1.125rem;
}

/* --- スイッチ --- */
.switch-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-height: var(--size-tap-min);
  cursor: pointer;
}

.switch-text {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  flex: 1;
  min-width: 0;
}

.switch-title {
  font-size: var(--text-body);
  color: var(--color-content);
}

.switch-hint {
  font-size: var(--text-caption);
  color: var(--color-content-faint);
}

/* 実体のチェックボックスは隠さず、視覚だけ差し替える（キーボード操作を保つ） */
.switch-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.switch-track {
  position: relative;
  flex: none;
  width: 3rem;
  height: 1.75rem;
  border-radius: 999px;
  background-color: var(--color-surface-inset);
  border: 1px solid var(--color-line);
  transition: background-color var(--duration-base) ease, border-color var(--duration-base) ease;
}

.switch-thumb {
  position: absolute;
  top: 50%;
  left: 0.1875rem;
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 999px;
  background-color: var(--color-content-faint);
  transform: translateY(-50%);
  transition: transform var(--duration-base) var(--ease-out-quart), background-color var(--duration-base) ease;
}

.switch-input:checked ~ .switch-track {
  background-color: color-mix(in srgb, var(--color-accent) 30%, transparent);
  border-color: var(--color-accent-dim);
}

.switch-input:checked ~ .switch-track .switch-thumb {
  transform: translate(1.25rem, -50%);
  background-color: var(--color-accent-bright);
}

.switch-input:focus-visible ~ .switch-track {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}
</style>
