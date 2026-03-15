/**
 * バックエンド未起動時のローカルフォールバック（デバッグ用）
 * VITE_USE_API=false の場合のみ使用
 */

import type { BattleLogEntry } from '@/types/game'

export function useBattleLocal() {
  function simulateTick(): BattleLogEntry[] {
    // Phase 1実装時にローカル戦闘計算を追加
    return []
  }

  return { simulateTick }
}
