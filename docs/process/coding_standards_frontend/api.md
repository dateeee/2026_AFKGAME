# フロントエンドコーディング規約 — API 通信とエラー処理

> [coding_standards_frontend.md](../coding_standards_frontend.md) の分冊。全層共通の規約は [common.md](common.md) が先。
> エンドポイント定義（URI・ステータス）の正は [tech_api.md](../../tech/basic/tech_api.md)、エラーコード体系の正は [tech_error_handling.md](../../tech/basic/tech_error_handling.md)、トークン保管・認証フローの正は [tech_auth.md](../../tech/detail/tech_auth.md) §7。本書は**フロント側の書き方**と、フロント独自の疑似コード（§5）だけを持つ。

---

## 1. 責務と一元化

| ファイル | 責務 |
|---------|------|
| `api/client.ts` | fetch 共通基盤（ヘッダ・トークン付与・401 リフレッシュ・リトライ・`USE_API` フォールバック）とゲーム系エンドポイント関数 |
| `api/auth.ts` | 認証エンドポイント関数 |
| `api/errors.ts` | エラー型（`ApiError`）と変換・判定関数 |

| # | 規約 |
|---|------|
| 1 | サーバー通信は `api/` の**エンドポイント関数**だけが行う（生 `fetch` を `api/` の外に書かない。[layering.md](layering.md) §2 #3） |
| 2 | エンドポイント関数は「HTTP メソッド + 対象」で命名する（`getGameState`・`postTick`・`putTowerMode`） |
| 3 | 共通処理（トークン付与・エラー変換・リトライ）は `client.ts` の内部関数に集約し、各エンドポイント関数で繰り返さない |

## 2. 型（バックエンドとの対応）

| # | 規約 |
|---|------|
| 1 | リクエスト・レスポンス型は `types/game.ts` に置き、バックエンドの Resource（DTO）と**1対1**で対応させる。フィールド名は camelCase のまま（Jackson が camelCase を維持するため変換層を作らない。[profile.md](../../../.claude/project/profile.md) §3） |
| 2 | 型は手書きで同期する（自動生成なし）。Resource を変更したら**同じ変更で** `types/game.ts` を直す（乖離は `full-review` が検出する） |
| 3 | JSON 解析結果への型付け（`as`）は `api/` の中だけに許す（[common.md](common.md) §3 #3）。`api/` の外へは型付きの値だけを出す |

## 3. 認証トークン

| # | 規約 |
|---|------|
| 1 | トークンの保管先・失効フローの**正は `tech_auth.md` §7**（アクセストークン = メモリ、リフレッシュトークン = `localStorage`）。本書は取り扱いの書き方だけを持つ |
| 2 | トークンの読み書きは `client.ts` の管理関数（`setTokens`・`clearTokens`・`getRefreshToken`）に限る。他のファイルから `localStorage` を触らない（[common.md](common.md) §6） |
| 3 | 401 応答はリフレッシュ → **1回だけ**再試行する。多重リフレッシュは Promise の共有で抑止する（`_refreshing` が実例）。リフレッシュ失敗は `SESSION_EXPIRED_CODE` の `ApiError` へ変換する |

## 4. リトライとフォールバック

| # | 規約 |
|---|------|
| 1 | 自動リトライは「**ネットワークエラー × 冪等メソッド（GET / HEAD）**」だけ（指数バックオフ・上限3回）。更新系（POST / PUT）はリトライしない — 60秒 tick が自然な再試行になるため、残るのは二重実行のリスクだけ（ハイブリッド tick 制での差分判断） |
| 2 | `VITE_USE_API=false` のフォールバック分岐は `client.ts` の `USE_API` 判定に集約する（[layering.md](layering.md) §4） |

## 5. エラー分類

フロントは以下の5分類で扱う。判定はすべて `errors.ts` の型・関数で行い、呼び出し側で `status` や文字列を直接見ない。

| 分類 | 判定 | フロントの扱い |
|------|------|--------------|
| 業務エラー | `ApiError`（`code` = サーバーのエラーコード） | **コードで分岐**する（メッセージ文字列で分岐しない）。表示文言はサーバーの日本語メッセージをそのまま使う |
| バリデーション | `code === 'VALIDATION_ERROR'` | `error.details` の `target` + `code` から**フロントが文言を組み立て**、該当項目へ表示する（[component.md](component.md) §6 #3） |
| 通信断 | `isNetworkError`（疑似ステータス `NETWORK_ERROR_STATUS = 0`） | `ConnectionBanner` による全画面表示。§4 #1 のリトライ対象 |
| セッション失効 | `isSessionExpired()`（`SESSION_EXPIRED_CODE = 'AUTH_REFRESH_FAILED'`） | リトライ・バナーでは回復しない。ログイン画面へ誘導する |
| 非統一形式（プロキシの502 等） | `HTTP_<status>` フォールバック | 個別分岐せず汎用エラー表示に倒す |

- 疑似コード（`NETWORK_ERROR_STATUS`・`SESSION_EXPIRED_CODE`・`HTTP_<status>`）は**フロント独自の値で、本書と `errors.ts` が正**（サーバーのコード表には載らない）
- 例外オブジェクトを画面へ出すときは `errorMessage()` で文字列化する（想定外の型もここで吸収する）

## 6. 表示への変換と想定外エラー

| 事象 | 表示 |
|------|------|
| 通信断 | `ConnectionBanner`（全画面共通・自動で出し入れ） |
| セッション失効 | ログイン画面へ誘導（`isSessionExpired()` 判定） |
| 業務エラー | 発生した画面・フォームの近傍でフィードバック（[component.md](component.md) §6） |
| 想定外（描画エラー・未処理の Promise 拒否） | `app.config.errorHandler` + `unhandledrejection` リスナーで捕捉し、汎用エラー表示に倒す（黙って握りつぶさない） |

- グローバル捕捉の置き場は `main.ts`（現状未実装。[known_issues.md](../../backlog/known_issues.md) #27 の修正対象）
- エラーを `catch` して何もしない経路を作らない。意図して無視する場合は理由コメントを書く（`GameView` の `loadTowers().catch` が実例）
