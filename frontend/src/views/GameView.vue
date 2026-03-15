<script setup lang="ts">
import { ref, computed } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { usePlayerStore } from '@/stores/playerStore'
import { useBattleStore } from '@/stores/battleStore'
import { formatGold, formatTime } from '@/utils/format'
import { postTowerSelect, postTowerRetire, putTowerMode, getGameState } from '@/api/client'
import type { BattleLogEntry } from '@/types/game'

const gameStore = useGameStore()
const playerStore = usePlayerStore()
const battleStore = useBattleStore()

const selectedTargetFloor = ref(5)

const hero = computed(() => playerStore.characters[0] ?? null)
const hpPercent = computed(() => hero.value ? (hero.value.hp / hero.value.maxHp) * 100 : 0)
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
      const crit = entry.critical ? ' CRITICAL!' : ''
      return `${entry.actor} → ${entry.target}: ${entry.damage}${crit} (HP: ${entry.target_hp})`
    }
    case 'defeat':
      return `${entry.target} defeated! +${entry.gold}G +${entry.exp}EXP`
    case 'level_up':
      return `LEVEL UP! ${entry.actor} LV${entry.level}`
    case 'encounter':
      return `--- Floor ${entry.floor}: ${entry.enemy} appeared (HP: ${entry.enemy_hp}) ---`
    case 'potion':
      return `${entry.actor} used HP Potion (HP: ${entry.hp}/${entry.max_hp})`
    case 'recovery':
      return `${entry.actor} recovered ${entry.amount} HP (${entry.hp}/${entry.max_hp})`
    case 'tower_target_reached':
      return `=== Target floor ${entry.floor} reached! ===`
    case 'tower_restart':
      return `--- Restarting tower from Floor 1 ---`
    case 'tower_exit':
      return `--- Exiting tower ---`
    case 'retreat_hp':
      return `Retreated! HP too low (${entry.hp}/${entry.max_hp})`
    case 'player_defeated':
      return `DEFEATED! Lost ${entry.exp_lost} EXP, ${entry.gold_lost}G`
    case 'lifesteal':
      return `${entry.actor} drained ${entry.amount} HP (${entry.hp}/${entry.max_hp})`
    case 'equipment_drop':
      return `[${entry.rarity}] ${entry.name} dropped! (${entry.slot})`
    case 'equipment_auto_sold':
      return `Auto-sold ${entry.name} for ${entry.gold}G`
    default:
      return JSON.stringify(entry)
  }
}

function dismissOffline() {
  battleStore.clearOfflineSummary()
}
</script>

<template>
  <div class="game-view">
    <!-- オフライン報酬モーダル -->
    <div v-if="battleStore.offlineSummary" class="modal-overlay" @click.self="dismissOffline">
      <div class="modal">
        <h2>Offline Rewards</h2>
        <div class="offline-summary">
          <p>Elapsed: {{ formatTime(battleStore.offlineSummary.elapsedSeconds) }}</p>
          <p>Ticks processed: {{ battleStore.offlineSummary.processedTicks }}</p>
          <p class="reward-gold">+{{ formatGold(battleStore.offlineSummary.totalGold) }} Gold</p>
          <p class="reward-exp">+{{ battleStore.offlineSummary.totalExp.toLocaleString() }} EXP</p>
          <p>Enemies defeated: {{ battleStore.offlineSummary.enemiesDefeated }}</p>
          <p>Floors cleared: {{ battleStore.offlineSummary.floorsCleared }}</p>
          <p v-if="battleStore.offlineSummary.potionsUsed > 0">
            Potions used: {{ battleStore.offlineSummary.potionsUsed }}
          </p>
          <p v-if="battleStore.offlineSummary.levelsGained > 0" class="reward-level">
            Level up x{{ battleStore.offlineSummary.levelsGained }}!
          </p>
        </div>
        <button class="btn btn-primary" @click="dismissOffline">OK</button>
      </div>
    </div>

    <!-- キャラクターパネル -->
    <section class="character-panel">
      <h2>Character</h2>
      <div v-if="hero" class="character-info">
        <p class="character-name">{{ hero.name }}</p>
        <p>LV {{ hero.level }}</p>

        <div class="stat-bar">
          <div class="bar-label">HP</div>
          <div class="bar-track">
            <div class="bar-fill bar-fill--hp" :style="{ width: `${hpPercent}%` }" />
          </div>
          <div class="bar-value">{{ hero.hp }} / {{ hero.maxHp }}</div>
        </div>

        <div class="stat-bar">
          <div class="bar-label">EXP</div>
          <div class="bar-track">
            <div class="bar-fill bar-fill--exp" :style="{ width: `${expPercent}%` }" />
          </div>
          <div class="bar-value">{{ hero.exp }} / {{ expRequired }}</div>
        </div>

        <div class="stats">
          <span>ATK {{ hero.baseAtk }}</span>
          <span>DEF {{ hero.baseDef }}</span>
          <span>SPD {{ hero.baseSpd }}</span>
        </div>

        <div class="potion-info">
          HP Potion: {{ potionCount }}
        </div>
      </div>
    </section>

    <!-- 塔パネル -->
    <section class="tower-panel">
      <h2>Tower</h2>

      <div v-if="isInTower" class="tower-active">
        <p class="tower-name">Goblin Tower</p>
        <p>Floor {{ gameStore.currentFloor }} / {{ gameStore.targetFloor }}</p>
        <p class="text-muted">
          Mode: {{ gameStore.towerMode === 'auto_repeat' ? 'Auto Repeat' : 'Stop on Clear' }}
        </p>

        <!-- 敵情報 -->
        <div v-if="gameStore.currentEnemy" class="enemy-info">
          <p class="enemy-name">{{ gameStore.currentEnemy.name }} LV{{ gameStore.currentEnemy.level }}</p>
          <div class="stat-bar">
            <div class="bar-label">HP</div>
            <div class="bar-track">
              <div
                class="bar-fill bar-fill--enemy"
                :style="{ width: `${(gameStore.currentEnemy.hp / gameStore.currentEnemy.maxHp) * 100}%` }"
              />
            </div>
            <div class="bar-value">{{ gameStore.currentEnemy.hp }} / {{ gameStore.currentEnemy.maxHp }}</div>
          </div>
        </div>

        <div class="tower-actions">
          <button class="btn btn-secondary" @click="toggleMode">
            {{ gameStore.towerMode === 'auto_repeat' ? 'Switch: Stop on Clear' : 'Switch: Auto Repeat' }}
          </button>
          <button class="btn btn-danger" @click="retireFromTower">Retire</button>
        </div>
      </div>

      <div v-else class="tower-select">
        <p class="tower-name">Goblin Tower (Floor 1-20)</p>
        <div class="floor-select">
          <label>Target Floor:</label>
          <input
            type="number"
            v-model.number="selectedTargetFloor"
            min="1"
            max="20"
            class="floor-input"
          />
        </div>
        <button class="btn btn-primary" @click="enterTower">Enter Tower</button>
      </div>
    </section>

    <!-- 戦闘ログ -->
    <section class="battle-log-panel">
      <h2>Battle Log</h2>
      <div class="battle-log">
        <template v-if="battleStore.battleLogs.length > 0">
          <div v-for="(tick, i) in battleStore.battleLogs.slice(-10)" :key="i" class="log-tick">
            <div v-for="(entry, j) in tick" :key="j" class="log-entry" :class="`log-${entry.type}`">
              {{ formatLogEntry(entry) }}
            </div>
          </div>
        </template>
        <p v-else class="text-muted">No battle logs yet. Enter a tower to start!</p>
      </div>
    </section>

    <div class="gold-display">
      Gold: {{ formatGold(gameStore.gold) }}
    </div>
  </div>
</template>

<style scoped>
.game-view {
  display: grid;
  gap: 1rem;
  grid-template-columns: 1fr;
}

@media (min-width: 768px) {
  .game-view {
    grid-template-columns: 1fr 1fr;
  }
}

.character-panel, .tower-panel, .battle-log-panel {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 1rem;
}

.battle-log-panel {
  grid-column: 1 / -1;
}

h2 {
  margin: 0 0 0.75rem;
  font-size: 1rem;
  color: var(--color-primary);
}

.character-name, .tower-name {
  font-weight: bold;
  font-size: 1.125rem;
}

.stat-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0.5rem 0;
}

.bar-label {
  width: 2rem;
  font-size: 0.75rem;
  font-weight: bold;
}

.bar-track {
  flex: 1;
  height: 12px;
  background: var(--color-bg);
  border-radius: 6px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.3s ease;
}

.bar-fill--hp { background: var(--color-hp); }
.bar-fill--exp { background: var(--color-exp); }
.bar-fill--enemy { background: #e74c3c; }

.bar-value {
  font-size: 0.75rem;
  min-width: 5rem;
  text-align: right;
}

.stats {
  display: flex;
  gap: 1rem;
  font-size: 0.875rem;
  margin-top: 0.5rem;
}

.potion-info {
  margin-top: 0.5rem;
  font-size: 0.875rem;
  color: var(--color-hp);
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

.log-defeat { color: var(--color-gold); }
.log-level_up { color: var(--color-exp); font-weight: bold; }
.log-encounter { color: var(--color-text-muted); }
.log-player_defeated { color: var(--color-danger); font-weight: bold; }
.log-potion { color: var(--color-hp); }

.gold-display {
  grid-column: 1 / -1;
  text-align: right;
  font-size: 1.125rem;
  font-weight: bold;
  color: var(--color-gold);
}

.text-muted {
  color: var(--color-text-muted);
}

.enemy-info {
  margin-top: 0.75rem;
  padding: 0.5rem;
  background: var(--color-bg);
  border-radius: 4px;
}

.enemy-name {
  font-weight: bold;
  color: #e74c3c;
  margin-bottom: 0.25rem;
}

.tower-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.75rem;
}

.tower-select {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.floor-select {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.floor-input {
  width: 4rem;
  padding: 0.375rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 0.875rem;
  text-align: center;
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

.btn-primary {
  background: var(--color-primary);
  color: white;
}

.btn-secondary {
  background: var(--color-border);
  color: var(--color-text);
}

.btn-danger {
  background: var(--color-danger);
  color: white;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 1.5rem;
  max-width: 400px;
  width: 90%;
  text-align: center;
}

.modal h2 {
  margin-bottom: 1rem;
}

.offline-summary {
  text-align: left;
  margin-bottom: 1rem;
}

.offline-summary p {
  margin: 0.25rem 0;
}

.reward-gold { color: var(--color-gold); font-weight: bold; }
.reward-exp { color: var(--color-exp); font-weight: bold; }
.reward-level { color: var(--color-primary); font-weight: bold; }
</style>
