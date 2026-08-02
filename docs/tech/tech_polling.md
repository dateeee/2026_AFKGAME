# AFK GAME — フロントエンドのtick制御

> 技術仕様の索引は [tech_spec.md](tech_spec.md)。サーバー側のtick制御は [tech_tick.md](tech_tick.md)、ゲームループ全体像とリトライ方針は [tech_architecture.md](tech_architecture.md)、ファイル構成は [tech_structure.md](tech_structure.md)。
> 本書は `usePolling.ts` / `useGameLoop.ts` の詳細設計（タイマー制御・多重実行の抑止・ストア反映）を定義する。

---

## 1. ポーリングのライフサイクル

| 契機 | 動作 |
|------|------|
| 起動（認証済み） | `GET /api/game/state` → 即時tick → 60秒間隔タイマー開始 |
| `visibilitychange`（hidden） | タイマー停止（tickは発火しない） |
| `visibilitychange`（visible） | 即時tick → タイマー再開 |
| `focus` | 直近tickから60秒以上経過していれば即時tick（§3） |
| SPA内の画面遷移 | 継続（停止しない） |
| プレイヤー操作API成功時 | レスポンスの最新状態で上書き。tickは追加発火しない |
| ログアウト・コンポーネント破棄 | タイマー停止・イベントリスナー解除 |

- 通信エラー時のリトライ（3回・指数バックオフ）は [tech_architecture.md](tech_architecture.md) が正。本書では重複定義しない
- `401` はトークンリフレッシュ後に1回だけ再試行する（[tech_auth.md](tech_auth.md)）。リフレッシュ失敗時はポーリングを停止しログイン画面へ

## 2. 多重tickの抑止

サーバー側にも排他がある（[tech_tick.md §3](tech_tick.md)）が、無駄なリクエストと画面のちらつきを避けるためフロント側でも抑止する。

| 抑止対象 | 方式 |
|---------|------|
| 同一タブでの再入 | **in-flightフラグ**。tick実行中は新規tickを発火せず破棄する（キューに積まない） |
| 起動時の重複 | 初期化の即時tickが完了してからタイマーを開始する |
| `visible` 連打 | 直近tick完了から5秒以内の即時tickは抑止する |
| 多重タブ | **リーダー選出**。リーダータブのみがtickを実行し、結果を他タブへ配信する |

### 2.1 多重タブのリーダー選出

| 項目 | 仕様 |
|------|------|
| 主方式 | `BroadcastChannel('afkgame')` |
| フォールバック | 非対応環境では `localStorage` のキー `tick_leader`（`{tabId, expiresAt}`） |
| リース期間 | 90秒。リーダーは30秒ごとに更新する |
| 失効時 | 期限切れを検知したタブが自身をリーダーとして書き込み、再選出する |
| 非リーダーの動作 | tickを実行せず、リーダーが配信した `updatedState` を反映する |
| 配信内容 | tickレスポンスそのもの（`battleLogs` / `updatedState` / `offlineSummary`） |

- リーダーが応答しなくなっても、最長90秒で他タブが引き継ぐ（ゲーム進行はサーバー側の `last_tick_at` 基準なので損失は生じない）

## 3. タイマー精度とドリフト

`setInterval` はバックグラウンドタブでスロットリングされ、端末スリープ中は停止する。**フロント側で正確な60秒を保証しない**設計とする。

| 事象 | 対処 |
|------|------|
| バックグラウンドでのスロットリング | 許容する。遅延分は次回tickでまとめて計算される（[tech_tick.md §1](tech_tick.md) の端数繰り越し） |
| 端末スリープ・PC休止からの復帰 | `visibilitychange` が発火しないケースがあるため `focus` でも即時tickを行う |
| タイマーのドリフト蓄積 | 補正しない。進行量はサーバーの `last_tick_at` が決めるため、発火間隔のズレは進行量に影響しない |
| フロントでの経過時間計算 | **行わない**（サーバー権威。端末時計は信用しない） |
| 残り時間の表示 | 表示専用。次回tick予測時刻は「直近tick成功時刻 + 60秒」で描画する |

## 4. ストアへの反映

tickレスポンスの反映順序を固定し、部分反映による表示不整合を防ぐ。

```
1. レスポンスを検証（必須フィールドの欠落は反映せずエラー扱い）
2. gameStore      … 塔・階・進行状態
3. playerStore    … ゴールド・キャラクター
4. equipmentStore … 装備一覧・装備中スロット
5. battleStore    … 戦闘ログ追加・オフラインサマリー
6. 接続エラーバナーを解除
```

| 項目 | 仕様 |
|------|------|
| 原子性 | 上記2〜5は例外を発生させない前提で連続実行する。1で弾かれた場合は**どのストアも更新しない** |
| 楽観更新 | 行わない。すべてサーバーレスポンスで上書きする（サーバー権威） |
| 戦闘ログの保持 | `settings.battleLogCount`（20/50/100/200）を超えた古いログをクライアント側で破棄する |
| オフラインサマリー | `offlineSummary` が非nullのときのみモーダル表示。`capped` が真なら上限到達の注記を添える |

## 5. 分岐一覧（テスト観点）

単体レベルの検証はE2E（Playwright）に統合する（[development_process.md §3.6](../development_process.md)）。

| # | 分岐 | 期待結果 |
|---|------|---------|
| 1 | tick実行中に次のtickが発火 | 2本目は破棄され、リクエストは1本のみ |
| 2 | タブを隠す → 戻す | 隠している間は0リクエスト、復帰時に即時1リクエスト |
| 3 | 5秒以内の `visible` 連打 | 追加リクエストなし |
| 4 | 2タブ同時起動 | tickリクエストはリーダーの1本のみ、両タブの表示が一致 |
| 5 | リーダータブを閉じる | 90秒以内に残タブがtickを再開 |
| 6 | tickが3回連続失敗 | 接続エラーバナー表示・ポーリングは継続 |
| 7 | 失敗後に成功 | バナー解除・状態反映 |
| 8 | `401` 応答 | リフレッシュ後に1回再試行 |
| 9 | `503 BATTLE_TICK_BUSY` | リトライせず次tickまで待機 |
| 10 | `offlineSummary` あり | サマリーモーダルを1回だけ表示 |

## 6. 現行実装との差異（製造工程の是正対象）

| 箇所 | 現行実装 | 本仕様 |
|------|---------|-------|
| [usePolling.ts:14](../../frontend/src/composables/usePolling.ts) | in-flightフラグなし（再入可能） | §2 再入抑止 |
| [usePolling.ts:50](../../frontend/src/composables/usePolling.ts) | `visible` のたびに無条件で即時tick | §2 5秒以内は抑止 |
| [usePolling.ts](../../frontend/src/composables/usePolling.ts) | 多重タブ制御なし | §2.1 リーダー選出 |
| [usePolling.ts](../../frontend/src/composables/usePolling.ts) | `focus` 未購読（スリープ復帰で取りこぼす） | §3 `focus` でも即時tick |
| [usePolling.ts:18](../../frontend/src/composables/usePolling.ts) | 検証なしで逐次ストアへ代入 | §4 検証してから反映 |

---

> 変更履歴は [tech_spec.md](tech_spec.md) を参照。
