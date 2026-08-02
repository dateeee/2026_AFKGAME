# AFK GAME — 実装の疑義バックログ

> テスト整備・レビューで検出した「実装と仕様の乖離」「デッドコード」「規約違反」のうち、**未対応**のものを管理する。
> 運用ルールは [development_process.md](development_process.md) §6 変更管理を参照。

---

## 1. 運用ルール

- 単体テスト・結合テスト・コードレビューで検出した実装側の疑義を、対応するまでここに記録する
- 対応方針は「仕様書を実装に合わせる」か「実装を修正する」かを都度判断する（[development_process.md](development_process.md) §6）
- **未確定仕様**は `open_specs.md`（現在は未確定ゼロのため不在）、**数値のみ調整待ち**は [balance_backlog.md](balance_backlog.md) に置き、本書には含めない
- 対応が完了した項目は §3 へ移す（直近10件のみ保持）

## 2. 未対応の項目

| # | 対象 | 内容 | 影響度 | 検出元 |
|---|------|------|--------|--------|
| 1 | `routers/auth.py` | パスワードリセットがメール確認トークンを共用。リセット完了で副作用的に `email_verified=True` になり、リセット用トークンを `GET /verify-email` に流用可能。[tech_auth.md](tech/tech_auth.md) §6 は同テーブルをメール確認用途のみと定義 | 中（仕様乖離） | 単体テスト |
| 2 | `routers/battle.py` | 簡易計算が [tech_offline.md](tech/tech_offline.md) §4 と乖離。仕様は「乱数なしの期待値計算・サマリーのみ返却」だが、実装は乱数込み10tickの実シミュレーション結果に倍率を掛け、10tick分のログも返す。`equipment_drops`・`equipment_auto_sold` は外挿されない | 中（仕様乖離） | 単体テスト |
| 3 | `services/battle_service.py` | `_get_potion_count()` がどこからも呼ばれていないデッドコード | 低 | 単体テスト |
| 4 | `services/equipment_service.py` | L109 が SQLAlchemy 2.0 非推奨の `Query.get()`。同ファイル内の `get_effective_stats` は `db.get()` を使用しており不統一 | 低 | 単体テスト |
| 5 | `services/equipment_service.py` | オートセルの `RARITY_ORDER` 未知値を例外もログもなく無視。設定APIでの入力検証要否は未確認 | 低 | 単体テスト |
| 6 | `app/config.py` | [tech_operations.md](tech/tech_operations.md) §12.2 の環境変数一覧のうち、実装は `JWT_SECRET`・`GOOGLE_CLIENT_ID`・`GOOGLE_CLIENT_SECRET` のみ。`APP_ENV`・`CORS_ORIGINS`・`FRONTEND_BASE_URL`・`LOG_LEVEL`・`LOG_FORMAT`・`SMTP_*` は未対応で、`LOG_LEVEL` / `LOG_FORMAT` は「環境変数で上書き可」とコメントされているが実装がない（[README.md](../README.md) も環境変数として案内）。§12.2 の**起動時バリデーション**（本番で必須変数が既定値のままなら起動中止）も未実装 | 中（本番の設定が効かない） | 結合テスト |

- 単体テストは**現状の実装に合わせて**作成済み。上記を修正する場合は該当テストの期待値も併せて更新すること
- #6 のうち `DATABASE_URL` は E2E のDB分離に必要なため先行対応済み（§3）

## 3. 対応済みの項目

| # | 対象 | 内容 | 対応 |
|---|------|------|------|
| 1 | `app/main.py` | ヘルスチェックが `GET /api/health` で `{"status":"ok"}` のみを返し、[tech_api.md](tech/tech_api.md) §運用・[tech_operations.md](tech/tech_operations.md) §12.3 の `GET /health`・`version`・`db`・DB異常時503 と乖離 | **実装を仕様へ合わせた**。`SELECT 1` による疎通確認と 503（`{"status":"degraded","db":"error"}`）を追加。単体3件・結合1件のテストを追加 |
| 2 | `app/config.py` | `DATABASE_URL` が定数固定で、[tech_operations.md](tech/tech_operations.md) §12.2 の環境変数指定が効かない | **実装を仕様へ合わせた**。`os.environ.get` で上書き可能にし、E2E は専用DBを指すようにした |
| 3 | `App.vue` / `useGameLoop.ts` | ゲーム状態の初期化が `onMounted` の1回のみ。ログイン画面では未認証のまま `GET /api/game/state` を叩いて401になり、ゲスト作成・ログイン後は App が再マウントされないため**ホームがエラーバナー付きの空表示のまま**だった（再読み込みで復旧）。[screen_transition.md](../diagrams/screen_transition.md)「ゲスト自動作成 → ホーム」と乖離 | **実装を修正**。未認証時は何も読まずに戻り、認証状態の変化を監視して初期化する。E2E（`auth.spec`）で検出・再発防止 |
| 4 | `LoginView.vue` | `RegisterView` が `?mode=register` で飛ばしているのに参照しておらず、`/register` へ行っても登録フォームが開かなかった | **実装を修正**。クエリを見て初期モードを決める。E2E（`auth.spec`）で検出・再発防止 |
