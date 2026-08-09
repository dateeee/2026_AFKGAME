# AFK GAME — エラーハンドリング

> [tech_spec.md](../tech_spec.md) §9。バックエンドがクライアントへ返すエラーの**形式とコード体系**を定める。
> ログの形式・項目名・失敗理由（`reason`）は [tech_logging.md](tech_logging.md)、通信切断時のリトライ挙動は [tech_architecture.md](tech_architecture.md)、例外クラスの実装規約は [exception.md](../../process/coding_standards_backend/exception.md) を参照。

## 統一エラーレスポンス形式

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

応答ボディのキーは camelCase（[tech_api/common.md](tech_api/common.md) §5.0）。**ログ項目は snake_case**（[tech_logging.md](tech_logging.md)「ログフォーマット」の `request_id` 等）であり、APIボディとは別体系である。

### 入力チェック違反の `details`

`VALIDATION_ERROR`（422）のときだけ `error.details` を添える。**どの項目が落ちたかをクライアントが特定できるようにする**ため（TERASOLUNA ガイドライン 5.1.3.4.5「クライアントが再操作で解消できる場合は詳細を含める」）。ほかのコードでは付けない。

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "リクエストの入力値が不正です",
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "details": [
      { "target": "password", "code": "Size" },
      { "target": "email", "code": "Email" }
    ]
  }
}
```

| キー | 内容 |
|------|------|
| `target` | 違反したリクエストボディのプロパティ名（camelCase。ネストは `party.members[0].id` のようにドット区切り） |
| `code` | Bean Validation の制約名（`NotBlank`・`Size`・`Email` 等）。**文言はフロントエンドが `target` + `code` から組み立てる**（サーバーは文言を解決しない） |

- **`rejectedValue`（入力値そのもの）は含めない**。パスワード・トークンが応答とアクセスログへ回るため
- 順序は保証しない。クライアントは `target` で引く
- ガイドラインの例（`{code, message, details}` をトップレベルに置く）とは入れ子が異なるが、**本プロジェクトは全エラーを `error` の下にそろえる方針を優先する**（本節冒頭の形式）

## エラーコード体系

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
| `VALIDATION_` | 入力チェック違反（422） | `VALIDATION_ERROR`。違反項目は `details`（上記） |
| `HTTP_` | 業務コードを持たない Spring MVC 標準例外（**4xx のみ**。400・404・405・415 等） | `HTTP_400`（JSON の構文破損）・`HTTP_405`。クライアントは個別分岐せず汎用エラー表示に倒す |

## AUTH_ コード一覧

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
| `AUTH_LINK_PAYLOAD_INVALID` | 400 | 移行要求のボディが `email`+`password` / `googleAuthCode` のちょうど一方でない（どちらも無い・両方ある） |
| `AUTH_VERIFICATION_INVALID` | 400 | メール確認トークンが不正・期限切れ・用途違い（**使用済みは 200。冪等**） |
| `AUTH_RESET_TOKEN_INVALID` | 400 | パスワードリセットトークンが不正・期限切れ・用途違い・使用済み |
| `AUTH_GOOGLE_NOT_CONFIGURED` | 501 | `GOOGLE_CLIENT_ID` 未設定 |
| `AUTH_GOOGLE_NOT_IMPLEMENTED` | 501 | Google OAuth 未実装 |

内部の切り分けに使う失敗理由（`reason`）は [tech_logging.md](tech_logging.md)「失敗理由（reason）の値」が正。クライアントへは理由を出し分けない（[exception.md](../../process/coding_standards_backend/exception.md) §4 #2）。

## グローバル例外ハンドラ

`@RestControllerAdvice` で未捕捉例外を捕捉し、ERRORレベルでスタックトレースをログ出力したうえで、クライアントには `500` + `INTERNAL_UNEXPECTED_ERROR` とリクエストIDだけを返す（スタックトレースは含めない）。リクエストIDでログと突合できる。
