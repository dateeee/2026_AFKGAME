# AFK GAME — 認証: メール送信

> [tech_auth.md](../tech_auth.md) の子ファイル（§16〜§17）。確認メール・パスワード再設定メールの**送信**を定める。
> 送信を要求する側は [account.md](account.md) §10（register）・[link.md](link.md) §18（link-account）・[password_reset.md](password_reset.md) §22（再設定要求）。トークンの**検証**は [verify.md](verify.md) §20・`password_reset.md` §24。
> 環境変数は [tech_operations.md](../../nonfunctional/tech_operations.md) §12.2、設定保持 Bean の作り方は [tech_backend.md](../../basic/tech_backend.md) §4.2、送信側の通信ログは [logging/communication.md](../../../process/coding_standards_backend/logging/communication.md) §2 が正。

## 16. メール送信の共通規約

### 16.1 送信のタイミングと失敗の扱い

| 項目 | 規約 |
|------|------|
| 実行位置 | **呼び出し元のトランザクションがコミットしたあと、その外**で実行する。送信要求はトランザクション内で登録し、コミット成功を待つ |
| ロールバック時 | 送信しない（存在しないユーザー・トークンに宛てたメールを出さないため） |
| 失敗の扱い | 業務エラーにしない。WARN ログだけ残し、API 応答・HTTPステータスを変えない |
| 再試行 | しない。1回の送信要求につき送信は1回 |
| 同期・非同期 | 同期で送り、応答時間に含める。接続・応答のタイムアウトは**5秒**で、超過は送信失敗とする |
| ログ | START / END を通信ログへ出す。**トークン生値・本文を出さない**。宛先は `LogKey.EMAIL` でマスクする |

### 16.2 設定値

値は `afkgame-env` の設定保持 Bean から読む。

| 設定 | プロパティキー | 環境変数 | 既定値 |
|------|--------------|---------|-------|
| SMTP接続先 | `mail.smtp.host` / `mail.smtp.port` | `SMTP_HOST` / `SMTP_PORT` | なし / `587` |
| SMTP認証 | `mail.smtp.user` / `mail.smtp.password` | `SMTP_USER` / `SMTP_PASSWORD` | なし |
| STARTTLS | `mail.smtp.starttls.required` | `SMTP_STARTTLS_REQUIRED` | `true`（**必須**。`false` はローカルの平文 SMTP 用） |
| タイムアウト | `mail.smtp.timeout` | — | `5000`（ミリ秒） |
| 差出人 | `mail.from` | — | `noreply@afkgame.example`（**仮置き**。運用ドメイン確定時に差し替える） |
| リンク生成元 | `frontend.base.url` | `FRONTEND_BASE_URL` | `http://localhost:5173` |

**`SMTP_HOST` が未設定なら送信せず、宛先と用途を INFO ログへ残すだけにする**（ローカルで SMTP を立てずに動かすため）。本番プロファイルでの未設定は起動時バリデーションで落とす（§12.2）。

### 16.3 メール本文

本文は**プレーンテキストのみ**（HTMLメールを送らない）。トークンの生値はリンクにだけ載せる。

| 種別 | 件名 | リンク | 有効期限 |
|------|------|-------|---------|
| 確認メール | `【AFK GAME】メールアドレスの確認` | `{frontend.base.url}/verify-email?token=<生値>` | 24時間（`tech_auth.md` §3 が正） |
| 再設定メール | `【AFK GAME】パスワードの再設定` | `{frontend.base.url}/password-reset?token=<生値>` | **1時間**（本節が正。乗っ取り時に書き換えられる窓を狭めるため確認メールより短い） |

本文には①操作の説明 ②リンク ③有効期限 ④「心当たりが無ければ破棄してよい」旨 の4点を含め、宛先以外の個人情報（表示名・パスワード）を入れない。

### 16.4 再送

**Phase 2 では確認メールの再送APIを設けない**（2026-08-09 確定）。発行経路は register（§10 手順6）と link-account（§18）の2つだけで、期限切れ・不達なら未確認のまま据え置く。`email_verified` はゲーム機能を何も制限しない（`tech_auth.md` §3「未確認状態でもログイン・プレイは可能」）ため、未確認のままでも支障が出ないことを前提にした判断である。

再設定メールは §22 を繰り返し呼べるため、実質の再送導線を持つ（呼び出し回数の制限は [tech_security.md](../../nonfunctional/tech_security.md) §11.6 が正。未実装は [known_issues.md](../../../backlog/known_issues.md) #7）。

## 17. 分岐一覧（メール送信）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | トランザクション | 呼び出し元がコミットに成功した | 送信を実行する |
| 2 | トランザクション | 呼び出し元がロールバックした | 送信せず、ログも残さない |
| 3 | SMTP設定 | `mail.smtp.host` が設定されている | SMTP サーバーへ送信する |
| 4 | SMTP設定 | `mail.smtp.host` が未設定 | 送信せず、宛先（マスク済み）と用途を INFO ログへ残して正常終了とする |
| 5 | 送信結果 | 送信に成功した | 通信ログへ END を出し、呼び出し元へ制御を返す |
| 6 | 送信結果 | 送信に失敗した（接続不可・認証失敗・タイムアウト） | WARN ログを残し、例外を呼び出し元へ伝播させない |
| 7 | 種別 | 確認メール | §16.3 の件名・リンク・有効期限24時間で本文を組む |
| 8 | 種別 | 再設定メール | §16.3 の件名・リンク・有効期限1時間で本文を組む |
