# バックエンドコーディング規約 — 通信ログ

> [logging.md](../logging.md)（ログ規約の索引）の分冊。**§2 通信ログ**を担当する。
> ログ3種別の定義・出力先・appender 割り当ては [logging.md](../logging.md) §1、禁止事項とテストは同 §6・§7。
> ログフォーマット・項目名・ロガー名体系・マスク規則の正は [tech_logging.md](../../../tech/basic/tech_logging.md)（重複させない）。
> 受信フィルタの作り方と登録順は [filter.md](../filter.md)、アプリケーションログは [application.md](application.md)。

---

## 2. 通信ログ（communication.log）

対象は**プロセス境界をまたぐ通信**。受信（クライアント → API）と送信（API → 外部サービス）の両方を、1通信につき **START 1行 + END 1行**で残す。

| # | 規約 |
|---|------|
| 1 | **受信は `RequestLogFilter` だけが出す**。コントローラ・Service から通信ログを書かない（`filter.md` §1 の使い分けに従う） |
| 2 | **送信は外部通信を行うクラスが出す**（メール送信・外部API呼び出し）。呼び出しの直前に START、`finally` で END |
| 3 | **START と END は必ず対で出す**。例外で抜ける経路でも END を落とさない（`try` / `finally` で組む） |
| 4 | 方向は `direction`（`in` / `out`）で区別する。送信は相手を `target` に書く（`smtp`・`google_oauth` 等の固定値） |
| 5 | **ボディを出さない**。出すのはメタ情報（メソッド・パス・ステータス・所要時間・方向・相手）だけ。リクエストボディ・レスポンスボディ・クエリ文字列の値を載せない |
| 6 | レベルは INFO。HTTP 4xx / 5xx は「通信としては成立した」ので INFO のまま END を残す。**通信自体が成立しなかったとき**（接続不能・タイムアウト）だけ END を ERROR にする |
| 7 | 業務上の失敗理由（`reason`）を通信ログに書かない。それは [application.md](application.md) §4 の業務ログが持つ |

**出力例（受信・text 形式）:**

```
[2026-08-09 14:38:30] INFO  afkgame.comm: START direction=in method=POST path=/api/auth/login client_ip=127.0.0.1 request_id=550e8400
[2026-08-09 14:38:30] INFO  afkgame.comm: END   direction=in method=POST path=/api/auth/login status_code=200 duration_ms=45 player_id=user_001 request_id=550e8400
```

**出力例（送信）:**

```
[2026-08-09 14:38:31] INFO  afkgame.comm: START direction=out target=smtp request_id=550e8400
[2026-08-09 14:38:31] INFO  afkgame.comm: END   direction=out target=smtp status_code=250 duration_ms=310 request_id=550e8400
```

| 項目 | START | END | 備考 |
|------|-------|-----|------|
| `direction` | ○ | ○ | `in` / `out` |
| `method` / `path` / `client_ip` | ○（受信） | ○（受信） | 受信のみ |
| `target` | ○（送信） | ○（送信） | 送信のみ |
| `status_code` / `duration_ms` | — | ○ | END でのみ確定する |
| `request_id` ほか横断項目 | ○ | ○ | MDC が載せる（各所で詰め直さない） |
