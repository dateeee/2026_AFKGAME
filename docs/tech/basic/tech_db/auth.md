# テーブル定義 — 認証・アカウント

> 親: [tech_db.md](../tech_db.md)。命名規約・型マッピング・共通の列規約・外部キー動作は親が正であり、本書では繰り返さない。
> 視覚化は [er_diagram/player.md](../../../diagrams/er_diagram/player.md)「認証・アカウント系」、認証フロー・トークン有効期間は [tech_auth.md](../../detail/tech_auth.md) が正。

---

## 1. `users`（Phase 2）

実装: `backend/app/models/user.py` `User`

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(50)` | 不可 | `user_<uuid4>` | PK。ゲストは `guest_<uuid4>` |
| `email` | `VARCHAR(255)` | 可 | — | UNIQUE。ゲストは NULL |
| `password_hash` | `VARCHAR(255)` | 可 | — | ゲスト・OAuth のみのユーザーは NULL |
| `google_id` | `VARCHAR(255)` | 可 | — | UNIQUE |
| `display_name` | `VARCHAR(100)` | 不可 | `冒険者` | — |
| `is_guest` | `BOOLEAN` | 不可 | `true` | — |
| `email_verified` | `BOOLEAN` | 不可 | `false` | — |
| `created_at` | `DATETIME(tz)` | 不可 | 現在時刻 | — |
| `last_login_at` | `DATETIME(tz)` | 不可 | 現在時刻 | — |

## 2. `refresh_tokens`（Phase 2）

実装: `backend/app/models/user.py` `RefreshToken`

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `INTEGER` | 不可 | 自動採番 | PK |
| `user_id` | `VARCHAR(50)` | 不可 | — | FK → `users.id` |
| `token_hash` | `VARCHAR(255)` | 不可 | — | UNIQUE。平文トークンは保存しない |
| `expires_at` | `DATETIME(tz)` | 不可 | — | 有効期間は [tech_auth.md](../../detail/tech_auth.md) §6 が正 |
| `revoked` | `BOOLEAN` | 不可 | `false` | 失効はレコード削除ではなくフラグで表す |
| `created_at` | `DATETIME(tz)` | 不可 | 現在時刻 | — |

## 3. `email_verification_tokens`（Phase 2）

実装: `backend/app/models/user.py` `EmailVerificationToken`

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `INTEGER` | 不可 | 自動採番 | PK |
| `user_id` | `VARCHAR(50)` | 不可 | — | FK → `users.id` |
| `token_hash` | `VARCHAR(255)` | 不可 | — | UNIQUE |
| `purpose` | `VARCHAR(20)` | 不可 | `verify_email` | `verify_email` / `password_reset`。用途をまたいだ流用を防ぐため発行・検証の双方で一致を要求する |
| `expires_at` | `DATETIME(tz)` | 不可 | — | 有効期間は [tech_auth.md](../../detail/tech_auth.md) §6 が正 |
| `used` | `BOOLEAN` | 不可 | `false` | 使用済みトークンの再利用を防ぐ |
| `created_at` | `DATETIME(tz)` | 不可 | 現在時刻 | — |

## 4. インデックスと検索パターン

主キーと一意制約が張るインデックスのみを持ち、二次インデックスは持たない（方針は [tech_db.md](../tech_db.md) §6）。

| 検索パターン | 使うインデックス | 判断 |
|------------|---------------|------|
| メールアドレスでログインする | `users.email`（UNIQUE） | 充足 |
| Google アカウントでログインする | `users.google_id`（UNIQUE） | 充足 |
| リフレッシュトークンを照合する | `refresh_tokens.token_hash`（UNIQUE） | 充足 |
| 確認・再設定トークンを照合する | `email_verification_tokens.token_hash`（UNIQUE） | 充足 |
| ユーザーの全リフレッシュトークンを失効させる | なし（`refresh_tokens.user_id`） | 二次インデックスを張らない。実行頻度がログアウト・パスワード変更時に限られるため、追加は [tech_db.md](../tech_db.md) §6-3 の再評価ラインで判断する |
