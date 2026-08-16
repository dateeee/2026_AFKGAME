# ドキュメント索引

`docs/**` の入口。担当ファイルを特定し、**必要なものだけ読む**（[CLAUDE.md](../CLAUDE.md) ドキュメント規約）。
大きな仕様書は **索引 + 個別ファイル**構成で、節番号は分割後も維持される。
分類軸は [documentation_rules/directories.md](process/documentation_rules/directories.md) §10、正の所在は [spec_ownership.md](process/spec_ownership.md)、
エージェント側のプロファイル索引は [.claude/project/INDEX.md](../.claude/project/INDEX.md)。

## 開発プロセス・台帳

| 分類 | ファイル |
|------|---------|
| 進め方 `docs/process/` | [development_process](process/development_process.md) 7工程・ゲート・進捗 / [phases](process/phases.md) 工程別の定義 / [documentation_rules](process/documentation_rules.md) 文書規約（索引 + 分冊2件） / [coding_standards_backend](process/coding_standards_backend.md) バックエンド規約（索引+分冊） / [coding_standards_frontend](process/coding_standards_frontend.md) フロントエンド規約（索引+分冊） / [spec_ownership](process/spec_ownership.md) 正の所在マップ / [worktree_guide](process/worktree_guide.md) 編集時の worktree 運用（索引 + 分冊1件。[session](process/worktree_guide/session.md) §5 セッション運用） |
| 状態 `docs/backlog/` | [balance_backlog](backlog/balance_backlog.md) 見直す数値 / [known_issues](backlog/known_issues.md) 実装の疑義 / [next_session](backlog/next_session.md) 引き継ぎ / [carryover_notes](backlog/carryover_notes.md) 後工程への申し送り / [efficiency_memo](backlog/efficiency_memo.md) 効率メモ / [java_migration](backlog/java_migration.md) Java移行計画（索引 + 分冊3件） |
| 横断 | [glossary](glossary.md) 用語集 / [changelog](changelog.md) 変更履歴 / [INDEX](../.claude/project/INDEX.md) 工程↔スキル対応表 |

## 仕様書
- [docs/design/game_spec.md](design/game_spec.md) — ゲーム仕様の索引
  - [requirements/](design/requirements/) 要件 — product / non_functional / operation
  - [systems/](design/systems/) — character / battle（索引+[battle/](design/systems/battle/) progress: 進行制御 / calculation: 戦闘計算 / action: 行動順・ターゲット選択）/ equipment / economy / dungeon / endgame / ui（索引+[ui/](design/systems/ui/) onboarding: 認証画面・チュートリアル）
- [docs/tech/tech_spec.md](tech/tech_spec.md) — 技術仕様の索引
  - [basic/](tech/basic/) 基本設計 — db（索引+[tech_db/](tech/basic/tech_db/) テーブル定義書）/ data（索引+[tech_data/](tech/basic/tech_data/) game_state: §1.1 の JSON 例）/ structure（§2〜§3）/ backend（§4）/ api（索引+[tech_api/](tech/basic/tech_api/) common: §5.0 共通仕様 / core・auth・gameplay・character・base・endgame）/ architecture / logging（索引+[tech_logging/](tech/basic/tech_logging/) format・fields・reason）/ error_handling（§9 エラー形式・コード体系）
  - [nonfunctional/](tech/nonfunctional/) 非機能 — performance / security / operations（§12.1〜§12.3 環境・設定・監視）/ maintenance（§12.4〜§12.7 手順）
  - [detail/](tech/detail/) 詳細設計 — battle / offline / skill / party / tick / polling / state / rng / numeric / shop（索引+[tech_shop/](tech/detail/tech_shop/) lineup・buy）/ design_system / auth（索引+[tech_auth/](tech/detail/tech_auth/) init: ゲスト作成の初期化 / account: 登録・ログイン・ログアウト）/ base / scout / forge（索引+[tech_forge/](tech/detail/tech_forge/) enhance・craft・disassemble）/ limitbreak / tower（索引+[tech_tower/](tech/detail/tech_tower/) list・select・progress・control）/ bossrush（Phase 5。索引+[tech_bossrush/](tech/detail/tech_bossrush/) start・wave・offline・control）
- [docs/data/master_data.md](data/master_data.md) — マスターデータの索引 + 塔データ一覧
  - [master/](data/master/) — character（§1 基礎ステータス / §8 限界突破 / §9 スキル）/ item / equipment / base / endgame
  - [CHARACTERS_OVERVIEW.md](data/characters/CHARACTERS_OVERVIEW.md) キャラクター一覧（§7）+ [characters/](data/characters/) タイプ別4件
  - [TOWERS_OVERVIEW.md](data/towers/TOWERS_OVERVIEW.md) 塔一覧 / [SKILLS_OVERVIEW.md](data/skills/SKILLS_OVERVIEW.md) スキル概要

## 設計図
[docs/diagrams/](diagrams/) — 全6図とも索引 + 同名ディレクトリ構成。
[er_diagram](diagrams/er_diagram.md) / [class_diagram](diagrams/class_diagram.md) / [battle_flow](diagrams/battle_flow.md) / [api_sequence](diagrams/api_sequence.md) / [system_architecture](diagrams/system_architecture.md)（構成 / tick / 権威 / 本番）/ [screen_transition](diagrams/screen_transition.md)（認証 / ナビ / Phase 5 / モーダル）
