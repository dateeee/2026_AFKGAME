# バックエンドコーディング規約 — 記述

> 親: [coding-standards-backend.md](../coding-standards-backend.md)。本書は同 **§3** を担当する。
> 層の責務は §2 [layering.md](layering.md)、例外・ログは §4 [exception-logging.md](exception-logging.md)。正はプロジェクト側のコーディング規約ドキュメント。

## 3. 記述

| # | 規約 |
|---|------|
| 1 | インデント半角スペース4（継続行8）、タブ禁止、1行120字目安、UTF-8 / LF |
| 2 | `import` は `java` → `javax` → `org` → `com` → その他の順、グループ間に空行1つ。ワイルドカード・未使用 import 禁止 |
| 3 | フィールドは原則 `private final`。可変が許されるのは ORM がマッピングする Entity のみ |
| 4 | 不変データは `record`、getter/setter を持つ class は Entity に限る |
| 5 | `null` を返しうるメソッドは Javadoc に明記。`Optional` は戻り値専用（フィールド・引数に使わない） |
| 6 | 日時は `java.time`（既定は `Instant`）。`java.util.Date`・`Calendar` 禁止 |
| 7 | 整数値は `long` を既定にし、通貨・カウンタを浮動小数で持たない |
| 8 | **マジックナンバー禁止**。技術定数は `private static final` + Javadoc、運用値は設定ファイル、バランス値はマスターデータ |
| 9 | 早期 return でネストを浅く（3段以上ネストさせない）。ループ内の文字列 `+` 連結をしない |
| 10 | 可視性は最小に。`@Override` を省略せず、`@SuppressWarnings` には理由コメントを添える |
