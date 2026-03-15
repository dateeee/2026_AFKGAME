# クラス図（ドメインモデル）

> ゲーム仕様: [game_spec.md](docs/design/game_spec.md) / 技術仕様: [tech_spec.md](docs/tech/tech_spec.md)

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
        +getTowerClearRecord(towerId) TowerProgress
    }

    class Settings {
        +float potionThreshold  0.1-0.5
        +int battleLogCount  20/50/100/200
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

    class TowerProgress {
        +string towerId
        +bool cleared
        +int highestFloor
    }

    Player "1" --> "1" Settings
    Player "1" --> "1" Party
    Player "1" --> "*" TowerProgress
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
        ---
        Active専用
        +float multiplier  スキル倍率
        +int cooldown  CDターン数
        +SkillTargetType targetType
        +SkillPriority priority
        +float triggerCondition  HP%閾値等
        ---
        Passive専用
        +string effectStat  対象ステータス
        +float effectValue  効果値
        +string effectType  percent/flat
        ---
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

## 装備・アイテム

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
classDiagram
    direction TB

    class Equipment {
        +uuid id
        +string baseId
        +EquipSlot slot
        +Rarity rarity
        +int level  ドロップ元敵LV相当
        +int enhanceLevel  0-10
        +int atk  nullable
        +int def  nullable
        +int hp  nullable
        +int spd  nullable
        +float lifesteal  nullable 0.03-0.08
        +bool isTwoHanded
        +bool locked
        +calcEffectiveStats() Stats
        +calcEnhancedStats() Stats
        +calcSellPrice() int
        +canEnhance(forgeLevel) bool
        +enhance()
        +calcBaseValue() int
        +getRarityMultiplier() float
    }

    class EquipSlot {
        <<enumeration>>
        weapon  武器
        shield  盾 片手武器時のみ
        head  頭
        body  胴体
        arms  腕
        waist  腰
        legs  足
        ears  耳
        ring  指輪
    }

    class Rarity {
        <<enumeration>>
        common  白 x1.0 付与1-2
        uncommon  緑 x1.3 付与2
        rare  青 x1.6 付与2-3
        epic  紫 x2.0 付与3
        legendary  橙 x2.5 付与4全種
    }

    class InventoryItem {
        +string itemId
        +ItemCategory category
        +int quantity
        +add(amount)
        +remove(amount) bool
        +isFull() bool
    }

    class ItemCategory {
        <<enumeration>>
        potion  ポーション 上限99
        material  素材
        currency  換金アイテム
    }

    class DropEntry {
        +string itemId
        +float dropRate  0.0-1.0
        +int quantity
        +roll() bool
    }

    Equipment --> EquipSlot
    Equipment --> Rarity
    InventoryItem --> ItemCategory
```

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
        +int floors  総階数
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
        +TurnResult[3] turns  3ターン分
        +Stats[] updatedPartyStats
        +int[] updatedEnemyHp
        +bool leveledUp
        +int newLevel
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

    BattleState "1" --> "1..3" Enemy
    BattleState "1" --> "*" Buff
    BattleState "1" --> "*" Debuff
    BattleState "1" --> "*" StatusAilment
    BattleState ..> TurnResult : produces
    BattleState ..> TickResult : produces
    BattleState ..> OfflineSummary : produces
    StatusAilment --> AilmentType
```

## ショップ・施設・ボスラッシュ

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
classDiagram
    direction TB

    class Shop {
        +PotionStock[] permanentStock  常設商品
        +DailyItem[5] dailyItems  日替わり5枠
        +datetime resetAt  次回リセット時刻
        +buy(itemId, qty) bool
        +buyDaily(slotIndex) bool
        +canBuy(itemId, qty, playerGold) bool
        +getDailyLineup() DailyItem[]
        +checkReset()
        +refreshDaily(highestFloor)
        +getAvailableRarities(highestFloor) Rarity[]
    }

    class DailyItem {
        +int slotIndex  0-4
        +string itemId  装備マスターID
        +EquipSlot category  weapon/armor/accessory
        +Rarity rarity  common-rare
        +int price  固定価格テーブル
        +bool sold  購入済みか
    }

    class Facility {
        +FacilityType type
        +int level  0-10
        +getEffect() float
        +getEffectDescription() string
        +canUpgrade() bool
        +getUpgradeCost() FacilityCost
        +upgrade()
        +build()
    }

    class FacilityCost {
        +int gold
        +int enhanceStone
        +int magicCrystal
        +int rareOre
        +int ancientFragment
    }

    class FacilityType {
        <<enumeration>>
        tavern  酒場: スカウト LV→レアリティ上限
        forge  鍛冶屋: 強化上限/製作レアリティ/コスト倍率
        training_ground  訓練場: 控えEXP獲得率 5-50%
        warehouse  倉庫: 所持上限 70-300枠 (未建設50)
        market  市場: ゴールドボーナス +5-50%
    }

    class BossRushState {
        +bool isActive
        +int currentWave
        +int bestWave
        +int bestWaveHp  タイブレーク用
        +bigint accumulatedGold
        +bigint accumulatedExp
        +start()
        +retire() Rewards
        +getWaveEnemies(wave) Enemy[]
        +getWaveScaling(wave) float
        +checkMilestone(wave) MilestoneReward
        +calcWaveRewards(wave) Rewards
        +applyHpRecovery(party, wave)
        +resetCooldownsAndBuffs(party)
        +isHpRecoveryWave(wave) bool
    }

    class MilestoneReward {
        +int wave  5/10/15/20/25/30...
        +int enhanceStone
        +int magicCrystal
        +int rareOre
        +int ancientFragment
        +int legendaryEquipCount
        +bool claimed
    }

    Shop "1" --> "5" DailyItem
    Facility --> FacilityType
    Facility ..> FacilityCost
    BossRushState ..> MilestoneReward
```
