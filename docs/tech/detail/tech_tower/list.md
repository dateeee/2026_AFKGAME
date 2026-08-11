# 塔一覧の組み立て（GET /api/tower/list）

> [tech_tower.md](../tech_tower.md) の子ファイル（§5・§6）。Phase 2〜（Phase 1 のフロントは塔1固定で select を呼ぶため未使用。[tech_api/gameplay.md](../../basic/tech_api/gameplay.md)「操作系」）。
> 解放判定・`cap` の解決規則は tech_tower.md §2 が正。本書は一覧APIへの当てはめと分岐一覧を持つ。

## 5. 一覧の組み立て

入力なし（認証のみ）。読み取り専用で、状態を変更しない。

1. プレイヤーの `TowerClearRecord` を全件取得し、塔ID → 記録の対応表を作る
2. 塔マスターの全塔を **[TOWERS_OVERVIEW.md](../../../data/towers/TOWERS_OVERVIEW.md) の番号順**で走査し、塔ごとに `TowerInfo` を組み立てる
   - `unlocked`: tech_tower.md §2 の解放判定（`cleared = true` の塔ID集合と解放条件の照合）
   - `cleared`・`highestFloor`: 記録の値。行が無ければ `false`・`0`
   - `targetFloorCap`: tech_tower.md §2 の `cap` 式
3. **未解放の塔も一覧に含める**（解放条件の表示に使う。表示の制御はフロント側）。未実装Phaseの塔はマスターに載せないため応答に現れない

| 項目 | 内容 |
|------|------|
| 応答 | `TowerInfo` の配列（フィールドは tech_tower.md §3） |
| 深淵の塔（Phase 5〜） | `totalFloors = null`（階数無限）・`targetFloorCap = highestFloor + 1` |
| イベントダンジョン（Phase 5〜） | 難易度ごとの**独立エントリ3件**へ展開。`totalFloors` は10固定。記録は難易度を畳み込んだキーで引く（キー体系の正は [tech_data.md §1.1](../../basic/tech_data.md)、一覧の仕様は [tech_api/endgame.md](../../basic/tech_api/endgame.md)「イベントダンジョン」） |

## 6. 分岐一覧（一覧・解放判定）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | 解放判定 | 解放条件が無い（初期解放の塔） | `unlocked = true` |
| 2 | 解放判定 | 前提塔のボス討伐済み（`cleared = true` の記録がある） | `unlocked = true` |
| 3 | 解放判定 | 前提塔が未クリア（記録なし・`cleared = false` とも） | `unlocked = false` |
| 4 | クリア記録 | その塔の記録がある | `cleared`・`highestFloor` を記録の値で返す |
| 5 | クリア記録 | その塔の記録が無い（未挑戦） | `cleared = false`・`highestFloor = 0` |
| 6 | 上限計算 | 開拓側が上限（`highestFloor + 1 <= totalFloors`） | `targetFloorCap = highestFloor + 1` |
| 7 | 上限計算 | 総階数が上限（`highestFloor + 1 > totalFloors`＝最上階まで到達済み） | `targetFloorCap = totalFloors` |
| 8 | 深淵の塔（Phase 5〜） | 総階数を持たない | `totalFloors = null`・`targetFloorCap = highestFloor + 1` |
| 9 | イベントダンジョン（Phase 5〜） | 難易度別エントリ | 3難易度へ展開し、記録を難易度別キーで引く |

> WARN許容 #8・#9: Phase 5 の例外系で片側のみ。対になる通常塔側の振る舞いは #6・#7（総階数あり）と #4・#5（塔ID単独キー）が担う。
