# 進行制御（retire / mode / retreat-conditions）

> [tech_tower.md](../tech_tower.md) の子ファイル（§11・§12）。要求・応答は tech_tower.md §3、エラーコードは同 §4。
> 3操作とも `players` 行ロック取得後に検証し、1リクエスト=1トランザクションで反映する（tech_tower.md §1）。

## 11. 3操作の処理フロー

### リタイア（POST /api/tower/retire）

1. 塔外（`currentTowerId = null`）なら `400 TOWER_NOT_IN_TOWER`
2. **即時に**塔・敵情報（`currentTowerId`・`currentFloor`・`targetFloor`・`currentEnemyId`・`currentEnemyHp`）をすべて null にし、探索セッションを確定（没収なし）してリセット → `IDLE`
3. `200 { "status": "ok" }`。HPは変更しない

- エンカウント待ち（`EXPLORING`）・戦闘中（`IN_BATTLE`）のどちらでも**待たずに成立**する。戦闘中の敵は破棄され、討伐しきっていない敵の報酬は得られない
- 「現在の戦闘完了後に成立」としない理由: 予約状態の保存先（`players` 列）とAPI応答上の表現が存在せず、フロントはリタイア直後の `GET /api/game/state` で塔外表示になることを前提にしているため（参照実装と同じ挙動）

### 進行モード切替（PUT /api/tower/mode）

1. Bean Validation: `mode` が `auto_repeat` \| `stop_on_clear` 以外・欠落は `422`
2. 塔外なら `400 TOWER_NOT_IN_TOWER`（入塔時は select の `mode` で指定するため、塔外での変更は持たない。[tech_state.md §4](../tech_state.md)）
3. `towerMode` を即時反映して `200`。次の目標到達・撤退判定（[progress.md §9](progress.md) 手順4・5）から新モードを使う

### 撤退条件更新（PUT /api/tower/retreat-conditions）

1. Bean Validation: `hpThreshold` が `0.0〜1.0`（両端を含む）の範囲外・型違反は `422`（[tech_numeric.md](../tech_numeric.md)「入力値の範囲」）
2. `players.hpThreshold` へ保存して `200`。塔外・入塔中のどちらでも変更できる（ボスラッシュ中 Phase 5〜 は不可。tech_state.md §4）
3. 次の階クリア判定（progress.md §9 手順5）から適用する。`0` は HP閾値撤退の無効化、`1.0` は全快でない限り毎階撤退

## 12. 分岐一覧（進行制御）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | リタイア | 塔外 | `400 TOWER_NOT_IN_TOWER` |
| 2 | リタイア | 入塔中（エンカウント待ち・戦闘中とも） | 即時に塔・敵情報クリア・セッション確定・`IDLE`・`200` |
| 3 | モード入力 | `mode` が2値以外・欠落 | `422`（変更しない） |
| 4 | モード入力 | 適合 | 状態判定へ |
| 5 | モード状態 | 塔外 | `400 TOWER_NOT_IN_TOWER` |
| 6 | モード状態 | 入塔中 | `towerMode` を即時反映・`200`（次tickの判定から適用） |
| 7 | 撤退条件入力 | `hpThreshold` が `0.0〜1.0` の範囲外・型違反 | `422`（変更しない） |
| 8 | 撤退条件入力 | 範囲内（`0.0`・`1.0` ちょうどを含む） | 保存して `200`（塔外でも可） |
| 9 | 撤退条件の効果 | `hpThreshold = 0` | HP閾値撤退が無効（progress.md §10 #18） |
| 10 | 撤退条件の効果 | `hpThreshold > 0` | progress.md §9 手順5の判定に使う |
