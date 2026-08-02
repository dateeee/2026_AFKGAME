/**
 * アイコン定義。
 * 絵文字は端末ごとに字形・色・大きさが変わるため使用しない。
 * 追加時は 24x24 グリッド・線のみ（塗りなし）で描き、線幅・端の丸めは AppIcon 側に任せる。
 */
export type IconName =
  | 'tower'
  | 'shield'
  | 'coin'
  | 'sliders'
  | 'chevron-down'
  | 'lock'
  | 'plus'
  | 'minus'
  | 'close'
  | 'alert'
  | 'check'

export const ICON_PATHS: Record<IconName, string> = {
  tower:
    '<path d="M5.5 21V9.5h13V21"/>' +
    '<path d="M5.5 9.5V3.8l2.6 1.7 2.2-1.7 1.7 1.3 1.7-1.3 2.2 1.7 2.6-1.7v5.7"/>' +
    '<path d="M10 21v-4.5a2 2 0 0 1 4 0V21"/>',
  shield: '<path d="M12 3.2 19 6v5.6c0 4.3-2.8 7.7-7 9.2-4.2-1.5-7-4.9-7-9.2V6z"/>',
  // 同心円だけだと的（ターゲット）に見えるため、内側は四芒星にする
  coin:
    '<circle cx="12" cy="12" r="8.2"/>' +
    '<path d="M12 7.3c.5 2.3 1.9 3.7 4.2 4.2-2.3.5-3.7 1.9-4.2 4.2-.5-2.3-1.9-3.7-4.2-4.2 2.3-.5 3.7-1.9 4.2-4.2z"/>',
  // 歯車は 22px では歯が潰れて太陽に見えるため、スライダー型を使う
  sliders:
    '<path d="M4 7h8.5M17.5 7H20M4 12h3.5M12.5 12H20M4 17h8.5M17.5 17H20"/>' +
    '<circle cx="15" cy="7" r="2.2"/><circle cx="10" cy="12" r="2.2"/><circle cx="15" cy="17" r="2.2"/>',
  'chevron-down': '<path d="m6 9.5 6 6 6-6"/>',
  lock:
    '<rect x="4.8" y="10.4" width="14.4" height="9.8" rx="2"/>' +
    '<path d="M8.4 10.4V7.2a3.6 3.6 0 0 1 7.2 0v3.2"/>',
  plus: '<path d="M12 5.5v13M5.5 12h13"/>',
  minus: '<path d="M5.5 12h13"/>',
  close: '<path d="m6.5 6.5 11 11M17.5 6.5l-11 11"/>',
  alert: '<path d="M12 4.2 20.8 20H3.2z"/><path d="M12 10v4.2"/><path d="M12 17.2h.01"/>',
  check: '<path d="m5.5 12.5 4.3 4.3L18.5 8"/>',
}
