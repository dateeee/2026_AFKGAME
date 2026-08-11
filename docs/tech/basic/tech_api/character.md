# AFK GAME — API設計: パーティ・スキル・限界突破（Phase 3〜4）

> 親: [tech_api.md](../tech_api.md) §5（索引）。全エンドポイントに適用する共通仕様は [common.md](common.md) §5.0。
> 呼び出し順は [api_sequence/character.md](../../../diagrams/api_sequence/character.md)。

---

## パーティ・スキル（Phase 3〜）
| メソッド | パス | 説明 |
|---------|------|------|
| PUT | `/api/party/edit` | パーティ編成の変更（`memberIds`: キャラID配列、最大4人）。**入塔中の変更は400**（入れ替えは塔外限定。[systems/character.md §2.7](../../../design/systems/character.md)）。未所持・重複するキャラIDは422 |
| POST | `/api/skill/learn` | スキル習得（`characterId`, `skillId`）。SP消費。前提スキル未習得時はエラー |
| PUT | `/api/skill/set-active` | アクティブスキルのセット変更（`characterId`, `activeSlots`: スキルID配列、最大2） |
| POST | `/api/skill/reset` | スキル全リセット（`characterId`）。ゴールド消費（LV×50G）。全SP返却 |
| POST | `/api/character/limit-break` | 限界突破（`characterId`, `materialCharacterId`）。素材キャラを消費。処理は [tech_limitbreak.md](../../detail/tech_limitbreak.md) |

