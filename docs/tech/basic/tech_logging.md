# AFK GAME — ログ設計・エラーハンドリング

> [tech_spec.md](../tech_spec.md) §6「ログ設計」。アーキテクチャ方針は [tech_architecture.md](tech_architecture.md)。

## ログライブラリ
Python標準 `logging` モジュールを使用。Uvicornのアクセスログと連携する。

## ログレベル方針

| レベル | 用途 | 例 |
|--------|------|-----|
| DEBUG | 開発用の詳細情報 | SQLクエリ、リクエストボディ、レスポンスボディ |
| INFO | 正常系イベント | リクエスト受信、tick処理完了（処理tick数・結果）、ゲストアカウント作成 |
| WARNING | 想定内のエラー | 認証失敗（401）、バリデーションエラー（422）、リソース不足（ゴールド不足等） |
| ERROR | 想定外のエラー | 未捕捉例外、DB接続失敗、データ整合性エラー |

## ログフォーマット

**開発時（テキスト形式）:**
```
[2026-03-15 14:38:30] WARNING  auth: 認証失敗 reason=player_not_found token=abc1****wxyz request_id=550e8400-e29b
```

**本番（構造化JSON）:**
```json
{
  "timestamp": "2026-03-15T14:38:30.123Z",
  "level": "WARNING",
  "logger": "auth",
  "message": "認証失敗",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "client_ip": "127.0.0.1",
  "method": "GET",
  "path": "/api/game/state",
  "reason": "player_not_found"
}
```

ログフォーマットの切り替えは環境変数 `LOG_FORMAT`（`text` / `json`、デフォルト: `text`）で制御。

## ロガー名体系

| ロガー名 | 対象 |
|----------|------|
| `afkgame.auth` | 認証処理（ゲスト作成、トークン検証） |
| `afkgame.battle` | 戦闘tick処理、オフライン計算 |
| `afkgame.game` | ゲーム状態取得・更新 |
| `afkgame.shop` | ショップ購入 |
| `afkgame.tower` | 塔選択・リタイア |
| `afkgame.middleware` | リクエストログミドルウェア |

## 認証エラーの詳細ログ

401レスポンス時に、失敗理由をWARNINGレベルで出力する。

| reason | 説明 | 出力例 |
|--------|------|--------|
| `header_missing` | Authorizationヘッダーなし | `WARNING auth: 認証失敗 reason=header_missing` |
| `invalid_format` | Bearer形式でない | `WARNING auth: 認証失敗 reason=invalid_format` |
| `player_not_found` | トークンに該当するプレイヤーなし | `WARNING auth: 認証失敗 reason=player_not_found token=abc1****wxyz` |
| `token_expired` | JWT期限切れ（Phase 2〜） | `WARNING auth: 認証失敗 reason=token_expired` |

## リクエストログミドルウェア

全APIリクエストに対して以下を実行する:

1. **リクエストID付与**: 各リクエストにUUID v4を生成し、レスポンスヘッダー `X-Request-ID` に含める
2. **処理時間計測**: リクエスト開始〜レスポンス完了の時間をミリ秒単位で計測
3. **INFOログ出力**: `method`, `path`, `status_code`, `duration_ms`, `player_id`（認証済みの場合）

```
[2026-03-15 14:38:30] INFO  middleware: POST /api/battle/tick 200 45ms player_id=550e8400 request_id=xxx
```

## 機密情報のマスク規則

| 対象 | マスク方法 |
|------|-----------|
| トークン値 | 先頭4文字 + `****` + 末尾4文字（例: `abc1****wxyz`） |
| パスワード | 出力禁止（ログに含めない） |
| メールアドレス | ローカル部の先頭2文字 + `***@` + ドメイン（例: `ab***@example.com`） |

## バックエンドエラーハンドリング

### 統一エラーレスポンス形式

全APIエラーレスポンスを以下の形式に統一する:

```json
{
  "error": {
    "code": "AUTH_PLAYER_NOT_FOUND",
    "message": "指定されたプレイヤーが見つかりません",
    "request_id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### エラーコード体系

| プレフィックス | 対象 | 例 |
|---------------|------|-----|
| `AUTH_` | 認証関連 | `AUTH_HEADER_MISSING`, `AUTH_INVALID_FORMAT`, `AUTH_PLAYER_NOT_FOUND`, `AUTH_TOKEN_EXPIRED` |
| `BATTLE_` | 戦闘関連 | `BATTLE_NOT_IN_TOWER`, `BATTLE_ALREADY_WIPED`, `BATTLE_TICK_BUSY`(503) |
| `GAME_` | ゲーム状態関連 | `GAME_STATE_NOT_FOUND` |
| `SHOP_` | ショップ関連 | `SHOP_INSUFFICIENT_GOLD`, `SHOP_ITEM_SOLD_OUT`, `SHOP_INVENTORY_FULL` |
| `TOWER_` | 塔関連 | `TOWER_NOT_UNLOCKED`, `TOWER_INVALID_FLOOR` |
| `EQUIP_` | 装備関連 | `EQUIP_NOT_FOUND`, `EQUIP_SLOT_MISMATCH` |
| `SKILL_` | スキル関連 | `SKILL_INSUFFICIENT_SP`, `SKILL_PREREQUISITE_NOT_MET`（一覧は [tech_party.md §7](../detail/tech_party.md)） |
| `PARTY_` | パーティ関連 | `PARTY_LOCKED_IN_TOWER`, `PARTY_MEMBER_NOT_OWNED` |
| `CHARACTER_` | キャラクター関連 | `CHARACTER_NOT_FOUND` |
| `BASE_` | 施設関連 | `BASE_INSUFFICIENT_MATERIALS`, `BASE_MAX_LEVEL` |
| `FORGE_` | 鍛冶屋関連 | `FORGE_INSUFFICIENT_MATERIALS`, `FORGE_LEVEL_TOO_LOW` |
| `RATE_LIMIT_` | レート制限 | `RATE_LIMIT_EXCEEDED`(429)。`Retry-After` ヘッダを併せて返す |
| `INTERNAL_` | サーバー内部エラー | `INTERNAL_UNEXPECTED_ERROR` |

### AUTH_ コード一覧

クライアントは**メッセージ文字列ではなくコードで**分岐する（`AUTH_TOKEN_EXPIRED` は refresh を試す、`AUTH_INVALID_TOKEN` はログアウトして再ログイン、のように挙動が異なるため）。

| コード | HTTP | 発生条件 |
|--------|------|---------|
| `AUTH_HEADER_MISSING` | 401 | `Authorization` ヘッダが無い |
| `AUTH_INVALID_FORMAT` | 401 | `Bearer ` で始まらない |
| `AUTH_TOKEN_EXPIRED` | 401 | アクセストークンの有効期限切れ（refresh 可能） |
| `AUTH_INVALID_TOKEN` | 401 | 署名不正・`sub` 欠落（再ログインが必要） |
| `AUTH_USER_NOT_FOUND` | 401 | トークンは正当だがユーザーが存在しない |
| `AUTH_PLAYER_NOT_FOUND` | 404 | ユーザーに対応する Player が無い |
| `AUTH_INVALID_CREDENTIALS` | 401 | ログインのメール／パスワード不一致（どちらが誤りかは返さない） |
| `AUTH_REFRESH_INVALID` | 401 | リフレッシュトークンが不正・再利用検知 |
| `AUTH_EMAIL_TAKEN` | 409 | 登録・アカウント移行でメールが使用済み |
| `AUTH_ALREADY_REGISTERED` | 400 | 本登録済みアカウントへの移行要求 |
| `AUTH_LINK_PAYLOAD_INVALID` | 400 | 移行要求に `email`+`password` も `googleAuthCode` も無い |
| `AUTH_VERIFICATION_INVALID` | 400 | メール確認トークンが不正・期限切れ・用途違い |
| `AUTH_RESET_TOKEN_INVALID` | 400 | パスワードリセットトークンが不正・期限切れ・用途違い |
| `AUTH_GOOGLE_NOT_CONFIGURED` | 501 | `GOOGLE_CLIENT_ID` 未設定 |
| `AUTH_GOOGLE_NOT_IMPLEMENTED` | 501 | Google OAuth 未実装 |

### グローバル例外ハンドラ

FastAPIの例外ハンドラで未捕捉例外を捕捉し、以下を実行する:

1. ERRORレベルでスタックトレースをログ出力
2. クライアントには `500` + `INTERNAL_UNEXPECTED_ERROR` を返却（スタックトレースは含めない）
3. リクエストIDをレスポンスに含め、ログとの突合を可能にする

## 設定値

```python
# backend/app/config.py に追加
LOG_LEVEL = "INFO"                  # ログレベル（環境変数 LOG_LEVEL で上書き可）
LOG_FORMAT = "text"                 # ログフォーマット（text / json、環境変数 LOG_FORMAT で上書き可）
```
