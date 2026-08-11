# AFK GAME — API設計: 拠点（施設・鍛冶屋。Phase 4）

> 親: [tech_api.md](../tech_api.md) §5（索引）。全エンドポイントに適用する共通仕様は [common.md](common.md) §5.0。
> 呼び出し順は [api_sequence/base.md](../../../diagrams/api_sequence/base.md)。

---

## 施設・拠点（Phase 4〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/base/build` | 施設を建設（`facilityId`）。ゴールド+素材を消費してLV0→LV1 |
| POST | `/api/base/upgrade` | 施設をレベルアップ（`facilityId`）。ゴールド+素材を消費 |
| POST | `/api/base/scout` | 酒場でスカウト実行。ゴールドを消費してキャラ1体をランダム獲得 |

`build` / `upgrade` の処理フロー・エラーコード・施設効果の解決規則は [tech_base.md](../../detail/tech_base.md) が正。

## 鍛冶屋（Phase 4〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/forge/enhance` | 装備強化（`equipmentId`）。強化石+ゴールドを消費して+1 |
| POST | `/api/forge/craft` | 装備製作（`rank`: 1-5 または `recipeId`。**排他・どちらか必須**）。素材+ゴールドを消費し、ランダム装備または吸収装備を生成 |
| POST | `/api/forge/disassemble` | 装備分解（`equipmentId`）。装備を消費して素材を獲得 |

3操作の処理フロー・エラーコード・生成規則は [tech_forge.md](../../detail/tech_forge.md)（索引）が正。

