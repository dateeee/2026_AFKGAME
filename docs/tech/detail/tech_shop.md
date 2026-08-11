# AFK GAME — 日替わりショップ処理

> [tech_spec.md](../tech_spec.md) 配下の詳細設計。ゲーム仕様は [systems/economy.md §2.5](../../design/systems/economy.md)、装備カテゴリは [systems/equipment.md §2.4](../../design/systems/equipment.md)、乱数規約は [tech_rng.md](tech_rng.md)、丸め規約は [tech_numeric.md](tech_numeric.md)。
> 本書は Phase 2 の日替わりショップ（品揃えの生成・24時間更新・購入）を定める。常設商品（ポーション）の購入は本書の対象外。

---

## 0. 子ファイル索引

処理フローと分岐一覧は子ファイルが正（**節番号は本書からの通し**）。

| 節 | 子ファイル | 対象 |
|----|-----------|------|
| §2・§3・§7 | [tech_shop/lineup.md](tech_shop/lineup.md) | 品揃えの生成 / ステータスと価格 / 分岐一覧（品揃えの生成・取得） |
| §4・§8 | [tech_shop/buy.md](tech_shop/buy.md) | 購入処理 / 分岐一覧（購入） |

## 1. 適用範囲と方針

| 項目 | 仕様 |
|------|------|
| 更新方式 | **遅延評価**。定期ジョブを持たず、`GET /api/shop/lineup` と `POST /api/shop/buy` の入口で鮮度を判定する（[tech_maintenance.md §12.6](../nonfunctional/tech_maintenance.md)） |
| 更新境界 | UTC日付の変わり目（00:00 UTC） |
| 枠数 | 5枠固定（武器2・防具2・アクセサリー1） |
| 在庫 | 各枠1個。購入すると次の更新まで売り切れ |
| 永続化 | 生成結果をDBへ保存する。RNGの内部状態は保存しない（`tech_rng.md` §2） |
| RNG | `java.util.Random` のインスタンスを引数で受け取る（静的・共有インスタンスの直接参照は禁止） |
| HP吸収 | 付与しない（ショップ販売なし。`systems/equipment.md` §2.4） |
| 強化値 | 常に 0 |

## 5. データ構造

親（更新サイクル）と子（5枠）の2テーブルで保存する。**列の型・制約の正は** [tech_db/item.md](../basic/tech_db/item.md)。

| テーブル | 件数 | 列 |
|---------|------|-----|
| `ShopDailyState` | プレイヤーごとに1件 | `id` / `player_id` / `reset_at`（次回リセット時刻・UTC翌日 00:00:00Z） |
| `ShopDailySlot` | 状態1件につき5件 | `id` / `shop_daily_state_id` / `slot_index`（0〜4） / `base_id` / `category`（`weapon`・`armor`・`accessory`） / `rarity` / `level` / `stat_atk` `stat_def` `stat_hp` `stat_spd`（未付与は NULL） / `price` / `sold` |

- 抽選結果（レアリティ・レベル・ステータス）は**生成時に確定して保存する**。表示した内容と購入結果を一致させるため、購入時に引き直さない
- 装備スロットと両手武器フラグは `base_id` からマスターデータで一意に定まるため保存しない
- `(shop_daily_state_id, slot_index)` の組で一意

## 6. API

### GET /api/shop/lineup

常設（既存の `lineup`）に加えて日替わり枠と次回更新時刻を返す。

```json
{
  "lineup": [ /* 常設商品（既存） */ ],
  "daily": [
    {
      "slotIndex": 0, "category": "weapon", "baseId": "sword", "name": "剣",
      "slot": "weapon", "rarity": "uncommon", "level": 20,
      "statAtk": 29, "statDef": null, "statHp": null, "statSpd": null,
      "price": 1500, "soldOut": false
    }
  ],
  "dailyResetAt": "2026-08-03T00:00:00Z"
}
```

- `daily` は常に5件（売り切れの枠も残す。フロントは `soldOut` で「売り切れ」表示に切り替える）
- 残り時間はフロント側で `dailyResetAt` との差から求める（サーバーは秒数を返さない）

### POST /api/shop/buy

`dailySlotIndex`（整数 0〜4）を追加する。日替わり購入時のレスポンスは購入後のゴールドと、付与した装備を返す。

```json
{ "status": "ok", "gold": 8500, "equipment": { /* 付与した装備1件 */ } }
```

