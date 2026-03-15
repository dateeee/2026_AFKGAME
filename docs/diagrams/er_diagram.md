# ER図（エンティティ関連図）

> 技術仕様: [tech_spec.md](../tech/tech_spec.md) / 認証仕様: [tech_auth.md](../tech/tech_auth.md)

## 認証・アカウント系

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
erDiagram
    User ||--o| Player : "has"
    User ||--o{ RefreshToken : "has"
    User ||--o{ EmailVerificationToken : "has"

    User {
        uuid id PK
        string email UK "nullable (guest)"
        string password_hash "nullable (guest/OAuth)"
        string google_id UK "nullable"
        enum auth_type "guest / email / google"
        boolean email_verified "default false"
        datetime created_at
        datetime last_login_at
    }

    RefreshToken {
        uuid id PK
        uuid user_id FK "references User.id"
        string token_hash UK
        datetime expires_at "30日後"
        boolean revoked "default false"
        datetime created_at
    }

    EmailVerificationToken {
        uuid id PK
        uuid user_id FK "references User.id"
        string token_hash UK
        datetime expires_at "24時間後"
        boolean used "default false"
        datetime created_at
    }
```

## プレイヤー・キャラクター系

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
erDiagram
    Player ||--|{ Character : "owns"
    Player ||--o{ Party : "has"
    Player ||--o{ TowerClearRecord : "has"
    Player ||--o| PlayerSettings : "has"
    Player }o--o| Tower : "currentTower"

    Character ||--o{ CharacterEquipSlot : "has 9 slots"
    Character ||--o{ LearnedSkill : "has"
    Character ||--o{ ActiveSkillSlot : "has max 2"
    Character ||--o| PrestigeBonus : "has"
    Character }o--o{ Party : "belongs to"

    LearnedSkill }o--|| SkillMaster : "references"
    ActiveSkillSlot }o--|| SkillMaster : "references"

    Player {
        uuid id PK
        uuid user_id FK "references User.id"
        bigint gold "default 0, BIGINT(64bit)"
        string current_tower_id FK "nullable, references Tower.id"
        int current_floor "nullable, 塔外時null"
        int target_floor "目標階"
        enum tower_mode "auto_repeat / stop_on_clear"
        float hp_threshold "撤退HP閾値 0.0-1.0"
        datetime last_tick_at "最終tick処理時刻"
        datetime created_at
    }

    PlayerSettings {
        uuid id PK
        uuid player_id FK "references Player.id"
        float potion_threshold "0.1-0.5, default 0.3"
        int battle_log_count "20/50/100/200, default 50"
        boolean toast_enabled "default true"
        enum auto_sell_rarity "null/common/uncommon, default null"
    }

    Character {
        uuid id PK
        uuid player_id FK "references Player.id"
        string name "キャラクター名"
        enum type "melee / magic / holy / agile"
        enum rarity "common / uncommon / rare / epic / legendary"
        int level "1-9999"
        bigint exp "現在レベル内の累積EXP"
        int limit_break "0-5 限界突破回数"
        int hp "現在HP"
        int max_hp "最大HP"
        int base_atk "基礎ATK"
        int base_def "基礎DEF"
        int base_spd "基礎SPD"
        int skill_points "未使用SP"
        datetime created_at
    }

    PrestigeBonus {
        uuid id PK
        uuid character_id FK "references Character.id"
        int prestige_count "転生回数"
        int prestige_points "未使用転生ポイント"
        int bonus_hp "HP強化投資pt (上限50)"
        int bonus_atk "ATK強化投資pt (上限50)"
        int bonus_def "DEF強化投資pt (上限50)"
        int bonus_spd "SPD強化投資pt (上限50)"
        int bonus_exp "EXP獲得ボーナス投資pt (上限30)"
        int bonus_skill_damage "スキルダメージ投資pt (上限30)"
    }

    Party {
        uuid id PK
        uuid player_id FK "references Player.id"
        int slot_index "0-3 パーティ内位置"
        uuid character_id FK "references Character.id"
    }

    TowerClearRecord {
        uuid id PK
        uuid player_id FK "references Player.id"
        string tower_id FK "references Tower.id"
        boolean cleared "ボス討伐済みか"
        int highest_floor "最高到達階"
    }

    CharacterEquipSlot {
        uuid id PK
        uuid character_id FK "references Character.id"
        enum slot "weapon/shield/head/body/arms/waist/legs/ears/ring"
        uuid equipment_id FK "nullable, references Equipment.id"
    }

    LearnedSkill {
        uuid id PK
        uuid character_id FK "references Character.id"
        string skill_id FK "references SkillMaster.id"
        datetime learned_at
    }

    ActiveSkillSlot {
        uuid id PK
        uuid character_id FK "references Character.id"
        int slot_index "0-1 セット枠番号"
        string skill_id FK "references SkillMaster.id"
    }

    SkillMaster {
        string id PK "例: sword_1, heal_3"
        string name "スキル名"
        enum tree "sword/magic/heal/buff/debuff/survival"
        int tier "1-4 段階"
        enum type "active / passive"
        float multiplier "スキル倍率 (active攻撃用)"
        int cooldown "CDターン数 (active用)"
        int sp_cost "習得必要SP (1/1/2/3)"
        string prerequisite_id FK "nullable, 前提スキルID"
        string description "効果説明"
    }
```

## 装備・アイテム系

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
erDiagram
    Player ||--o{ Equipment : "owns"
    Player ||--o{ InventoryItem : "has"
    CharacterEquipSlot }o--o| Equipment : "equips"
    InventoryItem }o--|| ItemMaster : "references"

    Equipment {
        uuid id PK
        uuid player_id FK "references Player.id"
        string base_id "装備マスターID"
        enum slot "weapon/shield/head/body/arms/waist/legs/ears/ring"
        enum rarity "common/uncommon/rare/epic/legendary"
        int level "装備レベル (= ドロップ元敵LV相当)"
        int enhance_level "強化段階 0-10"
        int stat_atk "nullable, ATK値"
        int stat_def "nullable, DEF値"
        int stat_hp "nullable, HP値"
        int stat_spd "nullable, SPD値"
        float lifesteal "nullable, HP吸収率 0.03-0.08"
        boolean is_two_handed "両手武器フラグ"
        boolean locked "売却ロック"
        datetime acquired_at
    }

    InventoryItem {
        uuid id PK
        uuid player_id FK "references Player.id"
        string item_id FK "references ItemMaster.id"
        int quantity "所持数"
    }

    ItemMaster {
        string id PK "例: hp_potion, enhance_stone"
        string name "アイテム名"
        enum category "potion / material / currency"
        int stack_limit "所持上限 (potion:99, material:9999)"
        int buy_price "nullable, ショップ購入価格"
        int sell_price "nullable, 売却価格"
        float heal_rate "nullable, 回復割合 (potion用)"
        string description "アイテム説明"
    }
```

## ショップ・施設系

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
erDiagram
    Player ||--o| ShopDailyState : "has"
    Player ||--o{ Facility : "has max 5"

    ShopDailyState ||--o{ ShopDailySlot : "has 5 slots"

    ShopDailyState {
        uuid id PK
        uuid player_id FK "references Player.id"
        datetime reset_at "次回リセット時刻 (00:00 UTC)"
    }

    ShopDailySlot {
        uuid id PK
        uuid shop_daily_state_id FK "references ShopDailyState.id"
        int slot_index "0-4 枠番号"
        string item_id "装備マスターID"
        enum category "weapon / armor / accessory"
        enum rarity "common / uncommon / rare"
        int price "購入価格"
        boolean sold "購入済みフラグ"
    }

    Facility {
        uuid id PK
        uuid player_id FK "references Player.id"
        enum facility_type "tavern/forge/training_ground/warehouse/market"
        int level "0-10 (0=未建設)"
    }
```

## 戦闘・ボスラッシュ系

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
erDiagram
    Player ||--o{ BattleLog : "has max 100"
    Player ||--o| BossRushState : "has"
    BossRushState ||--o{ BossRushMilestone : "tracks"

    BattleLog {
        uuid id PK
        uuid player_id FK "references Player.id"
        int tick_number "tick通番"
        datetime timestamp "処理時刻"
        json entries "ターンごとの行動ログ配列"
    }

    BossRushState {
        uuid id PK
        uuid player_id FK "references Player.id"
        boolean is_active "ボスラッシュ中か"
        int current_wave "現在ウェーブ"
        bigint accumulated_gold "累積獲得ゴールド"
        bigint accumulated_exp "累積獲得EXP"
        int best_wave "自己ベスト到達ウェーブ"
        int best_wave_hp "ベスト時の残HP合計 (タイブレーク用)"
    }

    BossRushMilestone {
        uuid id PK
        uuid boss_rush_state_id FK "references BossRushState.id"
        int wave "到達ウェーブ (5,10,15,...)"
        boolean claimed "報酬受取済みか"
        datetime claimed_at "nullable"
    }
```

## ダンジョン・塔・敵系（マスターデータ）

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
erDiagram
    Dungeon ||--|{ Tower : "contains"
    Tower ||--|{ FloorEncounter : "defines encounters"
    Tower ||--o{ TowerModifier : "has effects"
    FloorEncounter }o--|| EnemyMaster : "references"
    EnemyMaster ||--o{ EnemyDrop : "drops"
    EnemyDrop }o--|| ItemMaster : "references"

    Dungeon {
        string id PK "例: dungeon_001"
        string name "ダンジョン名"
        int sort_order "表示順"
        string unlock_tower_id FK "nullable, 解放条件の塔ID"
        string description "ダンジョン説明"
    }

    Tower {
        string id PK "例: goblin_tower"
        string dungeon_id FK "references Dungeon.id"
        string name "塔名"
        int floors "総階数"
        int recommended_lv_min "推奨LV下限"
        int recommended_lv_max "推奨LV上限"
        string unlock_tower_id FK "nullable, 前提塔ID"
    }

    TowerModifier {
        uuid id PK
        string tower_id FK "references Tower.id"
        string modifier_id "効果ID"
        enum type "stat_modifier/dot/recovery/restriction/bonus"
        string target "player / enemy"
        string stat "nullable, 対象ステータス"
        float value "効果値"
        string trigger "nullable, 発動タイミング"
        string description "効果説明テキスト"
    }

    FloorEncounter {
        uuid id PK
        string tower_id FK "references Tower.id"
        int floor_number "階層番号"
        string enemy_id FK "references EnemyMaster.id"
        int weight "出現重み (相対確率)"
        int enemy_count_min "出現数下限 (1-3)"
        int enemy_count_max "出現数上限 (1-3)"
    }

    EnemyMaster {
        string id PK "例: goblin, slime"
        string name "敵名"
        int level "敵レベル"
        int hp "HP"
        int atk "ATK"
        int def "DEF"
        int spd "SPD"
        int reward_gold "撃破時ゴールド"
        int reward_exp "撃破時EXP"
        boolean is_boss "ボスフラグ"
    }

    EnemyDrop {
        uuid id PK
        string enemy_id FK "references EnemyMaster.id"
        string item_id FK "references ItemMaster.id"
        float drop_rate "ドロップ率 0.0-1.0"
        int quantity "ドロップ数"
    }
```
