# 戦闘ターン処理フロー図

> 戦闘仕様: [tech_battle_offline.md](../tech/tech_battle_offline.md) / [game_spec.md §2.2](../design/game_spec.md)

## 塔探索の全体フロー

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
flowchart TD
    Start([塔選択]) --> SetParams["目標階・モード・撤退条件を設定"]
    SetParams --> Enter["1Fから探索開始\n全スキルCD=0"]

    Enter --> Encounter["現在階のエンカウントプールから\n敵を重み付き抽選\n(ボス階は固定)"]
    Encounter --> EnemyCount["敵出現数を決定\n(1-3体, 階定義に従う)"]
    EnemyCount --> BattleStart["戦闘開始"]

    subgraph 戦闘ループ["1階の戦闘 (複数ターン)"]
        BattleStart --> Turn["1ターン処理\n(詳細は下図)"]
        Turn --> FloorDone{"階の敵が\n全滅した?"}
        FloorDone -->|No| WipeCheck1{"味方全員HP=0?"}
        WipeCheck1 -->|No| Turn
        WipeCheck1 -->|Yes| Wipe
    end

    FloorDone -->|Yes| FloorClear["階クリア!\n報酬付与 (Gold+EXP+Drop)"]

    FloorClear --> EnvRecovery{"環境効果:\n階クリア回復?"}
    EnvRecovery -->|Yes| HealEnv["HP += floor(maxHP x 回復率)"]
    EnvRecovery -->|No| CheckHP

    HealEnv --> CheckHP{"HP閾値\n撤退チェック"}
    CheckHP -->|閾値以下| Retreat["撤退\n(報酬確定取得)"]
    CheckHP -->|閾値超| GoalCheck{"目標階に\n到達した?"}

    GoalCheck -->|No| NextFloor["次の階へ\n(CDは継続)"]
    GoalCheck -->|Yes| ModeCheck{"進行モード?"}

    ModeCheck -->|自動周回| Restart["1Fから再突入\n(HP持ち越し, CDリセットなし)"]
    ModeCheck -->|クリア後停止| Stop["戦闘停止\n(報酬確定取得)"]

    NextFloor --> Encounter
    Restart --> Encounter

    Wipe["全滅!\n強制撤退 (モード問わず)"]
    Wipe --> Penalty["ペナルティ:\n- 蓄積EXPの50%ロスト\n- 塔内取得ゴールド全ロスト\n- 塔内取得アイテム全ロスト"]
    Penalty --> End([探索終了])
    Retreat --> End
    Stop --> End
```

## 1ターンの処理フロー

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
flowchart TD
    Start([ターン開始]) --> Sort["全キャラ(味方+敵)を\nSPD降順でソート\n同SPD: キャラID順"]
    Sort --> NextChar["次のキャラクターを取得"]

    NextChar --> DeadCheck{HP = 0?}
    DeadCheck -->|Yes| SkipChar["スキップ"]
    DeadCheck -->|No| ParaCheck{麻痺状態?}

    ParaCheck -->|Yes| ParaRoll{"30%判定:\n行動不能?"}
    ParaRoll -->|行動不能| SkipChar
    ParaRoll -->|行動可能| StunCheck
    ParaCheck -->|No| StunCheck

    StunCheck{スタン状態?}
    StunCheck -->|Yes| SkipChar
    StunCheck -->|No| PoisonDOT

    subgraph DOT["毒DOT処理 (行動前)"]
        PoisonDOT{"毒状態?"}
        PoisonDOT -->|Yes| ApplyPoison["ダメージ =\nfloor(maxHP x 5%)\n最低1"]
        PoisonDOT -->|No| EnvDOT
        ApplyPoison --> EnvDOT

        EnvDOT{"環境毒霧?"}
        EnvDOT -->|Yes| ApplyEnvDOT["ダメージ =\nfloor(maxHP x 2%)\n(スキル毒と重複)"]
        EnvDOT -->|No| DOTDeath
        ApplyEnvDOT --> DOTDeath

        DOTDeath{DOTで\nHP = 0?}
    end

    DOTDeath -->|Yes| SkipChar
    DOTDeath -->|No| Regen

    Regen["パッシブ: リジェネ適用\n(ターン開始時HP回復)"]
    Regen --> PotionCheck

    PotionCheck{"HP <= 閾値\n(30%/50%/70%)?\nかつポーション所持?"}
    PotionCheck -->|Yes| UsePotion["ポーション自動使用\n優先: HP < ハイ < エリクサー\n(低回復量から使用)"]
    PotionCheck -->|No| EnvRestrict
    UsePotion --> EnvRestrict

    EnvRestrict{"環境制限?\n(no_potion /\npotion_half)"}
    EnvRestrict -->|no_potion| SkillEntry["ポーション使用不可"]
    EnvRestrict -->|potion_half| HalfPotion["回復量 x 0.5"]
    EnvRestrict -->|制限なし| SkillEntry
    HalfPotion --> SkillEntry

    subgraph SkillJudge["アクティブスキル発動判定"]
        SkillEntry["スキル判定開始"]
        SkillEntry --> SilenceCheck{"沈黙状態?"}
        SilenceCheck -->|Yes| NormalAtk["通常攻撃"]
        SilenceCheck -->|No| CheckSlots

        CheckSlots["セット枠2つをチェック\n優先度:\n蘇生 > 回復 > バフ/デバフ > 攻撃\n同優先度: セット枠1を優先"]

        CheckSlots --> ReviveCheck{"味方にHP=0 &\n蘇生スキルCD完了?"}
        ReviveCheck -->|Yes| UseRevive["蘇生スキル発動\nHP30%で復活\nCD=8T"]
        ReviveCheck -->|No| HealCheck

        HealCheck{"味方HP <= 40% &\n回復スキルCD完了?"}
        HealCheck -->|Yes| UseHeal["回復スキル発動"]
        HealCheck -->|No| BuffDebuffCheck

        BuffDebuffCheck{"バフ/デバフ\nスキルCD完了?"}
        BuffDebuffCheck -->|Yes| UseBuffDebuff["バフ/デバフ発動"]
        BuffDebuffCheck -->|No| AtkSkillCheck

        AtkSkillCheck{"攻撃スキル\nCD完了?"}
        AtkSkillCheck -->|Yes| UseAtkSkill["攻撃スキル発動"]
        AtkSkillCheck -->|No| NormalAtk
    end

    subgraph Target["ターゲット選択 (リアルタイム判定)"]
        UseRevive --> SelectDead["HP=0の味方"]
        UseHeal --> SelectLowHP["HP割合最低の味方"]
        UseBuffDebuff --> SelectBuff["味方全体 / 対象ルール"]
        UseAtkSkill --> SelectAtkTarget
        NormalAtk --> SelectAtkTarget

        SelectAtkTarget{"攻撃種別"}
        SelectAtkTarget -->|通常攻撃| RandTarget["ランダム1体"]
        SelectAtkTarget -->|単体スキル| MaxHPTarget["HP割合最大の敵"]
        SelectAtkTarget -->|範囲スキル| AllTarget["生存敵全体"]

        RandTarget --> TauntCheck{"挑発中の\n敵/味方あり?"}
        TauntCheck -->|Yes| TauntRoll["挑発率で\nターゲット振り分け\n(最大80%,\n複数時は按分)"]
        TauntCheck -->|No| DmgCalc
        TauntRoll --> DmgCalc
    end

    SelectDead --> ReviveApply["HP = maxHP x 30%"]
    SelectLowHP --> HealApply["HP回復量計算"]
    SelectBuff --> BuffApply["バフ/デバフ適用\n同一スキル: 上書き\n異スキル: 共存加算"]
    MaxHPTarget --> DmgCalc
    AllTarget --> AoECalc

    subgraph Damage["ダメージ計算"]
        DmgCalc["基本ダメージ =\nATK x (1 ± 0.1乱数) - DEF x 0.5\nスキル時:\nATK x スキル倍率 - DEF x 0.5"]

        DmgCalc --> CritCheck{"クリティカル?\n基本5% +\nパッシブ補正"}
        CritCheck -->|Yes| CritDmg["DEF減算後 x 1.5"]
        CritCheck -->|No| MinDmg

        CritDmg --> MinDmg{"最低ダメージ\n保証"}
        MinDmg -->|味方→敵| Min1["max(dmg, 1)\n詰み防止"]
        MinDmg -->|敵→味方| Min0["max(dmg, 0)\n高DEF有効"]

        Min1 --> PassiveDmgReduce
        Min0 --> PassiveDmgReduce

        PassiveDmgReduce["パッシブ:\n被ダメ軽減適用"]
        PassiveDmgReduce --> Lifesteal

        Lifesteal{"HP吸収装備?\n(3-8%)"}
        Lifesteal -->|Yes| ApplyLifesteal["HP += floor(dmg x 吸収率)"]
        Lifesteal -->|No| Counter
        ApplyLifesteal --> Counter

        Counter{"被攻撃側に\n反撃パッシブ?"}
        Counter -->|Yes| CounterAtk["反撃ダメージ適用"]
        Counter -->|No| DefeatCheck
        CounterAtk --> DefeatCheck
    end

    AoECalc["全体ダメージ =\n単体ダメージ x 0.7\n(各敵に適用)"] --> DefeatCheck

    ReviveApply --> NextCharCheck
    HealApply --> NextCharCheck
    BuffApply --> NextCharCheck

    DefeatCheck{"対象\nHP = 0?"}
    DefeatCheck -->|Yes| GrantReward["報酬付与:\nGold (市場ボーナス込み)\n+ EXP\n+ ドロップ抽選"]
    DefeatCheck -->|No| NextCharCheck

    GrantReward --> AllEnemyDead{"敵全滅?"}
    AllEnemyDead -->|Yes| SkipRemaining["残りキャラの\n行動スキップ"]
    AllEnemyDead -->|No| NextCharCheck

    SkipChar --> MoreChars
    NextCharCheck["次のキャラへ"] --> MoreChars
    SkipRemaining --> TurnEnd

    MoreChars{"未行動キャラ\nあり?"}
    MoreChars -->|Yes| NextChar
    MoreChars -->|No| TurnEnd

    subgraph TurnEndProc["ターン終了処理"]
        TurnEnd["1. 全スキルCDカウンター -1\n2. バフ/デバフ残ターン -1\n   (0になったら解除)\n3. 状態異常残ターン -1\n   (0になったら解除)"]
    end

    TurnEnd --> End([ターン終了])
```

## tick処理フロー

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
flowchart TD
    API["POST /api/battle/tick\n受信"] --> CalcTicks["未処理tick数 =\n(現在時刻 - lastTickAt) / 60秒"]

    CalcTicks --> HasTicks{"未処理tick\nあり?"}
    HasTicks -->|No| Return["現在の状態を\nそのまま返却"]
    HasTicks -->|Yes| TickLoop

    subgraph TickLoop["tick処理ループ"]
        ProcessTick["1 tickを処理"]
        ProcessTick --> T1["ターン1 処理\n(20秒分)"]
        T1 --> T1Result{"階クリア or\n全滅 or\n撤退?"}
        T1Result -->|No| T2["ターン2 処理\n(20秒分)"]
        T1Result -->|Yes| TickEnd

        T2 --> T2Result{"階クリア or\n全滅 or\n撤退?"}
        T2Result -->|No| T3["ターン3 処理\n(20秒分)"]
        T2Result -->|Yes| TickEnd

        T3 --> TickEnd["tick終了"]
    end

    TickEnd --> LevelUp{"レベルアップ\n判定"}
    LevelUp -->|Yes| ApplyLevelUp["ステータス上昇\n(タイプ成長率)\nSP +1"]
    LevelUp -->|No| LogSave

    ApplyLevelUp --> LogSave["戦闘ログ保存\n(DB上限100件,\n古い分自動パージ)"]

    LogSave --> MoreTicks{"未処理tick\n残り?"}
    MoreTicks -->|Yes| ProcessTick
    MoreTicks -->|No| UpdateDB["lastTickAt更新\nステータス保存"]

    UpdateDB --> Response["レスポンス返却:\n- battleLogs (上限50件)\n- updatedState\n- offlineSummary (復帰時)"]
```

## オフライン計算フロー

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
flowchart TD
    Start([復帰時 tick API呼出]) --> CalcPending["未処理tick数 =\n(現在時刻 - lastTickAt) / 60秒\n上限: 24時間 = 1440 tick"]

    CalcPending --> ThresholdCheck{"未処理tick数 <= 100?"}

    ThresholdCheck -->|Yes| NormalCalc
    ThresholdCheck -->|No| FastCalc

    subgraph NormalCalc["正規シミュレーション (1-100 tick)"]
        NormalStart["1 tickずつ\n戦闘処理を実行"]
        NormalStart --> NormalLoop["各tickで3ターン分の\n完全な戦闘シミュレーション"]
        NormalLoop --> NormalLog["個別の戦闘ログを生成\n(DB保存, 上限100件)"]
    end

    subgraph FastCalc["簡略計算 (101+ tick)"]
        FastStart["パーティステータスで\n1周回を分析"]
        FastStart --> ExpectedDmg["確定的に期待値算出:\n- パーティ合計攻撃力\n  (スキル・パッシブ込み)\n- 敵合計攻撃力\n  (ランダムターゲット均等分散)"]
        ExpectedDmg --> WipeJudge{"パーティ合計HP >\n期待被ダメ合計?\n(ポーション・回復込み)"}
        WipeJudge -->|No| FastWipe["全滅扱い → 計算終了"]
        WipeJudge -->|Yes| CalcCycle["1周回のtick数と\n期待報酬を算出"]
        CalcCycle --> CalcLvUp["次のLVアップまでの\n必要EXP → 必要周回数"]
        CalcLvUp --> BulkAdd["消化可能tick数 =\nmin(残りtick, LVアップまでtick)\n報酬を一括加算"]
        BulkAdd --> LvUpCheck{"LVアップ\n発生?"}
        LvUpCheck -->|Yes| Recalc["ステータス再計算\n(成長率適用)\nSP +1 (自動習得しない)\n目標階は固定"]
        LvUpCheck -->|No| MoreTicks{"残りtick > 0?"}
        Recalc --> MoreTicks
        MoreTicks -->|Yes| FastStart
        MoreTicks -->|No| FastEnd["サマリーのみ生成\n(個別ログなし)"]
    end

    NormalLog --> TowerIdle
    FastEnd --> TowerIdle
    FastWipe --> TowerIdle

    TowerIdle{"塔外待機中の\ntickあり?"}
    TowerIdle -->|Yes| NaturalHeal["HP自然回復:\nHP += (maxHP x 0.02\n+ DEF x 0.5)\nx 待機tick数\n(上限: maxHP)"]
    TowerIdle -->|No| Result

    NaturalHeal --> Result["結果返却:\n- offlineSummary\n- updatedState\n→ フロントでモーダル表示"]
```

## ボスラッシュ ウェーブ処理フロー

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
flowchart TD
    Start([ボスラッシュ開始]) --> StopTower["通常塔探索を停止\n(同時進行不可)"]
    StopTower --> InitWave["Wave 1 開始"]

    InitWave --> WaveSetup["ウェーブ開始処理:\n1. スキルCD全リセット\n2. バフ/デバフ全クリア\n3. 状態異常全クリア"]

    WaveSetup --> HpRecovery{"5ウェーブ目\n(Wave 5, 10, 15...)?\n"}
    HpRecovery -->|Yes| Heal10["パーティ全員\nHP 10%回復"]
    HpRecovery -->|No| DetermineEnemy
    Heal10 --> DetermineEnemy

    DetermineEnemy{"ウェーブ帯"}
    DetermineEnemy -->|Wave 1-5| NormalEnemy["通常敵\n(各塔の中~強め)"]
    DetermineEnemy -->|Wave 6-10| BossEnemy["既存ボス順:\nゴブリンキング\n→ベヒーモス\n→キングハイドラ\n→ポイズンドレイク\n→バアル"]
    DetermineEnemy -->|Wave 11+| ScaledBoss["ボス強化版:\n全ステータス\n+10% × (Wave-10)"]

    NormalEnemy --> Battle["戦闘\n(通常のターン処理)"]
    BossEnemy --> Battle
    ScaledBoss --> Battle

    Battle --> WaveResult{"結果"}
    WaveResult -->|敵撃破| WaveReward["ウェーブ報酬:\nGold + EXP\n(通常塔の50%相当)"]
    WaveResult -->|全滅| Wipe

    WaveReward --> Milestone{"マイルストーン\nウェーブ?\n(5,10,15,20,25,30...)"}
    Milestone -->|Yes, 初回| MilestoneReward["マイルストーン報酬:\nW5: 強化石x10\nW10: 魔法の結晶x5\nW15: 希少鉱石x3\nW20: 古代の欠片x1\nW25: 古代の欠片x2\nW30: 古代x3+レジェx1\n..."]
    Milestone -->|No or 既取得| NextWave["次のウェーブへ"]
    MilestoneReward --> NextWave

    NextWave --> WaveSetup

    Wipe["全滅!\n累積報酬は没収なし(確定取得)"]
    Wipe --> UpdateBest{"最高記録\n更新?"}
    UpdateBest -->|Yes| SaveBest["ベスト更新:\nbestWave = currentWave\nbestWaveHp = 残HP合計"]
    UpdateBest -->|No| End
    SaveBest --> End([ボスラッシュ終了])
```
