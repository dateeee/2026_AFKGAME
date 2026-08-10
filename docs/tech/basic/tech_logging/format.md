# ログ設計 — ログフォーマットと設定値

> 親: [tech_logging.md](../tech_logging.md)。ログライブラリ・共通部品の使い方・ログレベル方針は親が正であり、本書では繰り返さない。
> ログ3種別の出力先ファイルとローテーションは [logging.md](../../../process/coding_standards_backend/logging.md) §1、環境変数そのものの値の正は [tech_operations.md](../../nonfunctional/tech_operations.md) §12.2。

---

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

## 設定値

`logback.xml` が環境変数を直接読む（プロパティファイルを経由しない）。

| 変数 | 既定 | 効果 |
|------|------|------|
| `LOG_LEVEL` | `INFO` | ロガー `afkgame` のレベル（`${LOG_LEVEL:-INFO}`） |
| `LOG_FORMAT` | `text` | エンコーダと appender の切替。`<include resource="logback-appenders-${LOG_FORMAT}.xml"/>` で `text` / `json` の定義を読み分ける（`<springProfile>` が使えないため、logback の変数置換で切り替える） |
| `LOG_DIR` | `${catalina.base:-.}/logs` | 3種別のログファイルの出力先ディレクトリ（`logging.md` §1） |
