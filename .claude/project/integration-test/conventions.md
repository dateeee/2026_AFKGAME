# AFK GAME — 結合テストの記述規約

> [integration-test.md](../integration-test.md) の子ファイル（索引側が正）。担当: §1.1・§1.2。
> テストを**書くとき**に読む。シナリオ設計・完了判定では不要。

## 1.1 L1 の記述規約

| 項目 | 規約 |
|------|------|
| マーカー | `@Tag("integration")` |
| 実行 | `cd backend && mvn test -Dgroups=integration` |
| ファイル分割 | 導線ごとに1クラス（`AuthFlowIntegrationTest` / `TowerFlowIntegrationTest` / `BattleFlowIntegrationTest` / `ShopFlowIntegrationTest` / `EquipmentFlowIntegrationTest`） |
| プレイヤー生成 | 直接作らず **`POST /api/auth/guest` から始める** |
| DBセッション | 実際の Repository（`@SpringBootTest` の `SqlSessionFactory` が実装を供給）を使う。単体テストのモック Repository とは差し替え、本番と同じマッピング設定で検証する |
| 乱数 | 固定シードの `Random` を DI で注入する（`tech_rng.md`） |
| 時刻 | `rewind(player, 秒)` で `last_tick_at` を過去へ戻す。スリープしない |
| ドロップ | 固定した `Random` 注入で抽選を成立させる（ドロップ率は検証対象外） |
| ログ経由の値 | Logback の `ListAppender`（テストユーティリティ）で検証する（`afkgame` ロガーは `additivity=false`） |
| 構成のネスト | テストクラスに `@Configuration` を**ネストしない**。Spring Boot がそれをテスト本体の構成として採用し、壊れた構成でコンテキストが起動する。起動失敗を検証したい場合は `new ApplicationContextRunner().withBean(...)` で別コンテキストとして組む |

## 1.2 L2 の記述規約

| 項目 | 規約 |
|------|------|
| 実行 | `cd frontend && npm run test:e2e`（`playwright.config.ts` がフロント・バックを自動起動） |
| サーバー | バック :8100（`DATABASE_URL=jdbc:postgresql://localhost:5432/afkgame_e2e`）／フロント :5174。開発用の :8000 / :5173 とDBを分ける |
| 起動確認 | バックは `GET /health` が通るまで待つ。`reuseExistingServer` は使わない（開発用DBを掴む事故を防ぐ） |
| 実行順 | 1つのDBを共有するため `workers: 1` の直列。独立性は**テストごとにゲストを作る**ことで担保 |
| ヘルパー | `tests/e2e/support/harness.ts`。画面操作はUI経由、DB直接操作は**時刻の巻き戻しだけ** |
| 時刻 | `advanceTicks(page, n)` で `last_tick_at` を戻して再読み込み。tick を起こすのはアプリ側 |
| 乱数 | ドロップ・報酬は固定できないため `advanceUntil(page, 条件)` で条件成立まで進める。回数を決め打ちしない |
| リトライ | `retries: 0`。不安定なテストはリトライで隠さず原因を直す |
| セレクタ | `data-testid` は使わない。role・表示文言と、意味のあるクラス（`.tower-card` 等）で引く |
| 注意 | 正規表現マッチは空白を正規化しない。改行を含む要素は文字列マッチで引く |
