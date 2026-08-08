# システム構成図 — 全体アーキテクチャ

> 親: [system_architecture.md](../system_architecture.md)。技術仕様は [tech_spec.md](../../tech/tech_spec.md)、モジュール構成は [tech_structure.md](../../tech/basic/tech_structure.md)。

## 全体アーキテクチャ

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
flowchart TB
    subgraph Client["ブラウザ (Vue.js 3 SPA + TypeScript)"]
        direction TB

        subgraph Views["Views (ページコンポーネント)"]
            GameView["GameView.vue\n(メインゲーム画面)\nPhase 1~"]
            ShopView["ShopView.vue\n(ショップ)\nPhase 1~"]
            SettingsView["SettingsView.vue\n(設定)\nPhase 1~"]
            LoginView["LoginView.vue\n(ログイン)\nPhase 2~"]
            RegisterView["RegisterView.vue\n(登録)\nPhase 2~"]
            EquipView["EquipmentView.vue\n(装備管理)\nPhase 2~"]
            PartyView["PartyView.vue\n(パーティ編成)\nPhase 3~"]
            BaseView["BaseView.vue\n(拠点施設)\nPhase 4~"]
            BossRushView["BossRushView.vue\n(ボスラッシュ)\nPhase 5~"]
            EventView["EventView.vue\n(イベントダンジョン)\nPhase 5~"]
        end

        subgraph Components["Components (tech_design_system.md が正)"]
            UiPrimitives["ui/ UIプリミティブ\nBase{Button,Card,Modal,Badge,\nField,Select,TextInput}\nNumberStepper / StatBar\nAppIcon / icons.ts\n※ストアを参照しない"]
            Layout["layout/ アプリシェル\nAppShell (100dvh grid)\nAppHeader / AppNav / navItems.ts\nConnectionBanner (接続エラー)"]
            EquipComp["equipment/ Phase2~\nEquipmentCard / EquipmentCompare\nEquipmentInventory / EquipmentSlotGrid"]
        end

        subgraph Stores["Pinia Stores (状態管理)"]
            gameStore["gameStore.ts\nゴールド・塔情報・設定\nゲーム全体状態"]
            battleStore["battleStore.ts\n戦闘ログ・敵情報\nターン数"]
            playerStore["playerStore.ts\nキャラクター一覧\nパーティ・インベントリ"]
            equipmentStore["equipmentStore.ts\n装備一覧・装着状態\nPhase 2~"]
            authStore["authStore.ts\nJWTトークン・セッション\nlogin/logout/restoreSession\nPhase 2~"]
        end

        subgraph Composables["Composables (ロジック)"]
            usePolling["usePolling.ts\n60秒ポーリング制御\nsetInterval管理"]
            useGameLoop["useGameLoop.ts\nゲーム起動・状態管理\n復帰時処理"]
            useBattleLocal["useBattleLocal.ts\nローカル計算\n(デバッグ用フォールバック)"]
        end

        Router["router/index.ts\nVue Router\n画面ルーティング"]
        APIClient["api/client.ts\napi/auth.ts (Phase 2~)\nAxios/fetch\nREST通信レイヤー\nUSE_APIフラグ"]
        Types["types/game.ts\nTypeScript型定義\nゲーム関連の型"]
        Assets["assets/\nicons/ アイコン画像\nstyles/tokens.css デザイントークン\n(色・書体・寸法の唯一の定義元)\nstyles/main.css ベース・ユーティリティ"]

        Router --> Views
        Layout --> Views
        Views --> UiPrimitives
        Views --> EquipComp
        Views --> Stores
        Views --> Composables
        Composables --> Stores
        Composables --> APIClient
        Layout --> UiPrimitives
        Layout --> Stores
        EquipComp --> UiPrimitives
        EquipComp --> Stores
        UiPrimitives --> Assets
    end

    APIClient <-->|"REST API (JSON)\nAuthorization: Bearer token"| Routers

    subgraph Server["FastAPI バックエンド (Python)"]
        direction TB

        subgraph Routers["Routers (APIエンドポイント)"]
            authRouter["auth.py\nPOST /api/auth/guest\nPOST /api/auth/login\nPOST /api/auth/register\nPOST /api/auth/google\nPOST /api/auth/refresh\n他5エンドポイント"]
            gameRouter["game.py\nGET /api/game/state\nPUT /api/game/settings"]
            battleRouter["battle.py\nPOST /api/battle/tick"]
            towerRouter["tower.py\nPOST /api/tower/select\nPOST /api/tower/retire\nPUT /api/tower/mode\nPUT /api/tower/retreat-conditions"]
            shopRouter["shop.py\nGET /api/shop/lineup\nPOST /api/shop/buy"]
            equipRouter["equipment.py\nGET /api/equipment/list\nPOST /api/equipment/equip\nPOST /api/equipment/sell\nPOST /api/equipment/lock"]
            partyRouter["party.py Phase3~\nPUT /api/party/edit\nPOST /api/skill/learn\nPUT /api/skill/set-active\nPOST /api/skill/reset\nPOST /api/character/limit-break"]
            baseRouter["base.py Phase4~\nPOST /api/base/build\nPOST /api/base/upgrade\nPOST /api/base/scout"]
            forgeRouter["forge.py Phase4~\nPOST /api/forge/enhance\nPOST /api/forge/craft\nPOST /api/forge/disassemble"]
            bossRushRouter["boss_rush.py Phase5~\nPOST /api/boss-rush/start\nPOST /api/boss-rush/retire\nGET /api/boss-rush/ranking"]
            abyssRouter["abyss.py Phase5~\nGET /api/abyss/ranking"]
            prestigeRouter["prestige.py Phase5~\nPOST /api/prestige\nPUT /api/prestige/invest\nPOST /api/prestige/reset"]
        end

        subgraph Services["Services (ビジネスロジック)"]
            battleService["battle_service.py\n戦闘計算 (サーバー権威)\nターン処理・ダメージ計算\n塔進行・エンカウント\nオフライン計算 (正規/簡略)"]
            equipService["equipment_service.py\n装備ドロップ・売却・\n実効ステータス計算"]
            shopService["shop_daily_service.py\n日替わりショップ\n(生成・鮮度判定・購入)\nPhase 2~"]
            stateBuilder["game_state_builder.py\nゲーム状態レスポンス構築"]
            authService["auth_service.py\n認証ロジック\nJWT/bcrypt Phase2~"]
            baseService["base_service.py\n施設建設・LVアップ\nPhase 4~"]
            forgeService["forge_service.py\n装備強化・製作・分解\nPhase 4~"]
        end

        subgraph Models["Models (SQLAlchemy 2.0)"]
            playerModel["player.py\nPlayer, PlayerSettings\nTowerClearRecord\nParty (Phase3~)"]
            charModel["character.py\nCharacter\nPrestigeBonus (Phase5~)\nLearnedSkill (Phase3~)\nActiveSkillSlot (Phase3~)"]
            equipModel["equipment.py\nEquipment\nCharacterEquipSlot"]
            itemModel["item.py\nBattleLog, InventoryItem\nFacility (Phase4~)\nBossRushState (Phase5~)"]
            shopModel["shop.py Phase2~\nShopDailyState\nShopDailySlot"]
            userModel["user.py Phase2~\nUser, RefreshToken\nEmailVerificationToken"]
        end

        subgraph Schemas["Schemas (Pydantic v2)"]
            playerSchema["player.py\nPlayerResponse\nCharacterResponse\nGameStateResponse\nSettingsUpdate / SettingsResponse"]
            battleSchema["battle.py\nTickResponse\nOfflineSummary"]
            towerSchema["tower.py\nTowerSelectRequest\nTowerInfo"]
            equipSchema["equipment.py Phase2~\nEquipmentResponse\nEquipRequest"]
            shopSchema["shop.py\nShopLineupResponse\nShopDailyItemResponse"]
            authSchema["auth.py Phase2~\nLoginRequest\nAuthResponse"]
        end

        Config["config.py\nTICK_INTERVAL_SECONDS = 60\nTURNS_PER_TICK = 3\nFAST_CALC_THRESHOLD = 100\nMAX_OFFLINE_HOURS = 24\nMAX_BATTLE_LOG_RECORDS = 100\nMAX_LOG_PER_RESPONSE = 50\nMAX_PLAYER_LEVEL = 9999"]
        DB_Module["db/database.py\nDB接続設定\nセッション管理"]
        Alembic["alembic/\nDBマイグレーション\nスキーマ管理"]

        Routers --> Schemas
        Routers --> Services
        Services --> Models
        Models --> DB_Module

        battleService --> equipService
    end

    DB_Module <-->|"SQLAlchemy ORM"| DB

    subgraph DB["データベース"]
        SQLite["SQLite\n(local / production 初期)\nEBS上に配置"]
        PostgreSQL["PostgreSQL\n(移行後)\nDB 850MB接近 or\n書き込みロック競合で移行"]
    end

    SQLite -->|"§12.4 移行判断ライン"| PostgreSQL

    Alembic -->|"マイグレーション"| DB

    style Client fill:#e8f5e9
    style Server fill:#e3f2fd
    style DB fill:#fff3e0
```

- DB の移行判断ライン（§12.4）とデプロイ構成は [tech_operations.md](../../tech/nonfunctional/tech_operations.md) §12 が正。デプロイ構成の図は [deployment.md](deployment.md)
- Components の3層構成（トークン / UIプリミティブ / アプリシェル）と各層の責務は [tech_design_system.md](../../tech/detail/tech_design_system.md) が正。UIプリミティブはトークンだけを参照し、ストアには触れない
- Schemas は `backend/app/schemas/` の実装済みファイルのみを描く。Phase 3〜5 で追加するスキーマ（`PartyEdit` 等）は Routers の Phase 注記と [tech_structure.md](../../tech/basic/tech_structure.md) §2 を参照
