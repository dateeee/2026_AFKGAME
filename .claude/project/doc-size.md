# AFK GAME — 文字数是正プロファイル

`doc-size` スキルが読む固有値。一般手順は [.claude/skills/doc-size/SKILL.md](../skills/doc-size/SKILL.md)。
規約の正は [docs/process/documentation_rules.md](../../docs/process/documentation_rules.md)。

## 1. コマンド

| 目的 | コマンド |
|------|---------|
| 全体判定 | `python scripts/check_doc_size.py`（未登録の超過・変更履歴セクションがあれば exit 1） |
| 全件一覧 | `python scripts/check_doc_size.py --list`（文字数の降順。`!` 違反 / `~` 台帳登録済み） |
| H2内訳（測定） | `python scripts/check_doc_size.py --sections <path>...`（降順 + 残量。判定なし・常に exit 0） |
| H2超過の一覧 | `python scripts/check_doc_size.py --sections`（全ファイルの2,000字超H2をWARN表示） |
| リンク・索引検証 | `python scripts/check_docs.py`（分割後は必須。リンク・索引到達性・正の逸脱） |

## 2. 区分と上限

正は [documentation_rules.md](../../docs/process/documentation_rules.md) §3。

| 区分 | 対象 | 上限 | 超過時の扱い |
|------|------|------|------------|
| A | `CLAUDE.md` | 3,000 | **セッション内是正**（台帳登録不可） |
| B | `README.md`、`**/*_OVERVIEW.md` | 6,000 | 台帳へ登録 → 一括是正 |
| C | `docs/**`（設計図 `docs/diagrams/**` を含む） | 8,000 | 同上 |
| D | `.claude/**`（スクリプトは `.claude/` 全体をD判定） | 5,000 | **セッション内是正**（台帳登録不可） |
| 除外 | `docs/reviews/**`、`docs/changelog.md` | — | 追記型アーカイブ（§2・§9） |

H2セクションは2,000字、H3は1,000字（§4）。1ファイルのH2は7個以内。

## 3. 台帳（KNOWN_OVERSIZED）

`scripts/check_doc_size.py` の `KNOWN_OVERSIZED` dict。

- 登録形式: `"相対パス": "是正方針（分割/圧縮 + 方針の要旨）",`
- 登録できるのは**区分B・Cのみ**（`DEFERRABLE_ZONES`）。A・Dを書いても ERROR のまま
- 解消したら行を削除する。残すと「上限内 - 台帳から行を削除する」WARN が出続ける
- **Phase完了ゲート時点で空**であること（§7 締切。工程は跨いでよいがPhaseは跨がない）

## 4. 分割の標準形

ハブ&スポーク（§6.1）。索引（区分B/C）+ 子ファイル（区分C）。実績は §8。

| 索引 | 子ファイル |
|------|-----------|
| `docs/design/game_spec.md` | `docs/design/systems/` |
| `docs/tech/tech_spec.md` | `docs/tech/{basic,nonfunctional,detail}/` |
| `docs/data/master_data.md` | `docs/data/master/` |
| `docs/diagrams/*.md`（6図） | 同名ディレクトリ（`er_diagram/` 等） |

1エンティティ1ファイルの束は `*_OVERVIEW.md` を索引にする（`docs/data/towers/TOWERS_OVERVIEW.md`）。

区分A・Dは台帳に載らないため移管で余裕を作る: `CLAUDE.md` → 詳細を `docs/**` の正へ移しリンク参照、`.claude/project/profile.md` → 工程別ファイル `.claude/project/<スキル名>.md`（本ファイルがその形）。

## 5. 分割・改稿後に更新するファイル

| 対象 | 更新箇所 |
|------|---------|
| [README.md](../../README.md) | 「ドキュメント索引」の該当行 |
| [CLAUDE.md](../../CLAUDE.md) | 「ドキュメント規約」の索引表（大きな仕様書を分割した場合のみ） |
| [docs/process/spec_ownership.md](../../docs/process/spec_ownership.md) | 正の所在が別ファイルへ移る場合 |
| `.claude/project/review-*.md`、工程プロファイル | レビュー・工程スキルの対象ファイル一覧 |
| [docs/changelog.md](../../docs/changelog.md) | 先頭へ1行（**各ファイルに変更履歴を書かない**。§5.1） |

## 6. 実施タイミング

| 場面 | 内容 |
|------|------|
| Phase完了ゲート前 | 台帳を空にする（必須。§7 締切） |
| 工程の区切り | 区分A・Dの超過が出たセッション内に是正する |
| `doc-review` / `diagrams-review` の後 | レビューが検出した超過の是正 |

`fix-specs` の対象外（レビュー指摘ではなく規約違反の是正のため。§7）。
