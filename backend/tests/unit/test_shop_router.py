"""単体テスト: ショップルーター（routers/shop.py）

仕様: design/systems/economy.md「ショップ」、tech/basic/tech_api.md `/api/shop/*`
分岐観点:
  - ラインナップは potion カテゴリのみ・所持数は在庫レコードの有無で 0 起点
  - 購入は 未知ID(404) / 数量0以下(422) / ゴールド不足(400) / スタック上限超過(400)
  - 在庫レコードの有無で数量加算 / 新規作成に分かれる
  - 境界: ゴールドちょうど・スタック上限ちょうどは成功
"""

import pytest

from app.master_data.items import ITEMS, ItemData
from app.models.item import InventoryItem
from tests.helpers import error_code

pytestmark = pytest.mark.unit


def _potion(item_id: str, price: int = 40) -> ItemData:
    return ItemData(item_id, "テストポーション", "potion", price, 0.3, 99)


class TestLineup:
    def test_ポーションのラインナップと所持数を返す(self, client):
        res = client.get("/api/shop/lineup")
        assert res.status_code == 200
        lineup = {i["itemId"]: i for i in res.json()["lineup"]}
        hp = lineup["hp_potion"]
        assert hp["price"] == 25
        assert hp["quantityOwned"] == 5
        assert hp["stackLimit"] == 99

    def test_ポーション以外のカテゴリは含まれない(self, client, monkeypatch):
        monkeypatch.setitem(
            ITEMS, "iron_sword", ItemData("iron_sword", "鉄の剣", "weapon", 100, 0.0, 1)
        )
        ids = [i["itemId"] for i in client.get("/api/shop/lineup").json()["lineup"]]
        assert "iron_sword" not in ids

    def test_未所持のポーションは所持数0(self, client, monkeypatch):
        monkeypatch.setitem(ITEMS, "mp_potion", _potion("mp_potion"))
        lineup = {i["itemId"]: i for i in client.get("/api/shop/lineup").json()["lineup"]}
        assert lineup["mp_potion"]["quantityOwned"] == 0


class TestBuy:
    def _buy(self, client, item_id="hp_potion", quantity=1):
        return client.post("/api/shop/buy", json={"itemId": item_id, "quantity": quantity})

    def test_所持済みアイテムの購入で数量が加算される(self, client, db, player):
        res = self._buy(client, quantity=2)
        assert res.status_code == 200
        body = res.json()
        assert body["status"] == "ok"
        assert body["gold"] == 950  # 1000 - 25×2
        assert body["quantity"] == 7  # 5 + 2
        assert player.gold == 950

    def test_未所持アイテムの購入で新規インベントリが作られる(self, client, db, player, monkeypatch):
        monkeypatch.setitem(ITEMS, "mp_potion", _potion("mp_potion", price=40))
        res = self._buy(client, item_id="mp_potion", quantity=3)
        assert res.status_code == 200
        assert res.json()["quantity"] == 3
        assert res.json()["gold"] == 880  # 1000 - 40×3
        inv = db.query(InventoryItem).filter_by(
            player_id=player.id, item_id="mp_potion"
        ).first()
        assert inv is not None
        assert inv.quantity == 3

    def test_存在しないアイテムは404(self, client):
        res = self._buy(client, item_id="elixir")
        assert res.status_code == 404
        assert error_code(res) == "SHOP_ITEM_NOT_FOUND"

    @pytest.mark.parametrize(
        "quantity",
        [
            0,   # 境界そのもの（0個）
            -1,  # 負数
        ],
    )
    def test_数量0以下は422(self, client, quantity):
        # 下限はスキーマ検証（tech_api.md §エラー: 型・範囲違反は 422）
        res = self._buy(client, quantity=quantity)
        assert res.status_code == 422

    def test_ゴールド不足は400(self, client):
        res = self._buy(client, quantity=41)  # 25×41 = 1025 > 1000
        assert res.status_code == 400
        assert error_code(res) == "SHOP_INSUFFICIENT_GOLD"

    def test_ゴールドちょうどなら購入できる(self, client):
        res = self._buy(client, quantity=40)  # 25×40 = 1000 = 所持ゴールド
        assert res.status_code == 200
        assert res.json()["gold"] == 0

    def test_スタック上限超過は400(self, client, db, player):
        player.gold = 10_000
        db.commit()
        res = self._buy(client, quantity=95)  # 5 + 95 = 100 > 99
        assert res.status_code == 400
        assert error_code(res) == "SHOP_STACK_LIMIT"

    def test_スタック上限ちょうどまでは購入できる(self, client, db, player):
        player.gold = 10_000
        db.commit()
        res = self._buy(client, quantity=94)  # 5 + 94 = 99 = stack_limit
        assert res.status_code == 200
        assert res.json()["quantity"] == 99
