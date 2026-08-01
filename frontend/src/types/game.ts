/** プレイヤー情報 */
export interface Player {
  id: string
  gold: number
  currentTowerId: string | null
  currentFloor: number | null
  targetFloor: number | null
  towerMode: 'auto_repeat' | 'stop_on_clear'
  hpThreshold: number
  highestFloor: number
  currentEnemyId: string | null
  currentEnemyHp: number | null
}

/** キャラクター */
export interface Character {
  id: string
  name: string
  type: string
  level: number
  exp: number
  hp: number
  maxHp: number
  baseAtk: number
  baseDef: number
  baseSpd: number
  effectiveMaxHp?: number
}

/** プレイヤー設定 */
export interface Settings {
  potionThreshold: number
  battleLogCount: number
  toastEnabled: boolean
  autoSellRarity: string | null
}

/** 装備スロット */
export type EquipmentSlot = 'weapon' | 'shield' | 'head' | 'body' | 'arms' | 'waist' | 'legs' | 'ears' | 'ring'

/** レアリティ */
export type Rarity = 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary'

/** 装備 */
export interface Equipment {
  id: string
  baseId: string
  slot: EquipmentSlot
  rarity: Rarity
  level: number
  enhanceLevel: number
  statAtk: number | null
  statDef: number | null
  statHp: number | null
  statSpd: number | null
  lifesteal: number | null
  isTwoHanded: boolean
  locked: boolean
  acquiredAt: string
}

/** 塔クリア情報 */
export interface TowerClearInfo {
  cleared: boolean
  highestFloor: number
}

/** 塔情報（一覧API） */
export interface TowerInfo {
  id: string
  name: string
  dungeonName: string
  totalFloors: number
  unlockTowerId: string | null
  unlocked: boolean
  cleared: boolean
  highestFloor: number
}

/**
 * 戦闘ログエントリー
 * NOTE: battle_logsはCamelModel変換対象外。バックエンドからsnake_caseのまま返却される
 */
export interface BattleLogEntry {
  type: string
  actor?: string
  target?: string
  damage?: number
  critical?: boolean
  amount?: number
  hp?: number
  max_hp?: number
  floor?: number
  enemy?: string
  enemy_hp?: number
  gold?: number
  exp?: number
  level?: number
  stats?: Record<string, number>
  exp_lost?: number
  gold_lost?: number
  target_hp?: number
  message?: string
  name?: string
  rarity?: string
  slot?: string
}

/** オフライン報酬サマリー */
export interface OfflineSummary {
  elapsedSeconds: number
  processedTicks: number
  calcMethod: string
  totalGold: number
  totalExp: number
  enemiesDefeated: number
  potionsUsed: number
  levelsGained: number
  floorsCleared: number
}

/** 現在の敵情報 */
export interface EnemyInfo {
  id: string
  name: string
  hp: number
  maxHp: number
  level: number
}

/** ゲーム状態（APIレスポンス） */
export interface GameState {
  player: Player
  characters: Character[]
  settings: Settings
  potions: Record<string, number>
  towersCleared: Record<string, TowerClearInfo>
  currentEnemy: EnemyInfo | null
  equipment: Equipment[]
  equipped: Record<string, string | null>
}

/** tick APIレスポンス */
export interface TickResponse {
  battleLogs: BattleLogEntry[][]
  updatedState: GameState
  offlineSummary: OfflineSummary | null
  equipmentDrops: Equipment[]
  equipmentAutoSold: Array<{ name: string; rarity: string; gold: number }>
}

/** ゲストトークンレスポンス（Phase 1互換） */
export interface GuestTokenResponse {
  guestToken: string
}

/** ユーザー情報 */
export interface UserInfo {
  id: string
  email: string | null
  displayName: string
  isGuest: boolean
  emailVerified: boolean
}

/** 認証レスポンス */
export interface AuthResponse {
  accessToken: string
  refreshToken: string
  user: UserInfo
}
