# テスト実装パターン — AFK GAME 固有の具体例

> 一般形（モジュールの骨格・外部要因の固定・parametrize・例外の検証・フィクスチャ方針）は
> [.claude/skills/test-list/references/patterns.md](../skills/test-list/references/patterns.md)。
> **本書は一般形へ当てはめる固有の値のみ**を持つ。フィクスチャ一覧は [test-list.md](test-list.md) §4、除外規則は [unit-test.md](unit-test.md) §4。

参考にする既存テスト: `backend/tests/unit/test_target_floor.py`（最も整っている）、`test_battle_service.py`。

## 対象モジュールとフィクスチャ

| 一般形の箇所 | AFK GAME での値 |
|------------|---------------|
| 対象モジュールの import | `from app.services import battle_service as bs`（docstring の実装ファイルは `services/xxx.py`） |
| テスト関数の引数 | 共通フィクスチャ `db` / `player` / `character` / `client` / `tower_record` |

## 乱数の固定

差し替える実体（一般形の `m.random` ・ `<抽選関数>` に対応）。

```python
@pytest.fixture
def no_variance(monkeypatch):
    """ダメージ分散・クリティカル・装備ドロップを固定する"""
    monkeypatch.setattr(bs.random, "uniform", lambda a, b: 0.0)
    monkeypatch.setattr(bs.random, "random", lambda: 1.0)  # クリティカル発生せず
    monkeypatch.setattr(bs, "try_drop", lambda *a, **kw: (None, None))


monkeypatch.setattr(bs, "roll_encounter", lambda tower_id, floor: enemy)
```

## 時刻の固定

過去へずらす対象は `player.last_tick_at`（tick は60秒間隔）。トークン期限切れは DBレコードの `expires_at` を過去に設定する。

```python
def test_経過時間ぶんのtickが処理される(db, player, client):
    player.last_tick_at = datetime.now(timezone.utc) - timedelta(minutes=5)
    db.commit()
    # → 60秒tick × 5回ぶんが処理される
```

## 境界値の実例（`target_floor_cap`）

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

サービス層が送出する例外型は `app.exceptions.AppError`（`code` と `status_code` を持つ）。

```python
def test_gold不足なら購入できない(db, player):
    player.gold = 0
    with pytest.raises(AppError) as exc:
        buy_item(player, "hp_potion", 1, db)
    assert exc.value.code == "INSUFFICIENT_GOLD"
    assert exc.value.status_code == 400
```

ルーター経由は統一エラーボディ `error.code` を検証する。リクエスト／レスポンスは **camelCase**（CamelModel）。

```python
def test_未知の塔IDは404(client):
    res = client.post("/api/tower/select", json={"towerId": "unknown"})
    assert res.status_code == 404
    assert res.json()["error"]["code"] == "TOWER_NOT_FOUND"


def test_トークンなしは401(client):
    client.headers.pop("Authorization")  # client フィクスチャは認証済み
    assert client.get("/api/game/state").status_code == 401
```
