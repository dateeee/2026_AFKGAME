# ログ設計 — 失敗理由（reason）の値

> 親: [tech_logging.md](../tech_logging.md)。ログライブラリ・共通部品の使い方・ログレベル方針は親が正であり、本書では繰り返さない。
> ログ項目としての `reason` は [fields.md](fields.md)「ログ項目」。

想定内の失敗は WARNING で `reason` を残す。クライアントへは理由を出し分けない（[exception.md](../../../process/coding_standards_backend/exception.md) §4 #2）ため、内部の切り分けは本書が担う。コード側の正は `LogReason`。

**機能ごとに節を分ける**（1つの表へ足し続けない）。新しい `reason` は該当する節へ足し、無ければ節を新設する。

---

## 認証・トークン検証

| reason | メッセージ | 発生条件 |
|--------|-----------|---------|
| `header_missing` | 認証失敗 | `Authorization` ヘッダが無い |
| `invalid_format` | 認証失敗 | `Bearer ` で始まらない |
| `token_expired` | 認証失敗 | アクセストークンの有効期限切れ |
| `invalid_token` | 認証失敗 | 署名不正・`sub` 欠落 |
| `invalid_token_type` | 認証失敗 | 用途クレーム（`type`）が `access` でない |
| `user_not_found` | 認証失敗 / メール確認失敗 / パスワード再設定失敗 | トークンは正当だがユーザーが存在しない（確認・再設定では退会済み） |

## 登録・ログイン・アカウント移行

| reason | メッセージ | 発生条件 |
|--------|-----------|---------|
| `email_taken` | 登録失敗 / 移行失敗 | メールが使用済み |
| `email_taken_conflict` | 登録失敗 / 移行失敗 | 重複確認の通過後に一意制約違反で判明した |
| `email_not_found` | ログイン失敗 / 再設定要求を無視 | 該当するメールのユーザーが存在しない |
| `password_not_set` | ログイン失敗 / 再設定要求を無視 | Google連携のみでパスワード未設定 |
| `password_mismatch` | ログイン失敗 | パスワードが一致しない |
| `link_payload_invalid` | 移行失敗 | 連携方法が一意に決まらない（どちらも無い・両方ある） |
| `google_not_configured` | 移行失敗 | Google連携を要求されたが `GOOGLE_CLIENT_ID` が未設定 |
| `google_not_implemented` | 移行失敗 | Google連携を要求されたが Phase 2 では未対応 |
| `already_registered` | 移行失敗 | 移行対象が既に本登録済み |

## リフレッシュ・ログアウト

| reason | メッセージ | 発生条件 |
|--------|-----------|---------|
| `refresh_not_found` | リフレッシュ失敗 / ログアウト失敗 | リフレッシュトークンに該当する行が無い |
| `refresh_expired` | リフレッシュ失敗 | リフレッシュトークンの有効期限切れ |
| `refresh_reused` | 不正リフレッシュトークン検知 | 失効済みのトークンが再利用された（当該ユーザーの全トークンを失効させる） |
| `refresh_owner_mismatch` | 他ユーザーのリフレッシュトークンでログアウト | トークンの持ち主が認証ユーザーと異なる |

## メール確認・パスワード再設定

| reason | メッセージ | 発生条件 |
|--------|-----------|---------|
| `verification_not_found` | メール確認失敗 | 確認トークンに該当する行が無い |
| `verification_purpose_mismatch` | メール確認失敗 | 用途が `verify_email` でない（再設定トークンの流用） |
| `verification_expired` | メール確認失敗 | 確認トークンの有効期限切れ |
| `reset_not_found` | パスワード再設定失敗 | 再設定トークンに該当する行が無い |
| `reset_purpose_mismatch` | パスワード再設定失敗 | 用途が `password_reset` でない（確認トークンの流用） |
| `reset_used` | パスワード再設定失敗 | 再設定トークンが使用済み（確認と違い冪等にしない） |
| `reset_expired` | パスワード再設定失敗 | 再設定トークンの有効期限切れ |

## 戦闘tick

| reason | メッセージ | 発生条件 |
|--------|-----------|---------|
| `clock_skew` | tick処理をスキップ | `last_tick_at` が現在時刻より未来（サーバー時刻の巻き戻し・データ不整合）。`pending_ticks = 0` として扱い `last_tick_at` を更新しない（[tech_tick.md](../../detail/tech_tick.md) §1.1） |

## 横断（AOP境界ログ）

| reason | メッセージ | 発生条件 |
|--------|-----------|---------|
| `exception` | — | AOP境界ログ（`afkgame.layer`）のEND出力時、例外で抜けた（`logging/application.md` §3 規約3。ERRORにはしない） |
