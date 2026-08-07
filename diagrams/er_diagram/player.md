# ER図 — 認証・プレイヤー・キャラクター

> 親: [er_diagram.md](../er_diagram.md)。**DBスキーマの正は** [tech_db/auth.md](../../docs/tech/basic/tech_db/auth.md)・[tech_db/player.md](../../docs/tech/basic/tech_db/player.md)（`CharacterEquipSlot` のみ [tech_db/item.md](../../docs/tech/basic/tech_db/item.md)）であり、本図は視覚化として属性を再掲する（食い違いは定義書側へ揃える）。データ構造は [tech_data.md](../../docs/tech/basic/tech_data.md)、認証は [tech_auth.md](../../docs/tech/detail/tech_auth.md)。

## 認証・アカウント系

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
erDiagram
    User |o--o| Player : "has (ゲストはUserなしの場合あり)"
    User ||--o{ RefreshToken : "has"
    User ||--o{ EmailVerificationToken : "has"

    User {
        string id PK "user_UUID / guest_UUID"
        string email UK "nullable (guest)"
        string password_hash "nullable (guest/OAuth)"
        string google_id UK "nullable"
        string display_name "表示名"
        boolean is_guest "default true"
        boolean email_verified "default false"
        datetime created_at
        datetime last_login_at
    }

    RefreshToken {
        int id PK "auto increment"
        string user_id FK "references User.id"
        string token_hash UK
        datetime expires_at "30日後"
        boolean revoked "default false"
        datetime created_at
    }

    EmailVerificationToken {
        int id PK "auto increment"
        string user_id FK "references User.id"
        string token_hash UK
        string purpose "verify_email / password_reset, default verify_email"
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
    Player ||--o{ PartyMember : "has"
    Player ||--o{ TowerClearRecord : "has"
    Player ||--o| PlayerSettings : "has"
    Player }o--o| Tower : "currentTower"

    Character ||--o{ CharacterEquipSlot : "has 9 slots"
    Character ||--o{ LearnedSkill : "has"
    Character ||--o{ ActiveSkillSlot : "has max 2"
    Character ||--o| PrestigeBonus : "has"
    Character ||--o{ PartyMember : "is assigned to"

    LearnedSkill }o--|| SkillMaster : "references"
    ActiveSkillSlot }o--|| SkillMaster : "references"

    Player {
        string id PK
        string user_id FK, UK "nullable, references User.id（1ユーザー1プレイヤー）"
        bigint gold "default 0, BIGINT(64bit)"
        string current_tower_id "nullable, マスター参照 Tower.id（DB外部キーなし）"
        int current_floor "nullable, 塔外時null"
        int target_floor "nullable, 目標階（塔外時null）"
        enum tower_mode "auto_repeat / stop_on_clear"
        float hp_threshold "撤退HP閾値 0.0-1.0"
        string current_enemy_id "nullable, 現在戦闘中の敵ID"
        int current_enemy_hp "nullable, 敵の残HP"
        bigint run_gold "塔内累積ゴールド"
        int highest_floor "最高到達階"
        datetime last_tick_at "最終tick処理時刻"
        datetime created_at
    }

    PlayerSettings {
        uuid id PK
        uuid player_id FK, UK "references Player.id（1プレイヤー1設定）"
        float potion_threshold "0.1-0.5, default 0.3"
        int battle_log_count "20/50/100, default 50"
        boolean toast_enabled "default true"
        enum auto_sell_rarity "null/common/uncommon, default null"
    }

    Character {
        uuid id PK
        uuid player_id FK "references Player.id"
        string name "キャラクター名"
        enum type "melee / magic / holy / agile"
        int level "1-9999"
        bigint exp "現在レベル内の累積EXP"
        int hp "現在HP"
        int max_hp "最大HP"
        int base_atk "基礎ATK"
        int base_def "基礎DEF"
        int base_spd "基礎SPD"
        int limit_break "0-5 限界突破回数"
        int skill_points "未使用SP"
        datetime created_at
        enum rarity "nullable, common-legendary (Phase 3〜・未実装)"
    }

    PrestigeBonus {
        uuid id PK "Phase 5〜 (未実装)"
        uuid character_id FK, UK "references Character.id（1キャラ1レコード）"
        int prestige_count "転生回数"
        int prestige_points "未使用転生ポイント"
        int bonus_hp "HP強化投資pt (上限50)"
        int bonus_atk "ATK強化投資pt (上限50)"
        int bonus_def "DEF強化投資pt (上限50)"
        int bonus_spd "SPD強化投資pt (上限50)"
        int bonus_exp "EXP獲得ボーナス投資pt (上限30)"
        int bonus_skill_damage "スキルダメージ投資pt (上限30)"
    }

    PartyMember {
        uuid id PK "Phase 3〜 (未実装)"
        uuid player_id FK, UK "references Player.id（slot_index / character_id と各々複合一意）"
        int slot_index UK "0-3 パーティ内位置"
        uuid character_id FK, UK "references Character.id"
    }

    TowerClearRecord {
        uuid id PK
        uuid player_id FK, UK "references Player.id（tower_id と複合一意）"
        string tower_id UK "マスター参照 Tower.id（DB外部キーなし）"
        boolean cleared "ボス討伐済みか"
        int highest_floor "最高到達階"
        datetime highest_floor_at "nullable, 最高到達階を更新した時刻（ランキングのタイブレーク用・Phase 5〜）"
    }

    CharacterEquipSlot {
        uuid character_id PK, FK "references Character.id"
        enum slot PK "weapon/shield/head/body/arms/waist/legs/ears/ring"
        uuid equipment_id FK "nullable, references Equipment.id"
    }

    LearnedSkill {
        uuid id PK "Phase 3〜 (未実装)"
        uuid character_id FK, UK "references Character.id（skill_id と複合一意）"
        string skill_id UK "マスター参照 SkillMaster.id（DB外部キーなし）"
        int cooldown_remaining "残CDターン数, default 0"
        datetime learned_at
    }

    ActiveSkillSlot {
        uuid id PK "Phase 3〜 (未実装)"
        uuid character_id FK, UK "references Character.id（slot_index と複合一意）"
        int slot_index UK "0-1 セット枠番号"
        string skill_id "マスター参照 SkillMaster.id（DB外部キーなし）"
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
