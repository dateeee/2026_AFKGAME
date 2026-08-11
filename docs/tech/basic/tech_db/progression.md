# テーブル定義 — 育成・編成（パーティ・スキル・転生）

> 親: [tech_db.md](../tech_db.md)。命名規約・型マッピング・共通の列規約・外部キー動作は親が正であり、本書では繰り返さない。
> 視覚化は [er_diagram/player.md](../../../diagrams/er_diagram/player.md)「プレイヤー・キャラクター系」、パーティ・スキル操作の処理仕様は [tech_party.md](../../detail/tech_party.md)。プレイヤー・キャラクター本体は [player.md](player.md)。
> 本書の4テーブルはいずれも `characters`（[player.md](player.md) §4）の子であり、`party_members` は `players`（同 §1）の子でもある。

---

## 1. `party_members`（Phase 3）

実装予定: `com.afkgame.domain.model.PartyMember`。編成の処理仕様は `tech_party.md` §1。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id` |
| `slot_index` | `INTEGER` | 不可 | — | パーティ内の位置（0〜3）。表示順のみに使い、行動順には使わない |
| `character_id` | `VARCHAR(36)` | 不可 | — | FK → `characters.id` |

一意制約: `uq_party_members_player_slot` = (`player_id`, `slot_index`) / `uq_party_members_player_character` = (`player_id`, `character_id`)（同一キャラの重複編成を防ぐ。サービス層の `422 PARTY_MEMBER_DUPLICATED` に対する二重の防御）

## 2. `learned_skills`（Phase 3）

実装予定: `com.afkgame.domain.model.LearnedSkill`。習得の処理仕様は `tech_party.md` §3。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `character_id` | `VARCHAR(36)` | 不可 | — | FK → `characters.id` |
| `skill_id` | `VARCHAR(50)` | 不可 | — | スキルマスターの ID。FKなし（親 §4-6） |
| `cooldown_remaining` | `INTEGER` | 不可 | `0` | 残クールダウンターン数。tick をまたいで保持する |
| `learned_at` | `DATETIME(tz)` | 不可 | 現在時刻 | — |

一意制約: `uq_learned_skills_character_skill` = (`character_id`, `skill_id`)（サービス層の `400 SKILL_ALREADY_LEARNED` に対する二重の防御）

## 3. `active_skill_slots`（Phase 3）

実装予定: `com.afkgame.domain.model.ActiveSkillSlot`。セットの処理仕様は `tech_party.md` §4。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `character_id` | `VARCHAR(36)` | 不可 | — | FK → `characters.id` |
| `slot_index` | `INTEGER` | 不可 | — | セット枠番号（0〜1） |
| `skill_id` | `VARCHAR(50)` | 不可 | — | スキルマスターの ID。FKなし（親 §4-6） |

一意制約: `uq_active_skill_slots_character_slot` = (`character_id`, `slot_index`)

## 4. `prestige_bonuses`（Phase 5・未実装）

実装予定: `com.afkgame.domain.model.PrestigeBonus`。投資上限の正は [master/endgame.md](../../../data/master/endgame.md) §16.1。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `character_id` | `VARCHAR(36)` | 不可 | — | FK → `characters.id`、UNIQUE（1キャラ1レコード） |
| `prestige_count` | `INTEGER` | 不可 | `0` | 転生回数 |
| `prestige_points` | `INTEGER` | 不可 | `0` | 未使用転生ポイント |
| `bonus_hp` | `INTEGER` | 不可 | `0` | HP強化への投資pt |
| `bonus_atk` | `INTEGER` | 不可 | `0` | ATK強化への投資pt |
| `bonus_def` | `INTEGER` | 不可 | `0` | DEF強化への投資pt |
| `bonus_spd` | `INTEGER` | 不可 | `0` | SPD強化への投資pt |
| `bonus_exp` | `INTEGER` | 不可 | `0` | EXP獲得ボーナスへの投資pt |
| `bonus_skill_damage` | `INTEGER` | 不可 | `0` | スキルダメージへの投資pt |

## 5. インデックスと検索パターン

主キーと一意制約が張るインデックスのみを持ち、二次インデックスは持たない（方針は `tech_db.md` §6）。

| 検索パターン | 使うインデックス | 判断 |
|------------|---------------|------|
| パーティ編成を `slot_index` 順に引く | `uq_party_members_player_slot` | 充足（左端が `player_id`） |
| キャラの習得スキルを引く | `uq_learned_skills_character_skill` | 充足（左端が `character_id`） |
| キャラのセット枠を引く | `uq_active_skill_slots_character_slot` | 充足（左端が `character_id`） |
