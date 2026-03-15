<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { usePlayerStore } from '@/stores/playerStore'
import { getShopLineup, postShopBuy, getGameState } from '@/api/client'
import { formatGold } from '@/utils/format'

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

const shopItems = ref<ShopItem[]>([])
const buyQuantity = ref(1)
const message = ref('')

onMounted(async () => {
  await loadShop()
})

async function loadShop() {
  try {
    const data = await getShopLineup()
    shopItems.value = data.lineup
  } catch (e) {
    message.value = 'ショップの読み込みに失敗しました'
  }
}

async function buyItem(itemId: string) {
  message.value = ''
  try {
    const result = await postShopBuy(itemId, buyQuantity.value)
    gameStore.gold = result.gold
    await loadShop()
    const state = await getGameState()
    playerStore.loadFromState(state)
    message.value = `${buyQuantity.value}個購入しました！`
    setTimeout(() => { message.value = '' }, 2000)
  } catch (e) {
    message.value = (e as Error).message
  }
}
</script>

<template>
  <div class="mx-auto max-w-[480px]">
    <h1 class="font-display text-xl font-bold text-accent mb-2">ショップ</h1>
    <p class="gold-text text-lg mb-4">ゴールド: {{ formatGold(gameStore.gold) }}</p>

    <div
      v-if="message"
      class="p-2 mb-3 bg-bg-secondary border border-border rounded-lg text-sm"
    >
      {{ message }}
    </div>

    <div class="space-y-3">
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
