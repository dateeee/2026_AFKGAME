# AFK GAME — AI開発ルール

放置系ファンタジーRPGのWebブラウザゲーム。
**プロジェクト概要・セットアップ・ドキュメント索引は [README.md](README.md) を参照。**

## ドキュメント規約（必読）

Markdownには文字数上限を設けている。詳細は [docs/documentation_rules.md](docs/documentation_rules.md)。

| 区分 | 対象 | 上限 |
|------|------|------|
| A | `CLAUDE.md` | 3,000字 |
| B | `README.md`、`*_OVERVIEW.md` | 6,000字 |
| C | `docs/**`、`diagrams/**` | 8,000字 |
| D | `.claude/skills/**`、`.claude/references/**`、`.claude/project/**` | 5,000字 |

- 原則: **1ファイル = 1テーマ = 1回の読み込みで完結**
- H2セクションは2,000字以内。表を優先し、同じ仕様を複数ファイルに重複させない（正の宣言は [docs/spec_ownership.md](docs/spec_ownership.md)）
- **変更履歴は各ファイルに書かない**。[docs/changelog.md](docs/changelog.md)（上限対象外）の先頭へ1行追記する
- ドキュメントの作成・改稿後は `python scripts/check_doc_size.py` と `python scripts/check_docs.py` を実行する（超過の扱いは規約§7の台帳運用）

大きな仕様書は **索引 + 個別ファイル**構成。索引で担当ファイルを特定し、必要なものだけ読むこと（節番号は分割後も維持）。

| 索引 | 個別ファイル |
|------|------------|
| [docs/design/game_spec.md](docs/design/game_spec.md) | `design/systems/` — character / battle / equipment / economy / dungeon / endgame / ui / ui_onboarding |
| [docs/tech/tech_spec.md](docs/tech/tech_spec.md) | `tech/basic/` — db / data / structure / api / api_common / architecture / logging、`tech/nonfunctional/` — performance / security / operations、`tech/detail/` — battle / offline / skill / party / tick / polling / state / rng / numeric / shop / design_system / auth |
| [docs/data/master_data.md](docs/data/master_data.md) | `data/master/` — character / item / equipment / base / endgame |
| `diagrams/*.md`（6図） | 同名ディレクトリ配下（`er_diagram/` 等） |

## アーキテクチャ不変条件

- **サーバー権威**: 戦闘計算はバックエンドで実行（チート対策）。フロントはログ表示のみ
- **ハイブリッドtick制**: 60秒間隔（固定）のtickで戦闘処理。オンライン中はポーリング、オフライン中は復帰時に一括計算
- **シングルプレイ専用**: マルチプレイは想定しない
- **開発時フォールバック**: バックエンド未起動でもフロント単体で動作可（`useBattleLocal.ts`、デバッグ用）

## 開発方針

- **7工程で管理**: 要件定義 → 基本設計 → 詳細設計 → テストリスト作成 → 製造 → 単体テスト → 結合テスト（[docs/development_process.md](docs/development_process.md)）
- **製造はTDD**: バックエンドの新規実装はテストを先に書く（Red → Green → Refactor）
- **仕様は全Phase確定 → 実装は段階的**: 全Phase(1-5)の仕様を確定してから Phase 1 から順に実装する
- **未確定仕様は原則ゼロ**。生じたものは `docs/open_specs.md` で管理し、確定・反映したら行を削除する（全解消でファイルごと削除）
- **テスト標準**: バックエンド単体テストは pytest で C1（分岐）カバレッジ100%、結合テストは FastAPI TestClient + Playwright（E2E）
- **実装規約**: スキーマは CamelModel で `schemas/`、ロジックは `services/`、ログは logging_config 準拠
- **作業はすべてスキル経由**: 7工程 + 支援9件を `.claude/skills/` に用意している（自動起動 / `/` で明示起動）。対応表は [.claude/project/INDEX.md](.claude/project/INDEX.md)
- **一般手順と固有値の分離**: `.claude/` は `project/**` のみ固有値。`skills/**` `references/**` `scripts/**` は非依存で他プロジェクトへ無改造コピー可（[_TEMPLATE.md](.claude/project/_TEMPLATE.md) に沿って書き直す）

## コスト規律（AIエージェント運用）

正は [.claude/project/profile.md](.claude/project/profile.md) §6（以下は要約）。

- **サブエージェントは並列化の価値がある場合のみ**（同時最大4体・担当ファイル列挙・機械的作業は `model: sonnet`）。1体で済む作業はメインコンテキストで行う
- 仕様書・コードは**必要なセクションだけ読む**（索引→該当ファイルのみ。再Read禁止）。レビュー系は差分モードが既定（正は `review-procedure.md` §1）
- 大きな出力（ログ・テスト結果・git履歴等）は **context-mode で処理**し、生出力を会話に持ち込まない
- 工程の区切りでは `/clear` を既定として提案する（同一タスク継続時のみ `/compact`）。レビュー→修正適用は別セッションに分ける
