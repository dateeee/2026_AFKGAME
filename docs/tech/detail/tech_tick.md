# AFK GAME — tick進行制御仕様

> 技術仕様の索引は [tech_spec.md](../tech_spec.md)。1tick内の戦闘処理は [tech_battle.md](tech_battle.md)、101tick以上の簡略計算は [tech_offline.md](tech_offline.md)、丸め規約は [tech_numeric.md](tech_numeric.md)。
> 本書は `POST /api/battle/tick` の**外枠**（何tick処理するか・いつコミットするか・二重実行をどう防ぐか）を定義する。戦闘の中身は扱わない。

---

## 1. 時刻とtick数の決定

| 項目 | 仕様 |
|------|------|
| 時刻の権威 | サーバーのUTC時刻のみ。クライアント送信の時刻は一切信用しない |
| 基準時刻 | `player.last_tick_at`（UTCで保存。SQLite由来のnaive値はUTCとして解釈する） |
| tick間隔 | 60秒固定（`TICK_INTERVAL_SECONDS`） |
| 未処理tick数 | `pending_ticks = floor((now − last_tick_at) / 60)` |
| **端数の扱い** | **繰り越す**。`last_tick_at ← last_tick_at + pending_ticks × 60秒`（`now` を代入しない） |
| `pending_ticks = 0` | 戦闘処理を行わず現在のゲーム状態のみ返す。`last_tick_at` は更新しない |

**端数繰り越しの根拠**: `last_tick_at ← now` とすると毎回 0〜59秒が切り捨てられる。60秒ポーリングでも通信遅延で実測間隔は 60秒+α になるため、切り捨て方式では**プレイし続けるほど進行が遅れる**（誤差1秒/tickでも24時間で約24tick分の損失）。繰り越しにより「経過実時間 ÷ 60 = 累計tick数」が保証される。

### 1.1 異常な時刻の扱い

| 条件 | 挙動 |
|------|------|
| `last_tick_at > now`（サーバー時刻の巻き戻し・データ不整合） | `pending_ticks = 0` として扱い、`last_tick_at` は更新しない。WARNINGログ（`reason=clock_skew`） |
| `last_tick_at` が未設定 | アカウント作成時刻を初期値とする |

## 2. 上限クランプ

| 項目 | 仕様 |
|------|------|
| 最大放置時間 | 24時間（`MAX_OFFLINE_HOURS`）= 1,440 tick |
| 超過時 | `pending_ticks = 1440` に切り詰め、**超過分は破棄**する（`last_tick_at ← now`） |
| 通知 | オフラインサマリーに `capped: true` を含め、UI側で「24時間分まで計算されました」と表示する |

- 超過分を破棄するのは、繰り越すと「24時間ごとにログインすれば無限に蓄積できる」ことになり放置上限が無意味になるため
- **クランプが発生した時のみ** §1 の端数繰り越し規則の例外となる（`last_tick_at ← now`）

## 3. 同時実行制御（多重tickの防止）

同一プレイヤーの `POST /api/battle/tick` が並行実行されると、双方が同じ `last_tick_at` を読んで同一区間を二重計算し、報酬が二重付与される。

| 発生源 | 例 |
|--------|-----|
| 多重タブ | 2つのタブが各自60秒ポーリングを回す |
| リトライ | タイムアウト後のリトライ（サーバー側は処理成功済み） |
| 起動時の重複 | 初期化時の即時tickとポーリング初回tickの競合 |

### 3.1 排他方式

| 項目 | 仕様 |
|------|------|
| ロック取得 | tick処理の先頭で対象 `players` 行を**排他ロック**して読む |
| SQLite | `BEGIN IMMEDIATE` でトランザクションを開始する（書き込みロックを先に取得） |
| 他RDBMSへ移行時 | `SELECT ... FOR UPDATE` に置き換える |
| ロック競合時 | 待機する（`busy_timeout` 5秒）。超過時は `503` + `BATTLE_TICK_BUSY` |
| ロック範囲 | `last_tick_at` の読み取り 〜 更新 〜 コミットまで |

- ロックにより後発リクエストは先発のコミット後に `last_tick_at` を読むため `pending_ticks = 0` となり、二重付与が起きない（**追加の冪等キーを持たずに冪等になる**）
- フロント側でも多重tickを抑止する（[tech_polling.md §2](tech_polling.md)）。サーバー側ロックはその最終防衛線

## 4. トランザクション境界

| 項目 | 仕様 |
|------|------|
| 境界 | 1リクエスト = 1トランザクション（`pending_ticks` 全件をまとめてコミット） |
| コミット位置 | 全tickの処理完了 + `last_tick_at` 更新の後に**1回だけ** |
| 例外発生時 | 全ロールバック。`last_tick_at` が進まないため、次回リクエストで同じ区間を再計算する |
| 部分コミット | **禁止**。「報酬は入ったが `last_tick_at` が進んでいない」状態を作らない |
| レスポンス構築 | コミット後に行う |

- 再計算では乱数結果が変わるが、クライアントへ未返却の区間であるため差異は観測されない（[tech_rng.md §3](tech_rng.md)）
- 最大1,440tickを1トランザクションで処理するため、tick単位のDB書き込みは行わずメモリ上で集計し、最後に一括反映する

## 5. 分岐一覧（単体テスト観点）

C1網羅の対象分岐。[phases.md §3.4](../../process/phases.md) のテストリストと §3.6 の基準に対応する。

| # | 分岐 | 期待結果 |
|---|------|---------|
| 1 | `elapsed < 60秒` | `pending_ticks=0`、状態のみ返却、`last_tick_at` 不変 |
| 2 | `elapsed = 60秒ちょうど` | `pending_ticks=1` |
| 3 | `elapsed = 119秒` | `pending_ticks=1`、59秒を繰り越す |
| 4 | `pending_ticks = 100`（閾値ちょうど） | 正規シミュレーション |
| 5 | `pending_ticks = 101` | 簡略計算 |
| 6 | `elapsed = 24時間ちょうど` | `pending_ticks=1440`、`capped=false` |
| 7 | `elapsed > 24時間` | `pending_ticks=1440`、`capped=true`、`last_tick_at ← now` |
| 8 | `last_tick_at > now` | `pending_ticks=0`、WARNINGログ |
| 9 | 塔外待機中（`IDLE`） | 戦闘なし。HP自然回復のみ（[tech_offline.md §4](tech_offline.md)） |
| 10 | パーティが空 | 戦闘なし・状態のみ返却 |
| 11 | ロック競合（`busy_timeout` 超過） | `503 BATTLE_TICK_BUSY` |
| 12 | tick処理中の例外 | 全ロールバック・`last_tick_at` 不変 |

## 6. 現行実装との差異（製造工程の是正対象）

| 箇所 | 現行実装 | 本仕様 |
|------|---------|-------|
| [battle.py:41](../../../backend/app/routers/battle.py) | 24時間超を無言で切り詰め（クランプ自体は §2 どおり） | §2 `capped` をサマリーに含める |
| [battle.py:28](../../../backend/app/routers/battle.py) | 行ロックなし（二重tickが成立する） | §3 排他ロック |
| [battle.py:57](../../../backend/app/routers/battle.py) | 簡略計算 = 10tickサンプルの平均 × 残り | [tech_offline.md §4](tech_offline.md) の期待値計算 |

§1 の端数繰り越しは実装済み（[battle.py:73](../../../backend/app/routers/battle.py)。backend-review ISSUE-102）。
