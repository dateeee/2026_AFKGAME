"""結合テスト: 装備ドロップ → 装備変更 → ステータス反映

シナリオ導出元: docs/diagrams/api_sequence/gameplay.md §6、docs/design/systems/equipment.md
検証内容: 戦闘のドロップが所持一覧へ入り、装着が実効ステータスと売却可否まで連結すること。
"""

import pytest

from app.config import TICK_INTERVAL_SECONDS

pytestmark = pytest.mark.integration


def _farm(api, guest_player, rewind, ticks: int = 30) -> dict:
    """入塔して指定tickぶん戦闘し、tickレスポンスを返す"""
    assert api.post(
        "/api/tower/select",
        json={"towerId": "goblin_tower", "targetFloor": 1, "mode": "auto_repeat"},
    ).status_code == 200
    rewind(guest_player, ticks * TICK_INTERVAL_SECONDS)
    res = api.post("/api/battle/tick")
    assert res.status_code == 200
    return res.json()


class TestScenario05装備ドロップから装着まで:
    """必須シナリオ #5: ドロップ〜装備〜ステータス計算の連結"""

    def test_ドロップした装備を装着するとステータスへ反映され解除で戻る(
        self, api, guest, guest_player, rewind, fixed_rng, always_drop
    ):
        drops = _farm(api, guest_player, rewind)["equipmentDrops"]
        assert drops, "敵撃破時に装備がドロップすること"

        # ドロップは所持装備一覧へ永続する
        owned = {e["id"] for e in api.get("/api/equipment/list").json()}
        assert {d["id"] for d in drops} <= owned

        # HP補正を持つ装備でステータス反映を確認する
        target = next(d for d in drops if d["statHp"])
        character = api.get("/api/game/state").json()["characters"][0]
        base_max_hp = character["maxHp"]
        assert character["effectiveMaxHp"] == base_max_hp  # 未装備なら素のまま

        res = api.post(
            "/api/equipment/equip",
            json={
                "characterId": character["id"],
                "slot": target["slot"],
                "equipmentId": target["id"],
            },
        )
        assert res.status_code == 200

        state = api.get("/api/game/state").json()
        assert state["equipped"][target["slot"]] == target["id"]
        assert state["characters"][0]["effectiveMaxHp"] == base_max_hp + target["statHp"]

        # 解除すると実効ステータスが素の値へ戻る
        res = api.post(
            "/api/equipment/equip",
            json={"characterId": character["id"], "slot": target["slot"]},
        )
        assert res.status_code == 200

        state = api.get("/api/game/state").json()
        assert state["equipped"][target["slot"]] is None
        assert state["characters"][0]["effectiveMaxHp"] == base_max_hp

    def test_装着中とロック中の装備は売却されない(
        self, api, guest, guest_player, rewind, fixed_rng, always_drop
    ):
        drops = _farm(api, guest_player, rewind)["equipmentDrops"]
        assert len(drops) >= 2, "売却シナリオには2件以上のドロップが必要"

        character = api.get("/api/game/state").json()["characters"][0]
        equipped_item, locked_item = drops[0], drops[1]

        # 1件を装着、もう1件をロック
        assert api.post(
            "/api/equipment/equip",
            json={
                "characterId": character["id"],
                "slot": equipped_item["slot"],
                "equipmentId": equipped_item["id"],
            },
        ).status_code == 200
        res = api.post("/api/equipment/lock", json={"equipmentId": locked_item["id"]})
        assert res.status_code == 200
        assert res.json()["locked"] is True

        gold_before = api.get("/api/game/state").json()["player"]["gold"]

        # どちらも売却対象にならない
        res = api.post(
            "/api/equipment/sell",
            json={"equipmentIds": [equipped_item["id"], locked_item["id"]]},
        )
        assert res.status_code == 200
        assert res.json() == {"goldEarned": 0, "itemsSold": 0}

        owned = {e["id"] for e in api.get("/api/equipment/list").json()}
        assert {equipped_item["id"], locked_item["id"]} <= owned
        assert api.get("/api/game/state").json()["player"]["gold"] == gold_before

    def test_売却すると一覧から消えてゴールドが増える(
        self, api, guest, guest_player, rewind, fixed_rng, always_drop
    ):
        drops = _farm(api, guest_player, rewind)["equipmentDrops"]
        target = drops[0]
        gold_before = api.get("/api/game/state").json()["player"]["gold"]

        res = api.post("/api/equipment/sell", json={"equipmentIds": [target["id"]]})
        assert res.status_code == 200
        sold = res.json()
        assert sold["itemsSold"] == 1
        assert sold["goldEarned"] > 0

        owned = {e["id"] for e in api.get("/api/equipment/list").json()}
        assert target["id"] not in owned
        assert api.get("/api/game/state").json()["player"]["gold"] == gold_before + sold["goldEarned"]

    def test_オートセル設定を超えないレアリティは自動売却されドロップに残らない(
        self, api, guest, guest_player, rewind, fixed_rng, always_drop
    ):
        assert api.put("/api/game/settings", json={"autoSellRarity": "legendary"}).status_code == 200

        body = _farm(api, guest_player, rewind)
        # 最上位レアリティまで自動売却対象にしたため、所持装備は増えず売却額だけが入る
        assert body["equipmentDrops"] == []
        assert body["equipmentAutoSold"], "自動売却された装備が返ること"
        assert api.get("/api/equipment/list").json() == []
        assert api.get("/api/game/state").json()["player"]["gold"] > 0
