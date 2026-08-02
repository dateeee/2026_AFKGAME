import type { IconName } from '@/components/ui/icons'

export interface NavItem {
  to: string
  label: string
  icon: IconName
}

/**
 * グローバルナビの項目。モバイルのボトムナビと PC のサイドナビで共有する。
 * 画面を追加するときはここに1行足すだけでよい（両方に自動で反映される）。
 * ※ 5項目を超えるとモバイルで1項目あたり 64px を割るため、
 *   その時点で「その他」へのまとめを検討すること。
 */
export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'ホーム', icon: 'tower' },
  { to: '/equipment', label: '装備', icon: 'shield' },
  { to: '/shop', label: 'ショップ', icon: 'coin' },
  { to: '/settings', label: '設定', icon: 'sliders' },
]
