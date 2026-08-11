# AFK GAME — API設計: エンドコンテンツ（Phase 5）

> 親: [tech_api.md](../tech_api.md) §5（索引）。全エンドポイントに適用する共通仕様は [common.md](common.md) §5.0。
> 呼び出し順は [api_sequence/endgame.md](../../../diagrams/api_sequence/endgame.md)。

---

## ボスラッシュ（Phase 5〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/boss-rush/start` | ボスラッシュ開始。通常塔探索を停止してボスラッシュモードに移行 |
| POST | `/api/boss-rush/retire` | ボスラッシュリタイア。**即時に**終了し、累積報酬を確定取得（戦闘途中でも待たない。塔リタイアと同じ扱い） |
| GET | `/api/boss-rush/ranking` | サーバーランキング取得（上位100件）。認証必須 |

## 深淵の塔（Phase 5〜）
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/abyss/ranking` | 最深到達階のサーバーランキング取得（上位100件）。認証必須 |

> 深淵の塔への入塔・目標階設定・リタイアは通常の塔と同じ `/api/tower/*` を `towerId: "abyss_tower"` で使用する（専用エンドポイントは設けない）。`/api/tower/list` では `totalFloors` を `null` で返し、階数無限を表す。

## イベントダンジョン（Phase 5〜）

機能仕様は `systems/endgame.md` §2.13（常設3種 × 固定難易度3段階）。進行は通常の塔と同じ階層制のため、専用エンドポイントは設けず `/api/tower/*` を難易度パラメータ付きで再利用する。ダンジョンIDは `trial_maze` / `treasure_vault` / `training_hall`（正は [glossary.md](../../../glossary.md)）。

| 項目 | 仕様 |
|------|------|
| 難易度の指定 | `/api/tower/select` の任意パラメータ `difficulty`（`beginner` \| `intermediate` \| `advanced`）。イベントダンジョンでは必須（欠落は400）、通常塔・深淵の塔では指定不可（指定は400）。探索中の変更は不可 |
| 一覧取得 | `/api/tower/list` はイベントダンジョンを**難易度ごとの独立エントリ**で返す（各エントリが `difficulty`（通常塔は `null`）・`highestFloor`・`targetFloorCap` を持つ。`totalFloors` は 10 固定） |
| 到達記録 | サーバーが `towerId` と `difficulty` を `towersCleared` のキーへ畳み込む（キー体系の正は [tech_data.md](../tech_data.md) §1.1）。クライアントはキーを組み立てない |

## 転生（Phase 5〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/prestige` | 転生実行（`characterId`）。LV9999チェック後、LV/EXP/SPリセット・転生ポイントを付与（付与量は [master/endgame.md §16.1](../../../data/master/endgame.md)） |
| PUT | `/api/prestige/invest` | 転生ポイント投資（`characterId`, `stat`, `points`）。指定のボーナスにポイントを割り振る |
| POST | `/api/prestige/reset` | 転生ボーナス全リセット（`characterId`）。ゴールド消費で全ポイント返還 |
