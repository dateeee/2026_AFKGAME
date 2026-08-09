# AFK GAME — 塔操作・階進行（索引）

> Phase 1〜（イベントダンジョン・深淵の塔への適用は Phase 5〜）。`/api/tower/*` の5APIと、tick内の階クリア後処理（階進行）の詳細設計。本書は**共通部分**（適用範囲・データ解決・API・エラーコード）を持ち、処理フローと分岐一覧は子ファイルにある。
> **数値の正**: 各塔の階数・エンカウントプール・解放条件は [TOWERS_OVERVIEW.md](../../data/towers/TOWERS_OVERVIEW.md) と各塔ファイル。状態遷移・操作可否の正は [tech_state.md](tech_state.md)、戦闘処理とエンカウント抽選は [tech_battle.md](tech_battle.md) §3、目標階上限の式は [tech_api.md](../basic/tech_api.md)「操作系」。

## 0. 子ファイル索引

| 節 | 対象 | ファイル | 分岐一覧 |
|----|------|---------|---------|
| §5・§6 | 一覧 `GET /api/tower/list` | [tech_tower/list.md](tech_tower/list.md) | 9件 |
| §7・§8 | 入塔 `POST /api/tower/select` | [tech_tower/select.md](tech_tower/select.md) | 15件 |
| §9・§10 | 階進行（tick内・階クリア後） | [tech_tower/progress.md](tech_tower/progress.md) | 21件 |
| §11・§12 | 進行制御 `retire` / `mode` / `retreat-conditions` | [tech_tower/control.md](tech_tower/control.md) | 10件 |

節番号は通し番号。分割の理由は対象APIが5本あるため（[detail-design.md §4](../../../.claude/project/detail-design.md) の分割基準）。

## 1. 適用範囲と方針

| 項目 | 内容 |
|------|------|
| 対象 | `/api/tower/*` の5API + tick内の階進行（階クリア確定〜次階送り・塔離脱） |
| 対象外 | ターン処理・エンカウント抽選・報酬付与（[tech_battle.md](tech_battle.md) §3）、全滅時の処理・探索セッション（[tech_state.md](tech_state.md) §3）、tick数の算定・排他（[tech_tick.md](tech_tick.md)） |
| 排他 | `players` を更新する4操作（select・retire・mode・retreat-conditions）は tick と同じく対象 `players` 行を行ロックしてから検証する（[tech_tick.md §3.1](tech_tick.md)。待機超過は同じく `503 BATTLE_TICK_BUSY` を流用） |
| tick との順序 | 操作系APIは未処理tickを消化しない。復帰時はフロントが最初に `/api/battle/tick` を呼ぶ（[tech_polling.md §2](tech_polling.md)）ため、操作が過去のオフライン区間へ遡及しない |
| 乱数 | 本書群の処理は乱数を消費しない（エンカウント抽選は tech_battle §3.2 = [tech_rng.md §1](tech_rng.md) #7・#8） |
| 永続化 | `players` の塔フィールドと `tower_clear_records`（[tech_db/player.md](../basic/tech_db/player.md) §1・§3）。1リクエスト=1トランザクション |
| 共通検証 | 未認証は共通の `401`、型・必須・値域違反は Bean Validation の `422`（[tech_api/common.md](../basic/tech_api/common.md)）。分岐一覧では `401` を扱わない |
| Phase | 塔の実装Phase対応は [game_spec.md §1](../../design/game_spec.md) が正。イベントダンジョン・深淵の塔の固有仕様は [systems/endgame.md](../../design/systems/endgame.md) §2.13・§2.14 と tech_api.md「イベントダンジョン」 |

## 2. データの解決

塔マスター（各塔ファイル §1 基本情報・§5 塔構成）から `id`・`name`・所属ダンジョン名・`totalFloors`・解放条件・`floorEncounters` を引く。

| 項目 | 解決規則 |
|------|---------|
| 解放判定 | 解放条件（`unlockTowerId`）が無い塔は常に解放。有る塔は、その塔の `TowerClearRecord.cleared = true`（最上階ボス討伐済み）なら解放 |
| 到達済み最高階 | その塔の `TowerClearRecord.highestFloor`。行が無ければ `0`（未挑戦） |
| 目標階上限 `cap` | `min(highestFloor + 1, totalFloors)`。式の正は tech_api.md「操作系」。深淵の塔（Phase 5〜）は総階数を持たず `highestFloor + 1` のみ |
| クリア記録の作成 | `tower_clear_records` の行は**階クリア時**に無ければ作成する（§9）。入塔時・一覧時には作らない |
| イベントダンジョン（Phase 5〜） | 到達記録は難易度を畳み込んだキーで難易度別に引く（キー体系の正は [tech_data.md §1.1](../basic/tech_data.md)。組み立てはサーバーが行う） |

## 3. API要求・応答

要求・応答とも camelCase（[tech_api/common.md](../basic/tech_api/common.md)）。エンドポイント一覧の正は tech_api.md「操作系」。

| API | 要求 | 成功応答（200） |
|-----|------|----------------|
| `GET /api/tower/list` | なし | `TowerInfo` の配列: `id` / `name` / `dungeonName` / `totalFloors` / `unlockTowerId`（null = 最初から解放） / `unlocked` / `cleared` / `highestFloor` / `targetFloorCap` |
| `POST /api/tower/select` | `towerId`（必須）・`targetFloor`（必須・1以上）・`mode`（省略時 `auto_repeat`）・`difficulty`（Phase 5〜・イベントのみ） | `{ "status": "ok", "towerId": ..., "targetFloor": ... }` |
| `POST /api/tower/retire` | なし | `{ "status": "ok" }` |
| `PUT /api/tower/mode` | `mode`（`auto_repeat` \| `stop_on_clear`） | `{ "status": "ok", "mode": ... }` |
| `PUT /api/tower/retreat-conditions` | `hpThreshold`（0.0〜1.0） | `{ "status": "ok", "hpThreshold": ... }` |

## 4. `TOWER_` エラーコード一覧

| コード | HTTP | 発生条件 |
|--------|------|---------|
| `TOWER_ALREADY_IN_TOWER` | 400 | 入塔中（ボスラッシュ中 Phase 5〜 を含む）の select |
| `TOWER_NOT_IN_TOWER` | 400 | 塔外での retire・mode |
| `TOWER_NOT_FOUND` | 404 | `towerId` が塔マスターに存在しない |
| `TOWER_NOT_UNLOCKED` | 403 | 未解放の塔への select |
| `TOWER_INVALID_FLOOR` | 400 | `targetFloor` が上限 `cap` を超える（下限違反は Bean Validation の 422。§7 手順1） |
| `TOWER_PARTY_WIPED` | 400 | パーティ全員HP0での select（Phase 1〜2 の通常経路では到達しない防御的検証。§7 手順7） |
| `TOWER_INVALID_DIFFICULTY` | 400 | Phase 5〜。イベントダンジョンで `difficulty` 欠落、または通常塔・深淵の塔で指定 |

- クライアントはコードで分岐しない（現行フロントは `TOWER_` コードを参照せず汎用エラー表示に倒している）が、E2E・単体テストはコードで検証する
