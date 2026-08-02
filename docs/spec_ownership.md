# AFK GAME — 正の所在マップ（spec ownership）

> 「同じ数値・仕様を複数ファイルに書かない。正となるファイルを1つ決め、他はリンクする」
> （[documentation_rules.md](documentation_rules.md) §5）を運用するための台帳。
> トピックごとに**正となるファイル**を宣言し、`python scripts/check_docs.py`（--owner）が逸脱を機械検出する。

---

## 1. 使い方

| 場面 | すること |
|------|---------|
| 仕様を書く・直すとき | 対象トピックが下表にあれば**正ファイルにだけ**書く。他ファイルからはリンクする |
| 新しい仕様ファイルを追加したとき | 索引（`README.md`）へ登録し、既存ファイルと記述が重なるトピックは下表へ行を追加して境界を宣言する |
| `doc-review` が重複記載を指摘したとき | `fix-specs` で正を決めて修正し、**下表へ行を追加**する（検出パターンを書けば再発を機械検出できる） |
| 正を移すとき | 下表の正ファイル列を更新し、旧・正ファイル側の記載をリンクに置き換える |

- **検出パターン**列は正規表現。`check_docs.py` が `docs/design/` `docs/tech/` `docs/data/` を走査し、正・許可以外のファイルでパターンが一致したら ERROR にする
- **許可**列は「現状すでに記載があり、参照として妥当な箇所」の凍結リスト。新たな転載を防ぐのが目的であり、許可は増やさず縮減していく
- パターンが書けない（文章的な）トピックは検出パターンを `—` にする。境界の宣言だけでも `doc-review` の照合基準になる

## 2. 対応表

| トピック | 正ファイル | 許可（参照可） | 検出パターン | 備考 |
|---------|-----------|--------------|-------------|------|
| 経験値計算式（100×level^1.5） | `docs/data/master/character.md` | `docs/design/systems/character.md`, `docs/design/game_spec.md`, `docs/glossary.md` | `\^1\.5` | 数値テーブルの導出元。許可3件は縮減候補 |
| ゲスト削除猶予（90日） | `docs/tech/tech_auth.md` | `docs/design/non_functional_requirements.md`, `docs/tech/tech_operations.md` | `90日` | 要求値は nfr、実現方式の正は auth |
| ダメージ計算式 | `docs/tech/tech_battle.md` | `docs/design/systems/battle.md` | — | 図（`battle_flow/`）は視覚化として再掲可、値の変更は正から |
| ヘッダの構成要素 | `docs/design/systems/ui.md` | `docs/tech/tech_design_system.md` | — | ISSUE-601 で確定（2026-08-02） |
| モーダルの表示位置・閉じ方 | `docs/tech/tech_design_system.md` | `docs/design/systems/ui.md` | — | ISSUE-502 で確定（2026-08-02） |
| ナビゲーション項目と「その他」まとめ | `docs/design/systems/ui.md` | `docs/tech/tech_design_system.md` | — | ISSUE-603 で確定（対象項目は `open_specs.md` 管理） |
| ログアウトの挙動 | `docs/tech/tech_auth.md` | `docs/design/systems/ui.md` | — | API定義は `tech_api.md` が正（ISSUE-602） |

## 3. 境界の一般原則

個別の行が無いトピックは、ファイルの役割から正を判断する。

| 内容 | 正の置き場 |
|------|-----------|
| 要求値（性能・容量・期限などの「満たすべき値」） | `docs/design/*_requirements.md` |
| ゲーム仕様の意味・ルール（何がどうなるか） | `docs/design/systems/` |
| 処理・計算式・分岐（どう計算するか） | `docs/tech/tech_*.md` |
| 数値の具体値（マスターデータ） | `docs/data/` |
| ビジュアル・コンポーネント規約（色・部品・トーン） | `docs/tech/tech_design_system.md` |
| 画面の構成・遷移・機能配置 | `docs/design/systems/ui*.md` |

図（`diagrams/`）はテキスト仕様の視覚化であり正にならない。図とテキストが食い違ったらテキスト側の正を起点に揃える（`diagrams-review`）。
