# テスト実装パターン — AFK GAME 固有の具体例

> 一般的な pytest の型は [.claude/skills/test-list/references/patterns.md](../skills/test-list/references/patterns.md)。本書は AFK GAME のモジュール名・エラーコードを使った実例のみを持つ。
> フィクスチャ一覧は [test-list.md](test-list.md) §4。

参考にする既存テスト: `backend/tests/unit/test_target_floor.py`（最も整っている）、`test_battle_service.py`。

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

戦闘系モジュールは `import random` して `random.uniform(...)` を呼ぶ形なので、**そのモジュールの `random` 属性**を差し替える。

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

抽選系（`random.choices` / `random.sample`）は関数ごと差し替えて戻り値を固定する。

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

トークン期限切れは、DBレコードの `expires_at` を過去に設定して再現する。

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

サービス層は `app.exceptions.AppError` を送出する。`code` と `status_code` の両方を検証する。

```python
from app.exceptions import AppError

def test_gold不足なら購入できない(db, player):
    player.gold = 0
    with pytest.raises(AppError) as exc:
        buy_item(player, "hp_potion", 1, db)
    assert exc.value.code == "INSUFFICIENT_GOLD"
    assert exc.value.status_code == 400
```

ルーター経由では統一エラーボディ（`error.code`）を検証する。

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

それでも作れない場合のみ `# pragma: no cover - <理由>` を付ける（[unit-test.md](unit-test.md) §4）。
