# AFK GAME — 認証: メール確認

> [tech_auth.md](../tech_auth.md) の子ファイル（§20〜§21）。`GET /api/auth/verify-email` の処理を定める。
> 共通規約（ハッシュ方式・現在時刻・エラー応答）は [account.md](account.md) §9、確認トークンを発行する側は `account.md` §10（register）と [link.md](link.md) §18（link-account）、メール本文と有効期限は [mail.md](mail.md) §16 が正。
> 列定義は [tech_db/auth.md](../../basic/tech_db/auth.md) §3、エラーコードは [tech_error_handling.md](../../basic/tech_error_handling.md)「AUTH_ コード一覧」。

## 20. メール確認（`GET /api/auth/verify-email?token=xxx`）

入口条件: 認証不要（[tech_api/common.md](../../basic/tech_api/common.md) §5.0 の認証不要リストに含まれる）。クエリ `token` はメール本文のリンクに載せた生値。
出口条件: 対象ユーザーの `email_verified` が true になり、使ったトークンが `used = true` になって `{"status": "ok"}` を 200 で返す。

1. `token` を検証する（必須）。違反は 422 `VALIDATION_ERROR` を返し、以降を実行しない
2. 生値の SHA-256（16進小文字）で `email_verification_tokens` を検索する。行が無ければ 400 `AUTH_VERIFICATION_INVALID`
3. `purpose` が `verify_email` でなければ 400 `AUTH_VERIFICATION_INVALID`（再設定トークンの流用を防ぐ。`tech_db/auth.md` §3）
4. `used` が既に true なら、何も更新せず `{"status": "ok"}` を 200 で返す。**メール内リンクの再クリックは正常操作**であり、確認は既に済んでいるため失敗にしない（ログアウトの二重実行と同じ扱い。§14 手順5）
5. `expires_at` が現在時刻以前なら 400 `AUTH_VERIFICATION_INVALID`。**再送APIは設けない**ため、期限切れのユーザーは未確認のまま据え置く（§16.4）
6. 行の `user_id` でユーザーを検索する。行が無ければ 400 `AUTH_VERIFICATION_INVALID`（退会済み）
7. ユーザーの `email_verified` を true へ更新する。既に true なら値を変えない
8. 使ったトークンを `used = true` へ更新する。**同じユーザーの他の確認トークンは変更しない**（期限切れで自然に無効になる）
9. 手順7〜8 を単一トランザクションでコミットし `{"status": "ok"}` を返す

確認は**トークンの提示だけ**で成立し、ログイン状態を要求しない（メールクライアントから別ブラウザで開くため）。`email_verified` はゲーム機能を何も制限しないので、未確認のままでもプレイできる（`tech_auth.md` §3）。

## 21. 分岐一覧（メール確認）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | トークン指定 | クエリに `token` がある | 手順2へ進む |
| 2 | トークン指定 | 未指定または空文字 | 422 `VALIDATION_ERROR` を返す |
| 3 | トークンの存在 | ハッシュが一致する行がある | 手順3へ進む |
| 4 | トークンの存在 | 一致する行が無い | 400 `AUTH_VERIFICATION_INVALID` を返す |
| 5 | 用途 | `purpose` が `verify_email` | 手順4へ進む |
| 6 | 用途 | `purpose` が `password_reset` | 400 `AUTH_VERIFICATION_INVALID` を返し、`email_verified` を変えない |
| 7 | 使用状態 | `used` が false | 手順5へ進む |
| 8 | 使用状態 | `used` が既に true | 何も更新せず 200 を返す（冪等。リンクの再クリック） |
| 9 | 有効期限 | `expires_at` が現在時刻より後 | 手順6へ進む |
| 10 | 有効期限 | `expires_at` が現在時刻以前 | 400 `AUTH_VERIFICATION_INVALID` を返し、`email_verified` を変えない |
| 11 | 対象ユーザー | `user_id` のユーザーが存在する | 手順7へ進む |
| 12 | 対象ユーザー | `user_id` のユーザーが存在しない（退会済み） | 400 `AUTH_VERIFICATION_INVALID` を返す |
| 13 | 確認状態 | `email_verified` が false | true へ更新し、トークンを使用済みにして 200 を返す |
| 14 | 確認状態 | `email_verified` が既に true（別トークンで確認済み） | true のまま、トークンだけ使用済みにして 200 を返す |
| 15 | 確認トランザクション | 手順7〜8 がともに成功 | コミットして 200 を返す |
| 16 | 確認トランザクション | 途中で失敗（DBエラー） | 全体をロールバックし、`email_verified`・`used` のいずれも変えない |
