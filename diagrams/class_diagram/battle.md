# クラス図 — ダンジョン・敵・戦闘状態

> 親: [class_diagram.md](../class_diagram.md)。仕様は [systems/battle.md](../../docs/design/systems/battle.md) / [systems/dungeon.md](../../docs/design/systems/dungeon.md)。

## ダンジョン・塔・敵

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
classDiagram
    direction TB

    class Dungeon {
        +string id
        +string name
        +Tower[] towers
        +UnlockCondition unlockCondition
        +isUnlocked(clearRecords) bool
        +getTowers() Tower[]
    }

    class Tower {
        +string id
        +string name
        +int floors  総階数 nullable(NULL=無限塔)
        +int recommendedLvMin
        +int recommendedLvMax
        +Modifier[] modifiers
        +UnlockCondition unlockCondition
        +isUnlocked(clearRecords) bool
        +getFloorEncounters(floor) EnemyEncounter[]
        +getBossFloor() int
        +getModifierEffects() ModifierEffect[]
        +hasModifier(type) bool
    }

    class UnlockCondition {
        +string type  tower_clear
        +string towerId  前提塔ID
        +isMet(clearRecords) bool
    }

    class EnemyEncounter {
        +int floor
        +Enemy enemy
        +int weight  出現重み
        +int enemyCountMin  1-3
        +int enemyCountMax  1-3
        +rollEnemyCount() int
    }

    class Enemy {
        +string id
        +string name
        +int level
        +Stats stats
        +Rewards rewards
        +DropEntry[] dropTable
        +bool isBoss
        +rollDrops() DropEntry[]
    }

    class Rewards {
        +int gold
        +int exp
        +applyMarketBonus(bonus) Rewards
        +applyEnvironmentBonus(bonus) Rewards
    }

    class Modifier {
        +string id
        +ModifierType type
        +string target  player/enemy
        +string stat  nullable
        +float value
        +string trigger  nullable
        +string description
        +apply(stats) Stats
    }

    class ModifierType {
        <<enumeration>>
        stat_modifier  入塔時+LVアップ時
        dot  各ターン行動前
        recovery  階クリア後
        restriction  ポーション判定時
        bonus  報酬計算時
    }

    Dungeon "1" --> "2..3" Tower
    Tower "1" --> "*" EnemyEncounter
    Tower "1" --> "*" Modifier
    Tower "1" --> "0..1" UnlockCondition
    EnemyEncounter --> Enemy
    Enemy "1" --> "1" Rewards
    Enemy "1" --> "*" DropEntry
    Modifier --> ModifierType

    note for Dungeon "イベントダンジョン（試練の迷宮/宝物庫/修練場）も\nDungeonクラスで表現。難易度（初級/中級/上級）は\nModifier（bonus型: 報酬倍率×1/2/4）で実装"
```

## 戦闘状態

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
classDiagram
    direction TB

    class BattleState {
        +Enemy[1..3] enemies
        +int turn  現在ターン
        +int towerGold  塔内累積ゴールド
        +int towerExp  塔内累積EXP
        +LootEntry[] towerLoot  塔内累積ドロップ
        +Buff[] activeBuffs
        +Debuff[] activeDebuffs
        +StatusAilment[] ailments
        +processTurn(party) TurnResult
        +processFullTick(party) TickResult
        +getSPDOrder(party, enemies) Character[]
        +calcDamage(attacker, target, skill) int
        +calcCritical(damage, critRate) int
        +applyMinDamage(damage, isPlayerAttack) int
        +applyLifesteal(attacker, damage)
        +applyDOT(character, envModifiers)
        +applyBuff(caster, skill, targets)
        +applyDebuff(caster, skill, targets)
        +selectTarget(actor, actionType, allies, enemies) Character
        +checkFloorClear() bool
        +checkWipe(party) bool
        +checkRetreat(party, conditions) bool
        +decrementCooldowns(allChars)
        +decrementBuffDurations()
        +decrementAilmentDurations()
        +grantRewards(enemy, marketBonus, envBonus) Rewards
    }

    class Buff {
        +string skillId  付与スキルID
        +string casterId  付与者キャラID
        +string stat  対象ステータス
        +float value  効果値
        +int remainingTurns  残りターン
        +isExpired() bool
        +isOwnBuff(characterId) bool
    }

    class Debuff {
        +string skillId
        +string casterId
        +string stat
        +float value  負の値
        +int remainingTurns
        +isExpired() bool
    }

    class StatusAilment {
        +AilmentType type
        +int remainingTurns
        +float applyRate  付与率
        +isExpired() bool
        +rollParalysis() bool  30%判定
    }

    class AilmentType {
        <<enumeration>>
        poison  毒: maxHP5%/ターン DOT
        stun  スタン: 1T行動不能
        paralysis  麻痺: 30%行動不能 2-3T
        silence  沈黙: スキル不可 2-3T
    }

    class TurnResult {
        +LogEntry[] entries
        +bool floorCleared
        +bool partyWiped
        +Rewards earnedRewards
    }

    class TickResult {
        +LogEntry[][] battleLogs  tickごとのターンログ配列
        +int totalGold
        +int totalExp
        +int enemiesDefeated
        +int potionsUsed
        +int levelsGained
        +int floorsCleared
        +bool defeated
        +Equipment[] equipmentDrops
        +object[] equipmentAutoSold  自動売却履歴(name/rarity/gold)
        +accumulate(other TickResult)  複数tick集約用(自身に加算)
    }

    class OfflineSummary {
        +int elapsedSeconds  経過秒数
        +int processedTicks  処理tick数
        +string calcMethod  normal / fast
        +int totalGold  獲得ゴールド合計
        +int totalExp  獲得EXP合計
        +LootEntry[] totalLoot  ドロップ一覧
        +int potionsUsed  消費ポーション数
        +int enemiesDefeated  撃破敵数
        +int levelsGained  上昇レベル数
        +int floorsCleared  クリア階数
    }

    class LogEntry {
        +string type  attack/heal/defeat等
        +string actor  行動者
        +string target  対象 nullable
        +int damage  nullable
    }

    class LootEntry {
        +string itemId
        +int quantity
    }

    BattleState "1" --> "1..3" Enemy
    BattleState "1" --> "*" Buff
    BattleState "1" --> "*" Debuff
    BattleState "1" --> "*" StatusAilment
    BattleState ..> TurnResult : produces
    BattleState ..> TickResult : produces
    BattleState ..> OfflineSummary : produces
    StatusAilment --> AilmentType
```
