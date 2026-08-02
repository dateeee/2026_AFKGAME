# テスト実装パターン（一般）

分岐を確実に選ぶための定型。**具体的なモジュール名・フィクスチャ名・エラーコードはプロジェクト固有プロファイルを参照**すること。

## モジュールの骨格

docstring に「仕様の参照先」と「分岐観点」を書く。これがテストと設計の対応表になる。

```python
"""単体テスト: <対象>（<実装ファイル>）

仕様: <参照する仕様書のパスとセクション>
分岐観点:
  - <観点1>
  - <観点2>
"""

import pytest

from <パッケージ> import <対象モジュール> as m

pytestmark = pytest.mark.unit


class Test対象の振る舞い:
    def test_条件を満たせばこうなる(self):
        ...
```

## 外部要因の固定

**分岐を選べないテストは書かない。** 乱数・時刻・外部I/O は必ず固定する。

### 乱数

対象モジュールが `import random` して呼ぶ形なら、**そのモジュールの `random` 属性**を差し替える。

```python
monkeypatch.setattr(m.random, "uniform", lambda a, b: 0.0)   # 分散なし
monkeypatch.setattr(m.random, "random", lambda: 1.0)         # 確率分岐に入らない
```

`random.random() < rate` は**両側**を通す。`0.0` で必ず発生、`1.0` で必ず非発生。
`random.seed(N)` による固定は「どの枝を通るか」がコードを読まないと分からないため、**monkeypatch を優先**する。
`choices` / `sample` を使う抽選は、抽選関数ごと差し替えて戻り値を固定する。

```python
monkeypatch.setattr(m, "<抽選関数>", lambda *a, **kw: <固定値>)
```

### 時刻

`datetime.now()` を直接呼ぶ実装では、**時刻を進めるのではなく起点を過去へずらす**ほうが壊れにくい。

```python
from datetime import datetime, timedelta, timezone

record.last_updated_at = datetime.now(timezone.utc) - timedelta(minutes=5)
```

期限切れ・タイムアウトは、レコードの期限カラムを過去に設定して再現する。

### 到達困難な分岐

DB制約違反・外部I/O失敗など通常経路で作れない分岐は、monkeypatch で例外を強制する。

```python
monkeypatch.setattr(db, "commit", lambda: (_ for _ in ()).throw(RuntimeError("boom")))
```

それでも作れない場合のみ除外指定（`# pragma: no cover - <理由>`）。**理由コメントは必須。**

## 境界値は parametrize に集約

境界そのものの値を必ず含め、各ケースに意図のコメントを付ける。

```python
@pytest.mark.parametrize(
    ("入力", "上限", "期待値"),
    [
        (0, 20, 1),    # 下限
        (19, 20, 20),  # 上限の1つ手前
        (20, 20, 20),  # 上限ちょうど（クランプ）
        (25, 20, 20),  # 上限超過（クランプ）
    ],
)
def test_上限でクランプされる(入力, 上限, 期待値):
    assert cap(入力, 上限) == 期待値
```

## 例外の検証

例外は**型だけでなく識別子・ステータスまで**検証する。型だけだと別の原因で落ちても通ってしまう。

```python
with pytest.raises(<例外型>) as exc:
    do_something()
assert exc.value.code == "<エラーコード>"
assert exc.value.status_code == 400
```

## APIルーターの検証

HTTPクライアントを使い、**ステータスコードと統一エラーボディの両方**を検証する。

```python
res = client.post("/api/xxx", json={...})
assert res.status_code == 404
assert res.json()["error"]["code"] == "<エラーコード>"
```

未認証は認証ヘッダを外して再現する。

```python
client.headers.pop("Authorization")
assert client.get("/api/xxx").status_code == 401
```

リクエスト／レスポンスのキー形式（camelCase / snake_case）はプロファイルで確認する。

## フィクスチャ

| 方針 | 内容 |
|------|------|
| 共通 | 全テストで使うものだけ `conftest.py` に置く |
| ローカル | 特定モジュールでしか使わないものはテストモジュール内に定義する |
| 一覧 | 利用可能な共通フィクスチャはプロファイルを参照する |

フィクスチャで作る初期データは**テストが依存する値だけ**を明示的に上書きする。既定値に暗黙依存したテストは、フィクスチャ変更で一斉に壊れる。
