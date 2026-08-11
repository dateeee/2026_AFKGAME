# AFK GAME — tick進行制御仕様

> 技術仕様の索引は [tech_spec.md](../tech_spec.md)。1tick内の戦闘処理は [tech_battle.md](tech_battle.md)、101tick以上の簡略計算は [tech_offline.md](tech_offline.md)、丸め規約は [tech_numeric.md](tech_numeric.md)。
> 本書は `POST /api/battle/tick` の**外枠**（何tick処理するか・いつコミットするか・二重実行をどう防ぐか）を定義する。戦闘の中身は扱わない。

---

## 1. 時刻とtick数の決定

| 項目 | 仕様 |
|------|------|
| 時刻の権威 | サーバーのUTC時刻のみ。クライアント送信の時刻は一切信用しない |
| 基準時刻 | `player.last_tick_at`（`timestamptz` にUTCで保存。読み出しは `Instant` で受ける） |
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
| トランザクション境界 | tick処理の Service メソッドに `@Transactional` を付与し、Spring 管理のトランザクションとして開始する |
| ロック取得 | トランザクション内で対象 `players` 行を `SELECT ... FOR UPDATE` で**行ロック**して読む（Repository のマッピング XML が発行する） |
| ロック競合時 | 待機する（`lock_timeout` 5秒。DataSource の接続プロパティで設定）。超過時は `503` + `BATTLE_TICK_BUSY` |
| ロック範囲 | `last_tick_at` の読み取り 〜 更新 〜 トランザクションコミットまで |

- ロックにより後発リクエストは先発のコミット後に `last_tick_at` を読むため `pending_ticks = 0` となり、二重付与が起きない（**追加の冪等キーを持たずに冪等になる**）
- フロント側でも多重tickを抑止する（[tech_polling.md §2](tech_polling.md)）。サーバー側ロックはその最終防衛線

## 4. トランザクション境界

| 項目 | 仕様 |
|------|------|
| 境界 | 1リクエスト = 1トランザクション（`@Transactional` を付与した Service メソッド1呼び出しで `pending_ticks` 全件をまとめてコミット） |
| コミット位置 | 全tickの処理完了 + `last_tick_at` 更新の後に**1回だけ**（メソッド正常終了時に Spring が自動コミット） |
| 例外発生時 | 全ロールバック（`@Transactional` の既定は非チェック例外で自動ロールバック）。`last_tick_at` が進まないため、次回リクエストで同じ区間を再計算する |
| 部分コミット | **禁止**。「報酬は入ったが `last_tick_at` が進んでいない」状態を作らない |
| レスポンス構築 | コミット後に行う |

- 再計算では乱数結果が変わるが、クライアントへ未返却の区間であるため差異は観測されない（[tech_rng.md §3](tech_rng.md)）
- 最大1,440tickを1トランザクションで処理するため、tick単位のDB書き込みは行わずメモリ上で集計し、最後に一括反映する

## 5. 分岐一覧（単体テスト観点）

C1網羅の対象分岐。[phases.md §3.4](../../process/phases.md) のテストリストと §3.6 の基準に対応する。

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | tick成立判定 | `elapsed < 60秒` | `pending_ticks=0`。状態のみ返却し `last_tick_at` は不変 |
| 2 | tick成立判定 | `elapsed = 60秒ちょうど` | `pending_ticks=1` |
| 3 | tick成立判定 | `elapsed = 119秒` | `pending_ticks=1`。59秒を繰り越す |
| 4 | 処理方式の選択 | `pending_ticks = 100`（閾値ちょうど） | 正規シミュレーションで処理する |
| 5 | 処理方式の選択 | `pending_ticks = 101` | 簡略計算で処理する |
| 6 | 24時間クランプ | `elapsed = 24時間ちょうど` | `pending_ticks=1440`、`capped=false` |
| 7 | 24時間クランプ | `elapsed > 24時間` | `pending_ticks=1440`、`capped=true`、`last_tick_at ← now` |
| 8 | 異常な時刻 | `last_tick_at > now` | `pending_ticks=0`。WARNINGログを残す |
| 9 | 戦闘の成立 | 塔外待機中（`IDLE`） | 戦闘なし。HP自然回復のみ（`tech_offline.md` §4）。消化した分だけ `last_tick_at` を進める |
| 10 | 戦闘の成立 | パーティが空 | 戦闘なし・状態のみ返却。`last_tick_at` は §1 のとおり進める |
| 11 | 排他制御 | ロック競合（`busy_timeout` 超過） | `503 BATTLE_TICK_BUSY` |
| 12 | 例外処理 | tick処理中の例外 | 全ロールバック・`last_tick_at` 不変 |

> WARN許容 #8・#11・#12: 例外経路（異常な時刻・ロック競合・処理中の例外）。対になる正常系は #1〜#7・#9・#10 が持つ

**#10 の `last_tick_at`**: 消化する戦闘が無くても §1 の繰り越し規則どおり
`last_tick_at ← last_tick_at + pending_ticks × 60秒` とする。据え置くと未処理tickが際限なく積み上がり、
編成を戻した瞬間に §2 のクランプ（#7）が働いて `capped=true`（切り詰めた）を返すため。

## 6. Java 実装時に満たすこと（未実装）

本仕様のうち Java 実装が未達の項目。移行 STEP 3 の実装で本仕様どおり満たす
（[java_migration.md](../../backlog/java_migration.md)）。

| 項目 | 満たすべき仕様 |
|------|--------------|
| 簡略計算 | 10tickサンプルの平均 × 残り ではなく、`tech_offline.md` §4 の期待値計算を使う |

§1 の端数繰り越し・§2 のクランプ（`capped`）・§3 の行ロック（`SELECT ... FOR UPDATE` と `BATTLE_TICK_BUSY`）は
`BattleServiceImpl` で実装済み（3-B 製造①-iii）。残りは `OfflineCalculator` を実装する回で満たす。
