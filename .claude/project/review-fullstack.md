# 統合整合レビュー — プロジェクト固有プロファイル

> 一般手順は [.claude/references/review-procedure.md](../references/review-procedure.md)、出力形式は [review-format.md](../references/review-format.md)。本書は AFK GAME 固有の値のみを持つ。
> 対象スキル: `full-review`（仕様↔コード・フロント↔バックの整合）。片側のコード品質は [review-code.md](review-code.md)。
> 不変条件は [profile.md](profile.md) §5。

## 0. レビューパラメータ

| 項目 | 値 |
|------|-----|
| 保存先ディレクトリ | `docs/reviews/full-review/`（ファイル名は `YYYY-MM-DD_HHMMSS.md`） |
| ローテーション | `python scripts/rotate_reviews.py --apply`（直下を最新10件に保ち、超過分は `archive/` へ移動） |
| レポートタイトル | フルスタック統合レビュー結果 |
| カテゴリ | 仕様書-コード整合性 / フロント・バック統合整合性 / アーキテクチャ方針適合 |

準備コマンド（モード判定・差分特定・ISSUE採番。全量時は `--full` を追加）:

```bash
python .claude/scripts/review_prep.py --dir docs/reviews/full-review \
    --paths backend frontend/src docs/design docs/tech docs/data \
    --title フルスタック統合レビュー結果 \
    --categories "仕様書-コード整合性 / フロント・バック統合整合性 / アーキテクチャ方針適合"
```

修正案は、フロント・バック両側の修正が必要な場合それぞれ記述する。

## 1. 対象ファイル

| 区分 | 対象 |
|------|------|
| コード | `backend/` 配下の全 `.java`（`target/` のビルド生成物除外）、`frontend/src/` 配下の全 `.vue` `.ts` `.css` |
| 仕様書 | `design/game_spec.md` → `systems/`、`tech/tech_spec.md` → `tech_data` / `tech_structure{,_backend}` / `tech_api` / `tech_architecture` / `tech_logging`、`tech_battle.md`・`tech_offline.md`・`tech_auth.md`、`data/master_data.md` → `data/master/` |

索引 + 個別ファイル構成のため、**照合に必要な個別ファイルのみ**読む。
**設計図（`docs/diagrams/`）は対象外**。図が絡む照合は `diagrams-review` の担当（[review-procedure.md](../references/review-procedure.md) §7）。

### 1.1 全量モードの分担（最大3体・`sonnet`）

| 担当 | 対象ファイル |
|------|------------|
| API・型整合 | `afkgame-web` の `@RestController`・Resource、`frontend/src/api/`、`frontend/src/types/`、`tech_api.md` |
| ロジック・マスターデータ | `afkgame-domain` の Service・Entity/Repository・マスターデータ、`design/systems/`、`tech_battle.md`、`tech_offline.md`、`tech_data.md`、`data/master/` |
| 画面・アーキテクチャ | `frontend/src/views/`・`components/`・`stores/`・`router/`、`design/systems/ui*.md`、`tech_architecture.md`、[profile.md](profile.md) §5 |

差分モードは分担しない（[review-procedure.md](../references/review-procedure.md) §1 規律2）。

## 2. 差分モードの照合先

| 変更ファイル | 照合先 |
|------------|--------|
| `afkgame-web` の `@RestController` | `tech_api.md`、`frontend/src/api/` |
| `afkgame-domain` の Entity/Repository | `tech_data.md` |
| `afkgame-web` の Resource | `frontend/src/types/`、`tech_api.md` |
| `afkgame-domain` の Service | `design/systems/`、`tech_battle.md`・`tech_offline.md` |
| `afkgame-domain` のマスターデータ | `docs/data/master/`、`docs/data/towers/`、`docs/data/skills/` |
| `frontend/src/router/index.ts` | `design/systems/ui*.md` |
| `frontend/src/views/`・`components/` | `design/systems/ui*.md` |
| `frontend/src/stores/`・`api/` | `afkgame-web` の `@RestController`・Resource |

## 3. 観点

| 分類 | # | 観点 |
|------|---|------|
| 仕様書-コード | 1 | **API完全性**: `tech_api.md` の全エンドポイントが `routers/` に実装され、メソッド・パス・リクエスト/レスポンス形式が一致するか |
| 仕様書-コード | 2 | **データモデル**: `models/` が `tech_data.md` のテーブル定義と一致するか（テーブル名・カラム名・型・リレーション、必須カラムの欠落、不要カラム） |
| 仕様書-コード | 3 | **ビジネスロジック**: `services/` が `design/systems/` を正しく実装しているか（ダメージ計算・経験値/レベルアップ・ドロップ率・オフライン報酬・ショップ価格） |
| 仕様書-コード | 4 | **マスターデータ**: `afkgame-domain` のマスターデータが `docs/data/` と一致するか。**Service に数値がハードコードされていないか** |
| 仕様書-コード | 5 | **UI仕様**: 各画面の構成要素・表示情報（ステータス・レベル・ゴールド等）が `systems/ui*.md` と一致するか |
| 仕様書-コード | 6 | **画面遷移**: `frontend/src/router/index.ts` が `systems/ui*.md` の画面一覧・遷移条件と一致し、全画面がルート定義にあり、認証ガードが適切か |
| 統合整合性 | 7 | **API型整合**: `frontend/src/types/` と `afkgame-web` の Resource の一致（Jackson が camelCase を維持するため変換なし、オプショナル/必須、enum・定数値） |
| 統合整合性 | 8 | **エンドポイント整合**: `frontend/src/api/` の URL・HTTPメソッド・ボディ・レスポンス期待値が `routers/` と一致するか |
| 統合整合性 | 9 | **認証フロー**: トークン管理、ヘッダー名・形式（Bearer 等）、認証エラー時のフロント側ハンドリング（リダイレクト等） |
| 統合整合性 | 10 | **データフロー**: ポーリング間隔がフロント・バック・仕様書で一致するか。オフライン復帰の取得フローが整合するか |
| アーキテクチャ | 11 | [profile.md](profile.md) §5 の不変条件（ハイブリッドtick制・サーバー権威・開発時フォールバック）に沿っているか |
| アーキテクチャ | 12 | フロントがビジネスロジック（ダメージ計算等）を持っていないか。**例外は `useBattleLocal.ts` のみ** |
| アーキテクチャ | 13 | `tech_architecture.md` の構成（層の分離・依存の向き）と実装が一致するか |

## 4. 機械的検証

以下は目視せず、スクラッチパッドの使い捨てスクリプトで行う。

| 対象 | 方法 |
|------|------|
| 型・スキーマの突き合わせ | `afkgame-web` の Resource フィールドと `types/` の TypeScript プロパティを抽出して差分を取る |
| エンドポイントの突き合わせ | `routers/` のデコレータと `frontend/src/api/` の呼び出しURLを抽出して差分を取る |
| ハードコード数値の検出 | `services/` 内の数値リテラルを抽出し、`master_data/` に対応があるか照合する |
| ルート定義の突き合わせ | `router/index.ts` の path と `systems/ui*.md` の画面一覧を抽出して差分を取る |

## 5. 重要度の基準

| 重要度 | 基準 |
|-------|------|
| **高** | フロント・バック間の通信が実際に失敗する問題、仕様との重大な乖離、データ不整合 |
| **中** | 型の不一致（動作はするが型安全性が損なわれる）、仕様との軽微な不一致 |
| **低** | 命名の不統一、改善が望ましいが動作に影響しない問題 |

担当範囲の切り分けは [review-procedure.md](../references/review-procedure.md) §7 を参照。
