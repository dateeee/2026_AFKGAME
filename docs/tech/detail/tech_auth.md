# AFK GAME — 認証システム仕様

> 技術仕様の全体は [tech_spec.md](../tech_spec.md)、ゲーム仕様は [game_spec.md](../../design/game_spec.md) を参照。

各操作の処理フローと分岐一覧は子ファイルが正（**節番号は本書からの通し**）。

| 節 | 子ファイル | 対象 |
|----|-----------|------|
| §8 | [tech_auth/init.md](tech_auth/init.md) | プレイヤー初期化（ゲスト作成） |
| §9〜§15 | [tech_auth/account.md](tech_auth/account.md) | 3操作の共通規約 / register / login / logout |
| §16〜§17 | [tech_auth/mail.md](tech_auth/mail.md) | 確認メール・再設定メールの送信 |
| §18〜§19 | [tech_auth/link.md](tech_auth/link.md) | link-account（ゲスト→本登録） |
| §20〜§21 | [tech_auth/verify.md](tech_auth/verify.md) | verify-email |
| §22〜§25 | [tech_auth/password_reset.md](tech_auth/password_reset.md) | password-reset（request / confirm） |

---

## 1. 認証方式

JWT（JSON Web Token）によるステートレス認証。アクセストークン + リフレッシュトークンの2トークン構成。

| 項目 | 値 |
|------|-----|
| アクセストークン有効期限 | 30分 |
| リフレッシュトークン有効期限 | 30日 |
| リフレッシュトークンローテーション | あり（リフレッシュ時に新トークン発行、旧トークン無効化） |
| パスワードハッシュ | bcrypt。`BCryptPasswordEncoder`（strength 12）で実装する（既存ハッシュと互換） |
| パスワード要件 | 長さの正は §9「入力長」 |

## 2. 登録方法

| 方法 | 詳細 |
|------|------|
| ゲストプレイ | 登録なしで即プレイ可能。サーバーがゲストアカウント（`guest_<UUID>`）を自動生成 |
| メール + パスワード | メールアドレスとパスワードで登録。確認メール送信→リンククリックで有効化 |
| Google OAuth 2.0 | Googleアカウントで登録/ログイン。認可コードフローを使用 |

## 3. 認証フロー

### ゲストプレイ
1. 初回アクセス時、サーバーがゲストアカウントを自動生成（`guest_<UUID>`）
2. ゲスト用のJWTを発行（通常ユーザーと同じトークン構造）
3. ゲストIDはLocalStorageに保存（ブラウザ識別用）
4. ゲストアカウントにもゲームデータが紐づき、サーバーDBに保存される
5. ゲストアカウントの有効期限: 90日（90日間アクセスがなければ自動削除）

### メール登録
1. メールアドレス + パスワードを送信
2. サーバーがアカウント作成（`email_verified: false`）
3. 確認メールを送信（確認トークン付きURL、有効期限24時間）
4. ユーザーがリンクをクリック → `email_verified: true` に更新
5. 未確認状態でもログイン・プレイは可能

### Google OAuth 2.0 連携
1. フロントからGoogleログイン画面にリダイレクト
2. Googleから認可コードを取得
3. バックエンドで認可コード → Googleアクセストークン → ユーザー情報取得
4. GoogleのメールアドレスでアカウントをDBに作成（or 既存アカウントと紐づけ）
5. JWTを発行してフロントに返却

### ゲスト→本登録の移行
1. ゲストプレイ中にメール登録またはGoogle連携を実行
2. ゲストアカウント自体を本登録化する（`is_guest = false` に変更し、email/password または Google 連携情報を付与）
3. ゲームデータはアカウントに紐づいたまま保持される（アカウントの作り直し・削除は行わない）
4. 新しいトークンペアを発行

### ログアウト
1. `POST /api/auth/logout` でリフレッシュトークンを `revoked = true` にする（API定義は [tech_api/auth.md](../basic/tech_api/auth.md) が正）
2. フロントはメモリ上のアクセストークンと LocalStorage のリフレッシュトークンを破棄し（保管先は §7）、ログイン画面へ戻る
3. ゲストの場合は併せて LocalStorage のゲストIDを破棄する。次回アクセス時は「ゲストプレイ」1. により**新しいゲストアカウントが生成される**ため、元のゲームデータには戻れない
4. 元データは90日の有効期限（「ゲストプレイ」5.）まで残るが、復旧の導線は設けない。UI側の警告文は [systems/ui.md](../../design/systems/ui.md) アカウント欄

## 4. JWT構造

**アクセストークン（ペイロード）:**
```json
{
  "sub": "user_id",
  "type": "access",
  "role": "user",
  "isGuest": false,
  "iat": 1709856000,
  "exp": 1709857800
}
```

**リフレッシュトークン:**
- DB保存（`refresh_tokens` テーブル）
- ローテーション: リフレッシュ時に旧トークンを `revoked = true` にし、新トークンを発行
- 不正検知: 既にrevokedなトークンでリフレッシュが試みられた場合、そのユーザーの全リフレッシュトークンを無効化

## 5. 認証API リクエスト/レスポンス例

**POST `/api/auth/guest`:**
```json
// リクエスト: ボディなし
// レスポンス:
{
  "accessToken": "eyJ...",
  "refreshToken": "abc123...",
  "user": {
    "id": "guest_550e8400-e29b-41d4-a716-446655440000",
    "isGuest": true
  }
}
```

**POST `/api/auth/register`:**
```json
// リクエスト:
{ "email": "user@example.com", "password": "securepass123" }
// レスポンス:
{
  "accessToken": "eyJ...",
  "refreshToken": "abc123...",
  "user": { "id": "user_001", "email": "user@example.com", "verified": false }
}
```

**POST `/api/auth/refresh`:**
```json
// リクエスト:
{ "refreshToken": "abc123..." }
// レスポンス:
{
  "accessToken": "eyJ...(new)",
  "refreshToken": "def456...(new)"
}
```

**POST `/api/auth/link-account`:**
```json
// リクエスト（メール連携の場合）:
{ "email": "user@example.com", "password": "securepass123" }
// リクエスト（Google連携の場合）:
{ "googleAuthCode": "4/0AX4..." }
// レスポンス:
{
  "accessToken": "eyJ...(new)",
  "refreshToken": "def456...(new)",
  "user": { "id": "user_001", "email": "user@example.com", "isGuest": false }
}
```

## 6. 認証関連DBモデル
`users`・`refresh_tokens`・`email_verification_tokens` の列・型・NULL可否・既定値・制約は [tech_db/auth.md](../basic/tech_db/auth.md) が正（DBスキーマの正の宣言は [spec_ownership.md](../../process/spec_ownership.md)）。本書は認証の処理仕様のみを扱う。

## 7. フロント認証フロー

**トークン管理:**
- アクセストークン: メモリ上に保持（Piniaストア）。リロード時は消失
- リフレッシュトークン: LocalStorage に保持（キー: `refresh_token`）。`httpOnly` Cookie は採らない — ログアウト・ゲストID破棄をフロント側で完結させるため（XSS リスクの受容判断は [tech_security.md](../nonfunctional/tech_security.md) §11.7）
- API呼び出し時にアクセストークンを `Authorization: Bearer <token>` ヘッダーに付与
- 401レスポンス受信時にリフレッシュトークンで自動再取得を試行
- リフレッシュも失敗した場合はログイン画面にリダイレクト（ゲストの場合は新規ゲスト作成）

**Phase 1との互換:**
- Phase 1（認証なし）では全ユーザーをゲスト扱い
- Phase 2で認証実装時にゲスト→本登録の移行パスを提供
- フロントの `api/client.ts` の `USE_API` フラグと併用（ローカル計算モード時は認証スキップ）

