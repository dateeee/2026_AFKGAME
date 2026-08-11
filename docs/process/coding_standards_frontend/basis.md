# フロントエンドコーディング規約 — 準拠元と原則

> [coding_standards_frontend.md](../coding_standards_frontend.md) の分冊。**規約に無い判断をするとき・規約を改訂するとき**に読む。
> 個々の規約は [layering.md](layering.md)（層）・[common.md](common.md)（全層共通）・[component.md](component.md) / [composition.md](composition.md) / [store.md](store.md) / [api.md](api.md) / [styling.md](styling.md) / [test.md](test.md) が持つ。

---

## 1. 適用範囲と準拠元

| 項目 | 内容 |
|------|------|
| 対象 | `frontend/` 配下の全 `.vue`・`.ts`・`.css`、`index.html`、`vite.config.ts`、`tsconfig*.json`、`playwright.config.ts` |
| 非対象 | `backend/`（別書 [coding_standards_backend.md](../coding_standards_backend.md)）、`scripts/`・`.claude/scripts/`（開発補助の Python）、ビルド・配備の設定値（正は [tech_operations.md](../../tech/nonfunctional/tech_operations.md) §12） |
| 準拠元 | [Vue 3 公式ガイド](https://ja.vuejs.org/guide/introduction.html)・[公式スタイルガイド](https://ja.vuejs.org/style-guide/)（以下「スタイルガイド」）、[Pinia 公式](https://pinia.vuejs.org/)、[Vue Router 公式](https://router.vuejs.org/)、[Tailwind CSS 公式](https://tailwindcss.com/docs)、[Playwright 公式](https://playwright.dev/docs/intro) |

**設計・実装は Vue 公式のベストプラクティスをベースに作る**。各分冊は準拠元との**差分**（本プロジェクト固有の決定・上書き）だけを持ち、書かれていない事柄は準拠元に従う。

スタイルガイドは優先度別に採否を決める。

| 優先度 | 採否 |
|--------|------|
| A（必須）・B（強く推奨） | **全面採用**。違反は修正対象（§2 #6） |
| C（推奨） | 公式の既定案に従う（別案を選ばない） |
| D（注意して使用） | **禁止**（`$parent`・`scoped` 内の要素セレクタ・暗黙の親子間通信）。使う判断は §2 #5 の手順で該当分冊へ差分を書いてから |

| 分冊 | ベースにする準拠元の章 |
|------|---------------------|
| `layering.md` | プロジェクト固有（ファイル構成の正 [tech_structure.md](../../tech/basic/tech_structure.md) に対応） |
| `common.md` | ガイド「TypeScript で Vue を使用する」、スタイルガイドの命名規則群 |
| `component.md` | ガイド「コンポーネントの基礎／詳細」「ベストプラクティス > アクセシビリティ／セキュリティー」、スタイルガイド優先度A |
| `composition.md` | ガイド「リアクティビティーの基礎」「コンポーザブル」「Composition API FAQ」 |
| `store.md` | Pinia 公式「Core Concepts」（Setup Stores） |
| `api.md` | プロジェクト固有（[tech_api.md](../../tech/basic/tech_api.md)・[tech_error_handling.md](../../tech/basic/tech_error_handling.md)・[tech_auth.md](../../tech/detail/tech_auth.md) §7 と連動） |
| `styling.md` | Tailwind 公式「Theme variables」+ [tech_design_system.md](../../tech/detail/tech_design_system.md) |
| `test.md` | Playwright 公式「Best Practices」 |

## 2. 原則

| # | 原則 |
|---|------|
| 1 | **Composition API が唯一の記述方式**。SFC は `<script setup lang="ts">`、ストアは Pinia の Setup Store 形式で書く。Options API・Options Store を使わない（混在すると同じ関心の書き方が2通りになり、レビューと再利用が成立しないため） |
| 2 | **サーバー権威**（[CLAUDE.md](../../../CLAUDE.md) アーキテクチャ不変条件）。戦闘計算・報酬決定をフロントに置かない。フロントの仕事は表示・入力・API 呼び出しに限る（唯一の例外は開発時フォールバック。[layering.md](layering.md) §4） |
| 3 | **仕様の正はドキュメント**。ビジュアルは `tech_design_system.md`、認証フローとトークン保管は `tech_auth.md`、エラーコード体系は `tech_error_handling.md`、tick 制御は `tech_polling.md` が正。規約は「コードの書き方」だけを持ち、仕様値を再掲しない（[spec_ownership.md](../spec_ownership.md)） |
| 4 | 分冊にも準拠元にも無い判断は**近傍の既存コードに倣う**。同じ層の既存ファイルと書き方を揃えることを好みより優先する |
| 5 | 準拠元と違う決め方をするときは、**該当する分冊へ「準拠元の該当箇所・本プロジェクトの決定・理由」の3点を書いてから**実装する（暗黙の逸脱を作らない） |
| 6 | **規約と異なる実装は修正対象**。発見したら [known_issues.md](../../backlog/known_issues.md) へ記録し、是正タスクとして解消する（規約側を改訂する判断をした場合のみ実装を維持する）。バックエンド（[basis.md](../coding_standards_backend/basis.md) §2 #4「触るときに直す」）より強い運用にするのは、規約より先にコードが書かれた経緯があり、放置すると #4 の「既存コードに倣う」が逸脱を再生産するため |

## 3. 適用と検証

| 手段 | 対象 | コマンド・スキル |
|------|------|----------------|
| 型検査 | 構文・型・`any` の混入 | `cd frontend && npm run type-check`（`vue-tsc`） |
| リント | スタイルガイド系ルール・Composition API の誤用 | `cd frontend && npm run lint`（ESLint flat config: `eslint-plugin-vue` + `@vue/eslint-config-typescript`） |
| 整形 | インデント・引用符・改行 | `cd frontend && npm run format:check`（Prettier。適用は `npm run format`） |
| E2E | 画面の振る舞い | `npm run test:e2e`（[test.md](test.md)） |
| レビュー | 機械判定できない規約（層の責務・置き場・UX・一貫性） | `frontend-review` スキル（観点の正は [.claude/project/review/frontend.md](../../../.claude/project/review/frontend.md) §2） |

- 新規・改修したコードは規約に従う。既存コードの逸脱は §2 #6 のとおり修正対象として記録・解消する
- 改訂は基本設計工程で行う（[phases.md](../phases.md) §3.2.2）。改訂したら [.claude/references/coding-standards-frontend.md](../../../.claude/references/coding-standards-frontend.md) を**同じ変更で**追随させる
