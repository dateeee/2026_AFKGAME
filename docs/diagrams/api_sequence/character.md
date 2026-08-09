# APIシーケンス図 — パーティ・スキル・限界突破（Phase 3）

> 親: [api_sequence.md](../api_sequence.md)。API定義は [tech_api.md](../../tech/basic/tech_api.md)。

## 6.5. パーティ編成フロー（Phase 3）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as Terasoluna(Spring MVC)
    participant DB as Database

    B->>API: PUT /api/party/edit<br/>{ memberIds: ["hero_001", "hero_003"] }

    API->>API: バリデーション:<br/>最大4人? ✓<br/>所持キャラ? ✓<br/>塔外にいる? ✓
    API->>DB: Party更新 (メンバー・並び順)

    Note over API: パーティ外キャラは控え枠。<br/>戦闘に参加せずEXPも獲得しない

    API-->>B: { status: "ok", party: [...] }

    B->>B: partyStore 更新<br/>次のtickから新編成で戦闘
```

- 編成ルール（最大4人・控え枠・入れ替えは塔外のみ）は [systems/character.md](../../design/systems/character.md) §2.7 が正

## 7. スキル習得・リセットフロー（Phase 3）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as Terasoluna(Spring MVC)
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
    participant API as Terasoluna(Spring MVC)
    participant DB as Database

    B->>API: POST /api/character/limit-break<br/>{<br/>  characterId: "hero_001",<br/>  materialCharacterId: "hero_002"<br/>}

    API->>API: バリデーション:<br/>同一キャラ名? ✓<br/>limitBreak < 5? (現在2) ✓<br/>素材キャラがパーティ外? ✓

    API->>DB: hero_001.limitBreak = 3<br/>(+5%→+10%→+15%)
    API->>DB: hero_002を削除<br/>(素材として消費)

    Note over API: ステータスボーナス:<br/>突破0: +0%<br/>突破1: +5%<br/>突破2: +10%<br/>突破3: +15% ← Now<br/>突破4: +20%<br/>突破5: +30%

    API-->>B: {<br/>  status: "ok",<br/>  limitBreak: 3,<br/>  bonusPercent: 15,<br/>  updatedStats: {...}<br/>}

    B->>B: キャラステータス更新表示
```
