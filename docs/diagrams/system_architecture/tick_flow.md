# システム構成図 — tick処理のデータフロー

> 親: [system_architecture.md](../system_architecture.md)。tick仕様は [tech_tick.md](../../tech/detail/tech_tick.md)、戦闘計算は [tech_battle.md](../../tech/detail/tech_battle.md)。

## tick処理のデータフロー

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
flowchart LR
    Browser["ブラウザ\nusePolling.ts"] -->|"POST /api/battle/tick\nAuthorization header"| Router["BattleController.java\nController"]

    Router -->|"リクエスト解析"| Schema1["BattleResource.java\nBean Validation\n入力バリデーション"]

    Schema1 -->|"validated data"| Service["BattleService.java\n未処理tick算出\n3ターン分戦闘計算\nダメージ・報酬・階層進行"]

    Service -->|"敵・塔データ取得\nエンカウント抽選"| MasterData["afkgame-domain\nTowers, Enemies (YAMLロード)\n塔・敵マスター"]

    Service -->|"DB読み書き"| Model["Entity + MyBatis3 Mapper\nPlayer, Character\nEquipment, BattleLog"]

    Model <-->|"SQL"| Database["PostgreSQL"]

    Model -->|"更新後データ"| Schema2["BattleResource.java\nBean Validation\nレスポンス構築"]

    Schema2 -->|"TickResponse JSON\nbattleLogs[]\nupdatedState{}\nofflineSummary{}?"| Browser
```
