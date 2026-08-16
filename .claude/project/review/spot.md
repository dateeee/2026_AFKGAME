# 対象指定レビュー — プロジェクト固有プロファイル

> 共通パラメータ・重要度の共通軸は [review.md](../review.md)。一般手順は [review-procedure.md](../../references/review-procedure.md)、出力形式は [review-format.md](../../references/review-format.md)。
> 対象スキル: `spot-review`。**工程別の一般観点は `.claude/skills/spot-review/references/**`**（プロジェクト非依存）。本書はそこへ上乗せする AFK GAME 固有値のみを持つ。

## 0. レビューパラメータ

| 項目 | 値 |
|------|-----|
| 保存先ディレクトリ | `docs/reviews/spot-review/` |
| レポートタイトル | 対象指定レビュー結果 |
| カテゴリ | 正しさ / 整合性 / 網羅性 / 規約 |

```bash
python .claude/scripts/review_prep.py --dir docs/reviews/spot-review \
    --paths <対象パス...> --full --title 対象指定レビュー結果 \
    --categories "正しさ / 整合性 / 網羅性 / 規約"
```

`--paths` は §1 で確定した対象。**既定は `--full`**（対象を毎回指定するため差分基点の意味が薄い）。引数 `diff` のときだけ `--full` を外す。

## 1. 対象 ↔ 工程 の対応

上から順に照合し、**最初に一致した行**の工程を採る。1つの依頼で複数のパスが指定された場合は工程ごとにまとめて扱う。

| # | パス | 工程 | 観点ファイル |
|---|------|------|------------|
| 1 | `docs/design/**` | 要件定義 | `requirements.md` |
| 2 | `docs/tech/basic/**` | 基本設計 | `basic-design.md` |
| 3 | `docs/tech/detail/**`、`docs/data/**` | 詳細設計 | `detail-design.md` |
| 4 | `backend/**/src/test/java/**/integration/**`、`frontend/tests/e2e/**` | 結合テスト | `integration-test.md` |
| 5 | `backend/**/src/test/java/**`、`frontend/**/*.spec.ts`、`frontend/**/*.test.ts` | 単体テスト（実装前のRedなら テストリスト作成） | `unit-test.md`（`test-list.md`） |
| 6 | `backend/**/src/main/**`、`frontend/src/**` | 製造 | `dev.md` |
| 7 | `docs/diagrams/**` | — | **対象外**。`diagrams-review` へ回す（§4） |
| 8 | `docs/process/**`、`docs/backlog/**`、`.claude/**`、`scripts/**` | — | 工程外。§3 の「工程外の対象」に従う |

- #5 の分岐: 対象テストが**まだ Red**（対応する実装が未着手）なら `test-list.md`、実装済みなら `unit-test.md`。判定は `python scripts/report_java_tests.py`（[commands/backend.md](../commands/backend.md) §2）の失敗内訳を根拠にする
- 工程名で指定された場合は本表を逆引きしてパスを決める。「テストリスト」は #5 のパスで `test-list.md` を使う

## 2. 上乗せする固有観点

一般観点（`references/**`）を当てたうえで、工程ごとに以下を追加する。**該当する行のファイルだけ**を読む。

| 工程 | 上乗せするもの |
|------|--------------|
| 全工程 | [profile.md](../profile.md) §5 **アーキテクチャ不変条件6件**（サーバー権威・ハイブリッドtick・シングルプレイ・開発時フォールバック・Phase厳守・データ駆動）への違反 |
| 要件定義 | 未確定仕様が未確定管理ファイル（`docs/backlog/open_specs.md`。全解消時は存在しない）に登録されているか。仕様の正の所在が [spec_ownership.md](../../../docs/process/spec_ownership.md) と一致するか |
| 基本設計 | [basic-design.md](../basic-design.md) §1 成果物・§3 固有の観点。設計図が絡む照合は行わない（§4） |
| 詳細設計 | [detail-design.md](../detail-design.md) §3 固有の観点・§4 分岐一覧の記法。丸めは `docs/tech/detail/tech_numeric.md`、乱数は `tech_rng.md` が正 |
| テストリスト作成 | [test-patterns.md](../test-patterns.md) の記述規約。`python scripts/check_branch_list.py --tests` の出力を先に取り込む |
| 製造（バックエンド） | [review/backend.md](backend.md) §2 の観点表と [coding-standards-backend.md](../../references/coding-standards-backend.md)。`python scripts/check_java_conventions.py` を先に実行して出力を取り込む |
| 製造（フロントエンド） | [review/frontend.md](frontend.md) §2 の観点表と [coding-standards-frontend.md](../../references/coding-standards-frontend.md) |
| 単体テスト | [unit-test.md](../unit-test.md) §1 前提（C1 設定）・§3 固有の分岐観点・§4 除外規則。カバレッジは `python scripts/report_java_tests.py` の出力が根拠（STALE 表示が出たら測り直す） |
| 結合テスト | [integration-test.md](../integration-test.md) §3 必須シナリオ・§4 固有の観点と、[integration-test/conventions.md](../integration-test/conventions.md) の L1 / L2 記述規約 |
| ドキュメント（対象が `docs/**` の全工程） | 文字数上限とリンクは `python scripts/check_doc_size.py` と `python scripts/check_docs.py` の出力を取り込む |

常用コマンドの正は [commands.md](../commands.md)。**常設スクリプトで判定できる項目を目視で重ねて探さない**（review-procedure.md §5）。

## 3. 重要度の基準

[review.md](../review.md) §2 の具体化。

| 重要度 | 基準 |
|-------|------|
| **高** | 不変条件（profile.md §5）への違反、セキュリティリスク、データ不整合を招く欠陥、仕様と実装の数値・分岐の食い違い、常設スクリプトが赤になる違反 |
| **中** | 規約・ベストプラクティスからの逸脱、未定義のまま実装へ流れる仕様、将来の不具合の原因、テストの検証不足 |
| **低** | 記法・命名の不統一、可読性、動作にも判定にも影響しない改善 |

**工程外の対象**（§1 #8）は、その領域の規約を正として当てる — `.claude/**` は [_TEMPLATE.md](../_TEMPLATE.md) の記述スキーマと「一般手順と固有値の分離」（CLAUDE.md）、`scripts/**` は [script-conventions.md](../../references/script-conventions.md)、`docs/process/**` `docs/backlog/**` は [documentation_rules.md](../../../docs/process/documentation_rules.md)。該当する規約が無い対象は、依頼時に観点をユーザーへ確認する。

## 4. 既存レビュー系5スキルとの使い分け

| 状況 | 使うスキル |
|------|----------|
| 工程のゲート（製造完了・Phase完了・仕様確定）に到達した | `backend-review` / `frontend-review` / `doc-review` / `diagrams-review` / `full-review` |
| 設計図（`docs/diagrams/**`）が絡む照合 | **`diagrams-review`**（図↔仕様・図↔コード・図↔図。`spot-review` は図を扱わない） |
| 仕様↔コード、フロント↔バックにまたがる整合 | `full-review` |
| ゲート外のタイミングで、特定のファイル・フォルダ・工程だけを見たい | **`spot-review`** |
| 定型レビューの対象外（`scripts/**`・`.claude/**`・`docs/process/**` 等） | **`spot-review`** |

同じ対象を直近の定型レビューが既にカバーしている場合は、重複計上を避けるため実施可否をユーザーへ確認する（review-procedure.md §7）。
