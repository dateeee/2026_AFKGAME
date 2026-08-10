# AFK GAME — 認証: アカウント移行（ゲスト→本登録）

> [tech_auth.md](../tech_auth.md) の子ファイル（§18〜§19）。`POST /api/auth/link-account` の処理を定める。
> 共通規約（正規化・ハッシュ・入力長・有効期限・現在時刻・エラー応答）は [account.md](account.md) §9、メール送信は [mail.md](mail.md) §16 が正。
> 移行後のフローは `tech_auth.md` §3「ゲスト→本登録の移行」、UI導線は [ui/onboarding.md](../../../design/systems/ui/onboarding.md)「ゲスト→本登録バナー」。

## 18. アカウント移行（`POST /api/auth/link-account`）

入口条件: `Authorization: Bearer <access_token>` が必須。ボディは `{ email, password }`（メール連携）または `{ googleAuthCode }`（Google連携）の**ちょうど一方**。
出口条件: 認証中のゲストユーザー自身が本登録化され（`is_guest = false`）、確認トークン1行とリフレッシュトークン1行が単一トランザクションで永続化され、トークンペアと `user` を 200 で返す（応答例は §5）。**ゲームデータは作り直さない**（`tech_auth.md` §3）。

1. アクセストークンを検証してユーザーを特定する（全エンドポイント共通の認証処理。失敗時のコードは AUTH_ 一覧）
2. ボディの形を判定する。`email`+`password` と `googleAuthCode` のちょうど一方でなければ（どちらも無い・両方ある）400 `AUTH_LINK_PAYLOAD_INVALID` を返し、何も変更しない
3. `googleAuthCode` が指定されている場合は、`GOOGLE_CLIENT_ID` が未設定なら 501 `AUTH_GOOGLE_NOT_CONFIGURED`、設定済みでも 501 `AUTH_GOOGLE_NOT_IMPLEMENTED` を返す。**Phase 2 の移行対象はメール連携だけ**とし、Google連携の実装は Google OAuth 対応時に行う
4. 手順1のユーザーが `is_guest = false` なら 400 `AUTH_ALREADY_REGISTERED` を返し、何も変更しない
5. ボディを検証する。`email` は必須・メール形式・254文字以内、`password` は必須・8文字以上128文字以下（§9「入力長」）。違反は 422 を返し、以降を実行しない
6. `email` を正規化し（§9）、正規化後の値で `users` を検索する。行があれば 409 `AUTH_EMAIL_TAKEN` を返し、何も変更しない
7. パスワードを bcrypt でハッシュ化する
8. 手順1のユーザーを更新する（正規化後の `email`、`password_hash`、`is_guest = false`、`email_verified = false`、`last_login_at` は現在時刻）。`id`・`display_name`・`created_at` は変更しない。**`is_guest = true` の行だけを更新し、更新件数が0なら 400 `AUTH_ALREADY_REGISTERED`** — 手順4の判定は READ COMMITTED では同時移行をすり抜けるため、二重移行の禁止をここで確定させる
9. 確認トークンを1件作成する（`purpose = verify_email`、`expires_at` は現在時刻 + 24時間、`used = false`）。生値のハッシュだけを保存する
10. トークンペアを発行する（§9）。**既存のリフレッシュトークンは失効させない**（ユーザーIDも権限も変わらないため。失効の方針は §12 と同じ）
11. 手順7〜10 を単一トランザクションでコミットする
12. コミット後に確認メールの送信を要求する（§16）

移行前に発行済みのアクセストークンは、期限切れ（最大30分）まで `isGuest = true` を載せたまま残る。**`isGuest` の値で機能を制限しない** — ゲスト扱いの判定が要る処理は、トークンではなくユーザーの現在値を見る。

## 19. 分岐一覧（アカウント移行）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | アクセストークン | 有効なトークンを提示 | 手順2へ進む |
| 2 | アクセストークン | 欠落・形式不正・期限切れ・署名不正 | AUTH_ 一覧に従い 401 を返し、ユーザーを変更しない |
| 3 | ペイロードの形 | `email`+`password` だけがある | メール連携として手順4へ進む |
| 4 | ペイロードの形 | `googleAuthCode` だけがある | Google連携として手順3へ進む |
| 5 | ペイロードの形 | どちらも無い | 400 `AUTH_LINK_PAYLOAD_INVALID` を返す |
| 6 | ペイロードの形 | 両方ある | 400 `AUTH_LINK_PAYLOAD_INVALID` を返す（連携先が一意に決まらない） |
| 7 | Google設定 | `GOOGLE_CLIENT_ID` が未設定 | 501 `AUTH_GOOGLE_NOT_CONFIGURED` を返す |
| 8 | Google設定 | `GOOGLE_CLIENT_ID` が設定済み | 501 `AUTH_GOOGLE_NOT_IMPLEMENTED` を返す（Phase 2 では未対応） |
| 9 | アカウント種別 | `is_guest` が true | 手順5へ進む |
| 10 | アカウント種別 | `is_guest` が false（本登録済み） | 400 `AUTH_ALREADY_REGISTERED` を返し、何も変更しない |
| 11 | メール形式 | メールアドレスとして妥当な形式 | 手順6へ進む |
| 12 | メール形式 | `@` を欠く等で形式が不正 | 422 `VALIDATION_ERROR` を返す |
| 13 | メール長 | 254文字以内 | 手順6へ進む |
| 14 | メール長 | 255文字以上 | 422 `VALIDATION_ERROR` を返す |
| 15 | パスワード長 | 8文字以上128文字以下 | 手順7へ進む |
| 16 | パスワード長 | 7文字以下（空文字を含む）または129文字以上 | 422 `VALIDATION_ERROR` を返す |
| 17 | メール重複 | 正規化後の値に一致する行が無い | 移行を続行する |
| 18 | メール重複 | 正規化後の値に一致する行がある | 409 `AUTH_EMAIL_TAKEN` を返し、何も変更しない |
| 19 | メール重複 | 手順6の通過後、更新時に `uq_users_email` 違反が起きる（同時移行） | 全体をロールバックし 409 `AUTH_EMAIL_TAKEN` を返す |
| 20 | 移行トランザクション | 手順7〜10 がすべて成功 | コミットし、トークンペアと `user` を 200 で返す |
| 21 | 移行トランザクション | 手順7〜10 の途中で失敗（DBエラー） | 全体をロールバックし、`is_guest`・`email`・確認トークン・リフレッシュトークンのいずれも変えない |
| 22 | 確認メール送信 | 送信に成功 | 応答を変えない |
| 23 | 確認メール送信 | 送信に失敗 | WARN ログを残し、移行は成功として 200 を返す（確認トークンの行は消さない） |
| 24 | 本登録化の更新件数 | 1件（手順4の時点から `is_guest` が変わっていない） | 確認トークンの作成（手順9）へ進む |
| 25 | 本登録化の更新件数 | 0件（同時移行で他のリクエストが先に本登録化した） | 400 `AUTH_ALREADY_REGISTERED` を返し、何も変更しない |
