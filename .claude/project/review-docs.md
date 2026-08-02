# ドキュメント系レビュー — プロジェクト固有プロファイル

> 一般手順は [.claude/references/review-procedure.md](../references/review-procedure.md)、出力形式は [review-format.md](../references/review-format.md)。本書は AFK GAME 固有の値のみを持つ。
> 対象スキル: `doc-review`（仕様書横断）、`diagrams-review`（設計図整合）、`fix-specs`（修正適用）。

## 0. レビューパラメータ

| スキル | 保存先ディレクトリ | レポートタイトル | カテゴリ |
|-------|-----------------|---------------|---------|
| `doc-review` | `docs/reviews/doc-review/` | 仕様レビュー結果 | 整合性 / 網羅性 / 規約 |
| `diagrams-review` | `docs/reviews/diagrams-review/` | 設計図レビュー結果 | 仕様書との整合性 / コードとの整合性 / 設計図間の整合性 / Mermaid構文 / 網羅性 |

ファイル名は `YYYY-MM-DD_HHMMSS.md`。保存後に `python scripts/rotate_reviews.py --apply` を実行する（[review-format.md](../references/review-format.md)「保存先」）。

## 1. 差分モードの照合先（`doc-review`）

変更ファイルごとに、下表の照合先を**該当セクションのみ**読む。索引構成のため、変更が子ファイルなら**その子と照合先のセクションだけ**を読む（索引の全文読み込みは不要）。

| 変更ファイル | 照合先 |
|------------|--------|
| `design/game_spec.md`、`design/systems/*.md` | `data/master/`、`tech/`、`TOWERS_OVERVIEW.md`、`SKILLS_OVERVIEW.md` |
| `data/master_data.md`、`data/master/*.md` | `design/systems/`、`tech/`、各塔ファイル（数値が変わった場合のみ） |
| `tech/*.md` | `design/systems/`、`data/master/`、相互 |
| `towers/NNN_*.md` | `TOWERS_OVERVIEW.md`、`master_data.md`、`game_spec.md`（塔・ドロップ関連） |
| `skills/NNN_*.md` | `SKILLS_OVERVIEW.md`、`game_spec.md`（スキル関連） |
| `open_specs.md`（存在する場合） | 確定項目が反映されるべき各仕様書 |
| `README.md` / `CLAUDE.md` / `development_process.md` / `glossary.md` / `documentation_rules.md` | ディレクトリ構成・索引・リンク・用語の整合のみ |

## 2. 全量モードの分担（`doc-review`・最大4体）

| 担当 | 対象ファイル |
|------|------------|
| 数値・計算式・定数 | `design/systems/`、`data/master/`、`tech_data.md`、`tech_battle.md`、`tech_offline.md` |
| 塔データ | `TOWERS_OVERVIEW.md`、`towers/001〜010`、`data/master/`、`systems/dungeon.md`、`master_data.md`（塔一覧） |
| スキル・API・データ構造 | `SKILLS_OVERVIEW.md`、`skills/001〜006`、`systems/character.md`、`tech_api.md`、`tech_data.md`、`tech_auth.md` |
| 網羅性・Phase整合・リンク | `README.md`、`CLAUDE.md`、`glossary.md`、`development_process.md`、`documentation_rules.md`、各索引 + 全ファイルへの grep（TBD・未定・Phase表記） |

## 3. `doc-review` の観点

| 分類 | # | 観点 |
|------|---|------|
| 整合性 | 1 | **数値の一致**: ダメージ計算式・経験値計算式・ポーション定義・装備ステータス計算式・ドロップ率スケーリング式、tick間隔・ターン数/tick が文書間で一致するか |
| 整合性 | 2 | **用語・IDの一致**: 敵ID・装備スロット名・塔ID・ダンジョンID・ポーションID |
| 整合性 | 3 | **仕様の矛盾**: 戦闘フロー・オフライン計算・認証フロー・ショップ仕様 |
| 整合性 | 4 | **Phase整合性**: 機能のPhase割り当てが全文書で一致するか |
| 整合性 | 5 | **塔データ**: 階層数・推奨LV・解放条件・ボス名が概要と一致するか |
| 整合性 | 6 | **API網羅性**: `game_spec.md` の機能に対応するAPIが `tech_api.md` にあるか |
| 整合性 | 7 | **データ構造**: `tech_data.md` のJSON構造が `game_spec.md` の仕様を表現できるか |
| 網羅性 | 8 | `open_specs.md` があれば、その確定済み項目が対応仕様書に反映されているか |
| 網羅性 | 9 | 「TBD」「後日検討」「未定」が未管理で残っていないか（grep で抽出してから該当箇所のみ読む）。`open_specs.md` が不在なら本文にこれらが残っていること自体が指摘対象 |
| 網羅性 | 10 | 言及されているが詳細が未定義の機能がないか |
| 網羅性 | 11 | `docs/` のファイルが `README.md` の索引・ディレクトリ構成に未記載でないか（重要度=中） |
| 規約 | 12 | `python scripts/check_doc_size.py --sections` の出力を**そのまま取り込む**（目視で数えない） |
| 規約 | 13 | 索引に子ファイルへのリンクが揃い、子の節番号・親リンクが維持されているか |
| 規約 | 14 | 同一の数値・仕様が複数ファイルに重複記載されていないか |

## 4. `diagrams-review` の観点

対象6図と照合先は [basic-design.md](basic-design.md) §1 の表。

| 分類 | 観点 |
|------|------|
| 仕様書との整合性 | ER図↔`tech_data.md` / クラス図↔`systems/`・`tech_data.md` / 画面遷移図↔`systems/ui*.md` / 戦闘フロー図↔`tech_battle.md`・`tech_offline.md` / APIシーケンス図↔`tech_api.md`・`tech_architecture.md` / システム構成図↔`tech_architecture.md`・不変条件 |
| コードとの整合性 | ER図↔`backend/app/models/` / クラス図↔`services/`・`stores/` / 画面遷移図↔`frontend/src/router/` / APIシーケンス図↔`routers/`・`frontend/src/api/` |
| 設計図間の整合性 | ER図↔クラス図（属性・リレーション）、画面遷移図↔APIシーケンス図（遷移で発生する呼び出し）、戦闘フロー図↔APIシーケンス図（呼び出しタイミング） |
| Mermaid構文 | [basic-design.md](basic-design.md) §4 の**機械検証**で判定する（目視しない） |
| 網羅性 | 主要機能に対応する図があるか、Phase 3〜5 の追加仕様が反映されているか、図内に TODO/TBD が残っていないか |
| 規約 | `check_doc_size.py` の `diagrams/` 配下 ERROR は重要度=高。超過時は同名ディレクトリへ図単位で切り出す案を書く |

## 5. 重要度の基準

| 重要度 | `doc-review` | `diagrams-review` |
|-------|-------------|------------------|
| **高** | 実装に直接影響する矛盾（数値の不一致、機能仕様の矛盾）、文字数上限の超過 | 設計図とコード/仕様書の重大な乖離（テーブル定義の不一致、画面遷移の欠落） |
| **中** | 実装時に混乱を招く不整合（用語の不一致、参照の欠落） | 設計図間の不整合、属性の過不足、フローの軽微な差異 |
| **低** | 文書構成・記載漏れ。H2の2,000字 WARN（Mermaid図1枚のセクションは報告不要） | 命名の不統一、構文上の改善点、可読性 |

担当範囲の切り分けは [review-procedure.md](../references/review-procedure.md) §7 を参照。

## 6. `fix-specs`（修正適用）の固有ルール

| # | ルール |
|---|-------|
| 1 | 対象は `docs/reviews/doc-review/` 直下の最新（`archive/` は見ない。引数でパス指定も可） |
| 2 | 重要度「高」から優先的に修正する |
| 3 | 修正前に該当箇所を必ず読み、レビュー時点から内容が変わっていないか確認する |
| 4 | 仕様書間の整合を保つため、関連する全ファイルをまとめて修正する |
| 5 | 修正案が曖昧な場合は [profile.md](profile.md) §5 の不変条件に従って最も妥当な修正を行う |
| 6 | 変更履歴は仕様書に書かず、`docs/changelog.md` の先頭へ `\| ファイル \| 内容 \|` 行を追記する |
| 7 | 未確定仕様に関する指摘があれば `open_specs.md` も更新する（不在なら新規作成する） |
| 8 | サブエージェントは「10ファイル以上にまたがる機械的修正」のみ。最大2体・`sonnet`・担当ファイル列挙を厳守 |
