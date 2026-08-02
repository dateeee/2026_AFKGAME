<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { usePlayerStore } from '@/stores/playerStore'
import { RARITY_COLORS, RARITY_LABELS, SLOT_LABELS } from '@/stores/equipmentStore'
import { getShopLineup, postShopBuy, postShopBuyDaily, getGameState } from '@/api/client'
import { formatGold, formatTime } from '@/utils/format'
import type { ShopDailyItem } from '@/types/game'

const gameStore = useGameStore()
const playerStore = usePlayerStore()

interface ShopItem {
  itemId: string
  name: string
  price: number
  healRatio: number
  quantityOwned: number
  stackLimit: number
}

const tab = ref<'permanent' | 'daily'>('permanent')
const shopItems = ref<ShopItem[]>([])
const dailyItems = ref<ShopDailyItem[]>([])
const dailyResetAt = ref('')
const buyQuantity = ref(1)
const message = ref('')
const nowMs = ref(Date.now())

let timer: ReturnType<typeof setInterval> | undefined

onMounted(async () => {
  await loadShop()
  timer = setInterval(() => { nowMs.value = Date.now() }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

/** 次回更新までの残り時間（サーバーは秒数を返さないため差分から求める） */
const remainingLabel = computed(() => {
  if (!dailyResetAt.value) return ''
  const diff = Math.floor((new Date(dailyResetAt.value).getTime() - nowMs.value) / 1000)
  return diff > 0 ? formatTime(diff) : 'まもなく更新'
})

function statLines(item: ShopDailyItem): string[] {
  const entries: Array<[string, number | null]> = [
    ['ATK', item.statAtk], ['DEF', item.statDef],
    ['HP', item.statHp], ['SPD', item.statSpd],
  ]
  return entries.filter(([, v]) => v !== null).map(([k, v]) => `${k} +${v}`)
}

async function loadShop() {
  try {
    const data = await getShopLineup()
    shopItems.value = data.lineup
    dailyItems.value = data.daily ?? []
    dailyResetAt.value = data.dailyResetAt ?? ''
  } catch (e) {
    message.value = 'ショップの読み込みに失敗しました'
  }
}

function flash(text: string) {
  message.value = text
  setTimeout(() => { message.value = '' }, 2000)
}

async function refreshPlayer() {
  const state = await getGameState()
  playerStore.loadFromState(state)
}

async function buyItem(itemId: string) {
  message.value = ''
  try {
    const result = await postShopBuy(itemId, buyQuantity.value)
    gameStore.gold = result.gold
    await loadShop()
    await refreshPlayer()
    flash(`${buyQuantity.value}個購入しました！`)
  } catch (e) {
    message.value = (e as Error).message
  }
}

async function buyDaily(item: ShopDailyItem) {
  message.value = ''
  try {
    const result = await postShopBuyDaily(item.slotIndex)
    gameStore.gold = result.gold
    await loadShop()
    await refreshPlayer()
    flash(`${item.name}を購入しました！`)
  } catch (e) {
    message.value = (e as Error).message
  }
}
</script>

<template>
  <div class="mx-auto max-w-[480px]">
    <h1 class="font-display text-xl font-bold text-accent mb-2">ショップ</h1>
    <p class="gold-text text-lg mb-4">ゴールド: {{ formatGold(gameStore.gold) }}</p>

    <div class="flex gap-2 mb-4">
      <button
        class="btn flex-1"
        :class="tab === 'permanent' ? 'btn-primary' : 'btn-secondary'"
        @click="tab = 'permanent'"
      >
        常設
      </button>
      <button
        class="btn flex-1"
        :class="tab === 'daily' ? 'btn-primary' : 'btn-secondary'"
        @click="tab = 'daily'"
      >
        日替わり
      </button>
    </div>

    <div
      v-if="message"
      class="p-2 mb-3 bg-bg-secondary border border-border rounded-lg text-sm"
    >
      {{ message }}
    </div>

    <div v-if="tab === 'permanent'" class="space-y-3">
      <div v-for="item in shopItems" :key="item.itemId" class="panel">
        <div class="flex flex-wrap gap-2 items-baseline mb-3">
          <span class="font-display font-semibold text-text-bright">{{ item.name }}</span>
          <span class="text-sm text-hp">HP {{ (item.healRatio * 100).toFixed(0) }}% 回復</span>
          <span class="gold-text text-sm">{{ item.price }}G</span>
          <span class="text-xs text-text-muted">所持: {{ item.quantityOwned }} / {{ item.stackLimit }}</span>
        </div>
        <div class="flex gap-2 items-center">
          <select v-model.number="buyQuantity" class="shop-select">
            <option :value="1">1</option>
            <option :value="5">5</option>
            <option :value="10">10</option>
          </select>
          <button
            class="btn btn-primary"
            @click="buyItem(item.itemId)"
            :disabled="gameStore.gold < item.price * buyQuantity || item.quantityOwned + buyQuantity > item.stackLimit"
          >
            購入 ({{ formatGold(item.price * buyQuantity) }}G)
          </button>
        </div>
      </div>
    </div>

    <div v-else class="space-y-3">
      <p class="text-xs text-text-muted">次回更新まで: {{ remainingLabel }}</p>

      <div
        v-for="item in dailyItems"
        :key="item.slotIndex"
        class="panel"
        :class="{ 'opacity-50': item.soldOut }"
        :style="{ borderColor: RARITY_COLORS[item.rarity] }"
      >
        <div class="flex flex-wrap gap-2 items-baseline mb-1">
          <span class="font-display font-semibold" :style="{ color: RARITY_COLORS[item.rarity] }">
            {{ item.name }}
          </span>
          <span class="text-xs text-text-muted">
            {{ RARITY_LABELS[item.rarity] }} / {{ SLOT_LABELS[item.slot] }} / Lv.{{ item.level }}
          </span>
        </div>
        <div class="flex flex-wrap gap-2 text-sm text-text-bright mb-3">
          <span v-for="line in statLines(item)" :key="line">{{ line }}</span>
        </div>
        <div class="flex gap-2 items-center">
          <span v-if="item.soldOut" class="text-sm text-text-muted">売り切れ</span>
          <button
            v-else
            class="btn btn-primary"
            @click="buyDaily(item)"
            :disabled="gameStore.gold < item.price"
          >
            購入 ({{ formatGold(item.price) }}G)
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.shop-select {
  padding: 0.375rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 0.375rem;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 0.875rem;
  font-family: var(--font-body);
  transition: border-color 150ms;
}

.shop-select:focus {
  border-color: var(--color-primary);
  outline: none;
}
</style>
