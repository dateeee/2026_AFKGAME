# スキル・テンプレート改善レポート（2026-08-04）

`.claude/`（スキル14件・共有リファレンス・プロジェクトプロファイル・テンプレート）を、
[claude-code-best-practice](https://github.com/shanraisshan/claude-code-best-practice)
（README・skills/commands/memory の各ベストプラクティス文書）と照合し、改善を実施した。

## 総評

**既存構成はベストプラクティスの大部分を既に満たしている。** 特に以下は参考リポジトリの推奨と一致しており、変更不要と判定した。

| 推奨プラクティス | 本プロジェクトの充足状況 |
|----------------|----------------------|
| description にトリガー句を書き自動起動を効かせる | 全スキルが「〜して」等の具体句を含む三人称 description を持つ |
| Progressive disclosure（必要時にのみ読む階層化） | 「0. 最初に読む」表＋索引・個別ファイル構成＋読み込み範囲の明示で徹底済み |
| スキルはフォルダ（references/ 等を同梱） | `test-list` `unit-test` が `references/` を保有 |
| 一般手順と固有値の分離 | `.claude/skills/`（非依存）と `.claude/project/`（固有値）の分離が設計原則として明文化済み |
| Gotchas セクションを持つ | 全スキルに「注意事項」節あり |
| 機械的作業にモデル指定 | `fix-specs` に `model: sonnet`、レビュー分担サブエージェントも `sonnet` 指定 |
| CLAUDE.md にセットアップ・テストコマンド／サイズ抑制 | 3,000字上限で管理、コマンドは profile.md §4 に集約 |
| settings.json をリポジトリで共有・hooks 活用 | `settings.json`＋Stop hook（未コミット確認）を共有済み |

## 実施した改善（3件）

### 1. 全スキル（14件）へ `argument-hint` を追加

- **問題**: 各スキルは引数仕様（`full`・レポートパス・Phase 絞り込み等）を本文「引数」節にだけ持ち、`/` 起動時の補完には何も表示されなかった。
- **根拠**: 参考リポジトリの skills frontmatter 一覧が `argument-hint`（補完に表示される引数ヒント）を推奨。
- **変更**: 14件の SKILL.md frontmatter に追加。YAML が `[...]` をリストと誤解釈しないよう引用符で囲んだ。

| スキル | 追加したヒント |
|-------|--------------|
| requirements / basic-design / detail-design / test-list / dev | `[対象機能]` |
| unit-test | `[対象モジュール]` |
| integration-test | `[対象機能 \| Phase]` |
| doc-review / diagrams-review / backend-review / frontend-review / full-review | `[full \| 追加観点]` |
| fix-specs | `[レポートパス \| ISSUE-NNN \| 重要度]` |
| resolve-specs | `[Phase \| カテゴリ \| 項目名]` |

### 2. resolve-specs 専用テンプレートをスキルフォルダへ移動

- **問題**: `templates.md` は `resolve-specs` 専用なのに共有領域 `.claude/references/` にあり、「スキル専用資産はスキルフォルダ内 `references/`」という既存パターン（`test-list` `unit-test`）と不整合だった。
- **根拠**: 参考リポジトリの「skills are folders — references/ 等のサブディレクトリで progressive disclosure」。
- **変更**（`git mv` で履歴保持）:
  - `.claude/references/resolve-specs/templates.md` → `.claude/skills/resolve-specs/references/templates.md`
  - `resolve-specs/SKILL.md` から `references/templates.md` を直接参照（該当ステップ到達後に読む規則は維持）
  - `.claude/project/requirements.md` §4 のリンクを新パスへ更新
  - これにより `.claude/references/` は真に共有のもの（review-format / review-procedure）のみになった

### 3. `_TEMPLATE.md` へ SKILL.md frontmatter 規約を追記

- **問題**: テンプレートはプロファイル（`.claude/project/`）の書き方のみ規定し、新スキル追加時の frontmatter 規約（name/description/argument-hint/model）と資料配置規則が未文書化だった。
- **変更**: 「スキーマ: SKILL.md frontmatter（新スキル追加時のみ）」節を追加。description のトリガー句規約・argument-hint の引用符規則・スキル専用資料の配置先・「注意事項＝Gotchas を実運用の失敗で育てる」運用を明記。

## 検討したが見送った項目

| 項目 | 見送り理由 |
|------|-----------|
| `when_to_use` フィールドへのトリガー句分離 | 現 description に既に内包。分離は表示上の差のみで機能向上なし |
| `context: fork` / `agent` によるレビューの隔離実行 | コスト規律（profile.md §6）でサブエージェント運用を統制済み。二重管理になる |
| `paths` による自動ロード（例: frontend 編集時に frontend-review） | レビューはゲートで明示起動する開発方針と衝突する |
| SKILL.md への `!command` 動的埋め込み | 差分特定は review-procedure.md の手順で十分。Windows 環境での可搬性リスクが上回る |
| `.claude/rules/` への CLAUDE.md 分割 | CLAUDE.md は 3,000字上限内で運用できており分割不要 |
| 「step-by-step で縛らずゴールと制約を与える」への書き換え | 7工程ゲート管理は本プロジェクトの品質担保の根幹。意図的な設計として維持 |

## 追加の推奨（ユーザー判断・今回は未変更）

| 対象 | 内容 |
|------|------|
| `.claude/settings.local.json` | `Bash("<リポジトリルート>/docs/tech_spec.md")` は誤承認由来の無意味な許可（対象ファイルも現存しない）。削除を推奨 |
| `.claude/settings.json` | `Bash` を包括許可しているため `Bash(find *)` は冗長。包括許可を続けるなら削除、絞るなら `Bash(python scripts/*)` 等へ置換を推奨 |

## 検証

- `python scripts/check_doc_size.py` … **exit 0**（132 files / 違反 0。残量 WARN 9 件は既存のもので今回の変更とは無関係）
- `python scripts/check_docs.py` … **exit 0**（リンク・索引到達性ほか全項目 OK。移動した `templates.md` のリンク切れなし）
- 変更履歴は `docs/changelog.md` 2026-08-04 ブロックへ追記済み
