# pytest 実装パターン

`backend/tests/` で使う定型。既存の `tests/unit/test_target_floor.py` / `test_battle_service.py` と揃える。

## 共通フィクスチャ（conftest.py）

| フィクスチャ | 内容 |
|------------|------|
| `db` | インメモリSQLite（StaticPool）のセッション。テストごとに作り直す |
| `user` | `test-user` / 非ゲスト |
| `player` | gold=1000、`PlayerSettings(potion_threshold=0.3)`、hp_potion×5、初期キャラを持つ |
| `character` | `player` の初期キャラクター |
| `client` | `get_db` を差し替え済み・Authorization ヘッダ付与済みの `TestClient` |
| `tower_record` | `tower_record(tower_id, highest_floor=0, cleared=False)` で塔別クリア記録を作るファクトリ |

不足するフィクスチャはテストモジュール内にローカル定義する（`conftest.py` は全テスト共通のものだけ）。

## モジュールの骨格

```python
"""単体テスト: <対象>（services/xxx.py）

仕様: <参照する仕様書>
分岐観点:
  - <観点1>
  - <観点2>
"""

import pytest

from app.services import battle_service as bs

pytestmark = pytest.mark.unit


class Test対象の振る舞い:
    def test_条件を満たせばこうなる(self, db, player):
        ...
```

## 乱数の固定

モジュールが `import random` して `random.uniform(...)` を呼ぶ形なので、**そのモジュールの `random` 属性**を差し替える。

```python
@pytest.fixture
def no_variance(monkeypatch):
    """ダメージ分散・クリティカル・装備ドロップを固定する"""
    monkeypatch.setattr(bs.random, "uniform", lambda a, b: 0.0)
    monkeypatch.setattr(bs.random, "random", lambda: 1.0)  # クリティカル発生せず
    monkeypatch.setattr(bs, "try_drop", lambda *a, **kw: (None, None))
```

確率分岐 `random.random() < rate` は **両側**を通す。`lambda: 0.0` で必ず発生、`lambda: 1.0` で必ず非発生。
`random.seed(N)` による固定は「どの枝を通るか」がコードを読まないと分からないため、**monkeypatch を優先**する。

`random.choices` / `random.sample` を使う抽選は、関数ごと差し替えて戻り値を固定する。

```python
monkeypatch.setattr(bs, "roll_encounter", lambda tower_id, floor: enemy)
```

## 時刻の固定

`datetime.now(timezone.utc)` を直接呼ぶ実装が多いため、**時刻を進めるのではなく起点を過去にずらす**。

```python
from datetime import datetime, timedelta, timezone

def test_経過時間ぶんのtickが処理される(db, player, client):
    player.last_tick_at = datetime.now(timezone.utc) - timedelta(minutes=5)
    db.commit()
    # → 60秒tick × 5回ぶんが処理される
```

トークン期限切れなどは、DBレコードの `expires_at` を過去に設定して再現する。

## 境界値は parametrize に集約

各ケースに**意図のコメント**を付ける。境界そのものの値を必ず含めること。

```python
@pytest.mark.parametrize(
    ("highest", "total", "expected"),
    [
        (0, 20, 1),    # 未挑戦 → 1階のみ
        (19, 20, 20),  # 最上階の1つ手前
        (20, 20, 20),  # 総階数でクランプ（+1 しない）
        (25, 20, 20),  # 記録が総階数を超えていてもクランプ
    ],
)
def test_有限塔は総階数でクランプされる(highest, total, expected):
    assert target_floor_cap(highest, total) == expected
```

## 例外・エラーレスポンス

```python
from app.exceptions import AppError

def test_gold不足なら購入できない(db, player):
    player.gold = 0
    with pytest.raises(AppError) as exc:
        buy_item(player, "hp_potion", 1, db)
    assert exc.value.code == "INSUFFICIENT_GOLD"
    assert exc.value.status_code == 400
```

ルーター経由では統一エラーボディを検証する。

```python
def test_未知の塔IDは404(client):
    res = client.post("/api/tower/select", json={"towerId": "unknown"})
    assert res.status_code == 404
    assert res.json()["error"]["code"] == "TOWER_NOT_FOUND"
```

## ルーターのテスト

`client` フィクスチャは認証済み。**未認証**を試すときはヘッダを外す。

```python
def test_トークンなしは401(client):
    client.headers.pop("Authorization")
    assert client.get("/api/game/state").status_code == 401
```

リクエスト／レスポンスは **camelCase**（CamelModel）である点に注意。

## 到達困難な分岐

DB制約違反・外部I/O失敗など通常経路で作れない分岐は monkeypatch で強制的に例外を起こす。

```python
def test_保存に失敗したらロールバックする(db, player, monkeypatch):
    monkeypatch.setattr(db, "commit", lambda: (_ for _ in ()).throw(RuntimeError("boom")))
```

それでも作れない場合のみ `# pragma: no cover - <理由>` を付ける。理由コメントは必須。
