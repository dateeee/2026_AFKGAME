# フロントエンドコーディング規約 — リアクティビティと composable

> [coding_standards_frontend.md](../coding_standards_frontend.md) の分冊。全層共通の規約は [common.md](common.md) が先。
> ベースはガイド「リアクティビティーの基礎」「コンポーザブル」「Composition API FAQ」（[basis.md](basis.md) §1）。本書はそこからの差分だけを持つ。
> ストア内の状態は [store.md](store.md)、コンポーネント側の書き方は [component.md](component.md)。

---

## 1. リアクティビティ

| # | 規約 |
|---|------|
| 1 | リアクティブな状態は **`ref()` に統一**する。`reactive()` を使わない — 分割代入・再代入で反応性を失う罠があり、`ref` は `.value` で境界が明示されるため（Composition API FAQ からの差分。既存コードも全面 `ref`） |
| 2 | 派生値は `computed()` で表現する。getter 内で状態変更・API 呼び出しの副作用を起こさない |
| 3 | 描画・監視に使わない一時値を `ref` にしない（通常の変数・引数で持つ） |
| 4 | 増え続ける配列（ログ・履歴）は**上限を決めて古いものから捨てる**（`battleStore` の `MAX_FRONTEND_LOGS` が実例。上限値の正は各設計書。[store.md](store.md) §5 #2） |
| 5 | `shallowRef` 等の最適化 API は、性能問題を計測してから使う（先回りで使わない） |

## 2. watch

| # | 規約 |
|---|------|
| 1 | 監視は `watch(対象, コールバック)` で**対象を明示**する。`watchEffect` は使わない（依存が暗黙になり、再実行の契機がコードから読めないため。使う判断は [basis.md](basis.md) §2 #5 の手順で本節を改訂してから） |
| 2 | 初期実行が要るときは `{ immediate: true }`（`App.vue` の認証監視が実例）。マウント時に1回だけなら `onMounted` を使う |
| 3 | `watch` の中で別のリアクティブ値を同期的に組み立てない（連鎖更新は `computed` で表現する） |
| 4 | `watch` 内で登録したリスナー・タイマーも §3 のクリーンアップ規約に従う（`BaseModal` の keydown 登録が実例） |

## 3. ライフサイクルとクリーンアップ

| # | 規約 |
|---|------|
| 1 | タイマー（`setInterval` / `setTimeout`）・イベントリスナー・監視は、登録した composable / コンポーネント自身が `onUnmounted`（または対応する解除関数）で必ず解除する（`usePolling` が実例） |
| 2 | `document` / `window` への直接操作は composable か `ui/` プリミティブに閉じる（`views/` から直接触らない。`BaseModal` のスクロール制御が実例） |
| 3 | DOM に依存する初期化は `onMounted`、DOM 反映後の処理は `await nextTick()` の後に行う |
| 4 | `visibilitychange` 等の画面状態イベントは composable で管理し、退避と復帰（再開判定）を**対**で書く（`usePolling` の `wasActiveBeforeHidden` が実例） |

## 4. composable 規約

| # | 規約 |
|---|------|
| 1 | `use` + 関心事の命名で `composables/` に置く。1 composable = 1 関心（ポーリングとゲームループを混ぜない） |
| 2 | 状態は `ref` のまま返し、呼び出し側で分割代入できる形にする（`reactive` で包んで返さない） |
| 3 | ストアとの線引き: 画面を跨いで残る**状態**はストアへ、タイマー・逐次制御・初期化手順という**振る舞い**は composable へ（[store.md](store.md) §1 #1） |
| 4 | モジュールスコープの状態（シングルトン）を持つ場合は、**インスタンス化の想定回数をコメントで明記**する（`usePolling` の「App.vue で1度だけインスタンス化される想定」が実例） |
| 5 | `usePolling` / `useGameLoop` の詳細設計（タイマー制御・多重実行の抑止・ストア反映順）の正は [tech_polling.md](../../tech/detail/tech_polling.md)。本書は書き方だけを持つ |
