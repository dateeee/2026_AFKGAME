"""結合テスト: 常設ショップ購入 → 所持金・在庫の反映

シナリオ導出元: docs/diagrams/api_sequence/gameplay.md §5、docs/tech/basic/tech_api.md 操作系
検証内容: 戦闘で得たゴールドがショップで使え、購入結果が在庫とゲーム状態の双方へ反映されること。
"""

import pytest

from app.config import TICK_INTERVAL_SECONDS

pytestmark = pytest.mark.integration

POTION_PRICE = 25
POTION_STACK_LIMIT = 99


def _lineup(api) -> dict:
    res = api.get("/api/shop/lineup")
    assert res.status_code == 200
    return {item["itemId"]: item for item in res.json()["lineup"]}


class TestScenario06常設ショップの購入:
    """必須シナリオ #6: gold不足時のエラー、購入後の整合"""

    def test_戦闘で得たゴールドでポーションを買うと在庫と所持金が整合する(
        self, api, guest, guest_player, rewind, fixed_rng
    ):
        # 所持金ゼロでは購入できない
        potion = _lineup(api)["hp_potion"]
        assert potion["price"] == POTION_PRICE
        assert potion["stackLimit"] == POTION_STACK_LIMIT
        assert api.get("/api/game/state").json()["player"]["gold"] == 0

        res = api.post("/api/shop/buy", json={"itemId": "hp_potion", "quantity": 1})
        assert res.status_code == 400
        # 常設枠・日替わり枠で同じエラーコード体系を返す（tech_logging.md §エラーコード体系）
        assert res.json()["error"]["code"] == "SHOP_INSUFFICIENT_GOLD"

        # 塔でゴールドを稼ぐ
        assert api.post(
            "/api/tower/select",
            json={"towerId": "goblin_tower", "targetFloor": 1, "mode": "auto_repeat"},
        ).status_code == 200
        rewind(guest_player, 20 * TICK_INTERVAL_SECONDS)
        gold = api.post("/api/battle/tick").json()["updatedState"]["player"]["gold"]
        assert gold >= POTION_PRICE, "20tickでポーション1個ぶんは獲得できること"

        owned_before = _lineup(api)["hp_potion"]["quantityOwned"]

        # 購入
        res = api.post("/api/shop/buy", json={"itemId": "hp_potion", "quantity": 1})
        assert res.status_code == 200
        body = res.json()
        assert body["gold"] == gold - POTION_PRICE
        assert body["quantity"] == owned_before + 1

        # 品揃えとゲーム状態の双方へ反映される
        assert _lineup(api)["hp_potion"]["quantityOwned"] == owned_before + 1
        state = api.get("/api/game/state").json()
        assert state["potions"]["hp_potion"] == owned_before + 1
        assert state["player"]["gold"] == gold - POTION_PRICE

    def test_所持上限を超える購入は拒否され所持金が減らない(self, api, db, guest, guest_player):
        guest_player.gold = 10_000
        db.commit()

        res = api.post("/api/shop/buy", json={"itemId": "hp_potion", "quantity": POTION_STACK_LIMIT})
        assert res.status_code == 400  # 初期所持5個 + 99個 > 上限99

        state = api.get("/api/game/state").json()
        assert state["player"]["gold"] == 10_000
        assert state["potions"]["hp_potion"] == 5

    def test_存在しない商品は404(self, api, db, guest, guest_player):
        guest_player.gold = 10_000
        db.commit()

        res = api.post("/api/shop/buy", json={"itemId": "no_such_item", "quantity": 1})
        assert res.status_code == 404
