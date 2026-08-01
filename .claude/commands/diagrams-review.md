---
description: 設計図（Mermaid図）と仕様書・コードの整合性レビュー（デフォルトは前回レビュー以降の差分、full 指定で全量）
argument-hint: "[full] [追加観点]"
model: sonnet
---

あなたはAFK GAMEプロジェクトの設計図（Mermaid図）レビュー専門家です。設計図と仕様書・コードの整合性を検証し、設計図間の矛盾や欠落を検出してください。

## コスト規律（最重要）

1. **デフォルトは差分レビュー**。`docs/reviews/diagrams-review_*.md` の最新タイムスタンプ以降に変更された設計図・仕様書・コードに関係する図のみをレビューする。`$ARGUMENTS` に `full` がある場合、または過去のdiagrams-reviewレポートがない場合のみ全量。
2. **サブエージェントは全量モードでのみ使用し、最大3体まで**。各エージェントには担当ファイルのみを列挙し「列挙外は読まない」「戻り値は指摘のみ・引用は1〜3行」を明示。モデルは `sonnet` を指定する。
3. **Mermaid構文チェックは目視しない**。スクラッチパッドに使い捨てスクリプト（mermaidパーサ or 正規表現による PK/FK・フェンス閉じ・end対応チェック）を書いて機械的に検証する。
4. 照合先の仕様書・コードは**該当セクション・該当ファイルのみ**読む（全文読み込み禁止）。

## レビュー対象ファイル

### 設計図
主要4図は **索引 + 同名ディレクトリ** 構成。索引で担当ファイルを特定し、**必要な子ファイルのみ**読むこと（全図の一括読み込みは禁止）:

1. `diagrams/er_diagram.md` → `er_diagram/` （player / item / battle）— ER図
2. `diagrams/class_diagram.md` → `class_diagram/` （player / battle / item）— クラス図
3. `diagrams/battle_flow.md` → `battle_flow/` （overview / turn / offline / bossrush）— 戦闘フロー図
4. `diagrams/api_sequence.md` → `api_sequence/` （core / auth / gameplay / character / base / endgame）— APIシーケンス図
5. `diagrams/screen_transition.md` — 画面遷移図（単一ファイル）
6. `diagrams/system_architecture.md` — システム構成図（単一ファイル）

### 仕様書（照合用）
仕様書も索引 + 個別ファイル構成。**照合に必要なファイル・セクションのみ**読むこと:

1. `CLAUDE.md` — アーキテクチャ不変条件・開発方針（プロジェクト概要は `README.md`）
2. `docs/design/game_spec.md` → `design/systems/` — ゲームシステム・バランス・UI仕様
3. `docs/tech/tech_spec.md` → `tech_data.md`（データ構造）・`tech_api.md`（API設計）・`tech_architecture.md`（アーキテクチャ）
4. `docs/tech/tech_battle.md` / `tech_offline.md` — 戦闘処理・オフライン計算
5. `docs/tech/tech_auth.md` — 認証システム
6. `docs/data/master_data.md` → `data/master/` — マスターデータ

### コード（照合用）
実装済みのコードがあれば読み込んでください:

1. `backend/app/models/` — SQLAlchemy モデル
2. `backend/app/routers/` — APIルーター
3. `backend/app/services/` — ビジネスロジック
4. `frontend/src/router/` — Vue Router 定義
5. `frontend/src/stores/` — Pinia ストア
6. `frontend/src/api/` — API通信層

## レビュー観点

### A. 仕様書との整合性

各設計図が対応する仕様書の内容と一致しているか:

1. **ER図 ↔ tech_data.md**: テーブル定義、カラム、型、リレーションが仕様書のデータ構造と一致するか
2. **クラス図 ↔ design/systems/ / tech_data.md**: ドメインモデルのクラス・属性・メソッドが仕様書の機能定義と一致するか
3. **画面遷移図 ↔ systems/ui.md**: 画面構成、遷移条件、Phase別タブ構成がUI仕様と一致するか
4. **戦闘フロー図 ↔ tech_battle.md / tech_offline.md**: ターン処理の順序、条件分岐、状態遷移が戦闘仕様と一致するか
5. **APIシーケンス図 ↔ tech_api.md / tech_architecture.md**: エンドポイント、リクエスト/レスポンスフロー、認証フローが仕様と一致するか
6. **システム構成図 ↔ tech_architecture.md / CLAUDE.md**: アーキテクチャ方針（ハイブリッドtick制、サーバー権威等）が反映されているか

### B. コードとの整合性

実装済みコードと設計図が乖離していないか:

1. **ER図 ↔ backend/app/models/**: SQLAlchemy モデルのテーブル名・カラム名・型・リレーションがER図と一致するか
2. **クラス図 ↔ backend/app/services/ + frontend/src/stores/**: ドメインロジックの構造が設計と一致するか
3. **画面遷移図 ↔ frontend/src/router/**: ルート定義、ガード条件が画面遷移図と一致するか
4. **APIシーケンス図 ↔ backend/app/routers/ + frontend/src/api/**: エンドポイントのパス・メソッド・フローが一致するか

### C. 設計図間の整合性

設計図同士で矛盾がないか:

1. **ER図 ↔ クラス図**: エンティティとドメインクラスの対応関係が整合しているか（属性・リレーション）
2. **画面遷移図 ↔ APIシーケンス図**: 画面遷移で発生するAPI呼び出しがシーケンス図に定義されているか
3. **戦闘フロー図 ↔ APIシーケンス図**: 戦闘関連のAPI呼び出しタイミングが両図で一致するか
4. **システム構成図 ↔ 他の全図**: システム構成の前提（コンポーネント構成、通信方式等）と他の図の内容が整合するか

### D. Mermaid構文の正当性

1. 各図のMermaidコードブロックが構文的に正しいか（閉じタグの欠落、不正なキーワード等）
2. ノード名・エッジラベルが意味のある内容になっているか
3. 図が過度に複雑化していないか（可読性の確認）

### E. 網羅性

1. 仕様書で定義された主要機能に対応する設計図が存在するか
2. 新しく追加された仕様（Phase 3〜5の機能等）が設計図に反映されているか
3. 設計図内で「TODO」「TBD」「未定」等の未完成箇所がないか

### F. ドキュメント規約（[docs/documentation_rules.md](../../docs/documentation_rules.md)）

1. `python scripts/check_doc_size.py` を実行し、`diagrams/` 配下の `ERROR`（上限超過）を重要度=高で報告する
2. 超過時は同名ディレクトリへ図単位で切り出す分割案を修正案に書く（既存の `er_diagram/` 等と同じ構成にする）
3. 索引ファイルに全子ファイルへのリンクが揃っているか確認する（§6 分割時の必須事項）

## 出力形式

`.claude/references/review-format.md` を読み、その形式に従って保存すること。本コマンドのパラメータ:

- prefix: `diagrams-review`
- レポートタイトル: `設計図レビュー結果`
- カテゴリ: 仕様書との整合性 / コードとの整合性 / 設計図間の整合性 / Mermaid構文 / 網羅性

## 注意事項

- 指摘は設計図の該当箇所と仕様書/コードの記述を引用して対比すること
- 重要度の基準:
  - **高**: 設計図とコード/仕様書の重大な乖離（テーブル定義の不一致、画面遷移の欠落等）
  - **中**: 設計図間の不整合、属性の過不足、フローの軽微な差異
  - **低**: 命名の不統一、構文上の改善点、可読性の問題
- 仕様書のみの問題（仕様書間の矛盾等）は `/doc-review` の担当とし、ここでは扱わない
- コードのみの問題は `/backend-review` `/frontend-review` の担当とし、ここでは扱わない

$ARGUMENTS が指定された場合、その内容をレビューのスコープまたは追加観点として考慮してください（例: `ER図重点`、`戦闘フロー図のみ`）。