# AFK GAME — 進行状態と操作可否

> 技術仕様の索引は [tech_spec.md](../tech_spec.md)。API一覧は [tech_api.md](../basic/tech_api.md)、戦闘・撤退のゲーム仕様は [battle.md](../../design/systems/battle.md)、画面遷移は [screen_transition.md](../../diagrams/screen_transition.md)。
> 本書はプレイヤーの**進行状態**を状態機械として定義し、各状態で許可される操作とデータの不変条件を確定する。

---

## 1. 状態の定義

| 状態 | 判定条件 | 意味 |
|------|---------|------|
| `IDLE`（塔外待機） | `currentTowerId = null` | 塔に入っていない。HP自然回復のみ進行する |
| `EXPLORING`（探索中） | `currentTowerId ≠ null` かつ `currentEnemyId = null` | 入塔済み・エンカウント待ち |
| `IN_BATTLE`（戦闘中） | `currentTowerId ≠ null` かつ `currentEnemyId ≠ null` | 交戦中 |
| `BOSS_RUSH`（Phase 5〜） | `bossRush.active = true` | ボスラッシュ中。通常塔とは排他 |

- 状態は専用カラムを持たず、上表の条件から**導出**する（状態カラムと実データの二重管理を避ける）
- 「全滅」「目標階クリア」は状態ではなく**遷移イベント**。tick処理の中で発生し、同一tick内で次状態へ遷移する

### 1.1 不変条件

| 条件 | 内容 |
|------|------|
| 塔の対 | `currentTowerId` / `currentFloor` / `targetFloor` は同時にnull、または同時に非null |
| 敵の対 | `currentEnemyId` と `currentEnemyHp` は同時にnull、または同時に非null |
| 敵の従属 | `currentEnemyId ≠ null` ならば `currentTowerId ≠ null` |
| 階の範囲 | `1 ≤ currentFloor` かつ `targetFloor ≤ min(塔別 highestFloor + 1, 塔の総階数)`（`tech_api.md`「操作系」が正。深淵の塔は総階数を持たないため `highestFloor + 1` のみ） |
| 排他 | `bossRush.active = true` ならば `currentTowerId = null` |
| HP | 全キャラの `hp` は `0 ≤ hp ≤ effectiveMaxHp` |

- 不変条件違反はデータ不整合として ERRORログ + `500 INTERNAL_UNEXPECTED_ERROR` を返す（黙って補正しない）

## 2. 状態遷移

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> EXPLORING: tower/select
    EXPLORING --> IN_BATTLE: エンカウント（tick内）
    IN_BATTLE --> EXPLORING: 敵撃破（tick内）
    IN_BATTLE --> IDLE: 全滅
    EXPLORING --> IDLE: リタイア / HP閾値撤退・目標階クリア（stop_on_clear）
    EXPLORING --> EXPLORING: 目標階クリア・HP閾値撤退（auto_repeat）→ 1階から再開
    IDLE --> BOSS_RUSH: boss-rush/start
    BOSS_RUSH --> IDLE: 全滅 / リタイア
```

| 遷移 | 契機 | 後始末 |
|------|------|-------|
| 塔選択 | `POST /api/tower/select` | `currentFloor = 1`、敵情報をクリア、探索セッションを開始（§3） |
| 全滅 | パーティ全員HP=0 | 塔・敵情報をクリア。ペナルティ適用（§3）→ `IDLE`。自動周回でも再スタートしない |
| リタイア | `POST /api/tower/retire` | **即時に**塔・敵情報をクリア（戦闘中でも待たない。[tech_tower/control.md §11](tech_tower/control.md)）。獲得済み報酬は保持 |
| HP閾値撤退 | 階クリア後にHPが閾値未満（判定式は [tech_tower/progress.md §9](tech_tower/progress.md)） | セッションを確定してリセット。`auto_repeat` は1階から再開（塔に留まる）、`stop_on_clear` は `IDLE` へ（battle.md §撤退条件） |
| 目標階クリア（`stop_on_clear`） | `currentFloor > targetFloor` | 塔・敵情報をクリアし `IDLE` へ |
| 目標階クリア（`auto_repeat`） | 同上 | `currentFloor = 1` に戻し探索継続。探索セッションは継続する |

- リタイアは**即時**に成立する（戦闘途中でも塔・敵情報をクリアする。予約状態を持たない。根拠は `tech_tower/control.md` §11）

## 3. 探索セッション（run）

`battle.md` §全滅時の処理 のペナルティは「**今回の塔探索中に**取得したゴールド・アイテムをすべて失う」と定義されている。これを成立させるため、入塔から離脱までを1つの**探索セッション**として集計する。

| フィールド | 内容 |
|-----------|------|
| `runGold` | セッション中に獲得したゴールドの累計 |
| `runItems` | セッション中に獲得したアイテム（`itemId` → 個数） |
| `runEquipmentIds` | セッション中に獲得した装備のID一覧 |

| 契機 | 探索セッションの扱い |
|------|-------------------|
| 塔選択（入塔） | 全フィールドを0／空でリセット |
| 目標階クリア（`auto_repeat`） | **継続**（周回をまたいで累積する） |
| リタイア・HP閾値撤退・目標階クリア（`stop_on_clear`） | 確定（没収なし）してリセット。HP閾値撤退の `auto_repeat` は確定リセット後、1階から新しいセッションを開始する（塔に留まる。[tech_tower/progress.md §9](tech_tower/progress.md)） |
| 全滅 | ペナルティを適用してリセット |

**全滅ペナルティの適用順序**:

```
1. runGold を所持ゴールドから減算（下限0）
2. runItems を所持アイテムから減算（下限0）
3. runEquipmentIds の装備を削除（装備中・ロック済みも対象）
4. 各キャラの現在レベル内の蓄積EXPを50%減算（レベルダウンしない）
5. 全キャラのHPを maxHP へ全回復
6. 塔・敵情報をクリアして IDLE へ
```

- 装備は「今回の探索中に取得したドロップアイテム」に含まれるものとして扱う（ロック済みでも没収対象。ロックは自動売却の除外用であり、全滅ペナルティの免除ではない）
- **HP全回復とする根拠**: battle.md のペナルティはEXP・ゴールド・アイテムの3項目に限定されている。HP0のまま `IDLE` にすると「自然回復を待つ時間」という第4のペナルティを暗黙に追加してしまうため、全回復とする
- 探索セッションはサーバー側で保持し、`GET /api/game/state` には含めない。全滅発生時のみ tickレスポンスに損失サマリーとして返す

## 4. 状態 × 操作の可否

| 操作 | `IDLE` | `EXPLORING` / `IN_BATTLE` | `BOSS_RUSH` |
|------|:------:|:------------------------:|:-----------:|
| `POST /api/tower/select` | ○ | ×（400 `TOWER_ALREADY_IN_TOWER`） | × |
| `POST /api/tower/retire` | ×（400 `TOWER_NOT_IN_TOWER`） | ○ | × |
| `PUT /api/tower/mode` | ×（400 `TOWER_NOT_IN_TOWER`） | ○ | × |
| `PUT /api/tower/retreat-conditions` | ○ | ○ | × |
| `POST /api/equipment/equip` | ○ | ○ | ○ |
| `POST /api/equipment/sell` / `lock` | ○ | ○（ロック中・装備中は不可） | ○ |
| `PUT /api/party/edit` | ○ | ×（400 `PARTY_LOCKED_IN_TOWER`） | × |
| `POST /api/skill/learn` / `set-active` | ○ | ○ | ○ |
| `POST /api/character/limit-break` | ○ | ×（400） | × |
| `POST /api/prestige` | ○ | ×（400） | × |
| `POST /api/shop/buy` / `item/sell` | ○ | ○ | ○ |
| `POST /api/base/*` / `forge/*` | ○ | ○ | ○ |
| `POST /api/boss-rush/start` | ○ | ×（400） | × |

- **パーティ編成・限界突破・転生を探索中に禁止する根拠**: いずれも編成やステータスを不連続に変える操作で、探索セッション中に適用すると同一セッション内の期待被ダメージ計算（[tech_offline.md §4.1](tech_offline.md)）の前提が崩れるため
- 装備変更・スキルセットは探索中も許可する（放置ゲームの操作性を優先。次tickから反映）
- 塔選択の入塔前提: パーティに `hp > 0` のキャラが1体以上いること（全員HP0での入塔は `400 TOWER_PARTY_WIPED`。[tech_tower/select.md §7](tech_tower/select.md)）

## 5. 分岐一覧（単体テスト観点）

塔操作（select・retire・mode）と階進行（目標階クリア・HP閾値撤退・上限追従）の分岐は [tech_tower.md §0](tech_tower.md) の各分冊が持つ。本表は状態機械そのもの（探索セッション・不変条件・操作ガード）の分岐のみ。

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | 編成ロック | `EXPLORING` / `IN_BATTLE` で `party/edit` | `400 PARTY_LOCKED_IN_TOWER` |
| 2 | 編成ロック | `IDLE` で `party/edit` | 変更できる（§4） |
| 3 | 全滅ペナルティ | `runGold = 0` で全滅 | ゴールド減算0・下限を割らない |
| 4 | 全滅ペナルティ | 装備中の装備をrun中に取得して全滅 | 装備解除のうえ削除 |
| 5 | 全滅ペナルティ | EXP=0 のキャラで全滅 | EXP減算0 |
| 6 | 全滅ペナルティ | 全滅後のHP | 全員 `maxHP` へ全回復 |
| 7 | 不変条件 | 不変条件（§1.1）に違反するデータを読んだ | `500 INTERNAL_UNEXPECTED_ERROR` + ERRORログ（黙って補正しない） |

> WARN許容 #7: 真偽の対を持たない例外経路（不変条件違反は §1.1 のとおり検出のみで、正常側は全分岐の前提として常に検証される）。#3〜#6 はペナルティの境界を1観点ずつ試験する行で、適用順そのものの網羅は §3 の6手順を通しで検証するテストが担う。
