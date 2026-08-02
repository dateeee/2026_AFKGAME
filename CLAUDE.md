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
| D | `.claude/commands/**`、`.claude/skills/**` | 5,000字 |

- 原則: **1ファイル = 1テーマ = 1回の読み込みで完結**
- H2セクションは2,000字以内。表を優先し、同じ仕様を複数ファイルに重複させない
- **変更履歴は各ファイルに書かない**。[docs/changelog.md](docs/changelog.md)（上限対象外）の先頭へ1行追記する
- ドキュメントの作成・改稿後は `python scripts/check_doc_size.py` を実行する（超過は exit 1）

大きな仕様書は **索引 + 個別ファイル**構成。索引で担当ファイルを特定し、必要なものだけ読むこと（節番号は分割後も維持）。

| 索引 | 個別ファイル |
|------|------------|
| [docs/design/game_spec.md](docs/design/game_spec.md) | `design/systems/` — character / battle / equipment / economy / dungeon / endgame / ui |
| [docs/tech/tech_spec.md](docs/tech/tech_spec.md) | `tech/tech_*.md` — data / structure / api / architecture / logging（詳細設計: battle / offline / tick / polling / state / rng / numeric、認証: auth） |
| [docs/data/master_data.md](docs/data/master_data.md) | `data/master/` — character / item / equipment / base / endgame |
| `diagrams/*.md`（4図） | 同名ディレクトリ配下（`er_diagram/` 等） |

## アーキテクチャ不変条件

- **サーバー権威**: 戦闘計算はバックエンドで実行（チート対策）。フロントはログ表示のみ
- **ハイブリッドtick制**: 60秒間隔（固定）のtickで戦闘処理。オンライン中はポーリング、オフライン中は復帰時に一括計算
- **シングルプレイ専用**: マルチプレイは想定しない
- **開発時フォールバック**: バックエンド未起動でもフロント単体で動作可（`useBattleLocal.ts`、デバッグ用）

## 開発方針

- **7工程で管理**: 要件定義 → 基本設計 → 詳細設計 → テストリスト作成 → 製造 → 単体テスト → 結合テスト（[docs/development_process.md](docs/development_process.md)）
- **製造はTDD**: バックエンドの新規実装はテストを先に書く（Red → Green → Refactor）
- **仕様は全Phase確定 → 実装は段階的**: 全Phase(1-5)の仕様を確定してから Phase 1 から順に実装する
- **未確定仕様**は [docs/open_specs.md](docs/open_specs.md) で管理。確定したら仕様書へ反映して削除し、すべて確定したらファイルごと削除する
- **テスト標準**: バックエンド単体テストは pytest で C1（分岐）カバレッジ100%、結合テストは FastAPI TestClient + Playwright（E2E）
- **実装規約**: スキーマは CamelModel で `schemas/`、ロジックは `services/`、ログは logging_config 準拠
- **スキル/コマンドの使い分け**: 常時適用したい規約は `.claude/skills/`（自動起動。`dev`＝製造、`unit-test`＝テスト）、工程ゲートは `.claude/commands/`（`/` で明示起動・`model` 固定）

## コスト規律（AIエージェント運用）

- **サブエージェントは並列化の価値がある場合のみ**起動する（同時最大4体）。1体で済む調査・修正はメインコンテキストで行う
- **機械的な作業**（一括置換・リンク修正・定型データ生成・構文検証）は `model: sonnet` のサブエージェントか使い捨てスクリプトで処理する
- サブエージェントには**担当ファイルのみを列挙**し、「列挙外は読まない」「戻り値は結論のみ」を明示する
- 仕様書・コードは**必要なセクションだけ読む**（大きなファイルの全文読み込みを避ける）
- レビュー系コマンド（`/doc-review` 等）は**差分モードがデフォルト**。全量は `full` 指定時のみ
- 工程の区切り（レビュー完了・コミット後）で `/compact` または `/clear` を提案する
