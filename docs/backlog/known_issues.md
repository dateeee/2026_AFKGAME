# AFK GAME — 実装の疑義バックログ

> テスト整備・レビューで検出した「実装と仕様の乖離」「デッドコード」「規約違反」のうち、**未対応**のものを管理する。
> 運用ルールは [development_process.md](../process/development_process.md) §6 変更管理を参照。

---

## 1. 運用ルール

- 単体テスト・結合テスト・コードレビューで検出した実装側の疑義を、対応するまでここに記録する
- 対応方針は「仕様書を実装に合わせる」か「実装を修正する」かを都度判断する（`development_process.md` §6）
- **未確定仕様**は `open_specs.md`、**数値のみ調整待ち**は [balance_backlog.md](balance_backlog.md) に置き、本書には含めない
- 対応が完了した項目は §3 へ移す（直近10件のみ保持）
- **Java 移行中の扱い**: Python 実装は削除済みのため、「対象」列は**機能・仕様の所在**を指す（コード固有の疑義は削除時に消えた）。残っているのは言語を変えても残る**仕様欠落・未実装**であり、[steps.md](java_migration/steps.md) の各 STEP で該当機能を実装する際に1件ずつ解消する

## 2. 未対応の項目

| # | 対象 | 内容 | 影響度 | 検出元 |
|---|------|------|--------|--------|
| 6 | 環境変数（`META-INF/spring/*.properties`） | [tech_operations.md](../tech/nonfunctional/tech_operations.md) §12.2 の環境変数一覧のうち、`FRONTEND_BASE_URL`・`SMTP_*` が未対応（`SPRING_PROFILES_ACTIVE`・`LOG_LEVEL`・`LOG_FORMAT`・`BATTLE_RNG_SEED`・`CORS_ORIGINS`・起動時バリデーションは対応済み → §3）。[README.md](../../README.md) も環境変数として案内している | 低（残りは本番設定の一部） | 結合テスト |
| 7 | `afkgame-web`（認証API） | [tech_security.md](../tech/nonfunctional/tech_security.md) §11.6 のレート制限（`RATE_LIMIT_EXCEEDED` / 429）が未実装。`/api/auth/login`（ブルートフォース）・`/api/auth/guest`（無制限のアカウント作成でDB肥大）・`/api/auth/password-reset/request`（トークン大量発行）が無防備。**アプリ層（Bucket4j 等）で持つか、インフラ層（WAF・ALB）へ寄せるかの方針決定が先**（決めたうえで tech_security.md へ明記する） | 中（本番公開前に必須） | backend-review ISSUE-103 |
| 14 | 装備ドロップの仕様 | 装備ドロップに所持枠上限（`EQUIPMENT_STORAGE_LIMIT`=50）の扱いが無い。ショップ購入は `SHOP_INVENTORY_FULL`（400）で止まるため、同じ「装備取得」で上限の扱いが経路により非対称になる。**上限到達時の挙動（ドロップ消滅／強制オートセル／上限撤廃）の仕様確定が先**（[economy.md](../design/systems/economy.md) §倉庫） | 中（仕様欠落） | backend-review ISSUE-107 |
| 15 | 戦闘ログのDB保存 | `max-battle-log-records`（100）は設定にあるが、`battle_logs` への書き込み・読み出し・ローテーションが未実装。戦闘ログはtick応答で返すのみで、[ui.md](../design/systems/ui.md) §設定画面「上限はDB保存件数100件」に対応する保存機構が無い。**DB保存を実装するか、Phase 1〜2 では保存しない設計に確定してテーブル・設定値を削除するかの決定が先**（[tech_db/battle.md](../tech/basic/tech_db/battle.md) §1 は `entries` を**配列**と定める） | 中（仕様と実装の不一致） | backend-review ISSUE-111 |
| 16 | `views/SettingsView.vue`, `composables/usePolling.ts` | 設定「通知表示（トースト）」の `toastEnabled` は保存・永続化されるが、トーストを表示するコンポーネント・呼び出しが存在せず、ONにしても何も起きない。`TickResponse.equipmentDrops` / `equipmentAutoSold` も受け取ったまま未使用。**トーストを実装するか、Phase 2 スコープ外として設定項目を外すかの決定が先**（`ui.md` §設定画面） | 中（設定が機能していない） | frontend-review ISSUE-802 |
| 8 | `composables/useBattleLocal.ts` | 開発時フォールバック（[CLAUDE.md](../../CLAUDE.md) アーキテクチャ不変条件4「バックエンド未起動でもフロント単体で動作可」）が実質未実装のスタブで、どこからも import されていない。`VITE_USE_API=false` では初期化がスキップされ、空の画面になる（クラッシュはしない）。**不変条件が求める水準の確定が先**（ダミー状態＋簡易ローカルtickを実装するか、不変条件を「クラッシュせず起動する」へ緩めるか） | 中（不変条件と実装の乖離） | コードレビュー |
| 10 | 深淵の塔（塔マスター・戦闘・API） | 深淵の塔（[endgame.md](../design/systems/endgame.md) §2.14 の無限塔）は総階数を持たないが、塔マスターの `totalFloors` は非 null を前提にしている。Phase 5 着手時に目標階の上限 / tick の目標到達判定 / `TowerInfo.totalFloors` をまとめて null 対応させる（片側だけ対応すると判定が非対称になる） | 低（Phase 5 の前提条件） | コードレビュー |
| 11 | 全滅ペナルティ | 「**塔内取得アイテム全ロスト**」が未実装（[battle.md](../design/systems/battle.md)「全滅時の処理」3項目のうちEXP・ゴールドのみ）。ドロップ装備を即座に永続化しオートセル益も即ゴールドへ加算すると、取り消し対象として管理できない。仕様・[battle_flow/overview.md](../diagrams/battle_flow/overview.md) が正 | 中（仕様乖離） | diagrams-review ISSUE-515 |
| 12 | 換金アイテム（`/api/item/*`） | 換金アイテムが**ドロップも売却APIも未実装**。仕様では [item.md §5](../data/master/item.md) の換金アイテムが Phase 2〜 ドロップし、[tech_api.md](../tech/basic/tech_api.md) の `POST /api/item/sell` で Phase 2〜 売却できる（素材分は Phase 4〜）。塔ファイル §7.4 のドロップテーブルが実装に反映されていない | 中（Phase 2 の機能欠落） | doc-review ISSUE-1020 |
| 18 | `HealthService` | ロガーが `getLogger(HealthService.class)` で、[tech_logging.md](../tech/basic/tech_logging.md)「ロガー名体系」（[規約](../process/coding_standards_backend/common.md) §7 #1）に反する（他6件は準拠）。体系にヘルス用の名前が無く、**仕様へ追加後に寄せる** | 低 | 規約整備 |
| 13 | 退会API（`/api/auth/delete-account`） | 退会（アカウント削除）が未実装。[main_nav.md](../diagrams/screen_transition/main_nav.md) は退会導線を Phase 2〜 として描き、`tech_api.md`・[api_sequence/auth.md](../diagrams/api_sequence/auth.md) §14 に再認証つきの削除フローを定義済み | 中（Phase 2 の機能欠落） | diagrams-review ISSUE-509 |

## 3. 対応済みの項目

| # | 対象 | 内容 | 対応 |
|---|------|------|------|
| 2 | `app/config.py` | `DATABASE_URL` が定数固定で、`tech_operations.md` §12.2 の環境変数指定が効かない | **実装を仕様へ合わせた**。`os.environ.get` で上書き可能にし、E2E は専用DBを指すようにした |
| 3 | `App.vue` / `useGameLoop.ts` | ゲーム状態の初期化が `onMounted` の1回のみ。ログイン画面では未認証のまま `GET /api/game/state` を叩いて401になり、ゲスト作成・ログイン後は App が再マウントされないため**ホームがエラーバナー付きの空表示のまま**だった（再読み込みで復旧）。[screen_transition.md](../diagrams/screen_transition.md)「ゲスト自動作成 → ホーム」と乖離 | **実装を修正**。未認証時は何も読まずに戻り、認証状態の変化を監視して初期化する。E2E（`auth.spec`）で検出・再発防止 |
| 4 | `LoginView.vue` | `RegisterView` が `?mode=register` で飛ばしているのに参照しておらず、`/register` へ行っても登録フォームが開かなかった | **実装を修正**。クエリを見て初期モードを決める。E2E（`auth.spec`）で検出・再発防止 |
| 5 | `routers/auth.py` | パスワードリセットがメール確認トークンを共用。リセット完了で副作用的に `email_verified=True` になり、リセット用トークンを `GET /verify-email` に流用可能だった | **実装を修正**（backend-review ISSUE-004）。`EmailVerificationToken.purpose` を追加して発行・検証で用途一致を要求する（Alembic `c7d1a4f2b830`）。用途をまたぐ流用と副作用の不在を単体テスト3件で固定 |
| 7 | `services/equipment_service.py` | オートセルの `RARITY_ORDER` 未知値を例外もログもなく無視。設定APIでの入力検証要否は未確認だった | **入力側で解決**（backend-review ISSUE-001）。`SettingsUpdate.auto_sell_rarity` を `Literal` にしたため、未知値は 422 で弾かれサービスへ到達しない |
| 8 | `app/config.py` / `logging_config.py` | `LOG_LEVEL` / `LOG_FORMAT` が config.py の死んだ定数で、`setup_logging` が `os.environ` を直接読んでいた。`tech_operations.md` §12.2 の起動時バリデーションも未実装 | **実装を仕様へ合わせた**（backend-review ISSUE-003・006）。環境変数の参照を config.py へ集約し、`setup_logging(level=, fmt=)` で上書きする形にした。`APP_ENV=production` かつ `JWT_SECRET` が既定値なら起動を中止する |
| 9 | `tests/e2e/tower.spec.ts` | 「未解放の塔は選べず解放条件が表示される」が失敗。未解放カードの `aria-disabled="true"` を Playwright 1.62 が disabled と判定し、`locked.click()` が15秒でタイムアウトしていた | **テスト側を修正**。`role="radio"` + `aria-disabled` + `tabindex="-1"` は未解放カードの正しい表現のため実装は変更せず、無効であること自体を属性で検証し、ハンドラのガードは `click({ force: true })` で確認する形にした（期待する結果は変えていない） |
| 10 | `routers/auth.py`, `app/dependencies.py` | 認証系のエラーが `HTTPException` のままで、`tech_logging.md` §エラーコード体系の `AUTH_*` コードを返していなかった（ハンドラが `HTTP_401` へ丸めるため、クライアントが「期限切れ＝refresh」と「不正トークン＝再ログイン」を判別できない） | **実装を仕様へ合わせた**（backend-review ISSUE-105）。全15コードを `AppError` へ置き換え、`tech_logging.md` §AUTH_ コード一覧に正を登録。フロントは `AUTH_REFRESH_FAILED` を検知してログイン画面へ誘導する（frontend-review ISSUE-803）。同項で挙げていたレート制限は §2 #7 として継続 |
| 11 | `models/shop.py` → `afkgame-initdb/V1__initial_schema.sql` | `ShopDailySlot` の一意制約名が `uq_shop_daily_slot_index` のままで、[tech_db/item.md](../tech/basic/tech_db/item.md) §5 が正とする `uq_shop_daily_slots_state_slot`（[tech_db.md](../tech/basic/tech_db.md) §2 の命名規約）と乖離（旧 §2 #17） | **定義書へ追従**（`steps.md` STEP 2-A）。Flyway `V1` を定義書から起こす際に規約どおりの名前で作成し、統合テストで制約名を固定した |
