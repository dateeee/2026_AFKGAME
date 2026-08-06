# ドキュメント系レビュー — プロジェクト固有プロファイル

> 一般手順は [.claude/references/review-procedure.md](../references/review-procedure.md)、出力形式は [review-format.md](../references/review-format.md)。本書は AFK GAME 固有の値のみを持つ。
> 対象スキル: `doc-review`（仕様書横断）、`fix-specs`（修正適用）。`diagrams-review`（設計図整合）の固有値は [review-diagrams.md](review-diagrams.md)。

## 0. レビューパラメータ

| スキル | 保存先ディレクトリ | レポートタイトル | カテゴリ |
|-------|-----------------|---------------|---------|
| `doc-review` | `docs/reviews/doc-review/` | 仕様レビュー結果 | 整合性 / 網羅性 / 規約 |

ファイル名は `YYYY-MM-DD_HHMMSS.md`。
ローテーションは `python scripts/rotate_reviews.py --apply`（直下を最新10件に保ち、超過分は `archive/` へ移動）。

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
| `README.md` / `CLAUDE.md` / `development_process.md`（+ `process/phases.md`） / `glossary.md` / `documentation_rules.md` | ディレクトリ構成・索引・リンク・用語の整合のみ |

## 2. 全量モードの分担（`doc-review`・最大4体）

| 担当 | 対象ファイル |
|------|------------|
| 数値・計算式・定数 | `design/systems/`、`data/master/`、`tech_data.md`、`tech_battle.md`、`tech_offline.md` |
| 塔データ | `TOWERS_OVERVIEW.md`、`towers/NNN_*.md`（配下全件）、`data/master/`、`systems/dungeon.md`、`master_data.md`（塔一覧） |
| スキル・API・データ構造 | `SKILLS_OVERVIEW.md`、`skills/NNN_*.md`（配下全件）、`systems/character.md`、`tech_api.md`、`tech_data.md`、`tech_auth.md` |
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
| 網羅性 | 9 | 曖昧語（TBD・未定・後日検討・適宜・おおよそ）: `check_docs.py --words` の出力をそのまま取り込む |
| 網羅性 | 10 | 言及されているが詳細が未定義の機能がないか |
| 網羅性 | 11 | 索引到達性・リンク切れ: `check_docs.py --reach --links` の出力をそのまま取り込む（重要度=中） |
| 規約 | 12 | `python scripts/check_doc_size.py --sections` の出力を**そのまま取り込む**（目視で数えない） |
| 規約 | 13 | 索引に子ファイルへのリンクが揃い、子の節番号・親リンクが維持されているか |
| 規約 | 14 | 重複記載: `check_docs.py --owner` の出力を取り込む。[spec_ownership.md](../../docs/spec_ownership.md) 未登録トピックの重複は目視。修正案に正の宣言（同表への登録）を含める |
| 網羅性 | 15 | 決定先送りの台帳登録・台帳存否の断定: `check_docs.py --pending --ledger` の出力をそのまま取り込む |

## 4. `diagrams-review` の観点

[review-diagrams.md](review-diagrams.md) へ切り出した（§0 パラメータ・§1 観点・§2 重要度基準）。

## 5. 重要度の基準（`doc-review`）

| 重要度 | 基準 |
|-------|------|
| **高** | 実装に直接影響する矛盾（数値の不一致、機能仕様の矛盾）、文字数上限の超過 |
| **中** | 実装時に混乱を招く不整合（用語の不一致、参照の欠落） |
| **低** | 文書構成・記載漏れ。H2の2,000字 WARN（Mermaid図1枚のセクションは報告不要） |

担当範囲の切り分けは [review-procedure.md](../references/review-procedure.md) §7 を参照。

## 6. 修正適用の固有ルール（`fix-specs`）

| # | ルール |
|---|-------|
| 1 | 対象は `docs/reviews/doc-review/` 直下の最新（`archive/` は見ない） |
| 2 | 修正案が曖昧な場合は [profile.md](profile.md) §5 の不変条件に従って最も妥当な修正を行う |
| 3 | 変更履歴は仕様書に書かず、`docs/changelog.md` の先頭へ `\| ファイル \| 内容 \|` 行を追記する |
| 4 | 未確定仕様に関する指摘があれば `open_specs.md` も更新する（不在なら新規作成する） |
| 5 | サブエージェントは「10ファイル以上にまたがる機械的修正」（最大2体）と「修正後の検証」（1体）のみ。`sonnet`・担当ファイル列挙を厳守 |
| 6 | `fix-specs` SKILL §3「修正の実行」#6 で確定した記述の境界（どちらが正か）を `spec_ownership.md` へ登録する |
| 7 | 修正後の「新たな矛盾」検証は、修正ファイルと照合先を列挙した `sonnet` サブエージェント1体で行う（本人の再読で済ませない） |
| 8 | 記述を別ファイルへ**移管**する・新規ファイルを作成する指摘は、対象トピックを**全文検索**し、レポートが挙げていない同種の記述も同時に処理する。検索範囲は `docs/**`（`reviews/` 除く）・`diagrams/**`・`CLAUDE.md`・`README.md`・`.claude/**`（プロファイルは仕様書と同じ優先度で更新する）。移管先が台帳の場合、台帳の「決定時にすること」へ言及元の全ファイルを列挙する |
