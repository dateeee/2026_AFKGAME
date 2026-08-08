# システム構成図 — tick処理のデータフロー

> 親: [system_architecture.md](../system_architecture.md)。tick仕様は [tech_tick.md](../../tech/detail/tech_tick.md)、戦闘計算は [tech_battle.md](../../tech/detail/tech_battle.md)。

## tick処理のデータフロー

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
flowchart LR
    Browser["ブラウザ\nusePolling.ts"] -->|"POST /api/battle/tick\nAuthorization header"| Router["battle.py\nRouter"]

    Router -->|"リクエスト解析"| Schema1["battle.py\nPydantic Schema\n入力バリデーション"]

    Schema1 -->|"validated data"| Service["battle_service.py\n未処理tick算出\n3ターン分戦闘計算\nダメージ・報酬・階層進行"]

    Service -->|"敵・塔データ取得\nエンカウント抽選"| MasterData["master_data/\ntowers.py, enemies.py\n塔・敵マスター"]

    Service -->|"DB読み書き"| Model["SQLAlchemy Models\nPlayer, Character\nEquipment, BattleLog"]

    Model <-->|"SQL"| Database["SQLite / PostgreSQL"]

    Model -->|"更新後データ"| Schema2["battle.py\nPydantic Schema\nレスポンス構築"]

    Schema2 -->|"TickResponse JSON\nbattleLogs[]\nupdatedState{}\nofflineSummary{}?"| Browser
```
