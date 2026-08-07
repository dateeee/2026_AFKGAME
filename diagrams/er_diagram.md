# ER図（エンティティ関連図）

> **DBスキーマの正は** [tech_db.md](../docs/tech/basic/tech_db.md)（テーブル定義書）。本図はその視覚化であり、食い違いは常に定義書側へ揃える。
> データ構造: [tech_data.md](../docs/tech/basic/tech_data.md) / 認証仕様: [tech_auth.md](../docs/tech/detail/tech_auth.md)
> 本書は索引。各系統は [er_diagram/](er_diagram/) 配下の個別ファイルに分割している。

## 索引

| 系統 | 主なテーブル | ファイル |
|------|------------|---------|
| 認証・アカウント系<br>プレイヤー・キャラクター系 | User / RefreshToken / EmailVerificationToken / Player / PlayerSettings / Character / CharacterEquipSlot / PartyMember / SkillMaster / LearnedSkill / ActiveSkillSlot / TowerClearRecord / PrestigeBonus | [er_diagram/player.md](er_diagram/player.md) |
| 装備・アイテム系<br>ショップ・施設系 | Equipment / InventoryItem / ItemMaster / ShopDailyState / ShopDailySlot / Facility | [er_diagram/item.md](er_diagram/item.md) |
| 戦闘・ボスラッシュ系<br>ダンジョン・塔・敵系（マスターデータ） | BattleLog / BossRushState / BossRushMilestone / Dungeon / Tower / TowerModifier / FloorEncounter / EnemyMaster / EnemyDrop | [er_diagram/battle.md](er_diagram/battle.md) |

- `CharacterEquipSlot` のみ**定義書と図の所在が分かれる**。定義書は [tech_db/item.md](../docs/tech/basic/tech_db/item.md)（装備系）、図は [er_diagram/player.md](er_diagram/player.md)（キャラを起点に読むため）。
