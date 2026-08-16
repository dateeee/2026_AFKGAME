# フロントエンドコーディング規約 — 共通

> [coding_standards_frontend.md](../coding_standards_frontend.md) の分冊。**どの層を書くときも本書を先に読む**。
> ベースはガイド「[TypeScript で Vue を使用する](https://ja.vuejs.org/guide/typescript/overview)」とスタイルガイドの命名規則群（[basis.md](basis.md) §1）。本書はそこからの差分だけを持つ。
> 層の定義と呼び出し可否は [layering.md](layering.md)、層固有の規約は [component.md](component.md)・[composition.md](composition.md)・[store.md](store.md)・[api.md](api.md)・[styling.md](styling.md)・[test.md](test.md)。

---

## 1. 配置と置き場

| 対象 | 置き場 |
|------|--------|
| 複数層・複数画面で共有する型 | `types/game.ts`（バックエンド Resource と対応する型もここ。[api.md](api.md) §2） |
| 単一モジュール専用の型 | 定義元ファイル（`api/errors.ts` の `ApiError`、`components/ui/icons.ts` の `IconName`） |
| 画面を跨いで使う表示名・定数 | 関連が最も強いモジュールで `export` する（`RARITY_LABELS` は `equipmentStore.ts`）。参照元が3層以上に広がったら定義専用ファイルへ昇格する |
| コンポーネント固有の定数 | そのファイル内に `const` + 理由コメント（`MAX_FRONTEND_LOGS` が実例） |
| リアクティビティに依存しない変換・整形 | `utils/`（[layering.md](layering.md) §1） |

## 2. 命名

| 対象 | 規約 | 例 |
|------|------|-----|
| コンポーネント（SFC） | PascalCase・**省略しない完全な単語**。分類別の接頭辞は [component.md](component.md) §4 | `BaseButton.vue` |
| ts ファイル | camelCase | `navItems.ts` |
| composable | `use` + 関心事 | `usePolling` |
| ストア | `use<領域>Store`（[store.md](store.md) §6） | `useAuthStore` |
| 変数・関数 | camelCase。boolean は `is` / `has` / `can` / `was` | `isAuthenticated` |
| 定数 | UPPER_SNAKE_CASE | `TICK_INTERVAL_MS` |
| 型 | PascalCase（接頭辞 `I` を付けない） | `BattleLogEntry` |
| CSS クラス | kebab-case（[styling.md](styling.md) §2） | `stat-bar-hp` |
| モジュール内部専用の関数 | `_` 接頭辞（`return`・`export` しない） | `_handleAuthResponse` |

- 略語は先頭のみ大文字（`ApiError`・`postTick`）

## 3. TypeScript 記述規約

| # | 規約 |
|---|------|
| 1 | `tsconfig.json` の `strict: true` を前提に書く。型エラーを `@ts-ignore` / `@ts-expect-error` で消さない |
| 2 | `any` を使わない。型が不明な外部値は `unknown` で受けて絞り込む（`errors.ts` の `errorMessage()` が実例） |
| 3 | `as` キャストを使ってよいのは2箇所だけ: ① DOM イベントの `event.target as HTML...Element`（`BaseSelect.vue`）② API 境界での JSON 解析結果への型付け（`api/` 内。[api.md](api.md) §2 #3）。それ以外は型ガード・ジェネリクスで解決する |
| 4 | 非 null アサーション（`!`）を使わない。`??`・`?.`・早期 return で絞り込む |
| 5 | null を取りうる状態は `T \| null` を明示する（`ref<OfflineSummary \| null>(null)`） |
| 6 | `export` する関数には戻り値型を書く（`stopActivePolling(): void`）。モジュール内部の短い関数は推論に任せてよい |

## 4. import 規約

| # | 規約 |
|---|------|
| 1 | 順序: ① 外部ライブラリ（`vue` → `pinia`・`vue-router` → その他）② 型（`import type`）③ 内部モジュール（`@/api` → `@/stores` → `@/composables` → `@/components` → `@/utils`） |
| 2 | `src/` 内の参照は `@/` エイリアスで書く。相対 import は同一ディレクトリ内のみ（`./usePolling`） |
| 3 | 型だけを使う import は `import type` で書く（`isolatedModules` 前提。値と型が混在するときは `import { x, type Y }`） |
| 4 | 循環 import を作らない。ストア ↔ composable で相互参照が要るときは、関数単位の `export` で片方向に倒す（`stopActivePolling` が実例。[store.md](store.md) §3 #2） |

## 5. コメント・JSDoc

| # | 規約 |
|---|------|
| 1 | コメント・JSDoc は日本語。`export` する関数・composable・ストア・複雑な定数に JSDoc を書く（1行目は「〜する。」の要約1文） |
| 2 | ファイル冒頭に役割コメントを書く（そのファイルが何を持ち、何を持たないか。`api/client.ts` が実例） |
| 3 | 仕様に由来する値・挙動には**仕様書の参照**を書く（`ui.md §設定画面`・`tech_polling.md §5` の形。実装の根拠を追える状態にする） |
| 4 | 行コメントは「何をしているか」ではなく「**なぜそうしたか**」を書く。コードを読めば分かることを繰り返さない |
| 5 | 意図的な未実装・仮実装はコメントに理由と解消条件を書く（`TODO` だけを残さない） |

## 6. 禁止事項

各分冊からの再掲（レビュー用の一覧）。

| 禁止 | 代わりに |
|------|---------|
| Options API・`defineComponent` | `<script setup lang="ts">`（[basis.md](basis.md) §2 #1） |
| `reactive()` | `ref()`（[composition.md](composition.md) §1 #1） |
| `console.*` の残置 | エラーは `errorMessage()` → 画面表示（[api.md](api.md) §6）。デバッグ出力はコミット前に消す |
| `v-html` | テンプレート補間・`BaseIcon`。唯一の例外は静的定数の SVG 描画（[component.md](component.md) §3 #4） |
| ユーザー・サーバー由来値の `:href` / `:style` 直接バインド | 許可リスト・トークン参照への変換（[component.md](component.md) §3 #5） |
| `localStorage` の直接操作 | トークンは `api/client.ts` の管理関数（保管先の正は [tech_auth.md](../../tech/detail/tech_auth.md) §7）。ゲーム状態・設定はサーバー保存 |
| 生の16進数カラー・寸法の直値 | `var(--*)` トークン（[tech_design_system.md](../../tech/detail/tech_design_system.md)） |
| 戦闘計算・報酬計算のフロント実装 | サーバー応答の反映のみ（[basis.md](basis.md) §2 #2。例外は `useBattleLocal`） |
| タイマー・リスナーの解除漏れ | composable 内で管理し `onUnmounted` で解除（[composition.md](composition.md) §3 #1） |
| `$parent`・`scoped` 内の要素セレクタ（スタイルガイド優先度D） | props / emits・クラスセレクタ |
| `@ts-ignore`・`any`・非 null アサーション | §3 |
