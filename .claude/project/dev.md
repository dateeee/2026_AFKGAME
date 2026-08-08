# 製造 — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/dev/SKILL.md](../skills/dev/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> 技術スタック・規約・不変条件は [profile.md](profile.md) §3・§5。

## 1. 成果物

### バックエンド（実装順）

| 順 | 層 | パス | 規約 |
|----|----|------|------|
| 1 | モデル | `backend/app/models/` | SQLAlchemy 2.0（`Mapped[]` + `mapped_column()`） |
| 2 | スキーマ | `backend/app/schemas/` | Pydantic v2。`CamelModel` を継承 |
| 3 | サービス | `backend/app/services/` | ビジネスロジックを集約 |
| 4 | ルーター | `backend/app/routers/` | FastAPI `APIRouter`（prefix / tags / response_model） |

### フロントエンド（実装順）

| 順 | 層 | パス | 規約 |
|----|----|------|------|
| 1 | 型定義 | `frontend/src/types/` | バックエンドスキーマに対応する TypeScript 型 |
| 2 | API層 | `frontend/src/api/` | API通信 |
| 3 | ストア | `frontend/src/stores/` | Pinia Setup Store 形式 |
| 4 | コンポーネント | `frontend/src/components/` | `<script setup lang="ts">` |
| 5 | ページ | `frontend/src/views/` | 必要な場合のみ |

**バックエンドを先に完成させてからフロントエンドへ移る。**

## 2. 参照先（読む順）

該当セクションのみを読む。索引から必要ファイルを特定し、全文読み込みは避ける。

| 順 | 参照先 | 内容 |
|----|--------|------|
| 1 | `docs/design/game_spec.md` → `design/systems/` | ゲームシステム・バランス仕様 |
| 2 | `docs/tech/tech_spec.md` → `tech_api.md` / `tech_data.md` / `tech_structure.md` | API設計・データ構造・実装配置 |
| 3 | `docs/tech/detail/tech_battle.md`・`tech_offline.md` | 戦闘処理・オフライン計算（戦闘関連の場合） |
| 4 | `docs/tech/detail/tech_auth.md` | 認証システム（認証関連の場合） |
| 5 | `docs/tech/detail/tech_rng.md`・`tech_numeric.md`・`tech_state.md` | 乱数・丸め・状態（該当する場合） |
| 6 | `docs/data/master_data.md` → `data/master/` | マスターデータ（数値定義） |
| 7 | `docs/data/towers/`・`docs/data/skills/` | 塔・スキルの個別データ（該当する場合のみ） |
| 8 | `docs/diagrams/` | ER図・クラス図・APIシーケンス図等 |
| 9 | `docs/backlog/open_specs.md` | 未確定仕様の確認（**存在する場合のみ**。不在＝未確定ゼロ） |

**未確定仕様チェック**: `open_specs.md` が存在し、実装対象に未確定項目が含まれる場合、ユーザーに通知し、実装を進めるか仕様確定（`resolve-specs` スキル）を先にするか確認する。

## 3. TDD の適用範囲

| 対象 | 適用 |
|------|------|
| `backend/` | **TDD適用**。`test-list` 工程のテストに対し Red-Green-Refactor を1テストずつ回す |
| `frontend/` | **TDD非適用**。従来どおり実装し、`vue-tsc` の型チェックと結合テストで検証する |

| # | ルール |
|---|-------|
| 1 | Red（テストが失敗する）→ Green（**最小の実装**で通す）→ Refactor（テストを保ったまま整理） |
| 2 | テストを通すために**期待値のほうを書き換えない**。テストが誤りなら詳細設計に戻って分岐一覧を正す |
| 3 | 分岐一覧に無い分岐を発見したら、**詳細設計へ追記してからテストを追加**する（実装を先に直さない） |

## 4. 固有の観点

| # | 観点 | 判定基準 |
|---|------|---------|
| 1 | Phase厳守 | 対象Phaseより後のPhaseの機能を実装していないか（将来拡張を考慮した設計は可） |
| 2 | 仕様外機能 | 仕様書に記載のない機能を追加していないか |
| 3 | データ駆動 | マスターデータの数値をハードコードしていないか |
| 4 | サーバー権威 | 戦闘計算・報酬決定をフロント側に置いていないか |
| 5 | 開発時フォールバック | `frontend/src/composables/useBattleLocal.ts` の単体動作を壊していないか |
| 6 | 既存パターン踏襲 | 命名規則・ディレクトリ構造・import規約が既存コードと揃っているか |
| 7 | ログ | `logging_config` 準拠か |

## 5. 動作確認

| 対象 | コマンド |
|------|---------|
| バックエンド構文 | `cd backend && python -m py_compile app/main.py` |
| バックエンドテスト | `cd backend && python -m pytest -q`（TDDの全テストがGreen） |
| フロント型チェック | `cd frontend && npm run type-check` |

## 6. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- `test-list` 工程の全テストが PASS（Green）
- `vue-tsc` の型チェックが PASS

## 7. 次工程

| 次にやること | 手段 |
|------------|------|
| 製造完了ゲート | `backend-review` スキル、`frontend-review` スキル（指摘対応まで完了してゲート通過） |
| 単体テストへ | `unit-test` スキル（C1網羅の測定と補完） |
