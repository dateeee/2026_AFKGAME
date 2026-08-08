# テーブル定義 — 装備・アイテム・ショップ・施設

> 親: [tech_db.md](../tech_db.md)。命名規約・型マッピング・共通の列規約・外部キー動作は親が正であり、本書では繰り返さない。
> 視覚化は [er_diagram/item.md](../../../diagrams/er_diagram/item.md)（`character_equip_slots` のみキャラクター系の図に属するため [er_diagram/player.md](../../../diagrams/er_diagram/player.md)）。日替わりショップの処理仕様は [tech_shop.md](../../detail/tech_shop.md)、装備の仕様は [systems/equipment.md](../../../design/systems/equipment.md)。

---

## 1. `equipment`（Phase 2）

実装予定: `com.afkgame.domain.model.Equipment`。テーブル名は不可算名詞のため単数形（親 §2）。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id` |
| `base_id` | `VARCHAR(50)` | 不可 | — | 装備マスターの ID。FKなし（親 §4-6） |
| `slot` | `VARCHAR(20)` | 不可 | — | `weapon` / `shield` / `head` / `body` / `arms` / `waist` / `legs` / `ears` / `ring` |
| `rarity` | `VARCHAR(20)` | 不可 | — | `common` / `uncommon` / `rare` / `epic` / `legendary` |
| `level` | `INTEGER` | 不可 | — | 装備レベル。決定規則は [master/equipment.md](../../../data/master/equipment.md) が正 |
| `enhance_level` | `INTEGER` | 不可 | `0` | 強化段階 |
| `stat_atk` | `INTEGER` | 可 | — | 未付与は NULL |
| `stat_def` | `INTEGER` | 可 | — | 未付与は NULL |
| `stat_hp` | `INTEGER` | 可 | — | 未付与は NULL |
| `stat_spd` | `INTEGER` | 可 | — | 未付与は NULL |
| `lifesteal` | `FLOAT` | 可 | — | HP吸収率。未付与は NULL |
| `is_two_handed` | `BOOLEAN` | 不可 | `false` | 生成時に `base_id` のマスター値を書き写す |
| `locked` | `BOOLEAN` | 不可 | `false` | 売却・自動売却の対象外にする |
| `acquired_at` | `DATETIME(tz)` | 不可 | 現在時刻 | 取得時刻。一覧の既定の並び順に使う |

ステータス列を個別の値として持ち、`base_id` のマスター値から都度算出しない。生成時の抽選結果を確定させ、表示と実効値を一致させるため。

## 2. `character_equip_slots`（Phase 2）

実装: `com.afkgame.domain.model.CharacterEquipSlot`。キャラクターと装備の交差テーブルで、キャラ1体につき9行（全スロット分）を持つ。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `character_id` | `VARCHAR(36)` | 不可 | — | **複合PK**。FK → `characters.id` |
| `slot` | `VARCHAR(20)` | 不可 | — | **複合PK**。`equipment.slot` と同じ9種 |
| `equipment_id` | `VARCHAR(36)` | 可 | — | FK → `equipment.id`。未装備は NULL |

主キーは `id` ではなく (`character_id`, `slot`) の複合主キー（親 §4-1 の例外）。行の同一性がこの組で決まり、代理キーを置いても一意制約を別途張る必要が生じるため。

## 3. `inventory_items`（Phase 2）

実装: `com.afkgame.domain.model.InventoryItem`

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id` |
| `item_id` | `VARCHAR(50)` | 不可 | — | アイテムマスターの ID。FKなし（親 §4-6） |
| `quantity` | `INTEGER` | 不可 | `0` | 所持数。上限は [master/item.md](../../../data/master/item.md) が正 |

一意制約: `uq_inventory_items_player_item` = (`player_id`, `item_id`)（同一アイテムは1行にまとめ、数量で表す）

アイテムの分類（`category`）・価格・回復割合は列に持たず、`item_id` からマスターを引いて得る。

## 4. `shop_daily_states`（Phase 2）

実装予定: `com.afkgame.domain.model.ShopDailyState`。日替わりショップの更新サイクルを表し、プレイヤーごとに1件。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id`、UNIQUE。同時リクエストで重複した状態が作られると以後どちらが読まれるか不定になるためDB側で防ぐ |
| `reset_at` | `DATETIME(tz)` | 不可 | — | 次回リセット時刻。確定規則は [tech_shop.md](../../detail/tech_shop.md) §2.1 が正 |

## 5. `shop_daily_slots`（Phase 2）

実装予定: `com.afkgame.domain.model.ShopDailySlot`。状態1件につき5行。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `shop_daily_state_id` | `VARCHAR(36)` | 不可 | — | FK → `shop_daily_states.id` |
| `slot_index` | `INTEGER` | 不可 | — | 枠番号（0〜4） |
| `category` | `VARCHAR(20)` | 不可 | — | `weapon` / `armor` / `accessory` |
| `base_id` | `VARCHAR(50)` | 不可 | — | 装備マスターの ID。FKなし（親 §4-6） |
| `rarity` | `VARCHAR(20)` | 不可 | — | 抽選結果。取りうる値は [tech_shop.md](../../detail/tech_shop.md) §2.3 が正 |
| `level` | `INTEGER` | 不可 | — | 装備レベル。算出規則は [tech_shop.md](../../detail/tech_shop.md) §3.1 が正 |
| `stat_atk` | `INTEGER` | 可 | — | 未付与は NULL |
| `stat_def` | `INTEGER` | 可 | — | 未付与は NULL |
| `stat_hp` | `INTEGER` | 可 | — | 未付与は NULL |
| `stat_spd` | `INTEGER` | 可 | — | 未付与は NULL |
| `price` | `INTEGER` | 不可 | — | 購入価格。算出規則は [tech_shop.md](../../detail/tech_shop.md) §3.2 が正 |
| `sold` | `BOOLEAN` | 不可 | `false` | 購入済みの枠は行を消さずフラグで表す（リセットまで枠を残すため） |

一意制約: `uq_shop_daily_slots_state_slot` = (`shop_daily_state_id`, `slot_index`)

装備スロットと両手武器フラグは `base_id` からマスターで一意に定まるため列に持たない。購入時に `equipment` へ書き写す。

## 6. `facilities`（Phase 4・未実装）

実装予定: `com.afkgame.domain.model.Facility`。拠点の施設をプレイヤーごとに保持する。効果・コストは [systems/economy.md](../../../design/systems/economy.md) と [master/base.md](../../../data/master/base.md) が正。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id` |
| `facility_type` | `VARCHAR(20)` | 不可 | — | `tavern` / `forge` / `training_ground` / `warehouse` / `market` |
| `level` | `INTEGER` | 不可 | `0` | 施設レベル（0 = 未建設） |

一意制約: `uq_facilities_player_type` = (`player_id`, `facility_type`)（1プレイヤーにつき各種類1行）

## 7. インデックスと検索パターン

主キーと一意制約が張るインデックスのみを持ち、二次インデックスは持たない（方針は [tech_db.md](../tech_db.md) §6）。

| 検索パターン | 使うインデックス | 判断 |
|------------|---------------|------|
| キャラの装備欄を引く | `character_equip_slots` の複合PK | 充足（左端が `character_id`） |
| プレイヤーの所持アイテムを引く | `uq_inventory_items_player_item` | 充足（左端が `player_id`） |
| プレイヤーのショップ状態を引く | `shop_daily_states.player_id`（UNIQUE） | 充足 |
| 品揃えを `slot_index` 順に引く | `uq_shop_daily_slots_state_slot` | 充足（左端が `shop_daily_state_id`） |
| プレイヤーの施設を引く | `uq_facilities_player_type` | 充足（左端が `player_id`） |
| 装備一覧を `acquired_at` 順に引く | なし（`equipment.player_id`） | 二次インデックスを張らない。1プレイヤーの所持上限が倉庫枠で抑えられており、追加は [tech_db.md](../tech_db.md) §6-3 の再評価ラインで判断する |
