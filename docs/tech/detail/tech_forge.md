# AFK GAME — 鍛冶屋処理（索引）

> Phase 4。`/api/forge/*` の3操作（強化・製作・分解）の詳細設計。本書は**共通部分**（適用範囲・効果解決・API・エラーコード）を持ち、操作ごとの処理フローと分岐一覧は子ファイルにある。
> **数値の正**: 強化コストは [master/equipment.md §12](../../data/master/equipment.md)、製作レシピは同 §13（ランダム）・§5.5.3（固定）、分解テーブルは同 §14。鍛冶屋LVの効果は [economy.md §2.9](../../design/systems/economy.md)。本書と子ファイルは**処理**のみを持つ。
> 建設・レベルアップと施設効果の解決規則は [tech_base.md](tech_base.md)、酒場スカウトは [tech_scout.md](tech_scout.md)。

## 0. 子ファイル索引

| 節 | 操作 | ファイル | 分岐一覧 |
|----|------|---------|---------|
| §3・§9 | 強化 `POST /api/forge/enhance` | [tech_forge/enhance.md](tech_forge/enhance.md) | 24件 |
| §4・§10 | 製作 `POST /api/forge/craft` | [tech_forge/craft.md](tech_forge/craft.md) | 30件 |
| §5・§6・§11 | 分解 `POST /api/forge/disassemble` | [tech_forge/disassemble.md](tech_forge/disassemble.md) | 20件 |

節番号は分割前の通し番号を維持する。素材の増減と所持枠の共通規則（§6）は分解のファイルにあり、3操作すべてに適用する。

## 1. 適用範囲と方針

| 項目 | 内容 |
|------|------|
| 対象 | `POST /api/forge/enhance`（強化）・`POST /api/forge/craft`（製作）・`POST /api/forge/disassemble`（分解） |
| 施設 | `forge` のみ。LV0（未建設）では3操作とも実行不可（`tech_base.md` §2.2） |
| 回数 | 1リクエスト＝**1回**（強化は+1のみ、製作は1件のみ。連続実行・一括処理を持たない） |
| 完了 | すべて**即時完了**（待ち時間・製作キューを持たない） |
| 進行状態 | 全状態で許可（[tech_state.md §4](tech_state.md) の `POST /api/base/*` / `forge/*` 行） |
| 乱数 | ランダム製作のみ使用（[tech_rng.md §1](tech_rng.md) #15）。強化・分解・固定レシピ製作は**乱数を消費しない** |
| 永続化 | `equipment`・`inventory_items`・`players.gold`（[tech_db/item.md](../basic/tech_db/item.md)）。同一トランザクション |
| エラーコード | §8。ゴールド不足と未建設は `BASE_` を流用する（`tech_base.md` §6） |

- 未認証は共通の `401`、リクエストボディの型・必須・範囲違反は `422`（[tech_api/common.md](../basic/tech_api/common.md)）。分岐一覧では `401` を扱わない
- 3操作とも**検証をすべて消費の前に行う**。消費したのに成果が得られない経路を持たない
- 3操作とも、まず `players` 行を**行ロック**して取得する（`tech_base.md` §3 手順2 と同じ方式）

## 2. 鍛冶屋LVからの効果解決

鍛冶屋は `economy.md` §2.9 鍛冶屋表の **LV1〜LV10 全レベル列挙**であり、解決は `tech_base.md` §2.1 に従い**施設LVと一致する行を引くだけ**（しきい値検索・補間を行わない）。採用行から3つを引く。

| 引くもの | 使う操作 |
|---------|---------|
| 強化上限（= 鍛冶屋LV） | 強化 |
| 製作可能レアリティ | 製作 |
| 強化コスト倍率 | 強化 |

- `facilities` に鍛冶屋の行が無いプレイヤーは LV0 とみなす（`tech_base.md` §1）
- 3つの値を実装へハードコードしない（不変条件「データ駆動」）。分解は鍛冶屋LVに依存しない（LV1以上なら結果は同じ）

## 7. API

| 項目 | 内容 |
|------|------|
| `enhance` | 要求 `{ "equipmentId": "..." }`。成功 `200` で `equipment`（更新後・実効ステータス込み）・`materials`・`gold` を返す |
| `craft` | 要求 `{ "rank": 1-5 }` または `{ "recipeId": "..." }`（排他・どちらか必須）。成功 `200` で `equipment`（生成物）・`materials`・`gold` を返す |
| `disassemble` | 要求 `{ "equipmentId": "..." }`。成功 `200` で `materials`（更新後）・`gained`（獲得素材と数量）を返す |
| 失敗 | §8 のコード。形式は [tech_error_handling.md](../basic/tech_error_handling.md)「統一エラーレスポンス形式」 |

- `materials` は3操作とも**更新後の全素材の所持数**を返す（フロントが差分計算をしないで済むようにする）

## 8. `FORGE_` エラーコード一覧

| コード | HTTP | 発生条件 |
|--------|------|---------|
| `FORGE_MAX_ENHANCE` | 400 | 強化で現在の強化段階が鍛冶屋LVの強化上限に達している |
| `FORGE_LEVEL_TOO_LOW` | 400 | 製作の要求レアリティが鍛冶屋LVの製作可能レアリティを上回る |
| `FORGE_INSUFFICIENT_MATERIALS` | 400 | 必要素材のいずれかが不足（`details` に不足分を全件列挙） |
| `FORGE_INVENTORY_FULL` | 400 | 製作で所持枠が上限に達している |
| `FORGE_EQUIPMENT_LOCKED` | 400 | 分解対象が装備中またはロック中 |

- 未建設は `BASE_NOT_BUILT`、ゴールド不足は `BASE_INSUFFICIENT_GOLD`（`tech_base.md` §6）、装備が見つからない場合は `EQUIP_NOT_FOUND`（`tech_error_handling.md`）を流用する
- `equipmentId` の欠落、`rank` の範囲外、未知の `recipeId`、`rank` と `recipeId` の排他違反は Bean Validation の `422` とし、`FORGE_` コードを使わない
