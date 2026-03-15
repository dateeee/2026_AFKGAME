/** ゴールド表示フォーマット */
export function formatGold(n: number): string {
  if (n >= 1_000_000_000) return `${(n / 1_000_000_000).toFixed(1)}B`
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 10_000) return `${(n / 1_000).toFixed(1)}K`
  return n.toLocaleString()
}

/** 時間表示フォーマット（秒→日本語表記） */
export function formatTime(seconds: number): string {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours > 0 && minutes > 0) return `${hours}時間${minutes}分`
  if (hours > 0) return `${hours}時間`
  if (minutes > 0) return `${minutes}分`
  return `${seconds}秒`
}
