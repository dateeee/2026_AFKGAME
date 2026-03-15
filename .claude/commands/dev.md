あなたはAFK GAMEプロジェクトのフルスタックエンジニアです。仕様書に基づいて、指定された機能をフロントエンド（Vue.js 3）とバックエンド（FastAPI）の両方で実装します。

## 手順

### 1. スコープの特定

`$ARGUMENTS` に基づいて実装対象を特定してください。

- **機能指定**: `戦闘システム` `レベルアップ` `オフライン報酬` `装備システム` など
- **引数なし**: game_spec.md の Phase 一覧を表示し、ユーザーにどの機能を実装するか選んでもらう

### 2. 仕様の読み込み

対象機能に関連する仕様書を読み込んでください:

1. `docs/design/game_spec.md` — ゲームシステム・バランス仕様
2. `docs/tech/tech_spec.md` — API設計・データ構造
3. `docs/tech/tech_battle_offline.md` — 戦闘ログ・オフライン計算（戦闘関連の場合）
4. `docs/tech/tech_auth.md` — 認証システム（認証関連の場合）
5. `docs/data/master_data.md` — マスターデータ（数値定義）
6. `docs/data/towers/` 配下の塔データ（塔関連の場合）
7. `docs/data/skills/` 配下のスキルデータ（スキル関連の場合）
8. `diagrams/` 配下の設計図（ER図・クラス図・APIシーケンス図等）
9. `docs/open_specs.md` — 未確定仕様の確認

**未確定仕様チェック**: 実装対象に未確定仕様（open_specs.md で `[ ]`）が含まれる場合、ユーザーに通知し、実装を進めるか仕様確定を先にするか確認する。

### 3. 既存コードの確認

実装前に既存コードを確認してください:

1. `frontend/src/` — 既存のVueコンポーネント・ストア・型定義・API層のパターン
2. `backend/app/` — 既存のモデル・スキーマ・サービス・ルーターのパターン
3. 命名規則、ディレクトリ構造、import規約を把握する
4. 再利用可能な既存のユーティリティ・コンポーネントを特定する

### 4. 実装計画の提示

実装を開始する前に、以下の形式で計画をユーザーに提示してください:

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

ユーザーの確認を得てから実装に進んでください。

### 5. 実装の実行

以下の順序で実装してください:

#### バックエンド（先に実装）
1. **モデル** (`backend/app/models/`) — SQLAlchemy 2.0 のMapped型を使用
2. **スキーマ** (`backend/app/schemas/`) — Pydantic v2 のBaseModel
3. **サービス** (`backend/app/services/`) — ビジネスロジック
4. **ルーター** (`backend/app/routers/`) — FastAPI のAPIRouter

#### フロントエンド（バックエンドの後に実装）
1. **型定義** (`frontend/src/types/`) — バックエンドのスキーマに対応するTypeScript型
2. **API層** (`frontend/src/api/`) — axiosまたはfetchによるAPI通信
3. **ストア** (`frontend/src/stores/`) — Pinia ストア（Composition API形式）
4. **コンポーネント** (`frontend/src/components/`) — UIコンポーネント（Composition API + `<script setup>`）
5. **ページ** (`frontend/src/views/`) — ページコンポーネント（必要な場合）

### 6. 動作確認

実装完了後、以下を実行してください:

- バックエンド: `cd backend && python -m py_compile app/main.py` 等で構文チェック
- フロントエンド: `cd frontend && npx vue-tsc --noEmit` 等で型チェック（設定がある場合）
- lint（設定がある場合）

### 7. 完了報告

実装完了後、以下の形式で報告してください:

```markdown
## 実装完了: [機能名]

### 作成・修正したファイル

#### バックエンド
| ファイル | 操作 | 内容 |
|---------|------|------|
| backend/app/models/xxx.py | 新規作成 | ... |

#### フロントエンド
| ファイル | 操作 | 内容 |
|---------|------|------|
| frontend/src/types/xxx.ts | 新規作成 | ... |

### 起動方法
- バックエンド: `cd backend && uvicorn app.main:app --reload`
- フロントエンド: `cd frontend && npm run dev`

### 次のステップ
- （未実装の関連機能、テスト追加、等）
```

## 技術ルール

- **SQLAlchemy 2.0**: `Mapped[]` 型アノテーション、`mapped_column()` を使用
- **Pydantic v2**: `model_config = ConfigDict(from_attributes=True)` を使用
- **Vue 3**: `<script setup lang="ts">` + Composition API
- **Pinia**: `defineStore` に Setup Store 形式（Composition API形式）を使用
- **TypeScript**: 厳密な型定義、`any` の使用を避ける

## 注意事項

- 仕様書に記載されていない機能を追加しないこと
- Phase割り当てを厳守し、対象Phaseより後のPhaseの機能は実装しない（ただし将来の拡張を考慮した設計は可）
- 既存コードのスタイル・パターンを踏襲すること
- マスターデータ（master_data.md, 各塔ファイル等）の数値をハードコードせず、データ駆動で設計すること
- 開発時フォールバック（バックエンド未起動時のフロント単体動作）を意識すること（CLAUDE.md記載の方針）