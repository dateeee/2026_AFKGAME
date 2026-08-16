# 開始（POST /api/boss-rush/start）

> [tech_bossrush.md](../tech_bossrush.md) の子ファイル（§5・§6）。要求・応答は tech_bossrush.md §3、エラーコードは同 §4、敵構成の解決は同 §2。
> `players` 行ロック取得後に検証し、1リクエスト=1トランザクションで反映する（tech_bossrush.md §1）。乱数は消費しない。

## 5. 開始の処理フロー

1. 対象 `players` 行を行ロックして読む。待機超過は `503 BATTLE_TICK_BUSY`（[tech_tick.md §3.1](../tech_tick.md)）
2. **状態の検証**（[tech_state.md §4](../tech_state.md) の操作可否表と一致させる）
   - `boss_rush_states.active = true` なら `400 BOSS_RUSH_ALREADY_ACTIVE`
   - `players.currentTowerId ≠ null` なら `400 BOSS_RUSH_IN_TOWER`（塔からの直接移行は許さない。先にリタイアさせる）
3. **パーティの検証**: 在籍パーティに `hp > 0` のキャラが1体もいなければ `400 BOSS_RUSH_PARTY_WIPED`（在籍0体を含む。塔の `TOWER_PARTY_WIPED` と同じ前提。[tech_tower/select.md §7](../tech_tower/select.md)）
4. **状態行の用意**: `boss_rush_states` を `player_id` で引き、無ければ全列を既定値（`active = false`・`current_wave = 0`・`accumulated_* = 0`・`best_* = 0`）で作成する
5. **進行中フィールドの初期化**: `current_wave = 1`・`accumulated_gold = 0`・`accumulated_exp = 0`・`active = true`。**`best_wave`・`best_wave_hp` は触らない**（挑戦をまたいで残る。[tech_db/battle.md §2](../../basic/tech_db/battle.md)）
6. **戦闘状態のクリア**: 全キャラのスキルCDを0にリセットし、バフ・デバフ・状態異常をすべて解除する（クリーンな状態でWave 1を開始する。[systems/endgame.md §2.11](../../../design/systems/endgame.md)）
7. `200 { "status": "ok", "bossRush": { "active": true, "wave": 1 } }`

- HPは**回復しない**。開始時点のHPをそのまま持ち込む（回復手段は定期回復〈5ウェーブごと〉とポーションのみ）
- `last_tick_at` は進めない。Wave 1 の戦闘は次の `POST /api/battle/tick` から始まる（操作系APIは未処理tickを消化しない。tech_tower.md §1 と同じ方針）
- 手順5で `accumulated_*` を0に戻すのは、異常終了（例外ロールバック後の再開）で前回の値が残っていた場合に持ち越さないため。正常な終了処理（[control.md §11](control.md)）は0に戻したうえで `active = false` にする

## 6. 分岐一覧（開始）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | 開始時の状態 | 挑戦中（`active = true`） | `400 BOSS_RUSH_ALREADY_ACTIVE` |
| 2 | 開始時の状態 | 塔探索中（`currentTowerId ≠ null`） | `400 BOSS_RUSH_IN_TOWER` |
| 3 | 開始時の状態 | 塔外待機（`IDLE`） | パーティ検証へ進む |
| 4 | パーティ | `hp > 0` のキャラが1体もいない（在籍0体を含む） | `400 BOSS_RUSH_PARTY_WIPED` |
| 5 | パーティ | `hp > 0` のキャラが1体以上いる | 開始処理へ進む |
| 6 | 状態行 | `boss_rush_states` に行が無い | 既定値で行を作成し、`best_wave = 0`・`best_wave_hp = 0` から始める |
| 7 | 状態行 | 行がある | `best_wave`・`best_wave_hp` を保持したまま進行中フィールドだけ初期化する |
| 8 | 累積の初期化 | 前回の挑戦の `accumulated_*` が残っている | 0へ戻してから開始する（持ち越さない） |
| 9 | 累積の初期化 | `accumulated_*` が0 | そのまま `current_wave = 1`・`active = true` にする |
| 10 | 排他 | `players` 行ロックの待機超過 | `503 BATTLE_TICK_BUSY`（状態を変更しない） |

> WARN許容 #10: 例外経路（ロック競合）。対になる正常系は #1〜#9 がロック取得済みを前提に持つ。
