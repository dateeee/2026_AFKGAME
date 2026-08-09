# AFK GAME — ログ設計・エラーハンドリング

> [tech_spec.md](../tech_spec.md) §6「ログ設計」。アーキテクチャ方針は [tech_architecture.md](tech_architecture.md)。

## ログライブラリ
Logback を使用。設定は `afkgame-env` の `logback.xml`（Boot 拡張の `logback-spring.xml` と `<springProfile>` は使えない）。Tomcat のアクセスログは Tomcat 側（`AccessLogValve`）が出力し、アプリのログとは別系統とする。

ログは**用途で3種別に分け、別ファイルへ出す**（通信 / アプリケーション / エラー）。種別の定義・出力先・ローテーション・書き方の正は [logging.md](../../process/coding_standards_backend/logging.md)。本書は種別を問わない**形式と語彙**（フォーマット・項目名・ロガー名・`reason`・エラーコード）を持つ。

## ログの書き方（共通部品）

各クラスは `LoggerFactory` を直接使わず、`afkgame-env` の `com.afkgame.env.logging` が提供する共通部品（`AppLogger` / `LoggerName` / `LogKey` / `LogReason` / `LogEntry`）で書く。**部品の役割と書き方の正は [logging.md](../../process/coding_standards_backend/logging.md) §4**。

`with()` で積んだ値は出力の間だけ MDC へ載り、text 形式では末尾の `key=value`、JSON 形式では独立フィールドとして出る。出力後は元の MDC へ戻すため、横断項目を壊さない。

## ログレベル方針

| レベル | 用途 | 例 |
|--------|------|-----|
| DEBUG | 開発用の詳細情報 | SQLクエリ |
| INFO | 正常系イベント | 通信の START / END、層をまたぐ呼び出しの START / END（AOP）、tick処理完了（処理tick数・結果）、ゲストアカウント作成 |
| WARNING | 想定内のエラー | 認証失敗（401）、バリデーションエラー（422）、リソース不足（ゴールド不足等） |
| ERROR | 想定外のエラー | 未捕捉例外、DB接続失敗、データ整合性エラー |

## ログフォーマット

**開発時（テキスト形式）:**
```
[2026-03-15 14:38:30] WARNING  afkgame.auth: ログイン失敗 reason=password_mismatch user_id=user_001 request_id=550e8400-e29b
```

**本番（構造化JSON）:**
```json
{
  "timestamp": "2026-03-15T14:38:30.123Z",
  "level": "WARNING",
  "logger": "afkgame.auth",
  "message": "ログイン失敗",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "client_ip": "127.0.0.1",
  "method": "POST",
  "path": "/api/auth/login",
  "reason": "password_mismatch"
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
| `afkgame.comm` | 通信の START / END。**通信ログファイルへ出る唯一のロガー**（`logging.md` §1.1） |
| `afkgame.layer` | AOP による境界ログ（Service・Repository の START / END） |
| `afkgame.middleware` | 例外ハンドラなどの横断処理 |
| `afkgame.health` | ヘルスチェック（運用監視向け） |

コード側の正は `LoggerName`。**実際に出力している領域だけ**を enum に持ち、新しい領域を書くときに追加する。

## ログ項目

項目名は snake_case（応答ボディの camelCase とは別体系）。コード側の正は `LogKey`。

| 項目 | 内容 | 付与 |
|------|------|------|
| `request_id` | リクエストID | `RequestLogFilter`（横断） |
| `player_id` | 認証済みユーザーID | `JwtAuthenticationFilter`（横断） |
| `client_ip` / `method` / `path` | 接続元・メソッド・パス | `RequestLogFilter`（横断） |
| `status_code` / `duration_ms` | ステータス・処理時間 | `RequestLogFilter`・例外ハンドラ |
| `reason` | 失敗理由 | 各処理 |
| `user_id` | 処理対象のユーザーID（認証済みを表す `player_id` とは別） | 各処理 |
| `token` / `email` | トークン・メールアドレス（**自動マスク**） | 各処理 |
| `direction` / `target` | 通信の方向（`in` / `out`）・送信先 | 通信ログ（`logging.md` §2） |
| `signature` / `args` / `result` | 境界のメソッド・引数・戻り値 | AOP（`logging.md` §3） |

横断項目はフィルタが MDC へ載せ、各所で詰め直さない（`common.md` §7 #5）。

## 失敗理由（reason）の値

想定内の失敗は WARNING で `reason` を残す。クライアントへは理由を出し分けない（[exception.md](../../process/coding_standards_backend/exception.md) §4 #2）ため、内部の切り分けは本表が担う。コード側の正は `LogReason`。

| reason | メッセージ | 発生条件 |
|--------|-----------|---------|
| `header_missing` | 認証失敗 | `Authorization` ヘッダが無い |
| `invalid_format` | 認証失敗 | `Bearer ` で始まらない |
| `token_expired` | 認証失敗 | アクセストークンの有効期限切れ |
| `invalid_token` | 認証失敗 | 署名不正・`sub` 欠落 |
| `invalid_token_type` | 認証失敗 | 用途クレーム（`type`）が `access` でない |
| `user_not_found` | 認証失敗 | トークンは正当だがユーザーが存在しない |
| `email_taken` | 登録失敗 | 登録時にメールが使用済み |
| `email_taken_conflict` | 登録失敗 | 重複確認の通過後に一意制約違反で判明した |
| `email_not_found` | ログイン失敗 | 該当するメールのユーザーが存在しない |
| `password_not_set` | ログイン失敗 | Google連携のみでパスワード未設定 |
| `password_mismatch` | ログイン失敗 | パスワードが一致しない |

## リクエストログ用フィルタ

全APIリクエストで、UUID v4 のリクエストID採番（レスポンスヘッダ `X-Request-ID`）と処理時間計測を行い、**通信ログ**へ START / END を出す。START は `direction`・`method`・`path`・`client_ip`、END は加えて `status_code`・`duration_ms`・`player_id`（認証済みの場合）。START / END の対や送信（外部API・SMTP）側の規約は [logging.md](../../process/coding_standards_backend/logging.md) §2 が正。

```
[2026-08-09 14:38:30] INFO  afkgame.comm: START direction=in method=POST path=/api/battle/tick client_ip=127.0.0.1 request_id=xxx
[2026-08-09 14:38:30] INFO  afkgame.comm: END   direction=in method=POST path=/api/battle/tick status_code=200 duration_ms=45 player_id=550e8400 request_id=xxx
```

## 機密情報のマスク規則

| 対象 | マスク方法 | 残せない長さの場合 |
|------|-----------|------------------|
| トークン値 | 先頭4文字 + `****` + 末尾4文字（例: `abc1****wxyz`） | 8文字以下は `****` のみ |
| パスワード | 出力禁止（ログに含めない） | — |
| メールアドレス | ローカル部の先頭2文字 + `***@` + ドメイン（例: `ab***@example.com`） | ローカル部が2文字以下は `***@<ドメイン>`、`@` が無ければ `****` |

伏せ字は固定長にして、元の値の長さを推測させない。適用は `LogKey.TOKEN` / `LogKey.EMAIL` が自動で行うため、**呼び出し側でマスクを書かない**。

## バックエンドエラーハンドリング

### 統一エラーレスポンス形式

全APIエラーレスポンスを以下の形式に統一する:

```json
{
  "error": {
    "code": "AUTH_PLAYER_NOT_FOUND",
    "message": "指定されたプレイヤーが見つかりません",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

応答ボディのキーは camelCase（[tech_api_common.md](tech_api_common.md) §5.0）。**ログ項目は snake_case**（上記「ログフォーマット」の `request_id` 等）であり、APIボディとは別体系である。

### エラーコード体系

| プレフィックス | 対象 | 例 |
|---------------|------|-----|
| `AUTH_` | 認証関連 | 一覧は下記「AUTH_ コード一覧」 |
| `BATTLE_` | 戦闘関連 | `BATTLE_NOT_IN_TOWER`, `BATTLE_ALREADY_WIPED`, `BATTLE_TICK_BUSY`(503) |
| `GAME_` | ゲーム状態関連 | `GAME_STATE_NOT_FOUND` |
| `SHOP_` | ショップ関連 | `SHOP_INSUFFICIENT_GOLD`, `SHOP_ITEM_SOLD_OUT`, `SHOP_INVENTORY_FULL` |
| `TOWER_` | 塔関連 | `TOWER_NOT_UNLOCKED`, `TOWER_INVALID_FLOOR` |
| `EQUIP_` | 装備関連 | `EQUIP_NOT_FOUND`, `EQUIP_SLOT_MISMATCH` |
| `SKILL_` | スキル関連 | `SKILL_INSUFFICIENT_SP`, `SKILL_PREREQUISITE_NOT_MET`（一覧は [tech_party.md §7](../detail/tech_party.md)） |
| `PARTY_` | パーティ関連 | `PARTY_LOCKED_IN_TOWER`, `PARTY_MEMBER_NOT_OWNED` |
| `CHARACTER_` | キャラクター関連 | `CHARACTER_NOT_FOUND` |
| `BASE_` | 施設関連 | `BASE_INSUFFICIENT_MATERIALS`, `BASE_MAX_LEVEL`（一覧は [tech_base.md §6](../detail/tech_base.md)） |
| `FORGE_` | 鍛冶屋関連 | `FORGE_INSUFFICIENT_MATERIALS`, `FORGE_LEVEL_TOO_LOW` |
| `RATE_LIMIT_` | レート制限 | `RATE_LIMIT_EXCEEDED`(429)。`Retry-After` ヘッダを併せて返す |
| `INTERNAL_` | サーバー内部エラー | `INTERNAL_UNEXPECTED_ERROR` |
| `HTTP_` | 業務コードを持たない Spring MVC 標準例外（**4xx のみ**。404・405・415 等） | `HTTP_405`。クライアントは個別分岐せず汎用エラー表示に倒す |

### AUTH_ コード一覧

クライアントは**メッセージ文字列ではなくコードで**分岐する（`AUTH_TOKEN_EXPIRED` は refresh、`AUTH_INVALID_TOKEN` は再ログインと、挙動が異なるため）。

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

`@RestControllerAdvice` で未捕捉例外を捕捉し、ERRORレベルでスタックトレースをログ出力したうえで、クライアントには `500` + `INTERNAL_UNEXPECTED_ERROR` とリクエストIDだけを返す（スタックトレースは含めない）。リクエストIDでログと突合できる。

## 設定値

`logback.xml` が環境変数を直接読む（プロパティファイルを経由しない）。値の正は [tech_operations.md](../nonfunctional/tech_operations.md) §12.2。

| 変数 | 既定 | 効果 |
|------|------|------|
| `LOG_LEVEL` | `INFO` | ロガー `afkgame` のレベル（`${LOG_LEVEL:-INFO}`） |
| `LOG_FORMAT` | `text` | エンコーダと appender の切替。`<include resource="logback-appenders-${LOG_FORMAT}.xml"/>` で `text` / `json` の定義を読み分ける（`<springProfile>` が使えないため、logback の変数置換で切り替える） |
| `LOG_DIR` | `${catalina.base:-.}/logs` | 3種別のログファイルの出力先ディレクトリ（`logging.md` §1） |
