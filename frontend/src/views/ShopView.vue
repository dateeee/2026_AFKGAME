<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { usePlayerStore } from '@/stores/playerStore'
import { RARITY_COLORS, RARITY_LABELS, SLOT_LABELS } from '@/stores/equipmentStore'
import { getShopLineup, postShopBuy, postShopBuyDaily, getGameState } from '@/api/client'
import { errorMessage } from '@/api/errors'
import { formatGold, formatTime } from '@/utils/format'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import type { ShopDailyItem, ShopItem } from '@/types/game'

const gameStore = useGameStore()
const playerStore = usePlayerStore()

const QUANTITY_OPTIONS: Array<{ value: number; label: string }> = [
  { value: 1, label: '1個' },
  { value: 5, label: '5個' },
  { value: 10, label: '10個' },
]

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
  timer = setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
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

// 画面を開いたままリセット時刻を跨いだら品揃えを取り直す。
// 旧品揃えのまま購入すると、実際には新しい品揃えに対する購入になってしまうため。
// 鮮度判定はサーバー（ensure_fresh_lineup）が行うので、呼ぶだけでよい。
watch(remainingLabel, (label, previous) => {
  if (label === 'まもなく更新' && previous !== 'まもなく更新') {
    loadShop()
  }
})

function statLines(item: ShopDailyItem): string[] {
  const entries: Array<[string, number | null]> = [
    ['ATK', item.statAtk],
    ['DEF', item.statDef],
    ['HP', item.statHp],
    ['SPD', item.statSpd],
  ]
  return entries.filter(([, v]) => v !== null).map(([k, v]) => `${k} +${v}`)
}

async function loadShop() {
  try {
    const data = await getShopLineup()
    shopItems.value = data.lineup
    dailyItems.value = data.daily ?? []
    dailyResetAt.value = data.dailyResetAt ?? ''
  } catch {
    message.value = 'ショップの読み込みに失敗しました'
  }
}

function flash(text: string) {
  message.value = text
  setTimeout(() => {
    message.value = ''
  }, 2000)
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
    message.value = errorMessage(e)
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
    message.value = errorMessage(e)
  }
}
</script>

<template>
  <div class="shop">
    <h1>ショップ</h1>

    <!-- タブ。押せる範囲を広く取り、選択中は面ごと持ち上げる -->
    <div class="tabs" role="tablist">
      <button
        v-for="t in [
          { id: 'permanent', label: '常設' },
          { id: 'daily', label: '日替わり' },
        ] as const"
        :key="t.id"
        class="tab"
        :class="{ 'tab-active': tab === t.id }"
        role="tab"
        :aria-selected="tab === t.id"
        @click="tab = t.id"
      >
        {{ t.label }}
      </button>
    </div>

    <p v-if="message" class="shop-message" role="status">{{ message }}</p>

    <!-- 常設 -->
    <div v-if="tab === 'permanent'" class="shop-list">
      <BaseCard v-for="item in shopItems" :key="item.itemId" as="article">
        <template #title>{{ item.name }}</template>
        <template #actions>
          <BaseBadge tone="gold" icon="coin" class="num">{{ item.price }}G</BaseBadge>
        </template>

        <p class="item-effect">
          HP <span class="num">{{ (item.healRatio * 100).toFixed(0) }}</span
          >% 回復
        </p>
        <p class="item-stock num">所持: {{ item.quantityOwned }} / {{ item.stackLimit }}</p>

        <div class="buy-row">
          <BaseSelect v-model="buyQuantity" :options="QUANTITY_OPTIONS" aria-label="購入数" />
          <BaseButton
            variant="primary"
            class="buy-btn"
            :disabled="
              gameStore.gold < item.price * buyQuantity ||
              item.quantityOwned + buyQuantity > item.stackLimit
            "
            @click="buyItem(item.itemId)"
          >
            購入 ({{ formatGold(item.price * buyQuantity) }}G)
          </BaseButton>
        </div>
      </BaseCard>
    </div>

    <!-- 日替わり -->
    <div v-else class="shop-list">
      <p class="daily-reset">
        次回更新まで: <span class="num">{{ remainingLabel }}</span>
      </p>

      <BaseCard
        v-for="item in dailyItems"
        :key="item.slotIndex"
        as="article"
        class="daily-card"
        :class="{ 'is-soldout': item.soldOut }"
        :style="{ '--rarity': RARITY_COLORS[item.rarity] }"
      >
        <template #title>
          <span class="daily-name">{{ item.name }}</span>
        </template>
        <template #actions>
          <BaseBadge tone="gold" icon="coin" class="num">{{ formatGold(item.price) }}G</BaseBadge>
        </template>

        <p class="daily-meta">
          {{ RARITY_LABELS[item.rarity] }} / {{ SLOT_LABELS[item.slot] }} / Lv.{{ item.level }}
        </p>

        <ul class="stat-chips">
          <li v-for="line in statLines(item)" :key="line" class="stat-chip num">{{ line }}</li>
        </ul>

        <p v-if="item.soldOut" class="soldout-note">売り切れ</p>
        <BaseButton
          v-else
          variant="primary"
          block
          :disabled="gameStore.gold < item.price"
          @click="buyDaily(item)"
        >
          購入 ({{ formatGold(item.price) }}G)
        </BaseButton>
      </BaseCard>
    </div>
  </div>
</template>

<style scoped>
.shop {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* --- タブ --- */
.tabs {
  display: flex;
  gap: 0.25rem;
  padding: 0.25rem;
  background-color: var(--color-surface-1);
  border: 1px solid var(--color-line-soft);
  border-radius: var(--radius-lg);
}

.tab {
  flex: 1;
  min-height: 2.5rem;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-content-muted);
  font-size: var(--text-label);
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) ease,
    color var(--duration-fast) ease;
}

@media (hover: hover) {
  .tab:hover:not(.tab-active) {
    color: var(--color-content);
  }
}

.tab-active {
  background-color: var(--color-surface-3);
  color: var(--color-content-strong);
  box-shadow: inset 0 1px 0 rgba(242, 239, 228, 0.06);
}

.shop-message {
  padding: 0.625rem 0.875rem;
  background-color: var(--color-surface-2);
  border: 1px solid var(--color-line-soft);
  border-left: 3px solid var(--color-accent);
  border-radius: var(--radius-md);
  font-size: var(--text-label);
  color: var(--color-content);
}

.shop-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

/* --- 常設 --- */
.item-effect {
  font-size: var(--text-body);
  color: var(--color-hp-bright);
}

.item-stock {
  margin-top: 0.125rem;
  font-size: var(--text-caption);
  color: var(--color-content-faint);
}

.buy-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.875rem;
}

/* 個数セレクトは内容幅のまま、購入ボタンが残りを埋める */
.buy-btn {
  flex: 1;
}

/* --- 日替わり --- */
.daily-reset {
  font-size: var(--text-caption);
  color: var(--color-content-faint);
}

/* レアリティは左端の帯で示す。枠線全体を色付けると一覧が騒がしくなる。
   BaseCard の .panel と同じ詳細度だと打ち消せないため、親クラスを重ねる */
.shop .daily-card {
  border-left: 3px solid var(--rarity, var(--color-line));
}

.daily-card.is-soldout {
  opacity: 0.5;
}

.daily-name {
  color: var(--rarity, var(--color-content-strong));
  letter-spacing: 0.02em;
}

.daily-meta {
  font-size: var(--text-caption);
  color: var(--color-content-muted);
}

.stat-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
  margin: 0.625rem 0 0.875rem;
  list-style: none;
}

.stat-chip {
  padding: 0.1875rem 0.5rem;
  background-color: var(--color-surface-inset);
  border-radius: var(--radius-sm);
  font-size: var(--text-caption);
  font-weight: 600;
  color: var(--color-content-strong);
}

.soldout-note {
  font-size: var(--text-label);
  color: var(--color-content-faint);
}
</style>
