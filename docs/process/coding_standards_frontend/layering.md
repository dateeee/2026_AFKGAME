# フロントエンドコーディング規約 — 層と依存方向

> [coding_standards_frontend.md](../coding_standards_frontend.md) の分冊。**新しいファイルの置き場・依存の向きに迷ったとき**に読む。全層共通の規約は [common.md](common.md) が先。
> ディレクトリツリーの正は [tech_structure.md](../../tech/basic/tech_structure.md)。本書が持つのは各層の**責務と呼び出し可否**。

---

## 1. 層の定義

| 層（`src/` 配下） | 置くもの | 責務 |
|------------------|---------|------|
| `views/` | `<画面名>View.vue` | 画面1枚の配置と画面固有ロジック。色・寸法の判断をしない（[tech_design_system.md](../../tech/detail/tech_design_system.md)） |
| `components/ui/` | 汎用 UI プリミティブ | 見た目と操作の挙動だけ。業務知識・ストア参照を持たない |
| `components/layout/` | アプリシェル・全画面共通バナー | ヘッダ・ナビ・スクロール境界・セーフエリア |
| `components/<機能>/` | 機能別部品（`equipment/`） | 特定機能の表示部品。ストア参照可 |
| `composables/` | `use*.ts` | リアクティブなロジック（初期化・ポーリング・タイマー制御） |
| `stores/` | `<領域>Store.ts`（Pinia） | 画面を跨いで保持する状態と API 呼び出し（[store.md](store.md)） |
| `router/` | `index.ts` | ルート定義とナビゲーションガード（§3） |
| `api/` | `client.ts`・`auth.ts`・`errors.ts` | サーバー通信の唯一の出入口（[api.md](api.md)） |
| `types/` | `game.ts` | 複数層で共有する型（[common.md](common.md) §1） |
| `utils/` | 純粋関数（`format.ts`） | リアクティビティに依存しない変換・整形 |
| `assets/styles/` | `tokens.css`・`main.css` | デザイントークンとグローバル CSS（[styling.md](styling.md)） |

`tech_design_system.md` の3層（トークン / UI プリミティブ / アプリシェル）は、本表の `assets/styles/`・`components/ui/`・`components/layout/` に対応する。

## 2. 呼び出し方向

| # | 規約 |
|---|------|
| 1 | 依存は `views → components / stores / composables → api → types` の一方向。逆流・循環を作らない |
| 2 | **サーバー通信は stores の action か composables を経由する**。`views/`・`components/` から `@/api` を import しない（応答のストア反映を経路として強制し、画面ごとの反映漏れを防ぐ。[.claude/project/review/frontend.md](../../../.claude/project/review/frontend.md) §2 観点7） |
| 3 | 生の `fetch()` を書けるのは `api/` のみ。ほかの層はエンドポイント関数（[api.md](api.md) §1）を呼ぶ |
| 4 | `components/ui/` は props / emits だけで完結させる。ストア・API・ルーターに依存させない（`tech_design_system.md` §2） |
| 5 | `provide` / `inject` は使わない。状態の共有は Pinia、親子の受け渡しは props / emits に一本化する（暗黙の親子結合を作らない）。採用する場合は `InjectionKey<T>` による型付けを前提に、[basis.md](basis.md) §2 #5 の手順で本節を改訂してから |
| 6 | `utils/` は Vue に依存しない純粋関数だけを置く（`ref` を受け取らない・状態を持たない） |

## 3. ルーティング

| # | 規約 |
|---|------|
| 1 | ルート定義は `router/index.ts` に集約する。各ルートは `path`・`name`・`component` を持ち、`name` は小文字1語（`'game'`・`'equipment'`）。画面遷移は `name` で指定する（`path` を直書きしない） |
| 2 | 画面コンポーネントは `() => import('@/views/XxxView.vue')` の**遅延ロード**で登録する（初期バンドルへ全画面を含めない） |
| 3 | 認証不要の画面だけ `meta: { public: true }` を付ける。認証判定は `router.beforeEach` のガード**1箇所**で行い、各画面で判定しない |
| 4 | 画面（ルート）の追加・削除は [ui.md](../../design/systems/ui.md) の画面遷移・ナビ構成と同時に行う（正は画面仕様側） |

## 4. 開発時フォールバック

| # | 規約 |
|---|------|
| 1 | `VITE_USE_API=false` でバックエンド未起動でも起動する（[CLAUDE.md](../../../CLAUDE.md) アーキテクチャ不変条件）。判定は `api/client.ts` の `USE_API` に集約し、分岐は `api/`・`composables/` に閉じる（`views/`・`components/` へ持ち込まない） |
| 2 | ローカル戦闘のフォールバックは `composables/useBattleLocal.ts`（デバッグ用）。ストアへの反映は本実装と同じ関数を通し、フォールバック専用の反映経路を作らない |
| 3 | フォールバックの到達水準（何がどこまで動くか）は [known_issues.md](../../backlog/known_issues.md) #8 の確定が正。本書は分岐の置き場だけを定める |

## 5. ディレクトリの追加

- 新しいディレクトリ・層を切るときは `tech_structure.md` のツリーへ同時に追記し、本書 §1 の表と §2 の依存方向を更新する
- 機能別の表示部品は `components/<機能>/` へ置く（`views/` や `components/` 直下にコンポーネントを増やさない）
