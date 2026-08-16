# フロントエンドコーディング規約 — コンポーネント（SFC）

> [coding_standards_frontend.md](../coding_standards_frontend.md) の分冊。全層共通の規約は [common.md](common.md) が先。
> ベースはガイド「コンポーネントの基礎／詳細」「ベストプラクティス > アクセシビリティ／セキュリティー」とスタイルガイド（[basis.md](basis.md) §1）。本書はそこからの差分だけを持つ。
> リアクティビティ・ライフサイクルは [composition.md](composition.md)、見た目の書き方は [styling.md](styling.md)。

---

## 1. SFC の構成

| # | 規約 |
|---|------|
| 1 | ブロック順は `<script setup lang="ts">` → `<template>` → `<style scoped>`。`lang="ts"` を省略しない |
| 2 | 1ファイル1コンポーネント。`.vue` から別のコンポーネントを `export` しない |
| 3 | `<script setup>` 内の記述順: import → `defineProps` → `defineEmits` → ストア・composable の確立 → ローカル状態（`ref`）→ 算出（`computed`）→ 関数 → `watch`・ライフサイクル |
| 4 | `<style>` は必ず `scoped`。グローバル CSS を書けるのは `assets/styles/main.css` だけ（[styling.md](styling.md) §2 #1） |

## 2. Props・Emits

| # | 規約 |
|---|------|
| 1 | `defineProps<T>()` の**型引数形式**で宣言する（実行時宣言オブジェクトを使わない）。既定値は `withDefaults()` |
| 2 | `defineEmits<{ close: [] }>()` の**タプル型形式**で宣言する。ペイロードの型を省略しない（`{ click: [MouseEvent] }`） |
| 3 | props は読み取り専用。子で書き換えず、変更は emits で親へ返す（単方向データフロー。スタイルガイド優先度A） |
| 4 | boolean props は「付けたら有効」に設計する（既定 false。`persistent`・`block` が実例） |
| 5 | props には表示に必要な最小の値を渡す。ストアのオブジェクトを丸ごと渡さない（`ui/` プリミティブへの業務型の流入を防ぐ。[layering.md](layering.md) §2 #4） |
| 6 | 使い方が自明でない props にだけ JSDoc を書く（`/** アイコンのみのボタン。ラベルは aria-label で渡すこと */` が実例） |

## 3. テンプレート規約

| # | 規約 |
|---|------|
| 1 | `v-for` には安定した一意 ID で `:key` を必ず付ける（スタイルガイド優先度A）。並び替え・挿入がある一覧に配列 index をキーにしない |
| 2 | `v-if` と `v-for` を同じ要素に書かない（スタイルガイド優先度A。`computed` で絞り込むか `<template>` で分ける） |
| 3 | テンプレート式は一目で読める長さに留める。条件・変換が2段を超えたら `computed` か関数へ出す |
| 4 | `v-html` は使わない。唯一の例外は**自前の静的定数**を描画する `BaseIcon`（`icons.ts` の SVG パス）。ユーザー・サーバー由来の文字列が渡る経路を作らない（XSS。ガイド「セキュリティー」） |
| 5 | `:href`・`:style` にユーザー・サーバー由来の値を直接束ねない（URL・スタイル注入）。色は `RARITY_COLORS` のように**トークン参照へ変換してから**束ねる |
| 6 | イベントは `@click="fn"` の関数参照か1文の式で書く。複数文をテンプレートに書かない |

## 4. 分類と命名

| ディレクトリ | 接頭辞 | 中身 |
|-------------|--------|------|
| `components/ui/` | `Base` | 汎用プリミティブ（ボタン・カード・入力・表示部品）。業務知識なし |
| `components/layout/` | シェル骨格は `App`、全画面共通バナーは裸名 | `AppShell`・`AppHeader`・`AppNav` / `ConnectionBanner`・`GuestUpgradeBanner` |
| `components/<機能>/` | 機能名 | `EquipmentCard`。ストア参照可 |
| `views/` | —（接尾辞 `View`） | `GameView.vue` |

- `ui/` の接頭辞はスタイルガイド優先度B「ベースコンポーネントの名前」の適用で、**例外を作らない**（`BaseIcon`・`BaseNumberStepper`・`BaseStatBar` も接頭辞に従う）
- 同じ見た目・挙動を画面側で再実装しない。部品の不足は `ui/` への追加が先（[tech_design_system.md](../../tech/detail/tech_design_system.md) §2）
- コンポーネントでないモジュール（`navItems.ts`・`icons.ts`）は camelCase の ts ファイルとして同居してよい

## 5. スロット・Teleport・テンプレート参照

| # | 規約 |
|---|------|
| 1 | 既定スロットで足りる部品に名前付きスロットを増やさない。名前付きは「構造が固定された枠」（ヘッダ・フッタ）に限る |
| 2 | `<Teleport>` は `ui/` プリミティブの中でだけ使う（`BaseModal`）。画面から直接使わない（重なり順・フォーカス管理を1箇所に集める） |
| 3 | テンプレート参照は `ref<HTMLElement \| null>(null)` で型付けする（`GameView` の `logScroller` が実例）。DOM 反映後の操作は `await nextTick()` の後に行う |
| 4 | `defineExpose` を使わない（親から子のメソッドを呼ぶ設計を作らない。必要になったら props / emits へ設計を戻す） |

## 6. 表示状態（ローディング・空・エラー）

| # | 規約 |
|---|------|
| 1 | 非同期データを表示する画面・一覧は、①ローディング ②空状態 ③エラー の3状態を明示的に描画する（データがある前提だけのテンプレートを書かない） |
| 2 | ローディング・エラーの状態はストアが持ち（[store.md](store.md) §4 #2）、画面はそれを描画するだけにする |
| 3 | フォームは送信前にクライアント側で即時バリデーションする。サーバー検証エラー（`VALIDATION_ERROR`）は `error.details` の `target` + `code` から**フロントが文言を組み立てて**該当項目へ表示する（[tech_error_handling.md](../../tech/basic/tech_error_handling.md)） |
| 4 | 通信断・セッション失効の全画面共通表示は [api.md](api.md) §6。画面ごとに再実装しない |

## 7. アクセシビリティ

| # | 規約 |
|---|------|
| 1 | 操作要素はセマンティクスで作る（押すのは `<button>`、遷移は `<router-link>` / `<a>`）。`div` + `@click` を作らない |
| 2 | アイコンのみのボタンには `aria-label` を必ず付ける（`BaseButton` の `iconOnly`） |
| 3 | フォーム入力はラベルと関連付ける（`BaseField` / `BaseTextInput` を使う） |
| 4 | 見出し（`h1`〜）は階層順に使う。装飾目的でレベルを選ばない |
| 5 | ロールとアクセシブルネームで要素を特定できる状態を保つ。**E2E のロケータ戦略（`getByRole`。[test.md](test.md) §3）はこの規約が前提**で、崩すとテストが書けなくなる |
| 6 | モーション・アニメーションは `prefers-reduced-motion` を尊重する（実装は `main.css`。[styling.md](styling.md) §1） |
