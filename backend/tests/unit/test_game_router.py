"""単体テスト: ゲーム状態ルーター（routers/game.py）

仕様: tech/tech_api.md `/api/game/state` `/api/game/settings`
分岐観点:
  - 設定レコードの有無（無ければ新規作成して更新）
  - 各設定項目は「指定した項目のみ」更新される（未指定 = None は素通り）
  - auto_sell_rarity のみ null 指定で明示リセットできる（model_fields_set 判定）
"""

import pytest

from app.models.player import PlayerSettings

pytestmark = pytest.mark.unit


class TestGameState:
    def test_現在のゲーム状態を返す(self, client):
        res = client.get("/api/game/state")
        assert res.status_code == 200
        body = res.json()
        assert body["player"]["gold"] == 1000
        assert body["potions"] == {"hp_potion": 5}
        assert len(body["characters"]) == 1
        assert body["currentEnemy"] is None
        assert body["settings"]["potionThreshold"] == 0.3

    def test_認証なしは401(self, client):
        res = client.get("/api/game/state", headers={"Authorization": ""})
        assert res.status_code == 401


class TestUpdateSettings:
    def test_全項目を更新できる(self, client, db, player):
        res = client.put(
            "/api/game/settings",
            json={
                "potionThreshold": 0.5,
                "battleLogCount": 30,
                "toastEnabled": False,
                "autoSellRarity": "rare",
            },
        )
        assert res.status_code == 200
        body = res.json()
        assert body["potionThreshold"] == 0.5
        assert body["battleLogCount"] == 30
        assert body["toastEnabled"] is False
        assert body["autoSellRarity"] == "rare"
        assert player.settings.potion_threshold == 0.5

    def test_未指定の項目は変更されない(self, client):
        res = client.put("/api/game/settings", json={})
        assert res.status_code == 200
        body = res.json()
        assert body["potionThreshold"] == 0.3
        assert body["battleLogCount"] == 50
        assert body["toastEnabled"] is True

    def test_autoSellRarityはnull指定でリセットされる(self, client):
        client.put("/api/game/settings", json={"autoSellRarity": "epic"})
        res = client.put("/api/game/settings", json={"autoSellRarity": None})
        assert res.status_code == 200
        assert res.json()["autoSellRarity"] is None

    def test_設定レコードが無ければ新規作成される(self, client, db, player):
        db.query(PlayerSettings).filter_by(player_id=player.id).delete()
        db.commit()
        db.expire(player)
        res = client.put("/api/game/settings", json={"potionThreshold": 0.7})
        assert res.status_code == 200
        assert res.json()["potionThreshold"] == 0.7
        assert db.query(PlayerSettings).filter_by(player_id=player.id).count() == 1
