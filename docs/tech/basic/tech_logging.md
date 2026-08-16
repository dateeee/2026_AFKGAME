# AFK GAME — ログ設計（索引）

> [tech_spec.md](../tech_spec.md) §6「ログ設計」。アーキテクチャ方針は [tech_architecture.md](tech_architecture.md)。
> **クライアントへ返すエラーの形式・コード体系・例外ハンドラは [tech_error_handling.md](tech_error_handling.md)**（§9）。本書はログ側の語彙のみを持つ。

---

## 子ファイル索引

本書はログの土台（ライブラリ・共通部品の使い方・レベル方針）が正。**機能追加のたびに行が増える表**は分冊が正。

| 分冊 | 担当する見出し |
|------|--------------|
| [tech_logging/format.md](tech_logging/format.md) | ログフォーマット / 設定値 |
| [tech_logging/fields.md](tech_logging/fields.md) | ロガー名体系 / ログ項目 / リクエストログ用フィルタ / 機密情報のマスク規則 |
| [tech_logging/reason.md](tech_logging/reason.md) | 失敗理由（reason）の値 |

**見出し名は分割前から変えていない。** コード側（Javadoc・`logback.xml`）や他の規約が持つ `tech_logging.md「<見出し>」` の参照は、本表で分冊を引く。

## ログライブラリ
Logback を使用。設定は `afkgame-env` の `logback.xml`（Boot 拡張の `logback-spring.xml` と `<springProfile>` は使えない）。Tomcat のアクセスログは Tomcat 側（`AccessLogValve`）が出力し、アプリのログとは別系統とする。

ログは**用途で3種別に分け、別ファイルへ出す**（通信 / アプリケーション / エラー）。種別の定義・出力先・ローテーション・書き方の正は [logging.md](../../process/coding_standards_backend/logging.md)。本書と分冊は種別を問わない**形式と語彙**（フォーマット・項目名・ロガー名・`reason`・エラーコード）を持つ。

## ログの書き方（共通部品）

各クラスは `LoggerFactory` を直接使わず、`afkgame-env` の `com.afkgame.env.logging` が提供する共通部品（`AppLogger` / `LoggerName` / `LogKey` / `LogReason` / `LogEntry`）で書く。**役割と書き方の正は [logging/application.md](../../process/coding_standards_backend/logging/application.md) §4**。

`with()` で積んだ値は出力の間だけ MDC へ載り、text 形式では末尾の `key=value`、JSON 形式では独立フィールドとして出る。出力後は元の MDC へ戻すため、横断項目を壊さない。

## ログレベル方針

| レベル | 用途 | 例 |
|--------|------|-----|
| DEBUG | 開発用の詳細情報 | SQLクエリ |
| INFO | 正常系イベント | 通信の START / END、層をまたぐ呼び出しの START / END（AOP）、tick処理完了（処理tick数・結果）、ゲストアカウント作成 |
| WARNING | 想定内のエラー | 認証失敗（401）、バリデーションエラー（422）、リソース不足（ゴールド不足等） |
| ERROR | 想定外のエラー | 未捕捉例外、DB接続失敗、データ整合性エラー |

- **`WARNING` は出力上の表記**で、Logback の標準レベル名は `WARN`。text 形式は `logback-appenders-text.xml` の `%replace(%level){'WARN','WARNING'}`、JSON 形式は `JsonLogFormatter#levelName` が変換する。**レベルの指定（`LOG_LEVEL`・`<logger level>`）とコード側の `Level` には `WARN` を使う**
