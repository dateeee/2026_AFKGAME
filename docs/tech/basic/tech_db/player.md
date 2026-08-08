# テーブル定義 — プレイヤー・キャラクター

> 親: [tech_db.md](../tech_db.md)。命名規約・型マッピング・共通の列規約・外部キー動作は親が正であり、本書では繰り返さない。
> 視覚化は [er_diagram/player.md](../../../diagrams/er_diagram/player.md)「プレイヤー・キャラクター系」、パーティ・スキル操作の処理仕様は [tech_party.md](../../detail/tech_party.md)。認証・アカウント系は [auth.md](auth.md)。

---

## 1. `players`（Phase 1）

実装: `com.afkgame.domain.model.Player`

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `user_id` | `VARCHAR(50)` | 可 | — | FK → `users.id`、UNIQUE。**1ユーザー = 1プレイヤー**をDB側で保証する（query-then-create の重複で `.first()` の返却が不定になるため） |
| `gold` | `BIGINT` | 不可 | `0` | 所持ゴールド |
| `current_tower_id` | `VARCHAR(50)` | 可 | — | 塔マスターの ID。FKなし（親 §4-6）。塔外は NULL |
| `current_floor` | `INTEGER` | 可 | — | 塔外は NULL |
| `target_floor` | `INTEGER` | 可 | — | 目標階。塔外は NULL |
| `tower_mode` | `VARCHAR(20)` | 不可 | `auto_repeat` | `auto_repeat` / `stop_on_clear` |
| `hp_threshold` | `FLOAT` | 不可 | `0.3` | 撤退HP閾値（0.0〜1.0） |
| `current_enemy_id` | `VARCHAR(50)` | 可 | — | 交戦中の敵マスターID。非交戦時は NULL |
| `current_enemy_hp` | `INTEGER` | 可 | — | 敵の残HP。非交戦時は NULL |
| `run_gold` | `BIGINT` | 不可 | `0` | 塔内の累積ゴールド |
| `highest_floor` | `INTEGER` | 不可 | `0` | 全塔を通じた最高到達階 |
| `last_tick_at` | `DATETIME(tz)` | 不可 | 現在時刻 | 最終tick処理時刻。オフライン復帰の起点 |
| `created_at` | `DATETIME(tz)` | 不可 | 現在時刻 | — |

## 2. `player_settings`（Phase 1）

実装: `com.afkgame.domain.model.PlayerSettings`

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id`、UNIQUE（1プレイヤー1設定） |
| `potion_threshold` | `FLOAT` | 不可 | `0.3` | ポーション使用HP閾値（0.1〜0.5、0.1刻み。選択肢の正は [systems/ui.md](../../../design/systems/ui.md)「設定項目」） |
| `battle_log_count` | `INTEGER` | 不可 | `50` | 表示ログ件数（20 / 50 / 100） |
| `toast_enabled` | `BOOLEAN` | 不可 | `true` | — |
| `auto_sell_rarity` | `VARCHAR(20)` | 可 | — | `common` / `uncommon`。自動売却の対象レアリティ上限で、NULL は自動売却なし（選択肢の正は [systems/ui.md](../../../design/systems/ui.md)「設定項目」） |

## 3. `tower_clear_records`（Phase 1）

実装予定: `com.afkgame.domain.model.TowerClearRecord`

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id` |
| `tower_id` | `VARCHAR(50)` | 不可 | — | 塔マスターの ID。FKなし（親 §4-6） |
| `cleared` | `BOOLEAN` | 不可 | `false` | ボス討伐済みか |
| `highest_floor` | `INTEGER` | 不可 | `0` | その塔での最高到達階 |
| `highest_floor_at` | `DATETIME(tz)` | 可 | — | **Phase 5・未実装**。最高到達階を更新した時刻（ランキングのタイブレーク用） |

一意制約: `uq_tower_clear_records_player_tower` = (`player_id`, `tower_id`)

## 4. `characters`（Phase 1）

実装: `com.afkgame.domain.model.Character`

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id` |
| `name` | `VARCHAR(50)` | 不可 | — | キャラクター名 |
| `type` | `VARCHAR(20)` | 不可 | `melee` | `melee` / `magic` / `holy` / `agile` |
| `level` | `INTEGER` | 不可 | `1` | 上限は [master/character.md](../../../data/master/character.md) が正 |
| `exp` | `BIGINT` | 不可 | `0` | 現在レベル内の累積EXP |
| `hp` | `INTEGER` | 不可 | — | 現在HP |
| `max_hp` | `INTEGER` | 不可 | — | 装備・バフを含まない素の最大HP |
| `base_atk` | `INTEGER` | 不可 | — | 基礎ATK |
| `base_def` | `INTEGER` | 不可 | — | 基礎DEF |
| `base_spd` | `INTEGER` | 不可 | — | 基礎SPD |
| `limit_break` | `INTEGER` | 不可 | `0` | 限界突破回数 |
| `skill_points` | `INTEGER` | 不可 | `0` | 未使用SP |
| `created_at` | `DATETIME(tz)` | 不可 | 現在時刻 | — |
| `rarity` | `VARCHAR(20)` | 可 | — | **Phase 3・未実装**。`common` / `uncommon` / `rare` / `epic` / `legendary`。倍率は [master/character.md](../../../data/master/character.md) §7.2 が正 |
| `master_id` | `VARCHAR(50)` | 可 | — | **Phase 4・未実装**。マスターキャラのID（`hero_002` 等。[master/character.md](../../../data/master/character.md) §7.1・§7.3 が正）。FKなし。同一キャラの判定（重複・限界突破）は `name` ではなくこの列で行う。Phase 3 以前に作られた行は NULL とし、Phase 4 の実装時に名前から補完する |

## 5. `party_members`（Phase 3）

実装予定: `com.afkgame.domain.model.PartyMember`。編成の処理仕様は [tech_party.md](../../detail/tech_party.md) §1。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id` |
| `slot_index` | `INTEGER` | 不可 | — | パーティ内の位置（0〜3）。表示順のみに使い、行動順には使わない |
| `character_id` | `VARCHAR(36)` | 不可 | — | FK → `characters.id` |

一意制約: `uq_party_members_player_slot` = (`player_id`, `slot_index`) / `uq_party_members_player_character` = (`player_id`, `character_id`)（同一キャラの重複編成を防ぐ。サービス層の `422 PARTY_MEMBER_DUPLICATED` に対する二重の防御）

## 6. `learned_skills`（Phase 3）

実装予定: `com.afkgame.domain.model.LearnedSkill`。習得の処理仕様は [tech_party.md](../../detail/tech_party.md) §3。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `character_id` | `VARCHAR(36)` | 不可 | — | FK → `characters.id` |
| `skill_id` | `VARCHAR(50)` | 不可 | — | スキルマスターの ID。FKなし（親 §4-6） |
| `cooldown_remaining` | `INTEGER` | 不可 | `0` | 残クールダウンターン数。tick をまたいで保持する |
| `learned_at` | `DATETIME(tz)` | 不可 | 現在時刻 | — |

一意制約: `uq_learned_skills_character_skill` = (`character_id`, `skill_id`)（サービス層の `400 SKILL_ALREADY_LEARNED` に対する二重の防御）

## 7. `active_skill_slots`（Phase 3）

実装予定: `com.afkgame.domain.model.ActiveSkillSlot`。セットの処理仕様は [tech_party.md](../../detail/tech_party.md) §4。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `character_id` | `VARCHAR(36)` | 不可 | — | FK → `characters.id` |
| `slot_index` | `INTEGER` | 不可 | — | セット枠番号（0〜1） |
| `skill_id` | `VARCHAR(50)` | 不可 | — | スキルマスターの ID。FKなし（親 §4-6） |

一意制約: `uq_active_skill_slots_character_slot` = (`character_id`, `slot_index`)

## 8. `prestige_bonuses`（Phase 5・未実装）

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

## 9. インデックスと検索パターン

主キーと一意制約が張るインデックスのみを持ち、二次インデックスは持たない（方針は [tech_db.md](../tech_db.md) §6）。

| 検索パターン | 使うインデックス | 判断 |
|------------|---------------|------|
| 認証後に `user_id` からプレイヤーを引く | `players.user_id`（UNIQUE） | 充足 |
| プレイヤーの設定を引く | `player_settings.player_id`（UNIQUE） | 充足 |
| 塔ごとのクリア記録を引く | `uq_tower_clear_records_player_tower` | 充足（左端が `player_id`） |
| パーティ編成を `slot_index` 順に引く | `uq_party_members_player_slot` | 充足（左端が `player_id`） |
| キャラの習得スキルを引く | `uq_learned_skills_character_skill` | 充足（左端が `character_id`） |
| キャラのセット枠を引く | `uq_active_skill_slots_character_slot` | 充足（左端が `character_id`） |
| tick処理でプレイヤーの全キャラを引く | なし（`characters.player_id`） | 二次インデックスを張らない。行数が小さい間は全走査で足り、追加は [tech_db.md](../tech_db.md) §6-3 の再評価ラインで判断する |
| 深淵の塔ランキング上位100件を `highest_floor` 降順・`highest_floor_at` 昇順で引く | なし（`tower_id = 'abyss_tower'` で絞って全行走査 + ソート） | 全プレイヤー横断クエリ（もう1本は [battle.md](battle.md) §4 のボスラッシュランキング）。行数が利用者数に比例するため、(`tower_id`, `highest_floor`) の複合インデックス追加は [tech_db.md](../tech_db.md) §6-3 の再評価ラインで判断する |
