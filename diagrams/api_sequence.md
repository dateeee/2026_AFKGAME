# APIシーケンス図

> 技術仕様: [tech_spec.md §5,§7](docs/tech/tech_spec.md)

## 1. 初回アクセス（ゲスト作成 — Phase 1）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant LS as LocalStorage
    participant API as FastAPI
    participant DB as Database

    B->>LS: guest_token を確認
    LS-->>B: なし

    B->>API: POST /api/auth/guest
    API->>DB: UUID v4 生成
    API->>DB: Player作成 (gold=0, currentTower=null)
    API->>DB: Character作成 (勇者, melee, LV1)
    API->>DB: HPポーション x5 付与 (チュートリアル用)
    DB-->>API: OK
    API-->>B: { accessToken, refreshToken,<br/>user: { id, isGuest: true } }

    B->>LS: refresh_token を保存
    B->>B: access_token をメモリ(Pinia)に保持

    B->>API: GET /api/game/state<br/>Authorization: Bearer {token}
    API->>DB: Player + Characters + Inventory + Settings 取得
    DB-->>API: 全ゲーム状態
    API-->>B: ゲーム状態JSON (§1.1 フル構造)

    B->>B: Piniaストアに反映<br/>(gameStore, playerStore, battleStore)
    B->>B: Vue描画開始
    B->>B: チュートリアルヒント#1 表示<br/>「冒険者が自動で塔を探索します」
```

## 2. 再訪問（オフライン復帰）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant LS as LocalStorage
    participant API as FastAPI
    participant DB as Database

    B->>LS: refresh_token を確認
    LS-->>B: "refresh-token-value" (既存)

    B->>API: POST /api/auth/refresh<br/>{ refreshToken }
    API->>DB: トークン検証・ローテーション<br/>(旧トークン無効化)
    API->>DB: 新RefreshToken生成
    API-->>B: { accessToken, refreshToken }

    B->>LS: refresh_token を更新
    B->>B: accessToken をメモリ(Pinia)に保持

    B->>API: GET /api/game/state<br/>Authorization: Bearer {token}
    API->>DB: プレイヤーデータ取得
    DB-->>API: Player (lastTickAt = 6時間前)
    API-->>B: ゲーム状態JSON

    B->>API: POST /api/battle/tick<br/>Authorization: Bearer {token}

    Note over API: 経過時間を算出:<br/>6時間 = 360 tick<br/>360 > 100 → 簡略計算モード

    API->>API: 簡略計算実行:<br/>1. 1周回の期待報酬を算出<br/>2. LVアップ区間ごとに分割計算<br/>3. ステータス再計算を反復

    API->>DB: Player更新 (gold, exp, level)
    API->>DB: Character更新 (stats, sp)
    API->>DB: Inventory更新 (ポーション消費分)
    API->>DB: lastTickAt = 現在時刻

    API-->>B: TickResponse

    Note over B: TickResponse 内容:<br/>offlineSummary: {<br/>  elapsedSeconds: 21600,<br/>  processedTicks: 360,<br/>  calcMethod: "simplified",<br/>  totalGold: 12500,<br/>  totalExp: 45000,<br/>  enemiesDefeated: 720,<br/>  potionsUsed: 15,<br/>  levelsGained: 3,<br/>  floorsCleared: 8<br/>}

    B->>B: Piniaストア更新
    B->>B: OfflineRewardModal 表示<br/>(経過6時間, +12,500G, +45,000EXP...)
    B->>B: モーダル閉じ → 通常画面
```

## 3. オンライン中（ポーリングループ）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database

    Note over B: usePolling.ts:<br/>setInterval(60秒)

    loop 60秒ごとのポーリング
        B->>API: POST /api/battle/tick<br/>Authorization: Bearer {token}

        API->>DB: Player, Characters, Equipment 取得
        API->>API: 1 tick処理 (3ターン分)

        Note over API: ターン1: 勇者→ゴブリン 12dmg<br/>ターン2: ゴブリン→勇者 3dmg<br/>ターン3: 勇者→ゴブリン 14dmg (撃破!)

        API->>DB: 報酬付与 (gold+8, exp+18)
        API->>DB: BattleLog保存 (上限100件パージ)
        API->>DB: 階層進行 (3F→4F)
        API->>DB: lastTickAt更新

        API-->>B: TickResponse:<br/>battleLogs, updatedState

        B->>B: battleStore 更新
        B->>B: BattleLog.vue 自動スクロール

        opt レベルアップ発生
            B->>B: トースト通知<br/>「LV 5 → LV 6!」(3秒)
            B->>B: CharacterStatus.vue 更新
        end

        opt 階クリア
            B->>B: ログ内通知<br/>「4Fへ進む...」
        end

        opt ボス撃破 (目標階到達)
            B->>B: ボス撃破モーダル表示<br/>(報酬・次の塔解放)
        end

        opt 全滅
            B->>B: 全滅結果モーダル<br/>(ペナルティ詳細)
        end
    end
```

## 4. 塔選択フロー

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database

    B->>API: GET /api/tower/list
    API->>DB: TowerClearRecord取得
    DB-->>API: 塔別クリア状況
    API-->>B: [{<br/>  id, name, dungeonName, totalFloors,<br/>  unlockTowerId, unlocked, cleared, highestFloor<br/>}, ...]

    B->>API: POST /api/tower/select<br/>{<br/>  towerId: "forest_tower",<br/>  targetFloor: 15,<br/>  mode: "auto_repeat"<br/>}

    API->>DB: TowerClearRecord確認<br/>(前提塔クリア済み?)
    API->>API: バリデーション:<br/>塔解放済み? ✓<br/>targetFloor <= highestFloor? ✓

    Note over API: ※実装は塔の総階数上限のみ検証。<br/>到達済み最高階上限は未確定仕様<br/>(open_specs参照)

    alt 未解放塔を選択
        API-->>B: 403 Tower is locked
    else 既に入塔中
        API-->>B: 400 Already in a tower
    else 検証OK
        API->>DB: Player更新:<br/>currentTower = forest_tower<br/>currentFloor = 1<br/>targetFloor = 15<br/>towerMode = auto_repeat

        API-->>B: { status: "ok", updatedState }

        B->>B: gameStore更新
        B->>B: TowerInfo.vue 再描画<br/>「森の塔 1F / 目標: 15F」

        Note over B,API: 次のtickから森の塔1Fで戦闘開始
    end

    opt 進行中にモード変更
        B->>API: PUT /api/tower/mode<br/>{ mode: "stop_on_clear" }
        API->>DB: towerMode更新
        API-->>B: OK
    end

    opt 出発設定でHP閾値変更
        B->>API: PUT /api/tower/retreat-conditions<br/>{ hpThreshold: 30 }
        API->>DB: retreatHpThreshold更新
        API-->>B: OK
    end

    opt リタイア
        B->>API: POST /api/tower/retire
        Note over API: 即時撤退<br/>獲得済み報酬は保持(ペナルティなし)
        API-->>B: { status: "ok" }
    end
```

## 5. ショップ購入フロー

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database

    B->>API: GET /api/shop/lineup
    API->>DB: 常設ラインナップ取得

    API-->>B: {<br/>  lineup: [<br/>    { itemId: "hp_potion", name: "HPポーション",<br/>      price: 25, healRatio: 0.3,<br/>      quantityOwned: 10, stackLimit: 99 },<br/>    ...<br/>  ]<br/>}

    Note over API: 日替わりショップ (daily / dailySlotIndex /<br/>nextResetAt) は Phase 2後半・未実装

    opt 日替わりリセット (Phase 2後半・未実装)
        API->>API: リセット時刻チェック<br/>(00:00 UTC超過なら更新)
        API->>API: 新ラインナップ生成<br/>(到達階層に応じたレアリティ)
        API->>DB: ShopDailySlot x5 更新
    end

    Note over B: === 常設商品の購入 ===

    B->>API: POST /api/shop/buy<br/>{ itemId: "hp_potion", quantity: 5 }
    API->>API: 残金チェック: 25G x 5 = 125G <= 1500G ✓<br/>所持上限チェック: 10 + 5 = 15 <= 99 ✓
    API->>DB: gold -= 125
    API->>DB: hp_potion += 5
    API-->>B: { status: "ok", gold: 1375, itemId: "hp_potion", quantity: 5 }

    Note over B: === 日替わり商品の購入 (Phase 2後半・未実装) ===

    B->>API: POST /api/shop/buy<br/>{ dailySlotIndex: 0 }
    API->>API: sold=false確認 ✓<br/>残金チェック: 500G <= 1375G ✓<br/>所持上限チェック ✓
    API->>DB: gold -= 500
    API->>DB: Equipment生成 (鉄の剣)
    API->>DB: slot[0].sold = true
    API-->>B: { status: "ok", gold: 875, equipment: {...} }

    B->>B: トースト「鉄の剣を購入しました」
```

## 6. 装備変更フロー

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database

    Note over B: === 装備一覧取得 ===

    B->>API: GET /api/equipment/list
    API->>DB: Equipment一覧取得
    API-->>B: EquipmentResponse[]

    Note over B: === ロック切替 ===

    B->>API: POST /api/equipment/lock<br/>{ equipmentId: "iron_sword_001" }
    API->>DB: Equipment.locked更新
    API-->>B: { locked }

    Note over B: === 装備する ===

    B->>API: POST /api/equipment/equip<br/>{<br/>  characterId: "hero_001",<br/>  equipmentId: "iron_sword_001",<br/>  slot: "weapon"<br/>}

    API->>API: バリデーション:<br/>装備の所有者確認 ✓<br/>スロット適合確認 ✓<br/>両手武器チェック:<br/>  両手武器→盾スロット自動解除

    API->>DB: CharacterEquipSlot更新<br/>(weapon = iron_sword_001)

    opt 両手武器装備時
        API->>DB: 盾スロットを null に更新
    end

    API-->>B: { status: "ok" }

    Note over B: === 売却 ===

    B->>API: POST /api/equipment/sell<br/>{ equipmentIds: ["old_dagger_001"] }
    API->>API: ロック確認: locked=false ✓<br/>装備中でないことを確認 ✓<br/>売却価格 = 5 x 1.0(コモン) x 5(LV) = 25G
    API->>DB: Equipment削除
    API->>DB: gold += 25
    API-->>B: { goldEarned: 25, itemsSold: 1 }

    Note over B: === アイテム売却 (Phase 4〜) ===

    B->>API: POST /api/item/sell<br/>{ itemId: "goblin_fang", quantity: 3 }
    API->>API: 換金アイテム確認 ✓<br/>所持数チェック: 5 >= 3 ✓<br/>売却価格 = 単価 x 3
    API->>DB: InventoryItem.quantity -= 3
    API->>DB: gold += 売却額
    API-->>B: { status: "ok", gold: 950, soldPrice: 50 }
```

## 7. スキル習得・リセットフロー（Phase 3）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database

    Note over B: === スキル習得 ===

    B->>API: POST /api/skill/learn<br/>{<br/>  characterId: "hero_001",<br/>  skillId: "sword_2"<br/>}

    API->>API: バリデーション:<br/>前提スキル(sword_1)習得済み? ✓<br/>SP残量 >= 必要SP(1)? ✓<br/>未習得スキル? ✓
    API->>DB: LearnedSkill追加 (sword_2)
    API->>DB: skillPoints -= 1
    API-->>B: { status: "ok", remainingSP: 4 }

    Note over B: === アクティブスキル枠セット ===

    B->>API: PUT /api/skill/set-active<br/>{<br/>  characterId: "hero_001",<br/>  activeSlots: ["sword_1", "sword_2"]<br/>}

    API->>API: 習得済みスキル? ✓<br/>アクティブスキル? ✓<br/>最大2枠? ✓
    API->>DB: ActiveSkillSlot更新 (枠0=sword_1, 枠1=sword_2)
    API-->>B: { status: "ok" }

    Note over B: === スキルリセット ===

    B->>API: POST /api/skill/reset<br/>{ characterId: "hero_001" }

    API->>API: リセットコスト = LV x 50G<br/>LV10 → 500G<br/>残金チェック: 500G <= gold ✓

    API->>DB: gold -= 500
    API->>DB: LearnedSkill全削除
    API->>DB: ActiveSkillSlot全削除
    API->>DB: skillPoints = (現LV - 1) に戻す

    API-->>B: {<br/>  status: "ok",<br/>  gold: 400,<br/>  returnedSP: 9<br/>}
```

## 8. 限界突破フロー（Phase 3）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database

    B->>API: POST /api/character/limit-break<br/>{<br/>  characterId: "hero_001",<br/>  materialCharacterId: "hero_002"<br/>}

    API->>API: バリデーション:<br/>同一キャラ名? ✓<br/>limitBreak < 5? (現在2) ✓<br/>素材キャラがパーティ外? ✓

    API->>DB: hero_001.limitBreak = 3<br/>(+5%→+10%→+15%)
    API->>DB: hero_002を削除<br/>(素材として消費)

    Note over API: ステータスボーナス:<br/>突破0: +0%<br/>突破1: +5%<br/>突破2: +10%<br/>突破3: +15% ← Now<br/>突破4: +20%<br/>突破5: +30%

    API-->>B: {<br/>  status: "ok",<br/>  limitBreak: 3,<br/>  bonusPercent: 15,<br/>  updatedStats: {...}<br/>}

    B->>B: キャラステータス更新表示
```

## 9. 施設建設・レベルアップ（Phase 4）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database

    Note over B: === 施設建設 (LV0→LV1) ===

    B->>API: POST /api/base/build<br/>{ facilityId: "tavern" }

    API->>API: コスト確認 (master_data参照)<br/>gold, 強化石, etc.<br/>未建設(LV=0)確認

    API->>DB: gold -= コスト
    API->>DB: 素材消費
    API->>DB: Facility作成 (tavern, level=1)
    API-->>B: { status: "ok", facility: {type: "tavern", level: 1} }

    Note over B: === 施設レベルアップ ===

    B->>API: POST /api/base/upgrade<br/>{ facilityId: "tavern" }

    API->>API: 現在LV=1, 上限LV=10 ✓<br/>LV2コスト確認

    API->>DB: gold -= コスト
    API->>DB: 素材消費
    API->>DB: tavern.level = 2
    API-->>B: { status: "ok", facility: {type: "tavern", level: 2} }

    Note over B: === 酒場スカウト ===

    B->>API: POST /api/base/scout

    API->>API: 酒場LV確認 → 排出可能レアリティ決定<br/>LV3 → コモン~アンコモン<br/>スカウト費用: 1,000G<br/>残金チェック

    API->>API: ガチャ抽選:<br/>タイプ4種 x レアリティ = プールから1体

    API->>DB: gold -= 1000
    API->>DB: Character作成 (新キャラ)

    alt 新規キャラ
        API-->>B: { newCharacter: {...} }
        B->>B: 新キャラ加入モーダル表示
    else 重複キャラ
        API-->>B: { duplicateCharacter: {...},<br/>  canLimitBreak: true }
        B->>B: 「限界突破に使用できます」
    end
```

## 10. 鍛冶屋操作フロー（Phase 4）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database

    Note over B: === 装備強化 ===

    B->>API: POST /api/forge/enhance<br/>{ equipmentId: "iron_sword_001" }

    API->>API: 鍛冶屋LV=3 → 強化上限+3<br/>現在+1 < +3 ✓<br/>コスト: 強化石 + gold<br/>(コスト倍率 x0.9)

    API->>DB: 素材消費 (強化石, gold)
    API->>DB: enhanceLevel = 2<br/>実効ステータス再計算:<br/>ATK = 元ATK + (2 x 基礎値10%)
    API-->>B: { status: "ok", enhanceLevel: 2, updatedStats }

    Note over B: === 装備製作 ===

    B->>API: POST /api/forge/craft<br/>{ rank: 3 }

    API->>API: 鍛冶屋LV=5 → ランク3(レア)製作可 ✓<br/>素材: 強化石x20 + 魔法の結晶x8 + 希少鉱石x2<br/>gold: 5,000G

    API->>API: ランダム装備生成:<br/>スロット: ランダム<br/>レアリティ: レア固定<br/>ステータス: 2-3種ランダム
    API->>DB: 素材消費
    API->>DB: Equipment作成
    API-->>B: { status: "ok", equipment: {slot: "body", rarity: "rare", ...} }

    Note over B: === 装備分解 ===

    B->>API: POST /api/forge/disassemble<br/>{ equipmentId: "old_armor_001" }

    API->>API: レアリティ確認 → 獲得素材算出<br/>レア: 強化石x3 + 魔法の結晶x1

    API->>DB: Equipment削除
    API->>DB: 素材追加
    API-->>B: { status: "ok",<br/>  materials: {enhance_stone: +3, magic_crystal: +1} }
```

## 11. ボスラッシュフロー（Phase 5）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database

    B->>API: POST /api/boss-rush/start

    API->>API: 通常塔探索を停止<br/>(同時進行不可)
    API->>DB: BossRushState作成<br/>(isActive=true, wave=1)
    API->>DB: Player.currentTower = null
    API-->>B: { status: "ok", bossRush: {isActive: true, wave: 1} }

    Note over B,API: 以降は通常の tick ポーリングで進行<br/>各tickでウェーブ戦闘を処理

    loop tickごとの進行
        B->>API: POST /api/battle/tick
        API->>API: ボスラッシュモードで戦闘処理<br/>Wave開始: CD/バフ/状態異常リセット<br/>5Waveごと: HP10%回復
        API->>DB: accumulatedGold/Exp更新
        API-->>B: battleLogs + bossRushState
    end

    alt リタイア
        B->>API: POST /api/boss-rush/retire
        API->>DB: 累積報酬をPlayerに反映
        API->>DB: isActive = false
        API-->>B: { rewards: {gold, exp}, bestWave }
    else 全滅
        Note over API: tick処理内で全滅検知
        API->>DB: 累積報酬をPlayerに反映 (没収なし)
        API->>DB: ベスト記録更新判定
        API->>DB: isActive = false
        API-->>B: { wipe: true, rewards: {...}, newBest: true/false }
    end

    Note over B: === ランキング確認 ===

    B->>API: GET /api/boss-rush/ranking
    API->>DB: 上位100件取得<br/>ORDER BY best_wave DESC, best_wave_hp DESC
    API-->>B: { ranking: [{rank, name, wave, hp}, ...],<br/>  myRank: 42 }
```

## 11.5. イベントダンジョンフロー（Phase 5）

> イベントダンジョン（試練の迷宮・宝物庫・修練場）は通常の塔と同じデータ構造で管理される。
> 難易度（初級/中級/上級）は `Modifier`（bonus型: 報酬倍率 ×1/2/4）で実装し、
> 塔選択API `/api/tower/select` で同一のフローを使用する。
> 専用APIエンドポイントは不要。

## 12. 転生フロー（Phase 5）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database

    Note over B: === 転生実行 ===

    B->>API: POST /api/prestige<br/>{ characterId: "hero_001" }

    API->>API: LV9999チェック ✓<br/>パーティ外にする必要なし<br/>(転生後もパーティ残留)

    API->>DB: Character更新:<br/>level = 1<br/>exp = 0<br/>skillPoints = 0<br/>※装備・限界突破はそのまま

    API->>DB: LearnedSkill全削除<br/>ActiveSkillSlot全削除<br/>(SPは全返還 = LV1なので0SP)

    API->>DB: PrestigeBonus更新:<br/>prestigeCount += 1<br/>prestigePoints += 10

    API-->>B: {<br/>  status: "ok",<br/>  character: {level: 1, ...},<br/>  prestige: {count: 1, points: 10}<br/>}

    Note over B: === ポイント投資 ===

    B->>API: PUT /api/prestige/invest<br/>{<br/>  characterId: "hero_001",<br/>  stat: "atk",<br/>  points: 5<br/>}

    API->>API: 残ポイント >= 5 ✓<br/>ATK投資上限50 >= (現在0 + 5) ✓

    API->>DB: bonus_atk += 5<br/>prestigePoints -= 5

    Note over API: ATK +5%<br/>(1ptあたり+1%)

    API-->>B: {<br/>  status: "ok",<br/>  prestige: {<br/>    points: 5,<br/>    bonusAtk: 5<br/>  }<br/>}

    Note over B: === ボーナスリセット ===

    B->>API: POST /api/prestige/reset<br/>{ characterId: "hero_001" }

    API->>API: リセットコスト確認<br/>(master_data §16参照)
    API->>DB: gold -= コスト
    API->>DB: 全bonus = 0<br/>prestigePoints = 投資済み全pt返還
    API-->>B: { status: "ok", returnedPoints: 10 }
```

## 13. 通信エラー時（リトライ）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI

    B->>API: POST /api/battle/tick
    API--xB: 500 Internal Server Error

    Note over B: 指数バックオフ開始

    Note over B: 1秒待機
    B->>API: POST /api/battle/tick (リトライ1)
    API--xB: 500 Error

    Note over B: 2秒待機
    B->>API: POST /api/battle/tick (リトライ2)
    API--xB: 500 Error

    Note over B: 4秒待機
    B->>API: POST /api/battle/tick (リトライ3)
    API--xB: Timeout

    B->>B: 「接続エラー」バナー表示<br/>最終取得データをそのまま表示<br/>ユーザー操作はエラー表示

    Note over B: 次のtickタイミング (60秒後)<br/>自動リトライ再開

    B->>API: POST /api/battle/tick
    API-->>B: 200 OK<br/>{ battleLogs, updatedState }

    B->>B: バナー消去<br/>最新状態を反映
```

## 14. 認証フロー概要（Phase 2〜）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as FastAPI
    participant DB as Database
    participant Google as Google OAuth

    Note over B,Google: === メールログイン ===

    B->>API: POST /api/auth/login<br/>{ email, password }
    API->>DB: Userをemail検索
    API->>API: bcrypt検証 (cost=12)
    API->>DB: RefreshToken生成・保存
    API-->>B: {<br/>  accessToken (30分有効),<br/>  refreshToken (30日有効)<br/>}

    Note over B,Google: === Googleログイン ===

    B->>API: POST /api/auth/google<br/>{ authorizationCode }
    API->>Google: 認可コード → トークン交換
    Google-->>API: { access_token, id_token }
    API->>Google: ユーザー情報取得
    Google-->>API: { email, google_id }
    API->>DB: google_idでUser検索 or 新規作成
    API->>DB: RefreshToken生成
    API-->>B: { accessToken, refreshToken }

    Note over B,Google: === ゲスト→本登録 ===

    B->>API: POST /api/auth/link-account<br/>{ email, password }<br/>Authorization: Bearer {guest_token}
    API->>DB: ゲストUserにemail/password紐づけ<br/>is_guest = false
    API->>DB: 確認メール送信
    API->>DB: RefreshToken生成
    API-->>B: { accessToken, refreshToken }
    B->>B: ゲスト→本登録バナー消去

    Note over B,Google: === トークンリフレッシュ ===

    B->>API: POST /api/auth/refresh<br/>{ refreshToken }
    API->>DB: トークン検証・ローテーション<br/>(旧トークン無効化)
    API->>DB: 新RefreshToken生成
    API-->>B: { newAccessToken, newRefreshToken }

    Note over B,Google: === パスワードリセット ===

    B->>API: POST /api/auth/password-reset/request<br/>{ email }
    API->>DB: Userをemail検索
    API->>API: リセットトークン生成
    API->>DB: リセットトークン保存
    API-->>B: { status: "ok", message: "リセットメール送信" }

    Note over B: ユーザーがメール内リンクをクリック

    B->>API: POST /api/auth/password-reset/confirm<br/>{ token, newPassword }
    API->>DB: トークン検証（有効期限・使用済みチェック）
    API->>API: bcryptハッシュ生成
    API->>DB: password_hash更新<br/>トークンを使用済みに
    API-->>B: { status: "ok" }
```
