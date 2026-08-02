# 設計図レビュー — プロジェクト固有プロファイル

> 一般手順は [.claude/references/review-procedure.md](../references/review-procedure.md)、出力形式は [review-format.md](../references/review-format.md)。本書は `diagrams-review` 固有の値のみを持つ。
> 仕様書横断（`doc-review`）・修正適用（`fix-specs`）の固有値は [review-docs.md](review-docs.md)。

## 0. レビューパラメータ

| 項目 | 値 |
|------|-----|
| 保存先ディレクトリ | `docs/reviews/diagrams-review/` |
| レポートタイトル | 設計図レビュー結果 |
| カテゴリ | 仕様書との整合性 / コードとの整合性 / 設計図間の整合性 / Mermaid構文 / 網羅性 |

ファイル名は `YYYY-MM-DD_HHMMSS.md`。保存後に `python scripts/rotate_reviews.py --apply` を実行する（[review-format.md](../references/review-format.md)「保存先」）。

## 1. 観点

対象6図と照合先は [basic-design.md](basic-design.md) §1 の表。

| 分類 | 観点 |
|------|------|
| 仕様書との整合性 | ER図↔`tech_data.md` / クラス図↔`systems/`・`tech_data.md` / 画面遷移図↔`systems/ui*.md` / 戦闘フロー図↔`tech_battle.md`・`tech_offline.md` / APIシーケンス図↔`tech_api.md`・`tech_architecture.md` / システム構成図↔`tech_architecture.md`・不変条件 |
| コードとの整合性 | ER図↔`backend/app/models/` / クラス図↔`services/`・`stores/` / 画面遷移図↔`frontend/src/router/` / APIシーケンス図↔`routers/`・`frontend/src/api/` |
| 設計図間の整合性 | ER図↔クラス図（属性・リレーション）、画面遷移図↔APIシーケンス図（遷移で発生する呼び出し）、戦闘フロー図↔APIシーケンス図（呼び出しタイミング） |
| Mermaid構文 | [basic-design.md](basic-design.md) §4 の**機械検証**で判定する（目視しない） |
| 網羅性 | 主要機能に対応する図があるか、Phase 3〜5 の追加仕様が反映されているか、図内に TODO/TBD が残っていないか、決定先送りが台帳へリンクしているか（`check_docs.py --pending` の出力をそのまま取り込む） |
| 規約 | `check_doc_size.py` の `diagrams/` 配下 ERROR は重要度=高。超過時は同名ディレクトリへ図単位で切り出す案を書く |

## 2. 重要度の基準

| 重要度 | 基準 |
|-------|------|
| **高** | 設計図とコード/仕様書の重大な乖離（テーブル定義の不一致、画面遷移の欠落） |
| **中** | 設計図間の不整合、属性の過不足、フローの軽微な差異 |
| **低** | 命名の不統一、構文上の改善点、可読性 |

担当範囲の切り分けは [review-procedure.md](../references/review-procedure.md) §7 を参照。
