# APIシーケンス図 — 基本ループ

> 親: [api_sequence.md](../api_sequence.md)。API定義は [tech_api.md](../../tech/basic/tech_api.md)。

## 1. 初回アクセス（ゲスト作成 — Phase 1）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant LS as LocalStorage
    participant API as Terasoluna(Spring MVC)
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
    participant API as Terasoluna(Spring MVC)
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
    API->>DB: lastTickAt += 処理tick数 × 60秒<br/>(24時間超で打ち切った場合のみ現在時刻)

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
    participant API as Terasoluna(Spring MVC)
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

## 3.5. 設定変更（Phase 1〜）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as Terasoluna(Spring MVC)
    participant DB as Database

    Note over B: 設定画面で項目を変更<br/>(保存ボタンは無し = 変更即時反映)

    B->>API: PUT /api/game/settings<br/>{ potionThreshold, battleLogCount,<br/>  toastEnabled, autoSellRarity }<br/>Authorization: Bearer {token}

    API->>DB: PlayerSettings 取得<br/>(無ければ既定値で作成)
    API->>DB: 指定されたフィールドのみ更新

    Note over API: 未指定フィールドは変更しない。<br/>autoSellRarity は null 指定で OFF に戻せる

    API-->>B: SettingsResponse<br/>(更新後の全設定値)

    B->>B: settingsStore 更新<br/>次のtickから新しい閾値・表示件数が適用
```

- 設定はサーバー側に保存する（[systems/ui.md](../../design/systems/ui.md) 設定画面）。画面遷移は [screen_transition/main_nav.md](../screen_transition/main_nav.md) の設定画面

## 3.7. お知らせ確認（Phase 3〜）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant LS as LocalStorage
    participant API as Terasoluna(Spring MVC)

    Note over B: 起動時 (GET /api/game/state 後) に1回だけ取得

    B->>API: GET /api/notice/list<br/>Authorization: Bearer {token}
    Note over API: マスターデータから返す<br/>(DBテーブルなし)
    API-->>B: { notices: [{ noticeId, title,<br/>body, publishedAt }] }<br/>publishedAt 降順

    B->>LS: 既読 noticeId 一覧を取得
    B->>B: 未読件数 = 一覧 − 既読<br/>ヘッダの お知らせ にバッジ表示

    B->>B: ヘッダの お知らせ をタップ<br/>一覧表示 (新しい順)
    B->>LS: 表示中の全 noticeId を<br/>既読として保存
    B->>B: バッジ消灯
```

- API定義は `tech_api/core.md`「お知らせ」。要件・既読保持先の正は [operation_requirements.md](../../design/requirements/operation_requirements.md) §3.1（サーバーは既読状態を持たない）
- 画面遷移は `screen_transition/main_nav.md` の お知らせ画面

## 13. 通信エラー時（リトライ）

```mermaid
%%{init: {'theme': 'default', 'sequence': {'actorFontSize': 18, 'messageFontSize': 16, 'noteFontSize': 14}} }%%
sequenceDiagram
    participant B as ブラウザ
    participant API as Terasoluna(Spring MVC)

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
