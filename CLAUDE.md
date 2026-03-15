# AFK GAME

放置系ファンタジーRPGのWebブラウザゲーム。

## プロジェクト概要

プレイヤーは冒険者ギルドのマスターとなり、冒険者たちを育成・編成してダンジョンへ派遣する。
アプリを閉じている間も冒険者たちは自動で探索・戦闘を続け、戻ってきた時に報酬をまとめて受け取れる。

## 技術スタック

| レイヤー | 技術 |
|---------|------|
| フロントエンド | Vue.js 3 (SPA / Composition API / TypeScript) + Vite + Pinia |
| バックエンド | Python / FastAPI + SQLAlchemy 2.0 + Pydantic v2 |
| DB | SQLite（MVP）→ PostgreSQL（本番） |
| 描画方式 | テキストベース（Canvas不使用）。アイテム等にはアイコン画像を使用 |

## ディレクトリ構成

```
2026_AFKGAME/
├── CLAUDE.md                      # 本ファイル（プロジェクト概要）
├── docs/
│   ├── design/                    # ゲームデザイン仕様
│   │   └── game_spec.md           # ゲーム仕様書（システム設計・バランス・UI）
│   ├── tech/                      # 技術仕様
│   │   ├── tech_spec.md           # 技術仕様書（設計・API・データ構造）
│   │   ├── tech_battle_offline.md # 技術仕様：戦闘ログ・オフライン計算
│   │   └── tech_auth.md           # 技術仕様：認証システム（Phase 2〜）
│   ├── data/                      # マスターデータ
│   │   ├── master_data.md         # マスターデータ（共通数値定義）
│   │   ├── towers/                # 塔別マスターデータ
│   │   │   ├── TOWERS_OVERVIEW.md # 全塔概要一覧（推奨LV・フロア数・ダンジョン構成）
│   │   │   ├── 000_テンプレート.md # 新規塔作成用テンプレート
│   │   │   ├── 001_ゴブリンの塔.md # ゴブリンの塔（敵・構成・ドロップ）
│   │   │   ├── 002_森の塔.md      # 森の塔（敵・構成・ドロップ）
│   │   │   ├── 003_獣の塔.md      # 獣の塔（敵・構成・ドロップ）
│   │   │   ├── 004_毒沼の塔.md    # 毒沼の塔（敵・構成・ドロップ）
│   │   │   ├── 005_業火の塔.md    # 業火の塔（敵・構成・ドロップ）
│   │   │   ├── 006_氷雪の塔.md    # 氷雪の塔（敵・構成・ドロップ）
│   │   │   ├── 007_砂漠の塔.md    # 砂漠の塔（敵・構成・ドロップ）
│   │   │   ├── 008_深海の塔.md    # 深海の塔（敵・構成・ドロップ）
│   │   │   ├── 009_黄昏の塔.md    # 黄昏の塔（敵・構成・ドロップ）
│   │   │   └── 010_天空の塔.md    # 天空の塔（敵・構成・ドロップ）
│   │   └── skills/                # スキル系統別マスターデータ
│   │       ├── SKILLS_OVERVIEW.md # スキルシステム概要・共通ルール
│   │       ├── 000_テンプレート.md # 新規系統作成用テンプレート
│   │       ├── 001_剣術系統.md     # 剣術系統（物理単体攻撃）
│   │       ├── 002_魔法系統.md     # 魔法系統（魔法攻撃）
│   │       ├── 003_回復系統.md     # 回復系統（HP回復・蘇生）
│   │       ├── 004_強化系統.md     # 強化系統（バフ）
│   │       ├── 005_弱体系統.md     # 弱体系統（デバフ・状態異常）
│   │       └── 006_生存術系統.md   # 生存術系統（耐久・防御）
│   ├── glossary.md                # 用語集（ゲーム・技術用語）
│   ├── open_specs.md              # 未確定仕様一覧（確定次第削除、最終的にファイル自体を削除）
│   └── reviews/                   # 仕様レビュー結果（/doc-review コマンドで自動生成）
├── diagrams/                      # 設計図（Mermaid）
│   ├── er_diagram.md              # ER図（データベース設計）
│   ├── class_diagram.md           # クラス図（ドメインモデル）
│   ├── screen_transition.md       # 画面遷移図
│   ├── battle_flow.md             # 戦闘ターン処理フロー図
│   ├── system_architecture.md     # システム構成図
│   └── api_sequence.md            # APIシーケンス図
├── frontend/                      # Vue.js SPA
│   └── src/
│       ├── views/                 # ページコンポーネント
│       ├── components/            # UIコンポーネント
│       ├── stores/                # Pinia ストア
│       ├── composables/           # Composition API ロジック
│       ├── api/                   # API通信レイヤー
│       └── types/                 # TypeScript 型定義
├── backend/                       # FastAPI サーバー
│   └── app/
│       ├── routers/               # APIルーター
│       ├── services/              # ビジネスロジック
│       ├── models/                # SQLAlchemy モデル
│       └── schemas/               # Pydantic スキーマ
└── README.md
```

## アーキテクチャ方針

- **ハイブリッドtick制**: 戦闘はバックエンドで60秒間隔（固定）のtickごとに処理。オンライン中はポーリング、オフライン中は復帰時にまとめて計算
- **シングルプレイ専用**: マルチプレイは想定しない
- **サーバー権威**: 戦闘計算はサーバー側で実行（チート対策）。フロントはログ表示のみ
- **MVP同時開発**: Phase 1からフロント（Vue）＋バックエンド（FastAPI + SQLite）を同時開発
- **開発時フォールバック**: バックエンド未起動時はフロント単体でも動作可能（`useBattleLocal.ts`、デバッグ用）

## 開発フェーズ

- **Phase 1 (MVP)**: キャラ1体の自動戦闘、レベルアップ、オフライン報酬（1画面デモ）
- **Phase 2**: 装備システム、複数の塔、ショップ、認証
- **Phase 3**: パーティ編成、タイプ（素質）・スキルシステム
- **Phase 4**: 拠点建設（酒場・鍛冶屋・訓練場・倉庫・市場）、素材・生産システム
- **Phase 5**: エンドコンテンツ（ボスラッシュ、転生等）

## 開発方針

- **仕様は全Phase確定 → 実装は段階的**: 全Phase(1-5)の仕様を先に確定してから、Phase 1から順に実装する
- 未確定の仕様は [docs/open_specs.md](docs/open_specs.md) で管理。確定したら仕様書に反映し、open_specs.md から削除
- すべて確定したら open_specs.md 自体を削除する

## 仕様書

- [docs/design/game_spec.md](docs/design/game_spec.md) — ゲーム仕様（システム設計・バランス・UI）
- [docs/tech/tech_spec.md](docs/tech/tech_spec.md) — 技術仕様（API設計・データ構造・アーキテクチャ）
  - [docs/tech/tech_battle_offline.md](docs/tech/tech_battle_offline.md) — 戦闘ログ・オフライン計算
  - [docs/tech/tech_auth.md](docs/tech/tech_auth.md) — 認証システム（Phase 2〜）
- [docs/data/master_data.md](docs/data/master_data.md) — マスターデータ（敵・塔・キャラ数値定義）
  - [docs/data/towers/TOWERS_OVERVIEW.md](docs/data/towers/TOWERS_OVERVIEW.md) — 全塔概要一覧（推奨LV・フロア数・ダンジョン構成）
  - [docs/data/skills/SKILLS_OVERVIEW.md](docs/data/skills/SKILLS_OVERVIEW.md) — スキルシステム概要・系統別詳細
- [docs/glossary.md](docs/glossary.md) — 用語集（ゲーム・技術用語）
- [docs/open_specs.md](docs/open_specs.md) — 未確定仕様一覧（全確定後に削除）
- 設計図（`diagrams/`）
  - [diagrams/er_diagram.md](diagrams/er_diagram.md) — ER図
  - [diagrams/class_diagram.md](diagrams/class_diagram.md) — クラス図（ドメインモデル）
  - [diagrams/screen_transition.md](diagrams/screen_transition.md) — 画面遷移図
  - [diagrams/battle_flow.md](diagrams/battle_flow.md) — 戦闘ターン処理フロー図
  - [diagrams/system_architecture.md](diagrams/system_architecture.md) — システム構成図
  - [diagrams/api_sequence.md](diagrams/api_sequence.md) — APIシーケンス図

---

## 変更履歴

| 日付 | 内容 |
|------|------|
| 2026-03-08 | 初版作成 |
| 2026-03-08 | レビュー指摘対応: ディレクトリ構成に towers/TOWERS_OVERVIEW.md・003〜010_各塔.md を追加。仕様書セクションに TOWERS_OVERVIEW.md リンクを追加 |
| 2026-03-10 | 設計図6点を docs/diagrams/ に追加（ER図・クラス図・画面遷移図・戦闘フロー図・システム構成図・APIシーケンス図） |
| 2026-03-15 | Phase 4「冒険者派遣」→削除済みのため「拠点建設（5施設）、素材・生産システム」に修正 |
| 2026-03-15 | diagrams/ を docs/ 配下からトップレベルに移動（仕様書と設計図の分離） |
