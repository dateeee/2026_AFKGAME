---
name: unit-test
description: バックエンド（FastAPI）の単体テストを pytest で作成し、C1（分岐）カバレッジ100%を達成する。「テストを書いて」「カバレッジを上げて」「未達分岐を潰して」「pytest が落ちる」など、backend/app 配下の services / routers / master_data のテスト作成・カバレッジ不足解消・term-missing の未達分岐対応で使用する。分岐観点の洗い出し方、coverage レポートの読み方、乱数・時刻・DBの固定パターンを含む。
---

# 単体テスト（C1カバレッジ100%）

開発工程定義書 §3.5 に基づき、`backend/app/` の単体テストを作成する。
**完了基準は「全テストPASS かつ C1（分岐網羅）カバレッジ100%」**。行網羅（C0）100%では不十分。

## 前提

| 項目 | 値 |
|------|-----|
| 配置 | `backend/tests/unit/test_<対象モジュール>.py` |
| 対象 | `services/` `routers/` `master_data/`（`models/` `schemas/` は定義のみのため副次的） |
| 設定 | `backend/pytest.ini`（`--cov=app --cov-branch --cov-report=term-missing` は設定済み） |
| 共通フィクスチャ | `backend/tests/conftest.py`（`db` `user` `player` `character` `client` `tower_record`） |
| マーカー | `pytestmark = pytest.mark.unit` をモジュール先頭に置く |

## 手順

### 1. 現状のカバレッジを測る

```bash
cd backend && python -m pytest --cov=app --cov-branch --cov-report=term-missing -q
```

対象モジュールだけを見る場合は `--cov=app.services.battle_service` のように絞る。
**Missing 列の読み方は [references/c1_checklist.md](references/c1_checklist.md) の「レポートの読み方」を参照**（`181->189` のような矢印表記が C1 の未達分岐）。

### 2. 分岐観点を洗い出す

対象モジュールを読み、**すべての分岐点**を列挙する。
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

### 4. 再測定して未達分岐を潰す

再実行し、Missing に残った行番号・矢印表記を1つずつ潰す。
到達不能な分岐が残った場合は、まず**実装側のデッドコードを疑う**（潰せない分岐は仕様の抜けか不要コードのことが多い）。

### 5. 完了確認

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

- 追加・修正したテストファイルとテスト件数
- 対象モジュールの **C1カバレッジの前後の数値**
- 未達分岐が残る場合は、その行と理由（到達不能／仕様未確定／別Phaseの機能 等）
- テスト作成中に発見した実装側の不具合・仕様の疑義
