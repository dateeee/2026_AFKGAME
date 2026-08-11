# フロントエンドコーディング規約 — 記述

> 親: [coding-standards-frontend.md](../coding-standards-frontend.md)。本書は同 **§3** を担当する。
> 層の責務は §2 [layering.md](layering.md)、状態と API は §4 [state-api.md](state-api.md)。正はプロジェクト側のコーディング規約ドキュメント。

## 3. 記述

| # | 規約 |
|---|------|
| 1 | SFC は `<script setup lang="ts">` のみ（Options API・`defineComponent` 禁止）。ブロック順は script → template → style（`scoped` 必須） |
| 2 | `strict: true` 前提。`any`・`@ts-ignore`・非 null アサーション（`!`）禁止。不明な外部値は `unknown` で受けて絞り込む |
| 3 | `as` キャストは「DOM イベントの `target`」「API 境界の JSON 型付け（API 層内のみ）」の2パターンだけ |
| 4 | props は `defineProps<T>()` の型引数形式 + `withDefaults`、emits は `defineEmits` のタプル型形式。props を子で書き換えない |
| 5 | `v-for` は安定 ID の `:key` 必須。`v-if` と `v-for` を同じ要素に書かない |
| 6 | `v-html` 禁止（例外は正の側に明記された静的定数の描画のみ）。ユーザー・サーバー由来値を `:href` / `:style` へ直接束ねない |
| 7 | 命名: コンポーネント PascalCase（基盤部品は共通接頭辞・完全な単語）、composable `use*`、ストア `use<領域>Store`、boolean `is/has/can`、定数 UPPER_SNAKE_CASE、CSS kebab-case |
| 8 | import 順: 外部 → `import type` → 内部（エイリアス経由。相対は同一ディレクトリのみ）。循環 import 禁止 |
| 9 | コメント・JSDoc は日本語で「なぜ」を書く。`export` 関数に JSDoc + 戻り値型、仕様由来の値には仕様書参照を添える |
| 10 | `console.*` を残さない。デバッグ出力はコミット前に消し、エラーは表示用の変換関数を通して画面へ出す |
| 11 | スタイルの値はデザイントークン（`var(--*)`）参照。生の16進数・寸法直値禁止。ユーティリティクラス列挙によるスタイリングをしない |
