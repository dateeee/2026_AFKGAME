# フロントエンドコーディング規約 — テスト（E2E）

> [coding_standards_frontend.md](../coding_standards_frontend.md) の分冊。全層共通の規約は [common.md](common.md) が先（テストコードも同じ記述規約に従う）。
> ベースは [Playwright 公式](https://playwright.dev/docs/best-practices)「Best Practices」（[basis.md](basis.md) §1）。本書はそこからの差分だけを持つ。
> 結合テスト工程のレイヤー構成・必須シナリオの正は [.claude/project/integration-test.md](../../../.claude/project/integration-test.md)。

---

## 1. 構成と分担

| 項目 | 規約 |
|------|------|
| 自動テストの構成 | フロントの自動テストは **Playwright E2E のみ**。コンポーネント単体テスト（Vitest 等）は未導入 — ロジックの正はバックエンド単体テストが持ち、フロントは型検査（`vue-tsc`）+ E2E で検証する（[phases.md](../phases.md) §3.5） |
| 単体テストの導入判断 | フロントに計算・分岐ロジックが増え E2E で網羅できなくなったときに、改訂の起点（[phases.md](../phases.md) §3.2.2）として判断する。それまで「フロントへロジックを増やさない」（[basis.md](basis.md) §2 #2）が先 |
| E2E の位置づけ | 結合テスト工程の L2。何をどのレイヤーで検証するかの正は `integration-test.md` |

## 2. 配置と実行環境

| 項目 | 規約 |
|------|------|
| 配置 | `tests/e2e/<機能>.spec.ts`。共通ヘルパーは `support/harness.ts`、実行環境の固定値は `support/config.ts` |
| 実行 | 直列（`workers: 1`）・`retries: 0`（不安定なテストをリトライで隠さない）。設定は `playwright.config.ts` |
| サーバー | E2E 専用ポート・専用 DB を毎回作り直して起動する（開発用と分離。値の正は `support/config.ts`） |

## 3. 記述規約

| # | 規約 |
|---|------|
| 1 | ファイル冒頭の JSDoc に**検証対象の基本設計**（画面遷移・`tech_api.md` の該当箇所）と、下位レイヤー（L1）との分担を書く（`auth.spec.ts` が実例） |
| 2 | `test.describe` はシナリオ名、`test` の名前は**日本語**で期待する振る舞いを書く（「未認証でホームを開くとログイン画面へ送られる」） |
| 3 | ロケータは `getByRole`（+ アクセシブルネーム）を第一候補、`getByLabel` / `getByText` を第二候補とする。**`data-testid` は導入しない** — ロール・名前で特定できる状態を保つのが正（[component.md](component.md) §7）で、テスト専用属性はその崩れを隠すため |
| 4 | ロール・テキストで特定できない要素だけ scoped クラス（`.stat-bar-hp`）で引く（[styling.md](styling.md) §3 の連動ルールに従う） |
| 5 | 操作は必ず UI から行う。DB を直接触ってよいのは**時刻の巻き戻しだけ**（`rewindLastTick`。60秒 tick を実時間で待たない） |

## 4. 再現性

| # | 規約 |
|---|------|
| 1 | テストごとに自分のゲストプレイヤーを作り、他テストの状態に依存しない（直列実行でも独立に書く） |
| 2 | 実時間の `waitForTimeout` で tick を待たない（`rewindLastTick` + 画面更新の検証で書く） |
| 3 | ポート・DB 名・tick 間隔の固定値は `support/config.ts` に集約し、バックエンド設定と一致させる |

## 5. 本書が持たないもの（分担）

| 内容 | 正 |
|------|-----|
| 結合テストのレイヤー構成・必須シナリオ・実行手順 | [.claude/project/integration-test.md](../../../.claude/project/integration-test.md) |
| E2E 実行コマンドの一覧 | [.claude/project/commands.md](../../../.claude/project/commands.md) |
| 型検査・lint・整形のコマンド | [basis.md](basis.md) §3 |
