<script setup lang="ts">
import { ref, computed } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { usePlayerStore } from '@/stores/playerStore'
import { useBattleStore } from '@/stores/battleStore'
import { formatGold, formatTime } from '@/utils/format'
import { postTowerSelect, postTowerRetire, putTowerMode, getGameState } from '@/api/client'
import { RARITY_LABELS, SLOT_LABELS } from '@/stores/equipmentStore'
import type { BattleLogEntry } from '@/types/game'

const gameStore = useGameStore()
const playerStore = usePlayerStore()
const battleStore = useBattleStore()

const selectedTargetFloor = ref(5)

const hero = computed(() => playerStore.characters[0] ?? null)
const effectiveMaxHp = computed(() => hero.value?.effectiveMaxHp ?? hero.value?.maxHp ?? 1)
const hpPercent = computed(() => hero.value ? (hero.value.hp / effectiveMaxHp.value) * 100 : 0)
const expRequired = computed(() => hero.value ? Math.floor(100 * Math.pow(hero.value.level, 1.5)) : 100)
const expPercent = computed(() => hero.value ? (hero.value.exp / expRequired.value) * 100 : 0)
const potionCount = computed(() => playerStore.potions['hp_potion'] ?? 0)
const isInTower = computed(() => !!gameStore.currentTowerId)

async function enterTower() {
  try {
    await postTowerSelect('goblin_tower', selectedTargetFloor.value)
    const state = await getGameState()
    gameStore.loadFromState(state)
    playerStore.loadFromState(state)
  } catch (e) {
    gameStore.setConnectionError((e as Error).message)
  }
}

async function retireFromTower() {
  try {
    await postTowerRetire()
    const state = await getGameState()
    gameStore.loadFromState(state)
    playerStore.loadFromState(state)
  } catch (e) {
    gameStore.setConnectionError((e as Error).message)
  }
}

async function toggleMode() {
  const newMode = gameStore.towerMode === 'auto_repeat' ? 'stop_on_clear' : 'auto_repeat'
  try {
    await putTowerMode(newMode)
    gameStore.towerMode = newMode
  } catch (e) {
    gameStore.setConnectionError((e as Error).message)
  }
}

function formatLogEntry(entry: BattleLogEntry): string {
  switch (entry.type) {
    case 'attack': {
      const crit = entry.critical ? ' クリティカル！' : ''
      return `${entry.actor} → ${entry.target}: ${entry.damage}${crit} (HP: ${entry.target_hp})`
    }
    case 'defeat':
      return `${entry.target} 撃破！ +${entry.gold}G +${entry.exp}EXP`
    case 'level_up':
      return `レベルアップ！ ${entry.actor} LV${entry.level}`
    case 'encounter':
      return `--- フロア ${entry.floor}: ${entry.enemy} 出現 (HP: ${entry.enemy_hp}) ---`
    case 'potion':
      return `${entry.actor} HPポーション使用 (HP: ${entry.hp}/${entry.max_hp})`
    case 'recovery':
      return `${entry.actor} ${entry.amount} HP回復 (${entry.hp}/${entry.max_hp})`
    case 'tower_target_reached':
      return `=== 目標フロア ${entry.floor} 到達！ ===`
    case 'tower_restart':
      return `--- フロア1から再開 ---`
    case 'tower_exit':
      return `--- 塔を退出 ---`
    case 'retreat_hp':
      return `撤退！ HP不足 (${entry.hp}/${entry.max_hp})`
    case 'player_defeated':
      return `全滅！ ${entry.exp_lost} EXP、${entry.gold_lost}G 喪失`
    case 'lifesteal':
      return `${entry.actor} ${entry.amount} HP吸収 (${entry.hp}/${entry.max_hp})`
    case 'equipment_drop':
      return `[${RARITY_LABELS[entry.rarity!] ?? entry.rarity}] ${entry.name} ドロップ！ (${SLOT_LABELS[entry.slot!] ?? entry.slot})`
    case 'equipment_auto_sold':
      return `${entry.name} を自動売却 ${entry.gold}G`
    default:
      return JSON.stringify(entry)
  }
}

function dismissOffline() {
  battleStore.clearOfflineSummary()
}
</script>

<template>
  <div class="grid gap-4 grid-cols-1 md:grid-cols-2">
    <!-- Offline Rewards Modal -->
    <transition name="modal-fade">
      <div v-if="battleStore.offlineSummary" class="modal-overlay" @click.self="dismissOffline">
        <div class="modal-panel text-center">
          <h2 class="font-display text-lg font-semibold text-accent mb-4">オフライン報酬</h2>
          <div class="text-left space-y-1 mb-4">
            <p class="text-text-muted text-sm">経過時間: {{ formatTime(battleStore.offlineSummary.elapsedSeconds) }}</p>
            <p class="text-text-muted text-sm">処理ティック数: {{ battleStore.offlineSummary.processedTicks }}</p>
            <p class="gold-text text-lg">+{{ formatGold(battleStore.offlineSummary.totalGold) }} ゴールド</p>
            <p class="text-exp font-bold">+{{ battleStore.offlineSummary.totalExp.toLocaleString() }} EXP</p>
            <p class="text-sm">撃破した敵: {{ battleStore.offlineSummary.enemiesDefeated }}</p>
            <p class="text-sm">クリアしたフロア: {{ battleStore.offlineSummary.floorsCleared }}</p>
            <p v-if="battleStore.offlineSummary.potionsUsed > 0" class="text-sm text-hp">
              使用したポーション: {{ battleStore.offlineSummary.potionsUsed }}
            </p>
            <p v-if="battleStore.offlineSummary.levelsGained > 0" class="text-primary font-bold text-lg">
              レベルアップ x{{ battleStore.offlineSummary.levelsGained }}！
            </p>
          </div>
          <button class="btn btn-primary w-full" @click="dismissOffline">OK</button>
        </div>
      </div>
    </transition>

    <!-- Character Panel -->
    <section class="panel">
      <h2 class="panel-title">キャラクター</h2>
      <div v-if="hero">
        <p class="font-display text-lg font-bold text-text-bright">{{ hero.name }}</p>
        <p class="text-sm text-text-muted mb-3">LV {{ hero.level }}</p>

        <!-- HP Bar -->
        <div class="flex items-center gap-2 mb-2">
          <span class="w-8 text-xs font-bold text-hp">HP</span>
          <div class="stat-bar stat-bar-hp flex-1">
            <div class="stat-bar-fill" :style="{ width: `${hpPercent}%` }"></div>
          </div>
          <span class="text-xs min-w-[5rem] text-right">{{ hero.hp }} / {{ effectiveMaxHp }}</span>
        </div>

        <!-- EXP Bar -->
        <div class="flex items-center gap-2 mb-3">
          <span class="w-8 text-xs font-bold text-exp">EXP</span>
          <div class="stat-bar stat-bar-exp flex-1">
            <div class="stat-bar-fill" :style="{ width: `${expPercent}%` }"></div>
          </div>
          <span class="text-xs min-w-[5rem] text-right">{{ hero.exp }} / {{ expRequired }}</span>
        </div>

        <!-- Stats -->
        <div class="flex gap-4 text-sm mt-2">
          <span class="text-text-bright font-semibold">ATK {{ hero.baseAtk }}</span>
          <span class="text-text-bright font-semibold">DEF {{ hero.baseDef }}</span>
          <span class="text-text-bright font-semibold">SPD {{ hero.baseSpd }}</span>
        </div>

        <div class="mt-2 text-sm text-hp">
          HPポーション: {{ potionCount }}
        </div>
      </div>
    </section>

    <!-- Tower Panel -->
    <section class="panel">
      <h2 class="panel-title">塔</h2>

      <!-- In Tower -->
      <div v-if="isInTower">
        <p class="font-display text-lg font-bold text-text-bright">ゴブリンの塔</p>
        <p class="text-sm">フロア {{ gameStore.currentFloor }} / {{ gameStore.targetFloor }}</p>
        <p class="text-xs text-text-muted mt-1">
          モード: {{ gameStore.towerMode === 'auto_repeat' ? '自動周回' : 'クリア時停止' }}
        </p>

        <!-- Enemy Info -->
        <div v-if="gameStore.currentEnemy" class="mt-3 p-3 bg-bg rounded-lg border border-border">
          <p class="font-semibold text-danger-glow mb-1">
            {{ gameStore.currentEnemy.name }} LV{{ gameStore.currentEnemy.level }}
          </p>
          <div class="flex items-center gap-2">
            <span class="w-8 text-xs font-bold text-danger">HP</span>
            <div class="stat-bar flex-1 enemy-bar">
              <div
                class="stat-bar-fill"
                :style="{ width: `${(gameStore.currentEnemy.hp / gameStore.currentEnemy.maxHp) * 100}%` }"
              ></div>
            </div>
            <span class="text-xs min-w-[5rem] text-right">
              {{ gameStore.currentEnemy.hp }} / {{ gameStore.currentEnemy.maxHp }}
            </span>
          </div>
        </div>

        <div class="flex gap-2 mt-3">
          <button class="btn btn-secondary flex-1" @click="toggleMode">
            {{ gameStore.towerMode === 'auto_repeat' ? 'クリア時停止' : '自動周回' }}
          </button>
          <button class="btn btn-danger" @click="retireFromTower">撤退</button>
        </div>
      </div>

      <!-- Tower Select -->
      <div v-else class="flex flex-col gap-3">
        <p class="font-display font-bold text-text-bright">ゴブリンの塔 (フロア 1-20)</p>
        <div class="flex items-center gap-2">
          <label class="text-sm">目標フロア:</label>
          <input
            type="number"
            v-model.number="selectedTargetFloor"
            min="1"
            max="20"
            class="floor-input"
          />
        </div>
        <button class="btn btn-primary" @click="enterTower">塔に入る</button>
      </div>
    </section>

    <!-- Battle Log -->
    <section class="panel col-span-full">
      <h2 class="panel-title">戦闘ログ</h2>
      <div class="battle-log">
        <template v-if="battleStore.battleLogs.length > 0">
          <div v-for="(tick, i) in battleStore.battleLogs.slice(-10)" :key="i" class="log-tick">
            <div
              v-for="(entry, j) in tick"
              :key="j"
              class="log-entry"
              :class="`log-${entry.type}`"
            >
              {{ formatLogEntry(entry) }}
            </div>
          </div>
        </template>
        <p v-else class="text-text-muted text-sm italic">まだ戦闘ログがありません。塔に入って冒険を始めましょう！</p>
      </div>
    </section>

    <!-- Gold Display -->
    <div class="col-span-full text-right">
      <span class="gold-text text-lg">ゴールド: {{ formatGold(gameStore.gold) }}</span>
    </div>
  </div>
</template>

<style scoped>
.enemy-bar .stat-bar-fill {
  background: linear-gradient(90deg, #991b1b, var(--color-danger-glow));
}

.battle-log {
  max-height: 300px;
  overflow-y: auto;
  font-size: 0.8125rem;
  font-family: var(--font-mono);
}

.log-tick {
  border-bottom: 1px solid var(--color-border);
  padding: 0.25rem 0;
}

.log-entry {
  padding: 0.125rem 0;
}

.log-defeat { color: var(--color-gold); font-weight: 600; }
.log-level_up { color: var(--color-exp); font-weight: 700; }
.log-encounter { color: var(--color-text-muted); font-style: italic; }
.log-player_defeated { color: var(--color-danger); font-weight: 700; text-transform: uppercase; }
.log-potion { color: var(--color-hp); }
.log-recovery { color: var(--color-hp); }
.log-equipment_drop { color: var(--color-rarity-rare); font-weight: 600; }
.log-equipment_auto_sold { color: var(--color-gold-dim); }
.log-lifesteal { color: var(--color-hp); font-style: italic; }

.floor-input {
  width: 4rem;
  padding: 0.375rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 0.375rem;
  background: var(--color-bg);
  color: var(--color-text-bright);
  font-size: 0.875rem;
  font-family: var(--font-body);
  text-align: center;
  transition: border-color 150ms;
}

.floor-input:focus {
  border-color: var(--color-primary);
  outline: none;
}
</style>
