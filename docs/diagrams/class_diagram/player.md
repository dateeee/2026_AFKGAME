# クラス図 — プレイヤー・パーティ・キャラクター・スキル

> 親: [class_diagram.md](../class_diagram.md)。仕様は [systems/character.md](../../design/systems/character.md)。
> 本図はドメインの構造を表す。永続化スキーマの正は [tech_db/player.md](../../tech/basic/tech_db/player.md) であり、集約 `Party` は `party_members` テーブル（1メンバー1行）として持つ。

## プレイヤー・パーティ・キャラクター

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
classDiagram
    direction TB

    class Player {
        +uuid id
        +bigint gold
        +Tower currentTower
        +int currentFloor
        +int targetFloor
        +TowerMode towerMode
        +float hpThreshold
        +string currentEnemyId  nullable
        +int currentEnemyHp  nullable
        +bigint runGold  塔内累積ゴールド
        +int highestFloor  最高到達階
        +Settings settings
        +datetime lastTickAt
        +getParty() Party
        +getCharacters() Character[]
        +getInventory() InventoryItem[]
        +getEquipments() Equipment[]
        +getFacilities() Facility[]
        +selectTower(towerId, targetFloor, mode)
        +retire()
        +updateSettings(settings)
        +addGold(amount)
        +spendGold(amount) bool
        +getTowerClearRecord(towerId) TowerClearRecord
    }

    class Settings {
        +float potionThreshold  0.1-0.5
        +int battleLogCount  20/50/100
        +bool toastEnabled
        +Rarity autoSellRarity  nullable
    }

    class Party {
        +Character[1..4] members
        +addMember(character, slotIndex)
        +removeMember(slotIndex)
        +getBySlot(slotIndex) Character
        +isFull() bool
        +getAllAlive() Character[]
        +isWiped() bool
    }

    class Character {
        +uuid id
        +string name
        +CharacterType type
        +Rarity rarity
        +int level  1-9999
        +bigint exp
        +int limitBreak  0-5
        +Stats baseStats
        +SkillSet skills
        +PrestigeData prestige
        +Equipment[9] equipment
        +levelUp()
        +gainExp(amount) bool
        +calcExpToNext() int
        +calcFinalStats(buffs, debuffs, envMods) Stats
        +calcRarityMultiplier() float
        +equipItem(slot, equipment)
        +unequipItem(slot) Equipment
        +doLimitBreak(materialChar)
        +canPrestige() bool
        +doPrestige()
        +isAlive() bool
        +takeDamage(amount)
        +heal(amount)
    }

    class Stats {
        +int hp
        +int maxHp
        +int atk
        +int def
        +int spd
        +applyGrowth(type, level) Stats
        +applyLimitBreak(breakCount) Stats
        +applyPrestige(bonus) Stats
        +applyEquipment(equips) Stats
        +applyPassive(passives) Stats
        +applyBuffDebuffEnv(buff, debuff, env) Stats
    }

    class PrestigeData {
        +int prestigeCount
        +int prestigePoints  未使用pt
        +int bonusHp  投資pt (上限50)
        +int bonusAtk  投資pt (上限50)
        +int bonusDef  投資pt (上限50)
        +int bonusSpd  投資pt (上限50)
        +int bonusExp  投資pt (上限30)
        +int bonusSkillDamage  投資pt (上限30)
        +invest(stat, points)
        +reset() int  ゴールドコスト返却
        +getTotalInvested() int
        +getBonusPercent(stat) float
    }

    class TowerClearRecord {
        +string towerId
        +bool cleared
        +int highestFloor
        +datetime highestFloorAt  nullable ランキングのタイブレーク用
    }

    class CharacterType {
        <<enumeration>>
        melee  近接型
        magic  魔力型
        holy  神聖型
        agile  敏捷型
    }

    class TowerMode {
        <<enumeration>>
        auto_repeat  自動周回
        stop_on_clear  クリア後停止
    }

    Player "1" --> "0..1" Settings
    Player "1" --> "0..1" Party
    Player "1" --> "*" TowerClearRecord
    Player --> TowerMode
    Character --> CharacterType
    Party "1" --> "1..4" Character
    Character "1" --> "1" Stats : baseStats
    Character "1" --> "0..1" PrestigeData
```

## スキルシステム

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
classDiagram
    direction TB

    class SkillSet {
        +Skill[] learned
        +Skill[2] activeSlots
        +int skillPoints  未使用SP
        +learn(skill) bool
        +canLearn(skill) bool
        +setActive(slotIndex, skill)
        +clearActive(slotIndex)
        +reset() int  返却SP数
        +getResetCost(charLevel) int
        +getLearnedByTree(tree) Skill[]
        +getPassives() Skill[]
    }

    class Skill {
        +string id
        +string name
        +SkillTree tree  6系統
        +int tier  1-4段階
        +SkillType type  active/passive
        +int spCost  1/1/2/3
        +string prerequisiteId  nullable
        Active専用
        +float multiplier  スキル倍率
        +int cooldown  CDターン数
        +SkillTargetType targetType
        +SkillPriority priority
        +float triggerCondition  HP%閾値等
        Passive専用
        +string effectStat  対象ステータス
        +float effectValue  効果値
        +string effectType  percent/flat
        +isPrerequisiteMet(learned) bool
        +isActive() bool
        +isPassive() bool
    }

    class SkillTree {
        <<enumeration>>
        sword  剣術: 物理単体攻撃
        magic  魔法: 魔法攻撃 単体/範囲
        heal  回復: HP回復/蘇生
        buff  強化: バフ
        debuff  弱体: デバフ/状態異常
        survival  生存術: 耐久/防御
    }

    class SkillType {
        <<enumeration>>
        active  戦闘中自動発動
        passive  常時効果
    }

    class SkillTargetType {
        <<enumeration>>
        single_enemy  HP割合最大の敵
        all_enemies  全敵 x0.7
        random_enemy  ランダム1体
        single_ally  HP割合最低の味方
        all_allies  味方全体
        dead_ally  HP0の味方
    }

    class SkillPriority {
        <<enumeration>>
        revive  蘇生 最優先
        heal  回復
        buff  バフ
        debuff  デバフ
        attack  攻撃 最低優先
    }

    SkillSet "1" --> "*" Skill : learned
    SkillSet "1" --> "0..2" Skill : activeSlots
    Skill --> SkillTree
    Skill --> SkillType
    Skill --> SkillTargetType
    Skill --> SkillPriority
```
