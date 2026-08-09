# ドキュメント索引

`docs/**` の入口。担当ファイルを特定し、**必要なものだけ読む**（[CLAUDE.md](../CLAUDE.md) ドキュメント規約）。
大きな仕様書は **索引 + 個別ファイル**構成で、節番号は分割後も維持される。
分類軸は [documentation_rules.md](process/documentation_rules.md) §10、正の所在は [spec_ownership.md](process/spec_ownership.md)、
エージェント側のプロファイル索引は [.claude/project/INDEX.md](../.claude/project/INDEX.md)。

## 開発プロセス・台帳

| 分類 | ファイル |
|------|---------|
| 進め方 `docs/process/` | [development_process](process/development_process.md) 7工程・ゲート・進捗 / [phases](process/phases.md) 工程別の定義 / [documentation_rules](process/documentation_rules.md) 文書規約 / [coding_standards_backend](process/coding_standards_backend.md) バックエンド規約（索引+分冊） / [spec_ownership](process/spec_ownership.md) 正の所在マップ / [worktree_guide](process/worktree_guide.md) 編集時の worktree 運用 |
| 状態 `docs/backlog/` | [balance_backlog](backlog/balance_backlog.md) 見直す数値 / [known_issues](backlog/known_issues.md) 実装の疑義 / [next_session](backlog/next_session.md) 引き継ぎ / [carryover_notes](backlog/carryover_notes.md) 後工程への申し送り / [efficiency_memo](backlog/efficiency_memo.md) 効率メモ / [java_migration](backlog/java_migration.md) Java移行計画（索引 + 分冊3件） |
| 横断 | [glossary](glossary.md) 用語集 / [changelog](changelog.md) 変更履歴 / [INDEX](../.claude/project/INDEX.md) 工程↔スキル対応表 |

## 仕様書
- [docs/design/game_spec.md](design/game_spec.md) — ゲーム仕様の索引
  - [requirements/](design/requirements/) 要件 — product / non_functional / operation
  - [systems/](design/systems/) — character / battle / equipment / economy / dungeon / endgame / ui / ui_onboarding
- [docs/tech/tech_spec.md](tech/tech_spec.md) — 技術仕様の索引
  - [basic/](tech/basic/) 基本設計 — db + [tech_db/](tech/basic/tech_db/)（テーブル定義書）/ data / structure / structure_backend / api / api_common / architecture / logging
  - [nonfunctional/](tech/nonfunctional/) 非機能 — performance / security / operations（+_procedure）
  - [detail/](tech/detail/) 詳細設計 — battle / offline / skill / party / tick / polling / state / rng / numeric / shop / design_system / auth（+auth_account: 登録・ログイン・ログアウト）/ base / scout / forge（索引+enhance・craft・disassemble）
- [docs/data/master_data.md](data/master_data.md) — マスターデータの索引 + 塔データ一覧
  - [master/](data/master/) — character / item / equipment / base / endgame
  - [TOWERS_OVERVIEW.md](data/towers/TOWERS_OVERVIEW.md) 塔一覧 / [SKILLS_OVERVIEW.md](data/skills/SKILLS_OVERVIEW.md) スキル概要

## 設計図
[docs/diagrams/](diagrams/) — 全6図とも索引 + 同名ディレクトリ構成。
[er_diagram](diagrams/er_diagram.md) / [class_diagram](diagrams/class_diagram.md) / [battle_flow](diagrams/battle_flow.md) / [api_sequence](diagrams/api_sequence.md) / [system_architecture](diagrams/system_architecture.md)（構成 / tick / 権威 / 本番）/ [screen_transition](diagrams/screen_transition.md)（認証 / ナビ / Phase 5 / モーダル）
