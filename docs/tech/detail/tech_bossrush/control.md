# 終了・ランキング（retire / ranking）

> [tech_bossrush.md](../tech_bossrush.md) の子ファイル（§11・§12）。要求・応答は tech_bossrush.md §3、エラーコードは同 §4。
> 終了処理は**全滅（[wave.md §7](wave.md) 手順3・[offline.md §9](offline.md) 手順2-c）とリタイアの共通処理**であり、経路によって結果を変えない。

## 11. 終了処理とリタイア

### 終了処理（全滅・リタイア共通）

順序は固定:

1. **ゴールドの確定**: `accumulated_gold` を `players.gold` へ加算し、`accumulated_gold = 0` にする。上限は飽和（[tech_numeric.md §3](../tech_numeric.md)）
2. **EXPの確定**: `accumulated_exp` を**在籍パーティ全員へ全額付与**する（人数で割らない。戦闘不能のメンバーにも付与する。[tech_battle.md §3.1.5](../tech_battle.md)）。ここでレベルアップ判定が起きる。`accumulated_exp = 0` にする
3. **HPの回復**: 全キャラのHPを `effectiveMaxHp` へ全回復する
4. **状態のリセット**: `active = false`・`current_wave = 0`。**`best_wave`・`best_wave_hp` は保持する**
5. **戦闘状態のクリア**: 全キャラのスキルCD・バフ・デバフ・状態異常を解除して `IDLE` へ（[tech_state.md §2](../tech_state.md)）

- **累積報酬は没収しない**（全滅・リタイアとも確定取得。[systems/endgame.md §2.11](../../../design/systems/endgame.md)）。塔の全滅ペナルティ（tech_state.md §3）はボスラッシュに適用しない
- 手順3でHPを全回復する根拠は塔の全滅処理と同じ。ペナルティが無いにもかかわらずHP0のまま `IDLE` へ戻すと「自然回復を待つ時間」という実質的なペナルティを課すことになるため（tech_state.md §3 の同趣旨の判断に揃える）
- 記録（`best_*`）は突破時に確定済み（wave.md §7 手順4-b）。**終了時には記録を更新しない**。全滅したウェーブは突破していないため対象外

### リタイア（POST /api/boss-rush/retire）

1. 対象 `players` 行を行ロックして読む。待機超過は `503 BATTLE_TICK_BUSY`
2. `active = false` なら `400 BOSS_RUSH_NOT_ACTIVE`
3. **即時に**上の終了処理を実行する。進行中のウェーブの戦闘は破棄し、討伐しきっていない敵の報酬は得られない
4. `200`（応答は tech_bossrush.md §3）

- 塔のリタイア（[tech_tower/control.md §11](../tech_tower/control.md)）と同じく**予約状態を持たない**。`boss_rush_states` に予約を表す列は無い（tech_state.md §2）
- リタイアは未処理tickを消化しない。復帰時はフロントが先に `/api/battle/tick` を呼ぶため、リタイアが過去のオフライン区間へ遡及しない

### ランキング（GET /api/boss-rush/ranking）

1. `boss_rush_states` を全行走査し、次の2条件を満たす行だけを対象にする
   - 所属ユーザーが本登録済み（`users.is_guest = false`）。ゲストは順位に載せない（[systems/endgame.md §2.11](../../../design/systems/endgame.md)「ゲスト時」）
   - `best_wave >= 1`（未挑戦を除く）
2. `best_wave` 降順 → `best_wave_hp` 降順 → `player_id` 昇順で整列し、上位100件を返す（3つ目のキーは同値時の順序を安定させるためで、順位指標ではない）
3. `myRank` は、呼び出し元が手順1の条件を満たすときに**全体での順位**を返す。上位100件の外でも順位を返す。条件を満たさない（ゲスト・未挑戦）なら `null`

- ゲストも**閲覧はできる**（認証は必要。ゲストトークンで `200`）。載らないのは自分の記録だけ
- ゲストの `best_*` は保存し続ける。本登録へ移行した時点で以後のランキングに載る（記録を作り直さない）

## 12. 分岐一覧（終了・ランキング）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | リタイア | `active = false` | `400 BOSS_RUSH_NOT_ACTIVE` |
| 2 | リタイア | `active = true` | 即時に終了処理を実行して `200`（進行中の戦闘の完了を待たない） |
| 3 | ゴールド確定 | 加算後が `MAX_GOLD` を超える | `MAX_GOLD` で飽和させ、`accumulated_gold = 0` にする |
| 4 | ゴールド確定 | 加算後が `MAX_GOLD` 以下 | 全額を加算し、`accumulated_gold = 0` にする |
| 5 | EXP確定 | 付与でレベルアップのしきい値に到達した | レベルアップを反映する（在籍全員へ全額付与） |
| 6 | EXP確定 | しきい値に到達しない | EXPだけを加算する |
| 7 | EXP確定 | LV9999のキャラ | 超過EXPを切り捨てる（[tech_numeric.md §5](../tech_numeric.md) #11） |
| 8 | 終了時のHP | 全滅で終了した | 全キャラを `effectiveMaxHp` へ全回復する |
| 9 | 終了時のHP | リタイアで終了した | 同じく全回復する（終了経路で差を設けない） |
| 10 | 記録の保持 | 終了処理を通った | `best_wave`・`best_wave_hp` を変更しない（突破時に確定済み） |
| 11 | 排他 | `players` 行ロックの待機超過 | `503 BATTLE_TICK_BUSY`（状態を変更しない） |
| 12 | ランキング対象 | ゲスト（`users.is_guest = true`） | 一覧に含めない（自己ベストの保存は続ける） |
| 13 | ランキング対象 | 本登録済みで `best_wave = 0`（未挑戦） | 一覧に含めない |
| 14 | ランキング対象 | 本登録済みで `best_wave >= 1` | `best_wave` 降順・`best_wave_hp` 降順・`player_id` 昇順で整列して上位100件に載せる |
| 15 | 自分の順位 | 呼び出し元がゲスト、または `best_wave = 0` | `myRank` を `null` で返す |
| 16 | 自分の順位 | 呼び出し元が本登録済みで `best_wave >= 1` | 全体での順位を `myRank` に返す（上位100件の外でも返す） |

> WARN許容 #10・#11: #10 は「更新しない」ことを保証する行で、更新側の対は wave.md §8 #9〜#12 が持つ。#11 は例外経路（ロック競合）で、正常系は #1〜#10 がロック取得済みを前提とする。
