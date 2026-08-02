"""単体テスト: 日替わりショップAPI（routers/shop.py）

仕様: tech/tech_shop.md §6 API、§8 分岐一覧（購入・取得）の #1-#6 / #15-#18

分岐観点:
  - リクエストの排他: itemId のみ / dailySlotIndex のみ / 両方 / どちらも未指定
  - 枠番号の範囲（0〜4 と範囲外）
  - 品揃え取得の soldOut（全未購入 / 一部購入済み / 全購入済み）
  - 次回更新時刻は現在のUTC日付の翌日 00:00:00Z
"""

from datetime import datetime, timezone

import pytest

from app.models.shop import ShopDailySlot, ShopDailyState
from app.services import shop_daily_service as sds

pytestmark = pytest.mark.unit


NOW = datetime(2026, 8, 2, 12, 0, 0, tzinfo=timezone.utc)
RESET = datetime(2026, 8, 3, 0, 0, 0, tzinfo=timezone.utc)

# 枠の確定値。装備レベル5 → 基礎値 floor(5×1.5+2)=9、コモンATK floor(9×1.0×1.0×0.7)=6
_SEED_SLOTS = [
    # (slot_index, category, base_id, rarity, level, stat_atk, price)
    (0, "weapon", "sword", "common", 5, 6, 500),
    (1, "weapon", "dagger", "common", 5, 6, 500),
    (2, "armor", "helm", "common", 5, 6, 400),
    (3, "armor", "shield", "common", 5, 6, 400),
    (4, "accessory", "ring", "common", 5, 6, 300),
]


def _seed_state(db, player, sold=()) -> ShopDailyState:
    """確定値の5枠をDBへ直接作る（リセット時刻は凍結した現在時刻より後）"""
    state = ShopDailyState(player_id=player.id, reset_at=RESET)
    db.add(state)
    db.flush()
    for slot_index, category, base_id, rarity, level, stat_atk, price in _SEED_SLOTS:
        db.add(
            ShopDailySlot(
                shop_daily_state_id=state.id,
                slot_index=slot_index,
                category=category,
                base_id=base_id,
                rarity=rarity,
                level=level,
                stat_atk=stat_atk,
                stat_def=None,
                stat_hp=None,
                stat_spd=None,
                price=price,
                sold=slot_index in sold,
            )
        )
    db.commit()
    return state


@pytest.fixture(autouse=True)
def frozen_now(monkeypatch):
    """現在時刻を固定し、テスト中に日付が変わらないようにする"""
    monkeypatch.setattr(sds, "_now", lambda: NOW)


@pytest.fixture
def daily(db, player):
    _seed_state(db, player)


class Testリクエストの排他:
    def test_itemIdのみの指定は常設購入として処理される(self, client, daily):
        # §8-1 日替わり装備は付与されない
        res = client.post("/api/shop/buy", json={"itemId": "hp_potion", "quantity": 2})
        assert res.status_code == 200
        body = res.json()
        assert body["quantity"] == 7  # 5 + 2
        assert body["equipment"] is None

    def test_dailySlotIndexのみの指定は日替わり購入として処理される(self, client, daily):
        # §8-2
        res = client.post("/api/shop/buy", json={"dailySlotIndex": 0})
        assert res.status_code == 200
        body = res.json()
        assert body["gold"] == 500  # 1000 - 500
        assert body["equipment"]["baseId"] == "sword"

    def test_両方を指定したら422(self, client, daily):
        # §8-3
        res = client.post(
            "/api/shop/buy", json={"itemId": "hp_potion", "quantity": 1, "dailySlotIndex": 0}
        )
        assert res.status_code == 422

    def test_どちらも指定しなければ422(self, client, daily):
        # §8-4
        assert client.post("/api/shop/buy", json={}).status_code == 422


class Test枠番号:
    @pytest.mark.parametrize("slot_index", [0, 1, 2, 3, 4])  # 下端・中間・上端
    def test_0から4の範囲内なら購入処理を続行する(self, client, daily, slot_index):
        # §8-5
        res = client.post("/api/shop/buy", json={"dailySlotIndex": slot_index})
        assert res.status_code == 200

    @pytest.mark.parametrize("slot_index", [-1, 5])  # 下端の1つ手前 / 上端の1つ先
    def test_範囲外の枠番号は422(self, client, daily, slot_index):
        # §8-6
        res = client.post("/api/shop/buy", json={"dailySlotIndex": slot_index})
        assert res.status_code == 422


class Test品揃え取得:
    def test_全枠が未購入なら5件すべて売り切れではない(self, client, daily):
        # §8-15
        body = client.get("/api/shop/lineup").json()
        assert [d["soldOut"] for d in body["daily"]] == [False] * 5

    def test_一部が購入済みなら該当枠だけ売り切れになる(self, client, db, player):
        # §8-16
        _seed_state(db, player, sold=(2,))
        body = client.get("/api/shop/lineup").json()
        assert [d["soldOut"] for d in body["daily"]] == [False, False, True, False, False]

    def test_全枠が購入済みでも5件返り枠は消えない(self, client, db, player):
        # §8-17
        _seed_state(db, player, sold=(0, 1, 2, 3, 4))
        body = client.get("/api/shop/lineup").json()
        assert len(body["daily"]) == 5
        assert [d["soldOut"] for d in body["daily"]] == [True] * 5

    def test_次回更新時刻は現在のUTC日付の翌日0時を返す(self, client, daily):
        # §8-18 現在時刻は 2026-08-02T12:00:00Z に凍結している
        assert client.get("/api/shop/lineup").json()["dailyResetAt"] == "2026-08-03T00:00:00Z"
