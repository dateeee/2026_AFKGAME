# クラス図（ドメインモデル）

> ゲーム仕様: [game_spec.md](../docs/design/game_spec.md) / 技術仕様: [tech_spec.md](../docs/tech/tech_spec.md)
> 本書は索引。各ドメインは [class_diagram/](class_diagram/) 配下の個別ファイルに分割している。

> **注記**: 認証・アカウント系（User/RefreshToken等）はドメインモデルの対象外とし、ER図（[er_diagram.md](er_diagram.md)）を参照

## 索引

| ドメイン | 含まれるクラス | ファイル |
|---------|--------------|---------|
| プレイヤー・パーティ・キャラクター<br>スキルシステム | Player / Party / Character / Stats / PrestigeData / Settings / TowerClearRecord / Skill / SkillTree / SkillSet | [class_diagram/player.md](class_diagram/player.md) |
| ダンジョン・塔・敵<br>戦闘状態 | Dungeon / Tower / Enemy / EnemyEncounter / Modifier / BattleState / TurnResult / TickResult / Buff / Debuff / StatusAilment / OfflineSummary | [class_diagram/battle.md](class_diagram/battle.md) |
| 装備・アイテム<br>ショップ<br>施設・ボスラッシュ | Equipment / InventoryItem / DropEntry / Shop / DailyItem / PotionStock / Facility / FacilityCost / BossRushState / MilestoneReward | [class_diagram/item.md](class_diagram/item.md) |

- 「含まれるクラス」は主要クラスのみ。列挙型（Rarity・EquipSlot・EquipCategory 等）は各ファイルを参照
