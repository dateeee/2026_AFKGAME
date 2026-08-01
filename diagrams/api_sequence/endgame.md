# APIシーケンス図 — ボスラッシュ・転生（Phase 5）

> 親: [api_sequence.md](../api_sequence.md)。API定義は [tech_api.md](../../docs/tech/tech_api.md)。

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
