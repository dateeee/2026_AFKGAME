# ログ設計 — ロガー名・ログ項目・マスク規則

> 親: [tech_logging.md](../tech_logging.md)。ログライブラリ・共通部品の使い方・ログレベル方針は親が正であり、本書では繰り返さない。
> 出力の形と環境変数は [format.md](format.md)、失敗理由（`reason`）の値は [reason.md](reason.md)。

---

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
| `error_code` | システム例外のエラーコード（`INTERNAL_` 接頭辞）。応答には出さない | 例外ハンドラ |
| `user_id` | 処理対象のユーザーID（認証済みを表す `player_id` とは別） | 各処理 |
| `token` / `email` | トークン・メールアドレス（**自動マスク**） | 各処理 |
| `direction` / `target` | 通信の方向（`in` / `out`）・送信先 | 通信ログ（`logging/communication.md`） |
| `signature` / `args` / `result` | 境界のメソッド・引数・戻り値 | AOP（`logging/application.md` §3） |

横断項目はフィルタが MDC へ載せ、各所で詰め直さない（`common.md` §7 #5）。

## リクエストログ用フィルタ

全APIリクエストで、UUID v4 のリクエストID採番（レスポンスヘッダ `X-Request-ID`）と処理時間計測を行い、**通信ログ**へ START / END を出す。出力項目・START / END の対・送信（外部API・SMTP）側の規約は [logging/communication.md](../../../process/coding_standards_backend/logging/communication.md) §2 が正。

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
