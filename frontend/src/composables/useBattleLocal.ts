/**
 * バックエンド未起動時のローカルフォールバック（デバッグ用）
 * VITE_USE_API=false の場合のみ使用
 *
 * 【未実装】現状はスタブで、どこからも import されていない。
 * VITE_USE_API=false では useGameLoop が初期化をスキップするだけなので、
 * 画面はクラッシュしないがキャラクターも塔一覧も空のままになる。
 * アーキテクチャ不変条件「バックエンド未起動でもフロント単体で動作可」（CLAUDE.md）が
 * 求める水準を確定させてから実装する（known_issues.md に記録済み）。
 */

import type { BattleLogEntry } from '@/types/game'

export function useBattleLocal() {
  function simulateTick(): BattleLogEntry[] {
    return []
  }

  return { simulateTick }
}
