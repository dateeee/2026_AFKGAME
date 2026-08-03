# AFK GAME — 実装の疑義バックログ

> テスト整備・レビューで検出した「実装と仕様の乖離」「デッドコード」「規約違反」のうち、**未対応**のものを管理する。
> 運用ルールは [development_process.md](development_process.md) §6 変更管理を参照。

---

## 1. 運用ルール

- 単体テスト・結合テスト・コードレビューで検出した実装側の疑義を、対応するまでここに記録する
- 対応方針は「仕様書を実装に合わせる」か「実装を修正する」かを都度判断する（[development_process.md](development_process.md) §6）
- **未確定仕様**は [open_specs.md](open_specs.md)、**数値のみ調整待ち**は [balance_backlog.md](balance_backlog.md) に置き、本書には含めない
- 対応が完了した項目は §3 へ移す（直近10件のみ保持）

## 2. 未対応の項目

| # | 対象 | 内容 | 影響度 | 検出元 |
|---|------|------|--------|--------|
| 2 | `services/battle_service.py` | 簡易計算が [tech_offline.md](tech/tech_offline.md) §4 と乖離。仕様は「乱数なしの期待値計算・サマリーのみ返却」だが、実装は乱数込み10tickの実シミュレーション結果に倍率を掛け、10tick分のログも返す。`equipment_drops`・`equipment_auto_sold` は外挿されない（ルーターから `process_pending_ticks` へ移設済みだが、算出方法は未修正） | 中（仕様乖離） | 単体テスト |
| 3 | `services/battle_service.py` | `_get_potion_count()` がどこからも呼ばれていないデッドコード | 低 | 単体テスト |
| 6 | `app/config.py` | [tech_operations.md](tech/tech_operations.md) §12.2 の環境変数一覧のうち、`CORS_ORIGINS`・`FRONTEND_BASE_URL`・`SMTP_*` が未対応（`APP_ENV`・`LOG_LEVEL`・`LOG_FORMAT`・起動時バリデーションは対応済み → §3）。[README.md](../README.md) も環境変数として案内している | 低（残りは本番設定の一部） | 結合テスト |
| 7 | `routers/auth.py`, `app/dependencies.py` | 認証系のエラーが `HTTPException` のままで、[tech_logging.md](tech/tech_logging.md) §エラーコード体系の `AUTH_*` コードを返していない（ハンドラが `HTTP_401` へ丸める）。ゲーム系（`SHOP_`/`TOWER_`/`EQUIP_`）は `AppError` へ統一済み。[tech_security.md](tech/tech_security.md) §11.6 のレート制限（`RATE_LIMIT_EXCEEDED`）未実装と同じ範囲のため、まとめて対応する | 低（コードで分岐する画面がまだない） | コードレビュー |
| 8 | `composables/useBattleLocal.ts` | 開発時フォールバック（[CLAUDE.md](../CLAUDE.md) アーキテクチャ不変条件4「バックエンド未起動でもフロント単体で動作可」）が実質未実装のスタブで、どこからも import されていない。`VITE_USE_API=false` では初期化がスキップされ、空の画面になる（クラッシュはしない）。**不変条件が求める水準の確定が先**（ダミー状態＋簡易ローカルtickを実装するか、不変条件を「クラッシュせず起動する」へ緩めるか） | 中（不変条件と実装の乖離） | コードレビュー |
| 10 | `master_data/towers.py`, `services/battle_service.py`, `schemas/tower.py` | 深淵の塔（[endgame.md](design/systems/endgame.md) §2.14 の無限塔）は総階数を持たないが、`TowerData.total_floors` は `int` 固定。Phase 5 着手時に `target_floor_cap` / `process_tick` の目標到達判定 / `TowerInfo.total_floors` をまとめて `None` 対応させる（片側だけ対応すると判定が非対称になるため、現在は全て非 None を不変条件としている） | 低（Phase 5 の前提条件） | コードレビュー |

- 単体テストは**現状の実装に合わせて**作成済み。上記を修正する場合は該当テストの期待値も併せて更新すること

## 3. 対応済みの項目

| # | 対象 | 内容 | 対応 |
|---|------|------|------|
| 1 | `app/main.py` | ヘルスチェックが `GET /api/health` で `{"status":"ok"}` のみを返し、[tech_api.md](tech/tech_api.md) §運用・[tech_operations.md](tech/tech_operations.md) §12.3 の `GET /health`・`version`・`db`・DB異常時503 と乖離 | **実装を仕様へ合わせた**。`SELECT 1` による疎通確認と 503（`{"status":"degraded","db":"error"}`）を追加。単体3件・結合1件のテストを追加 |
| 2 | `app/config.py` | `DATABASE_URL` が定数固定で、[tech_operations.md](tech/tech_operations.md) §12.2 の環境変数指定が効かない | **実装を仕様へ合わせた**。`os.environ.get` で上書き可能にし、E2E は専用DBを指すようにした |
| 3 | `App.vue` / `useGameLoop.ts` | ゲーム状態の初期化が `onMounted` の1回のみ。ログイン画面では未認証のまま `GET /api/game/state` を叩いて401になり、ゲスト作成・ログイン後は App が再マウントされないため**ホームがエラーバナー付きの空表示のまま**だった（再読み込みで復旧）。[screen_transition.md](../diagrams/screen_transition.md)「ゲスト自動作成 → ホーム」と乖離 | **実装を修正**。未認証時は何も読まずに戻り、認証状態の変化を監視して初期化する。E2E（`auth.spec`）で検出・再発防止 |
| 4 | `LoginView.vue` | `RegisterView` が `?mode=register` で飛ばしているのに参照しておらず、`/register` へ行っても登録フォームが開かなかった | **実装を修正**。クエリを見て初期モードを決める。E2E（`auth.spec`）で検出・再発防止 |
| 5 | `routers/auth.py` | パスワードリセットがメール確認トークンを共用。リセット完了で副作用的に `email_verified=True` になり、リセット用トークンを `GET /verify-email` に流用可能だった | **実装を修正**（backend-review ISSUE-004）。`EmailVerificationToken.purpose` を追加して発行・検証で用途一致を要求する（Alembic `c7d1a4f2b830`）。用途をまたぐ流用と副作用の不在を単体テスト3件で固定 |
| 6 | `services/equipment_service.py` | L109 が SQLAlchemy 2.0 非推奨の `Query.get()`。`get_effective_stats` の `db.get()` と不統一。同関数の relationship フォールバックは実質デッドコード | **実装を修正**（backend-review ISSUE-012）。`db.get()` へ統一し、到達しないフォールバックを削除 |
| 7 | `services/equipment_service.py` | オートセルの `RARITY_ORDER` 未知値を例外もログもなく無視。設定APIでの入力検証要否は未確認だった | **入力側で解決**（backend-review ISSUE-001）。`SettingsUpdate.auto_sell_rarity` を `Literal` にしたため、未知値は 422 で弾かれサービスへ到達しない |
| 8 | `app/config.py` / `logging_config.py` | `LOG_LEVEL` / `LOG_FORMAT` が config.py の死んだ定数で、`setup_logging` が `os.environ` を直接読んでいた。[tech_operations.md](tech/tech_operations.md) §12.2 の起動時バリデーションも未実装 | **実装を仕様へ合わせた**（backend-review ISSUE-003・006）。環境変数の参照を config.py へ集約し、`setup_logging(level=, fmt=)` で上書きする形にした。`APP_ENV=production` かつ `JWT_SECRET` が既定値なら起動を中止する |
| 9 | `tests/e2e/tower.spec.ts` | 「未解放の塔は選べず解放条件が表示される」が失敗。未解放カードの `aria-disabled="true"` を Playwright 1.62 が disabled と判定し、`locked.click()` が15秒でタイムアウトしていた | **テスト側を修正**。`role="radio"` + `aria-disabled` + `tabindex="-1"` は未解放カードの正しい表現のため実装は変更せず、無効であること自体を属性で検証し、ハンドラのガードは `click({ force: true })` で確認する形にした（期待する結果は変えていない） |
