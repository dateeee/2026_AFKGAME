---
name: unit-test
description: バックエンド（FastAPI）の単体テストを pytest で作成し、C1（分岐）カバレッジ100%を達成する。「実装前にテストを書いて」「テストリストを作って」「テストを書いて」「カバレッジを上げて」「未達分岐を潰して」「pytest が落ちる」など、backend/app 配下の services / routers / master_data のテストファースト作成・カバレッジ不足解消・term-missing の未達分岐対応で使用する。分岐観点の洗い出し方、coverage レポートの読み方、乱数・時刻・DBの固定パターンを含む。
---

# 単体テスト（テストファースト / C1カバレッジ100%）

開発工程定義書 §3.4（テストリスト作成）・§3.6（単体テスト）に基づき、`backend/app/` のテストを作成する。
**完了基準は「全テストPASS かつ C1（分岐網羅）カバレッジ100%」**。行網羅（C0）100%では不十分。

## モードの判定

依頼内容から、どちらのモードかを最初に判定する。

| モード | 状況 | 進め方 |
|--------|------|--------|
| **A. テストリスト作成**（既定） | 実装が**まだ無い**新規機能・新規分岐 | §3.4。手順1をスキップし、**手順2 → 3 → Red確認**まで。実装は書かない（dev スキルへ引き継ぐ） |
| **B. 測定と補完** | 実装済み。製造完了後のC1確認、既存コードの遡及整備 | §3.6。手順1〜5をそのまま実施 |

**モードAでは実装コードを書かない。** テストを書き、実行して**期待どおり失敗する（Red）**ことを確認したら、そこで報告して止める。
モードAで「テストが最初から成功してしまう」場合は、そのテストが分岐を検証できていない（アサーションが弱い／既存挙動をなぞっているだけ）ことを疑う。

## 前提

| 項目 | 値 |
|------|-----|
| 配置 | `backend/tests/unit/test_<対象モジュール>.py` |
| 対象 | `services/` `routers/` `master_data/`（`models/` `schemas/` は定義のみのため副次的） |
| 設定 | `backend/pytest.ini`（`--cov=app --cov-branch`、term-missing + HTML の両レポート設定済み）/ `backend/.coveragerc`（除外・整形） |
| レポート | ターミナル（term-missing）と `backend/htmlcov/index.html`（HTML・未達行がハイライト表示。gitignore済み） |
| 共通フィクスチャ | `backend/tests/conftest.py`（`db` `user` `player` `character` `client` `tower_record`） |
| マーカー | `pytestmark = pytest.mark.unit` をモジュール先頭に置く |

## 手順

### 1. 現状のカバレッジを測る（モードBのみ）

```bash
cd backend && python -m pytest --cov=app --cov-branch --cov-report=term-missing -q
```

`cd backend && python -m pytest -q` だけでも同じ計測が走る（オプションは pytest.ini に設定済み）。
対象モジュールだけを見る場合は `--cov=app.services.battle_service` のように絞る。
**Missing 列の読み方は [references/c1_checklist.md](references/c1_checklist.md) の「レポートの読み方」を参照**（`181->189` のような矢印表記が C1 の未達分岐）。

未達分岐が多いときは HTML レポート（`backend/htmlcov/index.html`）を開くと、未実行行が赤、**部分分岐が黄**でソース上に直接表示されるため原因を特定しやすい。

### 2. 分岐観点を洗い出す

**モードA**では、詳細設計の**分岐一覧（単体テスト観点）**が一次ソース。実装が存在しないため、仕様書（`docs/tech/tech_*.md`・`docs/data/`）から観点を起こす。
分岐一覧が仕様書に無い場合は、テストを書き始める前にユーザーへ通知する（詳細設計への追記が先。開発工程定義書 §3.3 完了基準）。

**モードB**では対象モジュールを読み、**すべての分岐点**を列挙する。
構文別の観点（if / ループ / try / 三項 / 短絡評価 / デフォルト引数）と、本プロジェクト固有の観点（乱数・境界値・マスターデータ未知ID・DB未登録・認証）は
[references/c1_checklist.md](references/c1_checklist.md) にチェックリストとしてまとめてある。**テストを書く前に必ず読むこと。**

観点は「仕様上の意味」で言語化する。`if x > 0:` を「真/偽」ではなく「HPが残っている / 全滅した」と表現すると、テスト名と docstring がそのまま設計の説明になる。

### 3. テストを書く

実装パターン（乱数固定・時刻固定・例外検証・parametrize・TestClient）は
[references/patterns.md](references/patterns.md) を参照する。

スタイルは既存テスト（`backend/tests/unit/test_target_floor.py` が最も整っている）に合わせる。

- モジュール docstring に **仕様書の参照先** と **分岐観点** を書く
- 観点ごとに `class Test<対象>` でグループ化する
- テスト関数名は**日本語**で、期待する振る舞いを書く（例: `test_目標階が上限と一致していれば追従する`）
- 境界値・等価クラスは `@pytest.mark.parametrize` にまとめ、各ケースに `#` コメントで意図を書く
- 1テスト1観点。複数分岐を1つのテストに詰め込まない

### 4. Red を確認する（モードAはここで完了）

```bash
cd backend && python -m pytest tests/unit/test_<対象>.py -q
```

- 全件が **FAIL または ERROR** になること
- **落ち方が期待どおり**であること（未実装の関数なら `ImportError` / `AttributeError`、実装済みなら `AssertionError`。想定外の `TypeError` はテスト側のバグ）

ここで報告して止める。実装は **dev スキル**が Red → Green → Refactor で進める。

### 5. 再測定して未達分岐を潰す（モードB）

再実行し、Missing に残った行番号・矢印表記を1つずつ潰す。
到達不能な分岐が残った場合は、まず**実装側のデッドコードを疑う**（潰せない分岐は仕様の抜けか不要コードのことが多い）。
補完で新たに見つけた分岐は、詳細設計の**分岐一覧へ反映**する（次回以降のテストリストの入力になる）。

### 6. 完了確認

```bash
cd backend && python -m pytest --cov=app --cov-branch --cov-fail-under=100 -q
```

## 除外規則

`# pragma: no cover` は**理由コメント必須**。許容されるのは `if __name__ == "__main__":` などの起動コードや、実行環境依存で再現できない例外ハンドラのみ。

```python
except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
```

カバレッジを通すためだけに `pragma: no cover` を付けることは禁止。

## 報告

作業完了時は以下を報告する。

**モードA**: 追加したテストファイルと件数、分岐一覧との対応（どの観点をどのテストで押さえたか）、Red の実行結果（全件失敗と落ち方）、仕様書に不足していた分岐観点。

**モードB**:

- 追加・修正したテストファイルとテスト件数
- 対象モジュールの **C1カバレッジの前後の数値**
- 未達分岐が残る場合は、その行と理由（到達不能／仕様未確定／別Phaseの機能 等）
- テスト作成中に発見した実装側の不具合・仕様の疑義
