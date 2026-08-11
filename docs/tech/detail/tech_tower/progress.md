# 階進行（tick内・階クリア後の処理）

> [tech_tower.md](../tech_tower.md) の子ファイル（§9・§10）。tickのターン処理（[tech_battle.md §3.1](../tech_battle.md)）で**その階の全敵HP=0**（階クリア。同 §3.2）になった直後から、次階送りまたは塔離脱の確定までを定義する。
> 全滅時の処理（ペナルティ・強制撤退）は本書の対象外（[tech_state.md §3](../tech_state.md) が正）。乱数は消費しない。

## 9. 階クリア後の判定順序

クリアした階を `clearedFloor`（= 処理時点の `currentFloor`）とする。順序は固定:

1. **クリア記録の更新**: その塔の `TowerClearRecord` を取得し、無ければ `highestFloor = 0`・`cleared = false` で作成する
   - a. `clearedFloor > highestFloor` なら `highestFloor = clearedFloor`。**更新前の値を旧値として保持**（手順3で使う）
   - b. `clearedFloor > players.highestFloor`（全塔通算の表示用）なら同様に更新
   - c. `clearedFloor = totalFloors`（最上階ボス討伐）なら `cleared = true`（次の塔の解放条件になる。深淵の塔は総階数が無いため対象外＝常に `false`）
2. **環境効果 `recovery` の適用**（Phase 3〜）: 塔に `recovery` modifier があれば、生存者（`hp > 0`）へ `heal = floor(maxHP × value)` を適用。上限 `effectiveMaxHp`（[tech_data.md §1.5](../../basic/tech_data.md)）
3. **上限追従**: 次の3条件が**すべて**成立したら `targetFloor = 新cap`（実質 +1。[tech_api/gameplay.md](../../basic/tech_api/gameplay.md)「操作系」の上限追従）
   - `clearedFloor > 旧highestFloor`（新しい階のクリアである）
   - `targetFloor = 旧cap`（目標階が上限に一致していた）
   - `新cap > 旧cap`（総階数で頭打ちでない）。`cap = min(highestFloor + 1, totalFloors)`（tech_tower.md §2）
4. **目標到達判定**: `nextFloor = clearedFloor + 1` とし、`nextFloor > totalFloors` **または** `nextFloor > targetFloor` なら目標到達として進行モードで分岐（深淵の塔は `targetFloor` 判定のみ）:
   - `auto_repeat`: `currentFloor = 1`・敵情報クリアで塔に留まる。**探索セッションは継続**（周回をまたいで累積。tech_state.md §3）
   - `stop_on_clear`: 塔・敵情報をクリアし `IDLE` へ。セッション確定
5. **HP閾値撤退判定**（目標**未到達**のときだけ評価）: `hpThreshold > 0` かつ `Σhp < ΣeffectiveMaxHp × hpThreshold` なら撤退する
   - 判定量は**在籍パーティ全員の合計**（HP0のメンバーも分母に含む）。Phase 1〜2 は勇者1体なので単体のHP割合と同値
   - 撤退時はまずセッションを**確定（没収なし）してリセット**し、`auto_repeat` なら `currentFloor = 1`・敵情報クリアで塔に留まって新しいセッションを開始、`stop_on_clear` なら塔・敵情報をクリアし `IDLE` へ（[systems/battle.md](../../../design/systems/battle.md)「撤退条件」）
6. **次階送り**: どちらにも該当しなければ `currentFloor = nextFloor`・敵情報クリア（次の敵は次のエンカウントで抽選。tech_battle.md §3.2）

- 手順4・5の判定は**手順3の追従後の `targetFloor`** を使う（追従した周は目標到達にならず、放置のまま開拓が続く）
- 手順5の判定は**手順2の回復適用後**のHPで行う。比較は**未満**（`<`）で、`hpThreshold = 0` は無効化（既定 `0.3`。[tech_db/player.md §1](../../basic/tech_db/player.md)）
- リタイア（[control.md §11](control.md)）は即時成立のため、本書の順序に割り込む「予約」は存在しない

## 10. 分岐一覧（階進行）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | 記録更新 | `clearedFloor` が塔別 `highestFloor` を超える（行が無ければ作成して `0` と比較） | `highestFloor = clearedFloor` へ更新 |
| 2 | 記録更新 | 超えない（既踏の階の再クリア） | 記録を変更しない |
| 3 | 通算最高階 | `clearedFloor > players.highestFloor` | 更新する |
| 4 | 通算最高階 | 超えない | 変更しない |
| 5 | ボス討伐 | `clearedFloor = totalFloors`（最上階） | `cleared = true`（次の塔が解放される） |
| 6 | ボス討伐 | 最上階でない | `cleared` を変更しない |
| 7 | 環境効果回復（Phase 3〜） | `recovery` modifier がある | 生存者へ `floor(maxHP × value)` 回復（上限 `effectiveMaxHp`） |
| 8 | 環境効果回復（Phase 3〜） | 無い | 回復しない |
| 9 | 上限追従 | 3条件すべて成立 | `targetFloor = 新cap`（+1） |
| 10 | 上限追従 | 既踏の階の再クリア | 追従しない |
| 11 | 上限追従 | `targetFloor` が旧capより低い | 追従しない（プレイヤーが意図した周回階を維持） |
| 12 | 上限追従 | `新cap = 旧cap`（総階数で頭打ち） | 追従しない |
| 13 | 目標到達 | `nextFloor` が `totalFloors` または `targetFloor` を超える | 進行モードの分岐（#15・#16）へ |
| 14 | 目標到達 | 超えない | HP閾値判定（#17〜#19）へ |
| 15 | 到達時モード | `auto_repeat` | `currentFloor = 1`・敵情報クリア・セッション**継続** |
| 16 | 到達時モード | `stop_on_clear` | 塔・敵情報クリア・`IDLE`・セッション確定 |
| 17 | HP閾値 | `hpThreshold > 0` かつ `Σhp < ΣeffectiveMaxHp × hpThreshold` | セッション確定リセット後、撤退モードの分岐（#20・#21）へ |
| 18 | HP閾値 | `hpThreshold = 0` | 判定せず次階送り（無効化） |
| 19 | HP閾値 | `Σhp >= ΣeffectiveMaxHp × hpThreshold`（閾値ちょうどを含む） | 次階送り |
| 20 | 撤退時モード | `auto_repeat` | `currentFloor = 1` で塔に留まり、新しいセッションを開始 |
| 21 | 撤退時モード | `stop_on_clear` | 塔・敵情報クリア・`IDLE` |

> WARN許容 #13・#14: 「totalFloors 超過」と「targetFloor 超過」は or 条件の2観点だが、`targetFloor <= totalFloors` が select（[select.md](select.md) §7）で保証されるため totalFloors 側が単独で真になるのは `targetFloor = totalFloors` の周回時のみ。両者は #13 の試験で階数・目標階を変えて網羅する。
