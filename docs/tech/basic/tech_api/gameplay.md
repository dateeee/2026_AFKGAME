# AFK GAME — API設計: 操作系（塔・ショップ・装備・アイテム）

> 親: [tech_api.md](../tech_api.md) §5（索引）。全エンドポイントに適用する共通仕様は [common.md](common.md) §5.0。
> 呼び出し順は [api_sequence/gameplay.md](../../../diagrams/api_sequence/gameplay.md)。

---

## 操作系（プレイヤーのアクション）
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/tower/list` | 全塔の一覧を取得（名前・階数・解放条件・解放/クリア状態・最高到達階・`targetFloorCap`）（Phase 2〜） |
| POST | `/api/tower/select` | 塔・目標階の選択（`towerId`, `targetFloor`, `mode`: `auto_repeat` \| `stop_on_clear`、イベントダンジョンは `difficulty` を追加）。未解放の塔は403、入塔中は400、`targetFloor` が範囲外は400 |
| POST | `/api/tower/retire` | 塔からリタイア（獲得済み報酬は保持・ペナルティなし） |
| PUT | `/api/tower/mode` | 進行モードの切り替え（進行中でも変更可） |
| PUT | `/api/tower/retreat-conditions` | 撤退条件の更新（`hpThreshold`: 0〜1） |
| GET | `/api/shop/lineup` | ショップの現在の品揃えを取得。Phase 1: 常設のみ。Phase 2〜: 常設＋日替わり5枠＋次回更新時刻（[tech_shop.md §6](../../detail/tech_shop.md)） |
| POST | `/api/shop/buy` | ショップでアイテム購入。常設商品: `itemId` + `quantity`（ポーションID等は常設扱い、在庫無制限）。Phase 2〜: 日替わり商品は `dailySlotIndex`（枠番号指定、各1個限り）を追加。両方の指定・どちらも未指定は 422（`tech_shop/buy.md` §4） |
| GET | `/api/equipment/list` | プレイヤーの全装備一覧を取得（Phase 2〜） |
| POST | `/api/equipment/equip` | 装備の変更（Phase 2〜） |
| POST | `/api/equipment/sell` | 装備売却（`equipmentIds`）。装備を消費してゴールドを獲得（売却価格 = 5 × レアリティ倍率 × 装備レベル）（Phase 2〜） |
| POST | `/api/equipment/lock` | 装備のロック/アンロック切替（`equipmentId`）（Phase 2〜） |
| POST | `/api/item/sell` | アイテム売却（`itemId`, `quantity`）。**換金アイテムは Phase 2〜**（同Phaseからドロップするため。[master/item.md §5](../../../data/master/item.md)）、**素材は Phase 4〜**（生産システムと同時）。売却価格はアイテムごとの定義値 × `quantity` |

> **`targetFloor` の検証範囲**: `1 <= targetFloor <= min(その塔の TowerClearRecord.highestFloor + 1, totalFloors)`。塔ごとに個別判定し、範囲外は 400。深淵の塔（`abyss_tower`）は総階数を持たないため `highestFloor + 1` のみで判定する。この上限は `/api/tower/list` が塔ごとに `targetFloorCap` として返すため、クライアントは式を再実装しない。
> **上限追従**: 目標階が上限と一致している状態で新しい階をクリアした場合、サーバーが tick 処理内で `targetFloor` を +1 する（クライアントからの再設定は不要）。目標階が上限未満なら追従しない。
> 仕様は [systems/battle/progress.md](../../../design/systems/battle/progress.md) 「目標階設定」・[systems/endgame.md](../../../design/systems/endgame.md) §2.14 を参照。

