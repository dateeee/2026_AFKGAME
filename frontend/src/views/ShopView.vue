<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { usePlayerStore } from '@/stores/playerStore'
import { getShopLineup, postShopBuy, getGameState } from '@/api/client'
import { formatGold } from '@/utils/format'

const gameStore = useGameStore()
const playerStore = usePlayerStore()

interface ShopItem {
  item_id: string
  name: string
  price: number
  heal_ratio: number
  quantity_owned: number
  stack_limit: number
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
    message.value = 'Failed to load shop'
  }
}

async function buyItem(itemId: string) {
  message.value = ''
  try {
    const result = await postShopBuy(itemId, buyQuantity.value)
    gameStore.gold = result.gold
    // Refresh shop and player state
    await loadShop()
    const state = await getGameState()
    playerStore.loadFromState(state)
    message.value = `Purchased ${buyQuantity.value}x!`
    setTimeout(() => { message.value = '' }, 2000)
  } catch (e) {
    message.value = (e as Error).message
  }
}
</script>

<template>
  <div class="shop-view">
    <h1>Shop</h1>
    <p class="gold-info">Gold: {{ formatGold(gameStore.gold) }}</p>

    <div v-if="message" class="shop-message">{{ message }}</div>

    <div class="shop-items">
      <div v-for="item in shopItems" :key="item.item_id" class="shop-item">
        <div class="item-info">
          <span class="item-name">{{ item.name }}</span>
          <span class="item-detail">HP {{ (item.heal_ratio * 100).toFixed(0) }}% recovery</span>
          <span class="item-price">{{ item.price }}G</span>
          <span class="item-owned">Owned: {{ item.quantity_owned }} / {{ item.stack_limit }}</span>
        </div>
        <div class="item-actions">
          <select v-model.number="buyQuantity" class="qty-select">
            <option :value="1">1</option>
            <option :value="5">5</option>
            <option :value="10">10</option>
          </select>
          <button
            class="btn btn-primary"
            @click="buyItem(item.item_id)"
            :disabled="gameStore.gold < item.price * buyQuantity || item.quantity_owned + buyQuantity > item.stack_limit"
          >
            Buy ({{ formatGold(item.price * buyQuantity) }}G)
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.shop-view {
  max-width: 480px;
}

h1 {
  color: var(--color-primary);
  margin-bottom: 0.5rem;
}

.gold-info {
  color: var(--color-gold);
  font-weight: bold;
  font-size: 1.125rem;
  margin-bottom: 1rem;
}

.shop-message {
  padding: 0.5rem;
  margin-bottom: 0.75rem;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  font-size: 0.875rem;
}

.shop-item {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 0.75rem;
}

.item-info {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: baseline;
  margin-bottom: 0.75rem;
}

.item-name {
  font-weight: bold;
  font-size: 1rem;
}

.item-detail {
  color: var(--color-hp);
  font-size: 0.8125rem;
}

.item-price {
  color: var(--color-gold);
  font-weight: bold;
}

.item-owned {
  color: var(--color-text-muted);
  font-size: 0.8125rem;
}

.item-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.qty-select {
  padding: 0.375rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 0.875rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn:hover { opacity: 0.8; }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }

.btn-primary {
  background: var(--color-primary);
  color: white;
}
</style>
