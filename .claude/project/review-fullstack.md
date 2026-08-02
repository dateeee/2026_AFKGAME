# 統合整合レビュー — プロジェクト固有プロファイル

> 一般手順は [.claude/references/review-procedure.md](../references/review-procedure.md)、出力形式は [review-format.md](../references/review-format.md)。本書は AFK GAME 固有の値のみを持つ。
> 対象スキル: `full-review`（仕様↔コード・フロント↔バックの整合）。片側のコード品質は [review-code.md](review-code.md)。
> 不変条件は [profile.md](profile.md) §5。

## 0. レビューパラメータ

| 項目 | 値 |
|------|-----|
| prefix | `full-review` |
| レポートタイトル | フルスタック統合レビュー結果 |
| カテゴリ | 仕様書-コード整合性 / フロント・バック統合整合性 / アーキテクチャ方針適合 |

修正案は、フロント・バック両側の修正が必要な場合それぞれ記述する。

## 1. 対象ファイル

| 区分 | 対象 |
|------|------|
| コード | `backend/app/` 配下の全 `.py`（`__pycache__/` 除外）、`frontend/src/` 配下の全 `.vue` `.ts` `.css` |
| 仕様書 | `design/game_spec.md` → `systems/`、`tech/tech_spec.md` → `tech_data` / `tech_structure` / `tech_api` / `tech_architecture` / `tech_logging`、`tech_battle.md`・`tech_offline.md`・`tech_auth.md`、`data/master_data.md` → `data/master/` |
| 設計図 | `diagrams/` 6点（索引から必要な子ファイルのみ） |

索引 + 個別ファイル構成のため、**照合に必要な個別ファイルのみ**読む。
**サブエージェントは全量モードでのみ最大3体・`sonnet` 指定。**

## 2. 差分モードの照合先

| 変更ファイル | 照合先 |
|------------|--------|
| `backend/app/routers/*.py` | `tech_api.md`、`diagrams/api_sequence/`、`frontend/src/api/` |
| `backend/app/models/*.py` | `tech_data.md`、`diagrams/er_diagram/` |
| `backend/app/schemas/*.py` | `frontend/src/types/`、`tech_api.md` |
| `backend/app/services/*.py` | `design/systems/`、`tech_battle.md`・`tech_offline.md` |
| `backend/app/master_data/*.py` | `docs/data/master/`、`docs/data/towers/`、`docs/data/skills/` |
| `frontend/src/router/index.ts` | `diagrams/screen_transition.md`、`design/systems/ui.md` |
| `frontend/src/views/`・`components/` | `design/systems/ui.md` |
| `frontend/src/stores/`・`api/` | `backend/app/routers/`、`backend/app/schemas/` |

## 3. 観点

| 分類 | # | 観点 |
|------|---|------|
| 仕様書-コード | 1 | **API完全性**: `tech_api.md` の全エンドポイントが `routers/` に実装され、メソッド・パス・リクエスト/レスポンス形式が一致するか |
| 仕様書-コード | 2 | **データモデル**: `models/` が `diagrams/er_diagram/` と一致するか（テーブル名・カラム名・型・リレーション、必須カラムの欠落、不要カラム） |
| 仕様書-コード | 3 | **ビジネスロジック**: `services/` が `design/systems/` を正しく実装しているか（ダメージ計算・経験値/レベルアップ・ドロップ率・オフライン報酬・ショップ価格） |
| 仕様書-コード | 4 | **マスターデータ**: `backend/app/master_data/` が `docs/data/` と一致するか。**`services/` に数値がハードコードされていないか** |
| 仕様書-コード | 5 | **UI仕様**: 各画面の構成要素・表示情報（ステータス・レベル・ゴールド等）が `systems/ui.md` と一致するか |
| 仕様書-コード | 6 | **画面遷移**: `frontend/src/router/index.ts` が `diagrams/screen_transition.md` と一致し、全画面がルート定義にあり、認証ガードが適切か |
| 統合整合性 | 7 | **API型整合**: `frontend/src/types/` と `backend/app/schemas/` の一致（camelCase↔snake_case 変換、オプショナル/必須、enum・定数値） |
| 統合整合性 | 8 | **エンドポイント整合**: `frontend/src/api/` の URL・HTTPメソッド・ボディ・レスポンス期待値が `routers/` と一致するか |
| 統合整合性 | 9 | **認証フロー**: トークン管理、ヘッダー名・形式（Bearer 等）、認証エラー時のフロント側ハンドリング（リダイレクト等） |
| 統合整合性 | 10 | **データフロー**: ポーリング間隔がフロント・バック・仕様書で一致するか。オフライン復帰の取得フローが整合するか |
| アーキテクチャ | 11 | [profile.md](profile.md) §5 の不変条件（ハイブリッドtick制・サーバー権威・開発時フォールバック）に沿っているか |
| アーキテクチャ | 12 | フロントがビジネスロジック（ダメージ計算等）を持っていないか。**例外は `useBattleLocal.ts` のみ** |
| アーキテクチャ | 13 | `diagrams/system_architecture.md` と実装が一致するか |

## 4. 機械的検証

以下は目視せず、スクラッチパッドの使い捨てスクリプトで行う。

| 対象 | 方法 |
|------|------|
| 型・スキーマの突き合わせ | `schemas/` の Pydantic フィールドと `types/` の TypeScript プロパティを抽出して差分を取る |
| エンドポイントの突き合わせ | `routers/` のデコレータと `frontend/src/api/` の呼び出しURLを抽出して差分を取る |
| ハードコード数値の検出 | `services/` 内の数値リテラルを抽出し、`master_data/` に対応があるか照合する |
| ルート定義の突き合わせ | `router/index.ts` の path と画面遷移図のノードを抽出して差分を取る |

## 5. 重要度の基準

| 重要度 | 基準 |
|-------|------|
| **高** | フロント・バック間の通信が実際に失敗する問題、仕様との重大な乖離、データ不整合 |
| **中** | 型の不一致（動作はするが型安全性が損なわれる）、仕様との軽微な不一致 |
| **低** | 命名の不統一、改善が望ましいが動作に影響しない問題 |

**統合整合性の指摘を最優先で記述する**（片側だけの問題より、両方にまたがる問題を重視する）。
指摘はフロント・バック両側のコード、および仕様書の記述を引用して対比する。

担当範囲の切り分けは [review-procedure.md](../references/review-procedure.md) §7 を参照。
