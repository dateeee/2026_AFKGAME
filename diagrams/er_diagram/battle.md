# ER図 — 戦闘・ボスラッシュ・マスターデータ

> 親: [er_diagram.md](../er_diagram.md)。データ構造は [tech_data.md](../../docs/tech/tech_data.md)、数値は [master_data.md](../../docs/data/master_data.md)。

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

> **注**: このブロックのエンティティはDBテーブルではなく、コード内定義のマスターデータ（`backend/app/master_data/` 配下のdataclass）。FK表記は論理参照を示す（DBレベルのFK制約はない）。Dungeon・TowerModifier・recommended_lv等は将来のDB化を見据えた論理設計であり、現実装のTowerDataには未実装（Phase 3以降で追随）。

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
        int total_floors "総階数"
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
        int gold "撃破時ゴールド"
        int exp "撃破時EXP"
        boolean is_boss "ボスフラグ"
    }

    EnemyDrop {
        uuid id PK
        string enemy_id FK "references EnemyMaster.id"
        string item_id FK "references ItemMaster.id"
        float rate "ドロップ率 0.0-1.0"
        int quantity "ドロップ数（master_data §10.3 の階層ルールで決定）"
    }
```
