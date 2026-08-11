# フロントエンドコーディング規約 — ストア（Pinia）

> [coding_standards_frontend.md](../coding_standards_frontend.md) の分冊。全層共通の規約は [common.md](common.md) が先。
> ベースは [Pinia 公式](https://pinia.vuejs.org/)「Core Concepts」（[basis.md](basis.md) §1）。本書はそこからの差分だけを持つ。
> API 呼び出しの書き方は [api.md](api.md)、composable との線引きは [composition.md](composition.md) §4。

---

## 1. 責務と作成単位

| # | 規約 |
|---|------|
| 1 | ストアは「**画面を跨いで保持する状態**」だけに使う。1画面で完結する UI 状態（モーダル開閉・選択中の項目）はコンポーネントの `ref` に置く |
| 2 | 現在の構成は、サーバー状態の写像（`gameStore`・`playerStore`・`equipmentStore`・`battleStore`）とセッション（`authStore`）。新設は「複数画面から参照する状態」が生まれたときだけ |
| 3 | **サーバー権威**（[basis.md](basis.md) §2 #2）: ストアはサーバー応答を反映する側で、ゲームルールを計算しない。tick 応答・状態取得の反映は `loadFromState()` パターンで受ける |

## 2. Setup Store 形式

| # | 規約 |
|---|------|
| 1 | `defineStore('<領域>', () => { ... })` の **Setup Store 形式**だけを使う（Options Store 禁止。[profile.md](../../../.claude/project/profile.md) §3） |
| 2 | state は `ref`、getter は `computed`、action は `function` で定義し、公開するものを `return` で列挙する |
| 3 | **state の `ref` はすべて `return` に含める**（返さない `ref` は Pinia の追跡（devtools・プラグイン）から外れる） |
| 4 | 内部専用の関数は `return` に含めず `_` 接頭辞を付ける（`_handleAuthResponse` が実例。[common.md](common.md) §2） |

## 3. ストア間参照

| # | 規約 |
|---|------|
| 1 | 他ストアは **action・関数の中で** `useXxxStore()` を呼んで取得する（モジュールのトップレベルやセットアップ直下で確立しない。Pinia 初期化前アクセスと循環を防ぐ。`battleStore.addBattleLogs` が実例） |
| 2 | ストア間の循環依存を作らない。ストア ↔ composable の相互参照が要るときは、片側を関数単位の import に倒す（`authStore` → `stopActivePolling()` が実例。[common.md](common.md) §4 #4） |
| 3 | コンポーネントでストアの state を分割代入するときは `storeToRefs()` を使う（素の分割代入は反応性を失う）。分割せずプロパティ参照（`gameStore.towers`）するなら不要。action はそのまま分割してよい |

## 4. API 通信と状態

| # | 規約 |
|---|------|
| 1 | サーバーへの取得・更新はストアの action 内で `@/api` のエンドポイント関数を呼ぶ（[layering.md](layering.md) §2 #2） |
| 2 | 非同期 action を持つストアは `loading`・`error` を `ref` で持ち、開始時に設定・完了時に解除する（`authStore` が実例）。エラーは `errorMessage()` で文字列化して保持する |
| 3 | 楽観更新をしない。更新系はサーバー応答（更新後の状態）でストアを上書きする（サーバー権威） |
| 4 | 複数ストアを跨ぐ反映は呼び出し側（composable の tick 反映）が各ストアの `loadFromState()` を順に呼ぶ。**ストアの中から他ストアの state を書き換えない**（読み取りは #1 の形で可） |

## 5. リセットと保持上限

| # | 規約 |
|---|------|
| 1 | 全ストアに `reset()` を実装し、ログアウト・アカウント切替で呼ぶ（前アカウントの状態を残さない。`battleStore.reset()` が実例） |
| 2 | 増え続ける state は上限で刈る（[composition.md](composition.md) §1 #4）。設定値に依存する上限は `Math.min(設定値, 定数上限)` で防御する（`MAX_FRONTEND_LOGS` が実例） |

## 6. 命名

| 対象 | 規約 | 例 |
|------|------|-----|
| ファイル | `<領域>Store.ts` | `authStore.ts` |
| 関数 | `use<領域>Store` | `useAuthStore` |
| `defineStore` の id | 領域名（小文字1語・関数名と揃える） | `'auth'` |
| action | 動詞から始める | `loadTowers`・`addBattleLogs` |
