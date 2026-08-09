# AFK GAME — API共通仕様

> 親: [tech_api.md](../tech_api.md)（[tech_spec.md](../../tech_spec.md) §5）。本書は §5.0 共通仕様を担当する。
> エラー形式・エラーコードは [tech_logging.md](../tech_logging.md)、レート制限・認可は [tech_security.md](../../nonfunctional/tech_security.md)。

## 5.0 共通仕様

| 項目 | 規約 |
|------|------|
| ベースパス | `/api`（バージョン番号なし）。破壊的変更が必要になった場合のみ `/api/v2/...` を併設し、旧版を一定期間並行維持する |
| パス命名 | 小文字ケバブケース（例: `/api/boss-rush/start`）。リソース名は単数形 |
| ボディのキー | **camelCase**（バックエンドは Resource クラスのプロパティを camelCase で定義し、Jackson がそのまま直列化） |
| 日時 | ISO 8601 の UTC（例: `2026-03-15T12:00:00Z`）。ローカル時刻は返さない |
| 数値 | すべて整数（ゴールドは64bit）。割合は 0〜1 の小数 |
| 未知フィールド | リクエストの未定義フィールドは 422 で拒否（Jackson `FAIL_ON_UNKNOWN_PROPERTIES`） |
| 認証 | **全エンドポイントで `Authorization: Bearer <access_token>` 必須**。例外は下表のみ |
| 認証不要な例外 | `/api/auth/guest`, `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/verify-email`, `/api/auth/google`, `/api/auth/password-reset/*`, `/health` |
| 一覧系の件数 | ページングは設けない（1プレイヤーのデータ量が上限で抑えられているため）。`/api/boss-rush/ranking` と `/api/abyss/ranking` のみ上位100件固定 |

**共通ヘッダ**

| ヘッダ | 方向 | 内容 |
|--------|------|------|
| `Authorization` | Req | `Bearer <access_token>` |
| `Content-Type` | Req | `application/json`（ボディを持つ場合） |
| `X-Request-ID` | Res | リクエスト単位のUUID。ログとの突合に使う（`tech_logging.md`） |
| `Retry-After` | Res | 429 応答時のみ。再試行可能になるまでの秒数 |

**HTTPステータスコードの使い分け**

| コード | 用途 | 例 |
|--------|------|-----|
| 200 | 正常（レスポンスボディあり） | 取得・更新・アクション成功 |
| 400 | 状態が不正で実行できない | 入塔中の塔選択、ロック中装備の売却 |
| 401 | 未認証・トークン不正/期限切れ | `AUTH_TOKEN_EXPIRED` |
| 403 | 認証済みだが権限・解放条件を満たさない | 未解放の塔を選択 |
| 404 | 対象が存在しない／**他ユーザーのリソース** | 存在秘匿のため 403 ではなく 404 |
| 422 | 型・範囲・必須のバリデーション違反 | `quantity` が範囲外 |
| 429 | レート制限超過 | `RATE_LIMIT_EXCEEDED`（`tech_security.md` §11.6） |
| 500 | サーバー内部エラー | `INTERNAL_UNEXPECTED_ERROR` |
| 503 | 一時的に処理不能 | `BATTLE_TICK_BUSY`（tick処理のロック競合。[tech_tick.md](../../detail/tech_tick.md) §3.1） |

- 201・204 は使わない（作成系も更新後の状態を 200 で返す方針に統一）
- エラーボディは全コード共通で `{"error": {"code", "message", "requestId"}}`（`tech_logging.md`）
