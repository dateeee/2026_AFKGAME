# レビュー共通 — プロジェクト固有プロファイル

> 一般手順は [review-procedure.md](../references/review-procedure.md)、出力形式は [review-format.md](../references/review-format.md)。
> 本書はレビュー系5スキルと `fix-specs` に**共通する AFK GAME 固有値**のみを持つ。スキル別の値は §3 の分冊で、各スキルは**本書 + 自分の分冊のみ**読む。

## 1. 共通パラメータ

分冊 §0 は「保存先ディレクトリ・レポートタイトル・カテゴリ・準備コマンド」だけを持ち、以下は再掲しない。

| 項目 | 値 |
|------|-----|
| 保存先 | `docs/reviews/<スキル名>/`（ファイル名は `YYYY-MM-DD_HHMMSS.md`） |
| ローテーション | `python scripts/rotate_reviews.py --apply`（直下を最新10件に保ち、超過分は `archive/` へ移動。削除はしない） |
| 準備コマンド | モード判定・差分特定・ISSUE採番は `.claude/scripts/review_prep.py`。**分冊 §0 のコマンドをそのまま実行する**（全量時は `--full` を追加） |
| 該当箇所の示し方 | コードは行番号（`xxx.java 行N〜M`）、仕様書・設計図はセクション番号 |

## 2. 重要度の共通軸

| 重要度 | 基準 |
|-------|------|
| **高** | 実際に壊れる問題（通信失敗・データ不整合）、セキュリティリスク、仕様・設計との重大な乖離 |
| **中** | ベストプラクティスからの逸脱、型安全性の欠如、将来の問題の原因、軽微な不一致 |
| **低** | コードスタイル・命名の不統一・可読性など、動作に影響しない改善 |

分冊の「重要度の基準」は本表の**具体化**。判断が割れたら分冊が勝つ。
範囲外の問題を指摘に立てない切り分けは [review-procedure.md](../references/review-procedure.md) §7。

## 3. 分冊（スキル別の固有値）

| スキル | 分冊 |
|-------|------|
| `backend-review` | [review/backend.md](review/backend.md) |
| `frontend-review` | [review/frontend.md](review/frontend.md) |
| `full-review` | [review/fullstack.md](review/fullstack.md) |
| `doc-review` | [review/docs.md](review/docs.md) |
| `diagrams-review` | [review/diagrams.md](review/diagrams.md) |
| `fix-specs` | [review/docs.md](review/docs.md) §5 修正適用の固有ルール |
