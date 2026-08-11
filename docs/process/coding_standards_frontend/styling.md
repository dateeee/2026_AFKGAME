# フロントエンドコーディング規約 — スタイル

> [coding_standards_frontend.md](../coding_standards_frontend.md) の分冊。全層共通の規約は [common.md](common.md) が先。
> **ビジュアル（色・寸法・部品・アンチパターン）の正は [tech_design_system.md](../../tech/detail/tech_design_system.md)**（トークン3層・禁止事項表）。本書は「コードとしてどう書くか」だけを持つ。

---

## 1. 役割分担（Tailwind の使い方）

| # | 規約 |
|---|------|
| 1 | Tailwind CSS 4 の役割は**トークンエンジン**に限る: `tokens.css` の `@theme` によるトークン定義と、`main.css` の `@layer utilities` にある少数の共通ユーティリティ（セーフエリア・タップ領域・数値等幅） |
| 2 | **テンプレートへユーティリティクラスを並べるスタイリングはしない**（装飾は scoped CSS + `var(--*)`）。共通ユーティリティを増やす判断は `tech_design_system.md` の改訂が先 |
| 3 | グローバル CSS（リセット・base・ユーティリティ）を書けるのは `assets/styles/main.css` だけ。トークンの追加・変更は `tokens.css` だけ |

## 2. scoped CSS の書式

| # | 規約 |
|---|------|
| 1 | コンポーネントのスタイルは `<style scoped>` に書く（[component.md](component.md) §1 #4） |
| 2 | クラス名は kebab-case で部品の接頭辞を付ける（`.btn-primary`・`.stat-bar-hp`）。汎用すぎる名前（`.item`・`.box`）を作らない |
| 3 | 色・寸法の値は `var(--*)` トークンで書く（生の16進数・`rgb()` 禁止。`tech_design_system.md` 禁止事項表）。トークンに無い値が要るときは `tokens.css` への追加が先 |
| 4 | `:deep()` は原則使わない（子の見た目は子の props で変える）。使う場合は理由コメントを必ず添える |
| 5 | `:hover` は `@media (hover: hover)` で囲む — ほか `tech_design_system.md` 禁止事項表のルールに従う |

## 3. E2E ロケータとの連動

| # | 規約 |
|---|------|
| 1 | scoped クラス名は E2E テストのロケータに使われる（`.stat-bar-hp`。[test.md](test.md) §3 #4）。**クラス名を変えるときは `tests/e2e/` を grep して同時に直す** |
| 2 | アクセシビリティ規約（[component.md](component.md) §7）を満たしていれば大半は `getByRole` で特定でき、クラス名へのテスト依存は増えない。ロケータ用の専用クラスを足さない |
