"""単体テスト: 塔ルーター（/api/tower/*）

仕様: tech/tech_api.md 塔操作、design/systems/battle.md「目標階設定」
"""

import pytest

from tests.helpers import error_code

pytestmark = pytest.mark.unit


class TestListTowers:
    def test_未挑戦時は上限1階でロック状態を返す(self, client):
        res = client.get("/api/tower/list")
        assert res.status_code == 200
        towers = {t["id"]: t for t in res.json()}

        goblin = towers["goblin_tower"]
        assert goblin["unlocked"] is True  # 解放条件なし
        assert goblin["cleared"] is False
        assert goblin["highestFloor"] == 0
        assert goblin["targetFloorCap"] == 1

        assert towers["forest_tower"]["unlocked"] is False  # ゴブリンの塔クリアが条件

    def test_到達記録が上限に反映される(self, client, tower_record):
        tower_record("goblin_tower", highest_floor=12)
        towers = {t["id"]: t for t in client.get("/api/tower/list").json()}
        assert towers["goblin_tower"]["highestFloor"] == 12
        assert towers["goblin_tower"]["targetFloorCap"] == 13
        assert towers["forest_tower"]["targetFloorCap"] == 1  # 塔ごとに独立

    def test_総階数を超えて上限が伸びない(self, client, tower_record):
        tower_record("goblin_tower", highest_floor=20, cleared=True)
        towers = {t["id"]: t for t in client.get("/api/tower/list").json()}
        assert towers["goblin_tower"]["targetFloorCap"] == 20 == towers["goblin_tower"]["totalFloors"]
        assert towers["forest_tower"]["unlocked"] is True  # クリアで解放


class TestSelectTower:
    def _select(self, client, tower_id="goblin_tower", target_floor=1, mode="auto_repeat"):
        return client.post(
            "/api/tower/select",
            json={"towerId": tower_id, "targetFloor": target_floor, "mode": mode},
        )

    def test_未挑戦の塔は1階のみ選択できる(self, client):
        assert self._select(client, target_floor=1).status_code == 200

    def test_未挑戦の塔で2階を指定すると400(self, client):
        res = self._select(client, target_floor=2)
        assert res.status_code == 400
        assert error_code(res) == "TOWER_INVALID_FLOOR"

    def test_到達済み最高階プラス1まで選択できる(self, client, tower_record):
        tower_record("goblin_tower", highest_floor=5)
        assert self._select(client, target_floor=6).status_code == 200

    def test_到達済み最高階プラス2は400(self, client, tower_record):
        tower_record("goblin_tower", highest_floor=5)
        assert self._select(client, target_floor=7).status_code == 400

    def test_最上階到達後は総階数まで選択できる(self, client, tower_record):
        tower_record("goblin_tower", highest_floor=20, cleared=True)
        assert self._select(client, target_floor=20).status_code == 200

    def test_総階数を超える指定は400(self, client, tower_record):
        tower_record("goblin_tower", highest_floor=20, cleared=True)
        assert self._select(client, target_floor=21).status_code == 400

    def test_0階以下は422(self, client):
        # 下限はスキーマ検証（tech_api.md §エラー: 型・範囲違反は 422）
        assert self._select(client, target_floor=0).status_code == 422

    def test_他の塔の到達階は上限に影響しない(self, client, tower_record):
        tower_record("goblin_tower", highest_floor=20, cleared=True)
        # 森の塔は未挑戦なので上限は1階のまま
        assert self._select(client, "forest_tower", target_floor=2).status_code == 400
        assert self._select(client, "forest_tower", target_floor=1).status_code == 200

    def test_未解放の塔は403(self, client):
        assert self._select(client, "forest_tower").status_code == 403

    def test_存在しない塔は404(self, client):
        assert self._select(client, "no_such_tower").status_code == 404

    def test_入塔中の再選択は400(self, client):
        assert self._select(client).status_code == 200
        res = self._select(client)
        assert res.status_code == 400
        assert error_code(res) == "TOWER_ALREADY_IN_TOWER"

    def test_不正なモードは422(self, client):
        res = self._select(client, mode="invalid_mode")
        assert res.status_code == 422

    def test_入塔時に進行状態が初期化される(self, client, db, player):
        assert self._select(client, target_floor=1, mode="stop_on_clear").status_code == 200
        db.refresh(player)
        assert player.current_tower_id == "goblin_tower"
        assert player.current_floor == 1
        assert player.target_floor == 1
        assert player.tower_mode == "stop_on_clear"
        assert player.current_enemy_id is None
        assert player.run_gold == 0

    def test_認証なしは401(self, client):
        res = client.post(
            "/api/tower/select",
            json={"towerId": "goblin_tower", "targetFloor": 1},
            headers={"Authorization": ""},
        )
        assert res.status_code == 401


class TestRetireTower:
    def test_塔外でのリタイアは400(self, client):
        res = client.post("/api/tower/retire")
        assert res.status_code == 400
        assert error_code(res) == "TOWER_NOT_IN_TOWER"

    def test_リタイアで進行状態がクリアされる(self, client, db, player):
        client.post("/api/tower/select", json={"towerId": "goblin_tower", "targetFloor": 1})
        assert client.post("/api/tower/retire").status_code == 200
        db.refresh(player)
        assert player.current_tower_id is None
        assert player.current_floor is None
        assert player.target_floor is None


class TestTowerMode:
    @pytest.mark.parametrize("mode", ["auto_repeat", "stop_on_clear"])
    def test_有効なモードを設定できる(self, client, db, player, mode):
        res = client.put("/api/tower/mode", json={"mode": mode})
        assert res.status_code == 200
        db.refresh(player)
        assert player.tower_mode == mode

    def test_不正なモードは422(self, client):
        assert client.put("/api/tower/mode", json={"mode": "turbo"}).status_code == 422


class TestRetreatConditions:
    @pytest.mark.parametrize("threshold", [0.0, 0.3, 1.0])
    def test_0から1の閾値を設定できる(self, client, db, player, threshold):
        res = client.put("/api/tower/retreat-conditions", json={"hpThreshold": threshold})
        assert res.status_code == 200
        db.refresh(player)
        assert player.hp_threshold == threshold

    @pytest.mark.parametrize("threshold", [-0.1, 1.1])
    def test_範囲外の閾値は422(self, client, threshold):
        res = client.put("/api/tower/retreat-conditions", json={"hpThreshold": threshold})
        assert res.status_code == 422
