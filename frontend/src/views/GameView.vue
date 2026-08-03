<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { usePlayerStore } from '@/stores/playerStore'
import { useBattleStore } from '@/stores/battleStore'
import { formatGold, formatTime } from '@/utils/format'
import { postTowerSelect, postTowerRetire, putTowerMode, getGameState } from '@/api/client'
import { RARITY_LABELS, SLOT_LABELS } from '@/stores/equipmentStore'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import StatBar from '@/components/ui/StatBar.vue'
import NumberStepper from '@/components/ui/NumberStepper.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import type { BattleLogEntry, TowerInfo } from '@/types/game'

const gameStore = useGameStore()
const playerStore = usePlayerStore()
const battleStore = useBattleStore()

const selectedTowerId = ref('goblin_tower')
const selectedTargetFloor = ref(5)
const logScroller = ref<HTMLElement | null>(null)

onMounted(() => {
  gameStore.loadTowers().catch(() => { /* 一覧取得失敗時はゲーム状態から復元されるまで空表示 */ })
})

// 塔の解放状態は最新のゲーム状態（towersCleared）から算出する
function isUnlocked(tower: TowerInfo): boolean {
  if (tower.unlockTowerId === null) return true
  return gameStore.towersCleared[tower.unlockTowerId]?.cleared
    ?? tower.unlocked
}

function towerName(towerId: string | null): string {
  return gameStore.towers.find(t => t.id === towerId)?.name ?? ''
}

const selectedTower = computed(() =>
  gameStore.towers.find(t => t.id === selectedTowerId.value) ?? null
)

function selectTower(tower: TowerInfo) {
  if (!isUnlocked(tower)) return
  selectedTowerId.value = tower.id
}

// 選択可能な目標フロアの上限 = min(その塔の到達済み最高階 + 1, 総階数)（systems/battle.md 目標階設定）
const targetFloorCap = computed(() => selectedTower.value?.targetFloorCap ?? 1)

// 選択中の塔が変わったら目標フロアを上限に収める
watch(selectedTower, (tower) => {
  if (tower && selectedTargetFloor.value > tower.targetFloorCap) {
    selectedTargetFloor.value = tower.targetFloorCap
  }
})

const hero = computed(() => playerStore.characters[0] ?? null)
const effectiveMaxHp = computed(() => hero.value?.effectiveMaxHp ?? hero.value?.maxHp ?? 1)
// 必要EXPの式の正はサーバー（backend/app/master_data/characters.py の required_exp、
// 数値の正は docs/data/master/character.md）。ここは表示用の複製なので、
// バランス調整でサーバー式を変えたら必ず合わせること
// （恒久策の CharacterResponse.expRequired 追加は API 変更を伴うため full-review の管轄）。
const expRequired = computed(() => hero.value ? Math.floor(100 * Math.pow(hero.value.level, 1.5)) : 100)
const potionCount = computed(() => playerStore.potions['hp_potion'] ?? 0)
const isInTower = computed(() => !!gameStore.currentTowerId)

// 攻略中に最高到達階が伸びると targetFloorCap が変わる。塔選択へ戻ったタイミングで
// 一覧を取り直さないと、実際には選べる階が NumberStepper の max で弾かれ続ける
watch(isInTower, (inTower, wasInTower) => {
  if (wasInTower && !inTower) {
    gameStore.loadTowers().catch(() => { /* 失敗時は前回の一覧を保持する */ })
  }
})

const heroStats = computed(() => hero.value ? [
  { key: 'ATK', value: hero.value.baseAtk },
  { key: 'DEF', value: hero.value.baseDef },
  { key: 'SPD', value: hero.value.baseSpd },
] : [])

// 設定「戦闘ログ表示件数」（20/50/100・tick群単位）に従って直近ぶんだけ描画する。
// ストア側も同じ上限で保持しているが、設定を下げた直後は保持済みログが残るため表示側でも絞る
const visibleLogs = computed(() =>
  battleStore.battleLogs.slice(-gameStore.settings.battleLogCount)
)

// 新しいログは下に積まれるため、追加のたびに最新行まで送る
watch(() => battleStore.battleLogs.length, async () => {
  await nextTick()
  const el = logScroller.value
  if (el) el.scrollTop = el.scrollHeight
})

async function enterTower() {
  try {
    await postTowerSelect(selectedTowerId.value, selectedTargetFloor.value)
    const state = await getGameState()
    gameStore.loadFromState(state)
    playerStore.loadFromState(state)
  } catch (e) {
    gameStore.reportActionFailure(e)
  }
}

async function retireFromTower() {
  try {
    await postTowerRetire()
    const state = await getGameState()
    gameStore.loadFromState(state)
    playerStore.loadFromState(state)
  } catch (e) {
    gameStore.reportActionFailure(e)
  }
}

async function toggleMode() {
  const newMode = gameStore.towerMode === 'auto_repeat' ? 'stop_on_clear' : 'auto_repeat'
  try {
    await putTowerMode(newMode)
    gameStore.towerMode = newMode
  } catch (e) {
    gameStore.reportActionFailure(e)
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
  <div class="home-grid">
    <p v-if="gameStore.actionError" class="action-error" role="alert">
      {{ gameStore.actionError }}
    </p>

    <!-- オフライン報酬 -->
    <BaseModal :open="!!battleStore.offlineSummary" title="オフライン報酬" @close="dismissOffline">
      <template v-if="battleStore.offlineSummary">
        <p class="offline-lead">
          {{ formatTime(battleStore.offlineSummary.elapsedSeconds) }}のあいだ、冒険者は塔を進み続けました。
        </p>

        <div class="offline-headline">
          <div class="offline-figure">
            <span class="label-caps">獲得ゴールド</span>
            <span class="offline-value num text-gold">+{{ formatGold(battleStore.offlineSummary.totalGold) }}</span>
          </div>
          <div class="offline-figure">
            <span class="label-caps">獲得EXP</span>
            <span class="offline-value num text-exp-bright">
              +{{ formatGold(battleStore.offlineSummary.totalExp) }}
            </span>
          </div>
        </div>

        <dl class="offline-detail">
          <div><dt>撃破した敵</dt><dd class="num">{{ battleStore.offlineSummary.enemiesDefeated }}</dd></div>
          <div><dt>クリアしたフロア</dt><dd class="num">{{ battleStore.offlineSummary.floorsCleared }}</dd></div>
          <div><dt>処理ティック数</dt><dd class="num">{{ battleStore.offlineSummary.processedTicks }}</dd></div>
          <div v-if="battleStore.offlineSummary.potionsUsed > 0">
            <dt>使用したポーション</dt><dd class="num">{{ battleStore.offlineSummary.potionsUsed }}</dd>
          </div>
        </dl>

        <p v-if="battleStore.offlineSummary.levelsGained > 0" class="offline-levelup">
          レベルアップ ×{{ battleStore.offlineSummary.levelsGained }}
        </p>
      </template>

      <template #footer>
        <BaseButton variant="primary" block @click="dismissOffline">OK</BaseButton>
      </template>
    </BaseModal>

    <!-- キャラクター -->
    <BaseCard title="キャラクター">
      <div v-if="hero" class="hero">
        <div class="hero-head">
          <p class="hero-name">{{ hero.name }}</p>
          <BaseBadge tone="gold">LV {{ hero.level }}</BaseBadge>
        </div>

        <div class="bars">
          <StatBar label="HP" :value="hero.hp" :max="effectiveMaxHp" tone="hp" :low-threshold="0.3" />
          <StatBar label="EXP" :value="hero.exp" :max="expRequired" tone="exp" />
        </div>

        <dl class="stat-grid">
          <div v-for="stat in heroStats" :key="stat.key" class="stat-cell">
            <dt class="label-caps">{{ stat.key }}</dt>
            <dd class="stat-value num">{{ stat.value }}</dd>
          </div>
        </dl>

        <p class="hero-potion">HPポーション: <span class="num">{{ potionCount }}</span></p>
      </div>
    </BaseCard>

    <!-- 塔 -->
    <BaseCard title="塔">
      <!-- 攻略中 -->
      <div v-if="isInTower" class="tower-run">
        <div class="tower-run-head">
          <p class="tower-run-name">{{ towerName(gameStore.currentTowerId) || '塔' }}</p>
          <BaseBadge :tone="gameStore.towerMode === 'auto_repeat' ? 'success' : 'neutral'">
            モード: {{ gameStore.towerMode === 'auto_repeat' ? '自動周回' : 'クリア時停止' }}
          </BaseBadge>
        </div>

        <p class="tower-floor num">フロア {{ gameStore.currentFloor }} / {{ gameStore.targetFloor }}</p>

        <!-- 交戦中の敵 -->
        <div v-if="gameStore.currentEnemy" class="enemy">
          <p class="enemy-name">
            {{ gameStore.currentEnemy.name }}
            <span class="enemy-level num">LV{{ gameStore.currentEnemy.level }}</span>
          </p>
          <StatBar
            label="HP"
            :value="gameStore.currentEnemy.hp"
            :max="gameStore.currentEnemy.maxHp"
            tone="enemy"
          />
        </div>

        <div class="tower-actions">
          <BaseButton variant="secondary" block @click="toggleMode">
            {{ gameStore.towerMode === 'auto_repeat' ? 'クリア時停止' : '自動周回' }}
          </BaseButton>
          <BaseButton variant="danger" @click="retireFromTower">撤退</BaseButton>
        </div>
      </div>

      <!-- 塔選択 -->
      <div v-else class="tower-select">
        <!-- 塔の選択はラジオ相当。Tab で移動し Enter/Space で選べるようにする -->
        <ul class="tower-list" role="radiogroup" aria-label="挑戦する塔">
          <li
            v-for="tower in gameStore.towers"
            :key="tower.id"
            class="tower-card"
            :class="{
              'tower-card-selected': tower.id === selectedTowerId,
              'tower-card-locked': !isUnlocked(tower),
            }"
            role="radio"
            :aria-checked="tower.id === selectedTowerId"
            :aria-disabled="!isUnlocked(tower)"
            :tabindex="isUnlocked(tower) ? 0 : -1"
            @click="selectTower(tower)"
            @keydown.enter.prevent="selectTower(tower)"
            @keydown.space.prevent="selectTower(tower)"
          >
            <div class="tower-card-head">
              <p class="font-display tower-card-name">
                {{ tower.name }}
                <span class="tower-card-floors num">全{{ tower.totalFloors }}F</span>
              </p>
              <BaseBadge v-if="gameStore.towersCleared[tower.id]?.cleared" tone="gold" icon="check">
                クリア済
              </BaseBadge>
              <BaseBadge v-else-if="!isUnlocked(tower)" tone="neutral" icon="lock">未解放</BaseBadge>
            </div>
            <p v-if="!isUnlocked(tower)" class="tower-card-note">
              {{ towerName(tower.unlockTowerId) }}のボス討伐で解放
            </p>
            <p v-else-if="(gameStore.towersCleared[tower.id]?.highestFloor ?? 0) > 0" class="tower-card-note num">
              最高到達: {{ gameStore.towersCleared[tower.id]!.highestFloor }}F
            </p>
          </li>
        </ul>

        <p v-if="gameStore.towers.length === 0" class="empty-note">塔一覧を読み込み中...</p>

        <div class="target-floor">
          <span class="target-floor-label">目標フロア</span>
          <NumberStepper
            v-model="selectedTargetFloor"
            :min="1"
            :max="targetFloorCap"
            aria-label="目標フロア"
          />
          <span class="target-floor-cap num">/ {{ targetFloorCap }}（選択上限）</span>
        </div>

        <BaseButton
          variant="primary"
          size="lg"
          block
          :disabled="!selectedTower || !isUnlocked(selectedTower)"
          @click="enterTower"
        >
          塔に入る
        </BaseButton>
      </div>
    </BaseCard>

    <!-- 戦闘ログ -->
    <BaseCard title="戦闘ログ" class="log-card">
      <template #actions>
        <span v-if="isInTower" class="log-live">
          <span class="log-live-dot" aria-hidden="true"></span>進行中
        </span>
      </template>

      <div ref="logScroller" class="battle-log">
        <template v-if="visibleLogs.length > 0">
          <div v-for="(tick, i) in visibleLogs" :key="i" class="log-tick">
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
        <div v-else class="log-empty">
          <AppIcon name="tower" :size="28" />
          <p>まだ戦闘ログがありません。<br />塔に入って冒険を始めましょう！</p>
        </div>
      </div>
    </BaseCard>
  </div>
</template>

<style scoped>
.home-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

/* 業務エラー（塔進入・撤退の拒否など）。通信断は ConnectionBanner が担当する */
.action-error {
  margin: 0;
  padding: 0.625rem 0.875rem;
  background-color: var(--color-surface-2);
  border: 1px solid var(--color-line-soft);
  border-left: 3px solid var(--color-danger);
  border-radius: var(--radius-md);
  font-size: var(--text-label);
  color: var(--color-content);
}

/* --- キャラクター --- */
.hero {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.hero-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.hero-name {
  font-size: var(--text-title);
  font-weight: 700;
  color: var(--color-content-strong);
  letter-spacing: 0.02em;
}

.bars {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* 数値を主役にする。ラベルは小さく沈め、値を大きく明るく置く */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
}

.stat-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.125rem;
  padding: 0.5rem 0.25rem;
  background-color: var(--color-surface-2);
  border-radius: var(--radius-md);
}

.stat-value {
  font-size: var(--text-stat);
  font-weight: 700;
  color: var(--color-content-strong);
}

.hero-potion {
  font-size: var(--text-label);
  color: var(--color-content-muted);
}

/* --- 塔（攻略中） --- */
.tower-run {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.tower-run-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.tower-run-name {
  font-size: var(--text-title);
  font-weight: 700;
  color: var(--color-content-strong);
}

.tower-floor {
  font-size: var(--text-heading);
  font-weight: 600;
  color: var(--color-content);
}

.enemy {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.75rem;
  background-color: var(--color-surface-inset);
  border: 1px solid var(--color-line-soft);
  border-radius: var(--radius-md);
}

.enemy-name {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--color-danger-bright);
}

.enemy-level {
  margin-left: 0.375rem;
  font-size: var(--text-caption);
  color: var(--color-content-muted);
}

.tower-actions {
  display: flex;
  gap: 0.5rem;
}

/* --- 塔（選択） --- */
.tower-select {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.tower-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  list-style: none;
}

.tower-card {
  /* 行そのものがタップ対象。指で押せる高さを確保する */
  min-height: var(--size-tap-min);
  padding: 0.625rem 0.75rem;
  background-color: var(--color-surface-2);
  border: 1px solid var(--color-line-soft);
  border-left: 3px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color var(--duration-fast) ease, background-color var(--duration-fast) ease;
}

@media (hover: hover) {
  .tower-card:hover:not(.tower-card-locked) {
    background-color: var(--color-surface-3);
  }
}

/* 選択状態は左の帯で示す（枠線の色替えだけだと選択中か判別しにくい） */
.tower-card-selected {
  border-color: var(--color-line);
  border-left-color: var(--color-accent);
  background-color: var(--color-surface-3);
}

.tower-card-locked {
  opacity: 0.5;
  cursor: not-allowed;
}

.tower-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.tower-card-name {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--color-content-strong);
}

.tower-card-floors {
  margin-left: 0.375rem;
  font-size: var(--text-caption);
  font-weight: 400;
  color: var(--color-content-faint);
}

.tower-card-note {
  margin-top: 0.25rem;
  font-size: var(--text-caption);
  color: var(--color-content-muted);
}

.empty-note {
  font-size: var(--text-label);
  color: var(--color-content-faint);
}

.target-floor {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  flex-wrap: wrap;
}

.target-floor-label {
  font-size: var(--text-label);
  color: var(--color-content-muted);
}

.target-floor-cap {
  font-size: var(--text-caption);
  color: var(--color-content-faint);
}

/* --- 戦闘ログ --- */
.log-live {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  font-size: var(--text-caption);
  color: var(--color-hp-bright);
}

.log-live-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background-color: var(--color-hp);
  animation: live-pulse 2s ease-in-out infinite;
}

@keyframes live-pulse {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 1; }
}

.battle-log {
  /* スマホでは画面の 3 割程度に収め、下のナビまで届かせない */
  max-height: 34dvh;
  overflow-y: auto;
  overscroll-behavior: contain;
  font-family: var(--font-mono);
  font-size: var(--text-label);
  line-height: 1.55;
}

.log-tick {
  padding: 0.375rem 0 0.375rem 0.625rem;
  border-left: 2px solid var(--color-line-soft);
}

.log-tick + .log-tick {
  border-top: 1px solid var(--color-line-soft);
}

.log-entry {
  color: var(--color-content-muted);
  overflow-wrap: anywhere;
}

/* 種別ごとの色。彩度は抑え、重要なものだけ明るくする */
.log-attack { color: var(--color-content); }
.log-defeat { color: var(--color-gold); font-weight: 600; }
.log-level_up { color: var(--color-exp-bright); font-weight: 700; }
.log-encounter { color: var(--color-content-faint); }
.log-tower_target_reached { color: var(--color-accent-bright); font-weight: 700; }
.log-player_defeated { color: var(--color-danger-bright); font-weight: 700; }
.log-retreat_hp { color: var(--color-danger-bright); }
.log-potion,
.log-recovery { color: var(--color-hp-bright); }
.log-lifesteal { color: var(--color-hp); }
.log-equipment_drop { color: var(--color-rarity-rare); font-weight: 600; }
.log-equipment_auto_sold { color: var(--color-content-faint); }

.log-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.625rem;
  padding: 2rem 1rem;
  color: var(--color-content-faint);
  font-family: var(--font-body);
  font-size: var(--text-label);
  text-align: center;
}

/* --- オフライン報酬 --- */
.offline-lead {
  font-size: var(--text-label);
  color: var(--color-content-muted);
  margin-bottom: 1rem;
}

.offline-headline {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.offline-figure {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.75rem;
  background-color: var(--color-surface-2);
  border-radius: var(--radius-md);
}

.offline-value {
  font-size: var(--text-stat);
  font-weight: 700;
}

.offline-detail {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  font-size: var(--text-label);
}

.offline-detail > div {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.offline-detail dt {
  color: var(--color-content-muted);
}

.offline-detail dd {
  color: var(--color-content-strong);
  font-weight: 600;
}

.offline-levelup {
  margin-top: 0.875rem;
  padding: 0.5rem 0.75rem;
  border-radius: var(--radius-md);
  background-color: color-mix(in srgb, var(--color-exp) 14%, transparent);
  color: var(--color-exp-bright);
  font-weight: 700;
  text-align: center;
}

/* --- PC: 2カラム --- */
@media (min-width: 768px) {
  .home-grid {
    grid-template-columns: 1fr 1fr;
  }

  .log-card {
    grid-column: 1 / -1;
  }

  .battle-log {
    max-height: 22rem;
  }
}
</style>
