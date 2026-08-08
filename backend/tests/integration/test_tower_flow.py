"""結合テスト: 塔選択 → 目標階設定

シナリオ導出元: docs/diagrams/api_sequence/gameplay.md §4、docs/tech/basic/tech_api.md 操作系
検証内容: 塔別クリア記録の独立、目標階上限、入塔状態の遷移。
"""

import pytest

from app.models.player import TowerClearRecord

pytestmark = pytest.mark.integration


def _select(api, tower_id: str, target_floor: int, mode: str = "auto_repeat"):
    return api.post(
        "/api/tower/select",
        json={"towerId": tower_id, "targetFloor": target_floor, "mode": mode},
    )


class TestScenario02塔選択と目標階設定:
    """必須シナリオ #2: 塔別クリア記録の独立、上限追従"""

    def test_一覧取得から入塔しリタイアするまで通しで成立する(self, api, guest):
        towers = {t["id"]: t for t in api.get("/api/tower/list").json()}
        assert towers["goblin_tower"]["unlocked"] is True
        assert towers["goblin_tower"]["totalFloors"] == 20
        assert towers["forest_tower"]["unlocked"] is False
        assert towers["forest_tower"]["unlockTowerId"] == "goblin_tower"
        # 未挑戦の塔は1Fのみ選択できる
        assert towers["goblin_tower"]["targetFloorCap"] == 1

        # 未解放の塔は 403、上限超えの目標階は 400
        assert _select(api, "forest_tower", 1).status_code == 403
        assert _select(api, "goblin_tower", 2).status_code == 400

        # 入塔すると現在地と目標階がゲーム状態へ反映される
        assert _select(api, "goblin_tower", 1).status_code == 200
        player = api.get("/api/game/state").json()["player"]
        assert player["currentTowerId"] == "goblin_tower"
        assert player["currentFloor"] == 1
        assert player["targetFloor"] == 1
        assert player["towerMode"] == "auto_repeat"

        # 入塔中の再選択は 400
        assert _select(api, "goblin_tower", 1).status_code == 400

        # リタイアで塔から出る（獲得済み報酬は保持）
        assert api.post("/api/tower/retire").status_code == 200
        player = api.get("/api/game/state").json()["player"]
        assert player["currentTowerId"] is None
        assert player["currentFloor"] is None

        # 塔外でのリタイアは 400
        assert api.post("/api/tower/retire").status_code == 400

    def test_目標階の上限は塔ごとに独立している(self, api, db, guest, guest_player):
        # ゴブリンの塔だけ5Fまで到達済み・クリア済みにする
        db.add(
            TowerClearRecord(
                player_id=guest_player.id,
                tower_id="goblin_tower",
                highest_floor=5,
                cleared=True,
            )
        )
        db.commit()

        towers = {t["id"]: t for t in api.get("/api/tower/list").json()}
        assert towers["goblin_tower"]["highestFloor"] == 5
        assert towers["goblin_tower"]["targetFloorCap"] == 6
        # クリアで次の塔が解放される
        assert towers["forest_tower"]["unlocked"] is True
        # 解放されても森の塔の到達記録は独立して未挑戦のまま
        assert towers["forest_tower"]["highestFloor"] == 0
        assert towers["forest_tower"]["targetFloorCap"] == 1

        assert _select(api, "forest_tower", 2).status_code == 400
        assert _select(api, "forest_tower", 1).status_code == 200

        # ゲーム状態にも塔別のクリア記録が返る
        cleared = api.get("/api/game/state").json()["towersCleared"]
        assert cleared["goblin_tower"] == {"cleared": True, "highestFloor": 5}
        assert "forest_tower" not in cleared

    def test_進行中でもモードと撤退条件を変更できる(self, api, guest):
        assert _select(api, "goblin_tower", 1).status_code == 200

        assert api.put("/api/tower/mode", json={"mode": "stop_on_clear"}).status_code == 200
        assert api.put("/api/tower/retreat-conditions", json={"hpThreshold": 0.5}).status_code == 200

        player = api.get("/api/game/state").json()["player"]
        assert player["towerMode"] == "stop_on_clear"
        assert player["hpThreshold"] == 0.5
        # 変更しても入塔状態は維持される
        assert player["currentTowerId"] == "goblin_tower"
