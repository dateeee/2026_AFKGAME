# AFK GAME — ボスラッシュ（索引）

> Phase 5〜。`/api/boss-rush/*` の3APIと、tick内のウェーブ進行の詳細設計。本書は**共通部分**（適用範囲・データ解決・API・エラーコード）を持ち、処理フローと分岐一覧は子ファイルにある。
> **数値の正**: ウェーブ構成・強化倍率・マイルストーン報酬・累積報酬・定期回復率は [master/endgame.md](../../data/master/endgame.md) §15。ゲーム仕様の正は [systems/endgame.md](../../design/systems/endgame.md) §2.11、状態遷移・操作可否は [tech_state.md](tech_state.md)、ターン処理は [tech_battle.md](tech_battle.md) §3.1、テーブル定義は [tech_db/battle.md](../basic/tech_db/battle.md) §2・§3。

## 0. 子ファイル索引

| 節 | 対象 | ファイル | 分岐一覧 |
|----|------|---------|---------|
| §5・§6 | 開始 `POST /api/boss-rush/start` | [tech_bossrush/start.md](tech_bossrush/start.md) | 10件 |
| §7・§8 | ウェーブ進行（tick内・正規シミュレーション） | [tech_bossrush/wave.md](tech_bossrush/wave.md) | 25件 |
| §9・§10 | オフライン簡略計算（101tick以上） | [tech_bossrush/offline.md](tech_bossrush/offline.md) | 15件 |
| §11・§12 | 終了・ランキング `retire` / `ranking` | [tech_bossrush/control.md](tech_bossrush/control.md) | 16件 |

節番号は通し番号。分割の理由は対象APIが3本あり、tick内処理とオフライン処理を併せて分岐が50件を超えるため（[detail-design.md §4](../../../.claude/project/detail-design.md) の分割基準）。

## 1. 適用範囲と方針

| 項目 | 内容 |
|------|------|
| 対象 | `/api/boss-rush/*` の3API + tick内のウェーブ進行（ウェーブ開始〜突破・全滅の確定）+ オフライン簡略計算 |
| 対象外 | ターン処理・ダメージ計算（[tech_battle.md §3.1](tech_battle.md)）、tick数の算定・24時間クランプ・トランザクション境界（[tech_tick.md](tech_tick.md)）、塔の周回簡略計算（[tech_offline.md §4](tech_offline.md)） |
| 排他 | `active = true` の間は `currentTowerId = null`（塔探索と排他。tech_state.md §1.1）。`start`・`retire` は tick と同じく対象 `players` 行を行ロックしてから検証する（[tech_tick.md §3.1](tech_tick.md)。待機超過は同じく `503 BATTLE_TICK_BUSY` を流用） |
| 探索セッション | **持たない**。獲得は `boss_rush_states.accumulated_*` に積み、全滅でも没収しない（[tech_state.md §3](tech_state.md) のペナルティは塔探索専用で、ボスラッシュには適用しない） |
| 挑戦中の成長 | **起きない**。ゴールド・EXPは終了時にまとめて付与するため、挑戦中はレベル・ステータスが不変（§11） |
| 乱数 | エンカウント抽選を行わない（ウェーブ構成は §2 で一意に決まる）。ターン内の乱数消費は tech_battle §3.1 ＝ [tech_rng.md §1](tech_rng.md) |
| 永続化 | `boss_rush_states`・`boss_rush_milestones`（tech_db/battle.md §2・§3）と `players.gold`、各キャラのEXP・HP。1リクエスト=1トランザクション |
| 共通検証 | 未認証は共通の `401`、型・必須・値域違反は Bean Validation の `422`（[tech_api/common.md](../basic/tech_api/common.md)）。分岐一覧では `401` を扱わない |
| Phase | Phase 5〜。Phase 4 以前は3APIとも実装しない |

## 2. データの解決

ウェーブ番号 `wave`（1始まり）から敵構成と倍率を決める。**乱数を使わず一意に決まる**（数値の正は master/endgame.md §15.1）。

| 項目 | 解決規則 |
|------|---------|
| Wave 1〜5 の敵構成 | 塔 `00wave`（Wave1→塔001 … Wave5→塔005）の **`totalFloors − 1` 階**（最上階ボスの1つ下）の `floorEncounters` をそのまま用いる。敵の体数・LVも同階の定義値のまま |
| Wave 6〜10 の敵構成 | 塔 `00(wave − 5)` の**最上階ボス**1体。順序は master/endgame.md §15.1 の表と一致する |
| Wave 11〜 の敵構成 | Wave 10 と同じ構成を用い、HP/ATK/DEF/SPD・EXP・ゴールドへ強化倍率を乗じる |
| 強化倍率 | `wave <= 10` は 1.0、`wave >= 11` は `1.1 ^ (wave − 10)`（式の正は master/endgame.md §15.1） |
| 倍率適用後の丸め | ステータスは `floor`・下限1、EXP／ゴールドは `floor`・下限0。いずれも**倍率適用後に1回だけ**丸める（[tech_numeric.md §2](tech_numeric.md)） |
| マイルストーンの刻み | `5 <= wave <= 30` かつ `wave` が5の倍数、または `wave > 30` かつ `wave` が10の倍数（報酬内容の正は master/endgame.md §15.2） |
| 定期回復の契機 | `wave` が5の倍数のウェーブを**突破した直後**。回復量は `floor(effectiveMaxHp × 定期回復率)`（率の正は master/endgame.md §15.1） |
| 参照先が不在 | 塔・階・ボスがマスターデータに存在しない場合は ERRORログ（`INTERNAL_MASTER_DATA_INVALID`）+ `500 INTERNAL_UNEXPECTED_ERROR`。黙って別の敵で代替しない |

## 3. API要求・応答

要求・応答とも camelCase（[tech_api/common.md](../basic/tech_api/common.md)）。エンドポイント一覧の正は [tech_api/endgame.md](../basic/tech_api/endgame.md)「ボスラッシュ」。

| API | 要求 | 成功応答（200） |
|-----|------|----------------|
| `POST /api/boss-rush/start` | なし | `{ "status": "ok", "bossRush": { "active": true, "wave": 1 } }` |
| `POST /api/boss-rush/retire` | なし | `{ "status": "ok", "rewards": { "gold": ..., "exp": ... }, "wave": ..., "newBest": true\|false }` |
| `GET /api/boss-rush/ranking` | なし | `{ "entries": [ { "rank": 1, "playerName": ..., "bestWave": ..., "bestWaveHp": ... } ], "myRank": 12\|null }` |

- `rewards` は今回の挑戦で確定取得した累積分（`accumulated_*` の値）。`newBest` は今回の挑戦で `best_wave` を更新したか
- 全滅による終了は tickレスポンスで同じ内容を返す（`wipe: true` を伴う。[api_sequence/endgame.md](../../diagrams/api_sequence/endgame.md) §11）

## 4. `BOSS_RUSH_` エラーコード一覧

体系の正は [tech_error_handling.md](../basic/tech_error_handling.md)「エラーコード体系」。

| コード | HTTP | 発生条件 |
|--------|------|---------|
| `BOSS_RUSH_ALREADY_ACTIVE` | 400 | 挑戦中（`active = true`）の `start` |
| `BOSS_RUSH_IN_TOWER` | 400 | 塔探索中（`EXPLORING` / `IN_BATTLE`）の `start` |
| `BOSS_RUSH_NOT_ACTIVE` | 400 | 挑戦していない状態での `retire` |
| `BOSS_RUSH_PARTY_WIPED` | 400 | `hp > 0` のキャラが1体もいない状態での `start`（在籍0体を含む） |

- クライアントはコードで分岐せず汎用エラー表示に倒す（`TOWER_` と同じ扱い。[tech_tower.md §4](tech_tower.md)）。単体テスト・E2E はコードで検証する
