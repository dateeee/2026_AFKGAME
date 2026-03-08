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
│   ├── game_spec.md               # ゲーム仕様書（ゲームデザイン）
│   ├── tech_spec.md               # 技術仕様書（設計・API・データ構造）
│   ├── tech_battle_offline.md     # 技術仕様：戦闘ログ・オフライン計算
│   ├── tech_auth.md               # 技術仕様：認証システム（Phase 2〜）
│   ├── master_data.md             # マスターデータ（共通数値定義）
│   ├── towers/                    # 塔別マスターデータ
│   │   ├── 000_テンプレート.md     # 新規塔作成用テンプレート
│   │   ├── 001_ゴブリンの塔.md     # ゴブリンの塔（敵・構成・ドロップ）
│   │   └── 002_森の塔.md          # 森の塔（敵・構成・ドロップ）
│   └── open_specs.md              # 未確定仕様一覧（確定次第削除、最終的にファイル自体を削除）
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
- **Phase 3**: パーティ編成、職業・スキルシステム
- **Phase 4**: 拠点建設、冒険者派遣
- **Phase 5**: エンドコンテンツ（ボスラッシュ、転生等）

## 開発方針

- **仕様は全Phase確定 → 実装は段階的**: 全Phase(1-5)の仕様を先に確定してから、Phase 1から順に実装する
- 未確定の仕様は [docs/open_specs.md](docs/open_specs.md) で管理。確定したら仕様書に反映し、open_specs.md から削除
- すべて確定したら open_specs.md 自体を削除する

## 仕様書

- [docs/game_spec.md](docs/game_spec.md) — ゲーム仕様（システム設計・バランス・UI）
- [docs/tech_spec.md](docs/tech_spec.md) — 技術仕様（API設計・データ構造・アーキテクチャ）
  - [docs/tech_battle_offline.md](docs/tech_battle_offline.md) — 戦闘ログ・オフライン計算
  - [docs/tech_auth.md](docs/tech_auth.md) — 認証システム（Phase 2〜）
- [docs/master_data.md](docs/master_data.md) — マスターデータ（敵・塔・キャラ数値定義）
- [docs/open_specs.md](docs/open_specs.md) — 未確定仕様一覧（全確定後に削除）

---

## 変更履歴

| 日付 | 内容 |
|------|------|
| 2026-03-08 | 初版作成 |
