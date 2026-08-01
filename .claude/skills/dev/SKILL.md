---
name: dev
description: AFK GAME の機能をフロントエンド（Vue 3）とバックエンド（FastAPI）で実装する。「戦闘システムを実装して」「装備の売却機能を追加して」など、仕様書に基づく新規実装・機能追加の依頼で使用する。仕様書の読み込み順、Phase制約、実装順序（モデル→スキーマ→サービス→ルーター→フロント）、技術規約（SQLAlchemy 2.0 / Pydantic v2 / Pinia Setup Store）を含む。
---

# 機能実装

あなたはAFK GAMEプロジェクトのフルスタックエンジニアです。仕様書に基づいて、指定された機能をフロントエンド（Vue.js 3）とバックエンド（FastAPI）の両方で実装します。

## 1. スコープの特定

依頼された機能（例: `戦闘システム` `レベルアップ` `オフライン報酬` `装備システム`）を実装対象として特定する。
対象が示されていない場合は `docs/design/game_spec.md` の Phase 一覧を提示し、どの機能を実装するかユーザーに選んでもらう。

## 2. 仕様の読み込み

関連する仕様書のうち、**該当セクションのみ**を読む（索引から必要ファイルを特定し、全文読み込みは避ける）。

| 順 | 参照先 | 内容 |
|----|--------|------|
| 1 | `docs/design/game_spec.md` → `design/systems/` | ゲームシステム・バランス仕様 |
| 2 | `docs/tech/tech_spec.md` → `tech_api.md` / `tech_data.md` / `tech_structure.md` | API設計・データ構造・実装配置 |
| 3 | `docs/tech/tech_battle.md`・`tech_offline.md` | 戦闘処理・オフライン計算（戦闘関連の場合） |
| 4 | `docs/tech/tech_auth.md` | 認証システム（認証関連の場合） |
| 5 | `docs/data/master_data.md` → `data/master/` | マスターデータ（数値定義） |
| 6 | `docs/data/towers/`・`docs/data/skills/` | 塔・スキルの個別データ（該当する場合のみ） |
| 7 | `diagrams/` | ER図・クラス図・APIシーケンス図等 |
| 8 | `docs/open_specs.md` | 未確定仕様の確認 |

**未確定仕様チェック**: 実装対象に未確定仕様（open_specs.md で `[ ]`）が含まれる場合、ユーザーに通知し、実装を進めるか仕様確定（`/resolve-specs`）を先にするか確認する。

## 3. 既存コードの確認

1. `frontend/src/` — Vueコンポーネント・ストア・型定義・API層のパターン
2. `backend/app/` — モデル・スキーマ・サービス・ルーターのパターン
3. 命名規則、ディレクトリ構造、import規約を把握する
4. 再利用可能な既存のユーティリティ・コンポーネントを特定する

## 4. 実装計画の提示

実装を開始する前に、以下の形式で計画を提示し、ユーザーの確認を得る。

```
## 実装計画: [機能名]

### 対象Phase: Phase N

### 仕様の要約
- （仕様書から読み取った主要な要件を箇条書き）

### バックエンド
| ファイル | 操作 | 内容 |
|---------|------|------|
| backend/app/models/xxx.py | 新規作成 | SQLAlchemyモデル定義 |
| backend/app/schemas/xxx.py | 新規作成 | Pydanticスキーマ |
| backend/app/services/xxx.py | 新規作成 | ビジネスロジック |
| backend/app/routers/xxx.py | 新規作成 | APIエンドポイント |

### フロントエンド
| ファイル | 操作 | 内容 |
|---------|------|------|
| frontend/src/types/xxx.ts | 新規作成 | TypeScript型定義 |
| frontend/src/api/xxx.ts | 新規作成 | API通信 |
| frontend/src/stores/xxx.ts | 新規作成 | Piniaストア |
| frontend/src/components/Xxx.vue | 新規作成 | UIコンポーネント |

### 実装の注意点
- （Phase制約、依存関係、既存コードとの兼ね合い等）
```

## 5. 実装の実行

**バックエンドを先に実装する。**

1. **モデル** (`backend/app/models/`) — SQLAlchemy 2.0 の `Mapped[]` + `mapped_column()`
2. **スキーマ** (`backend/app/schemas/`) — Pydantic v2。CamelModel を継承
3. **サービス** (`backend/app/services/`) — ビジネスロジック
4. **ルーター** (`backend/app/routers/`) — FastAPI の APIRouter

続いてフロントエンド。

1. **型定義** (`frontend/src/types/`) — バックエンドのスキーマに対応するTypeScript型
2. **API層** (`frontend/src/api/`) — API通信
3. **ストア** (`frontend/src/stores/`) — Pinia（Setup Store 形式）
4. **コンポーネント** (`frontend/src/components/`) — `<script setup lang="ts">`
5. **ページ** (`frontend/src/views/`) — 必要な場合

## 6. 動作確認

- バックエンド: `cd backend && python -m py_compile app/main.py`
- フロントエンド: `cd frontend && npm run type-check`
- lint（設定がある場合）

単体テストの作成は本スキルの範囲外。製造完了後に **unit-test スキル**（C1カバレッジ100%）へ引き継ぐ。

## 7. 完了報告

作成・修正したファイルをバックエンド／フロントエンド別の表で示し、起動方法と次のステップ（未実装の関連機能・テスト追加等）を添える。

## 技術ルール

- **SQLAlchemy 2.0**: `Mapped[]` 型アノテーション、`mapped_column()`
- **Pydantic v2**: `model_config = ConfigDict(from_attributes=True)`
- **Vue 3**: `<script setup lang="ts">` + Composition API
- **Pinia**: `defineStore` に Setup Store 形式
- **TypeScript**: 厳密な型定義、`any` を避ける
- **ログ**: `logging_config` 準拠

## 注意事項

- 仕様書に記載されていない機能を追加しない
- Phase割り当てを厳守し、対象Phaseより後のPhaseの機能は実装しない（将来の拡張を考慮した設計は可）
- 既存コードのスタイル・パターンを踏襲する
- マスターデータの数値をハードコードせず、データ駆動で設計する
- 開発時フォールバック（バックエンド未起動時のフロント単体動作）を意識する
