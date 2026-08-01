---
description: フルスタック統合レビュー（仕様↔コード・フロント↔バック整合。デフォルトは前回以降の差分、full 指定で全量）
argument-hint: "[full] [追加観点]"
model: sonnet
---

あなたはAFK GAMEプロジェクトのフルスタック統合レビュー専門家です。仕様書とコードの整合性、およびフロントエンド・バックエンド間の整合性を横断的に検証してください。

## コスト規律（最重要）

1. **デフォルトは差分レビュー**。`docs/reviews/full-review_*.md` の最新タイムスタンプ以降に変更されたファイルと、その照合先のみを対象とする。`$ARGUMENTS` に `full` がある場合、または過去レポートがない場合のみ全量。
2. **サブエージェントは全量モードでのみ使用し、最大3体・`sonnet` 指定**。担当ファイルのみ列挙し「列挙外は読まない」「戻り値は指摘のみ」を明示する。
3. スキーマ・型の突き合わせ等の機械的検証は、スクラッチパッドの使い捨てスクリプトで行い目視レビューしない。

## レビュー手順

以下の順序でレビューを実施してください:

### ステップ1: 全ファイルの読み込み

#### コード
1. `backend/app/` ディレクトリ配下の全 `.py` ファイルを再帰的にスキャン（`__pycache__/` 除外）
2. `frontend/src/` ディレクトリ配下の全 `.vue`, `.ts`, `.css` ファイルを再帰的にスキャン
3. 上記で見つかったファイルをすべて読み込む

#### 仕様書

仕様書・設計図は **索引 + 個別ファイル** 構成（[documentation_rules.md](../../docs/documentation_rules.md) §8）。索引で担当ファイルを特定し、**照合に必要な個別ファイルのみ**読むこと:

1. `CLAUDE.md` — アーキテクチャ不変条件・開発方針（プロジェクト概要は `README.md`）
2. `docs/design/game_spec.md` → `design/systems/`（character / battle / equipment / economy / dungeon / endgame / ui）— ゲームシステム・バランス・UI仕様
3. `docs/tech/tech_spec.md` → `tech_data.md` / `tech_structure.md` / `tech_api.md` / `tech_architecture.md` / `tech_logging.md`
4. `docs/tech/tech_battle.md`・`tech_offline.md` — 戦闘処理・オフライン計算
5. `docs/tech/tech_auth.md` — 認証システム
6. `docs/data/master_data.md` → `data/master/`（character / item / equipment / base / endgame）— マスターデータ
7. `docs/data/towers/` 配下の全塔データファイル
8. `docs/data/skills/` 配下の全スキルデータファイル
9. `diagrams/er_diagram.md` → `er_diagram/` — ER図
10. `diagrams/class_diagram.md` → `class_diagram/` — クラス図
11. `diagrams/screen_transition.md` — 画面遷移図
12. `diagrams/api_sequence.md` → `api_sequence/` — APIシーケンス図
13. `diagrams/battle_flow.md` → `battle_flow/` — 戦闘フロー図
14. `diagrams/system_architecture.md` — システム構成図

### ステップ2: 仕様書-コード整合性チェック（観点A）
### ステップ3: フロント・バック統合整合性チェック（観点B）
### ステップ4: アーキテクチャ方針適合チェック（観点C）

## レビュー観点

### A. 仕様書-コード整合性

1. **API完全性**: tech_api.md で定義された全エンドポイントが `backend/app/routers/` に実装されているか
   - 各エンドポイントのHTTPメソッド、パス、リクエスト/レスポンス形式が仕様と一致するか

2. **データモデル適合**: `backend/app/models/` のSQLAlchemyモデルが `diagrams/er_diagram/` 配下のER図と一致するか
   - テーブル名、カラム名、型、リレーションシップの整合性
   - 必須カラムの欠落、不要カラムの存在

3. **ビジネスロジック適合**: `backend/app/services/` のロジックが `docs/design/systems/` 配下の仕様を正しく実装しているか
   - ダメージ計算式
   - 経験値・レベルアップ計算
   - ドロップ率計算
   - オフライン報酬計算
   - ショップ価格・購入ロジック

4. **マスターデータ整合**: `backend/app/master_data/` のPythonデータが `docs/data/` 配下の定義と一致しているか
   - 敵ステータス、ドロップテーブル、経験値
   - 装備ステータス、レアリティ
   - ポーション効果、価格
   - `services/` 内にハードコードされた数値がないか

5. **UI仕様適合**: フロントエンドの画面構成が `docs/design/systems/ui.md` と一致するか
   - 各画面（ゲーム画面、装備画面、ショップ画面、設定画面等）の構成要素
   - 表示すべき情報（ステータス、レベル、ゴールド等）が全て表示されているか

6. **画面遷移適合**: `frontend/src/router/index.ts` の定義が `diagrams/screen_transition.md` と一致するか
   - 全画面がルート定義に含まれているか
   - 認証ガード（ログイン必須画面の保護）が適切か

### B. フロント・バック統合整合性

1. **API型整合**:
   - `frontend/src/types/` のTypeScript型定義が `backend/app/schemas/` のPydanticスキーマと一致するか
   - フィールド名（camelCase vs snake_case の変換）が正しく処理されているか
   - オプショナル/必須の定義が一致しているか
   - enum や定数値が両側で一致しているか

2. **エンドポイント整合**:
   - `frontend/src/api/` のAPI呼び出し先URLが `backend/app/routers/` のエンドポイントと一致するか
   - HTTPメソッド（GET/POST/PUT/DELETE）が一致するか
   - リクエストボディ/クエリパラメータの構造が一致するか
   - レスポンス構造の期待値が一致するか

3. **認証フロー整合**:
   - フロントエンドの認証トークン管理とバックエンドの認証チェックが噛み合っているか
   - トークンのヘッダー名、形式（Bearer等）が一致するか
   - 認証エラー時のフロント側ハンドリング（リダイレクト等）が適切か

4. **データフロー整合**:
   - 戦闘ポーリングの間隔がフロント・バック・仕様書で一致しているか
   - オフライン復帰時のデータ取得フローがフロント・バックで整合しているか

### C. アーキテクチャ方針適合

1. **CLAUDE.md準拠**: プロジェクトのアーキテクチャ方針に沿った実装になっているか
   - ハイブリッドtick制（60秒間隔の固定tick）
   - サーバー権威（戦闘計算はサーバー側で実行）
   - 開発時フォールバック（バックエンド未起動時のフロント単体動作）

2. **関心の分離**: フロントエンドがビジネスロジック（ダメージ計算等）を持っていないか（サーバー権威の原則）
   - ただし `useBattleLocal.ts`（開発時フォールバック）は例外

3. **設計図との整合**: `diagrams/system_architecture.md` のシステム構成と実際の実装が一致しているか

## 出力形式

`.claude/references/review-format.md` を読み、その形式に従って保存すること。本コマンドのパラメータ:

- prefix: `full-review`
- レポートタイトル: `フルスタック統合レビュー結果`
- カテゴリ: 仕様書-コード整合性 / フロント・バック統合整合性 / アーキテクチャ方針適合
- 修正案は、フロント・バック両側の修正が必要な場合それぞれ記述する

## 注意事項

- 統合整合性の指摘を最優先で記述すること（フロントだけ・バックだけの問題より、両方にまたがる問題を重視する）
- 指摘はフロント・バック両側のコード、および仕様書の記述を引用して対比すること
- 重要度の基準:
  - **高**: フロント・バック間の通信が実際に失敗する問題、仕様との重大な乖離、データ不整合
  - **中**: 型の不一致（動作はするが型安全性が損なわれる）、仕様との軽微な不一致
  - **低**: 命名の不統一、改善が望ましいが動作に影響しない問題
- コード品質のみの問題（FastAPIパターン、Vue設計等）は `/backend-review` `/frontend-review` の担当とし、ここでは扱わない

$ARGUMENTS が指定された場合、その内容をレビューのスコープまたは追加観点として考慮してください（例: `認証フロー重点`、`装備システムの統合確認`）。