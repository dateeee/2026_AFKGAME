"""結合テスト: tick進行 → 戦闘ログ取得 / オフライン復帰 → 一括計算

シナリオ導出元: diagrams/api_sequence/core.md §2・§3、docs/tech/tech_offline.md
不変条件: サーバー権威（計算結果と保存後の状態が一致する）、60秒固定tick。
"""

import random

import pytest

from app.config import MAX_OFFLINE_HOURS, TICK_INTERVAL_SECONDS
from app.models.player import Player
from tests.integration.conftest import INTEGRATION_SEED

pytestmark = pytest.mark.integration

MAX_OFFLINE_TICKS = MAX_OFFLINE_HOURS * 3600 // TICK_INTERVAL_SECONDS


def _enter_tower(api) -> None:
    res = api.post(
        "/api/tower/select",
        json={"towerId": "goblin_tower", "targetFloor": 1, "mode": "auto_repeat"},
    )
    assert res.status_code == 200


class TestScenario03tick進行と戦闘ログ:
    """必須シナリオ #3: 60秒tickの進行、サーバー権威"""

    def test_入塔後のtickで戦闘が進みログと報酬が返る(
        self, api, guest, guest_player, rewind, fixed_rng
    ):
        _enter_tower(api)
        rewind(guest_player, 10 * TICK_INTERVAL_SECONDS)

        res = api.post("/api/battle/tick")
        assert res.status_code == 200
        body = res.json()

        # 10tick ぶんがまとめて処理される（60秒固定間隔）
        summary = body["offlineSummary"]
        assert summary["processedTicks"] == 10
        assert summary["calcMethod"] == "normal"

        # 戦闘ログが tick 単位の配列で返る
        assert body["battleLogs"]
        log_types = {entry["type"] for tick_logs in body["battleLogs"] for entry in tick_logs}
        assert "encounter" in log_types
        assert "attack" in log_types

        # 報酬が状態へ反映されている
        assert summary["enemiesDefeated"] > 0
        assert body["updatedState"]["player"]["gold"] == summary["totalGold"]
        assert body["updatedState"]["characters"][0]["exp"] > 0

    def test_tickの結果はDBへ保存され再取得しても一致する(
        self, api, guest, guest_player, rewind, fixed_rng
    ):
        """サーバー権威: クライアントは返却値をそのまま表示すればよい"""
        _enter_tower(api)
        rewind(guest_player, 5 * TICK_INTERVAL_SECONDS)

        updated = api.post("/api/battle/tick").json()["updatedState"]
        assert updated == api.get("/api/game/state").json()

    def test_階をクリアすると到達記録と目標階の上限が追従する(
        self, api, guest, guest_player, rewind, fixed_rng
    ):
        _enter_tower(api)
        assert api.get("/api/game/state").json()["player"]["targetFloor"] == 1

        rewind(guest_player, 10 * TICK_INTERVAL_SECONDS)
        state = api.post("/api/battle/tick").json()["updatedState"]

        # 1Fクリアで塔別到達記録が更新され、目標階が上限に追従して +1 される
        assert state["towersCleared"]["goblin_tower"]["highestFloor"] >= 1
        assert state["player"]["targetFloor"] >= 2
        assert state["player"]["currentTowerId"] == "goblin_tower"

    def test_1tick未満の経過では状態が変わらない(self, api, guest):
        _enter_tower(api)
        before = api.get("/api/game/state").json()

        body = api.post("/api/battle/tick").json()
        assert body["battleLogs"] == []
        assert body["offlineSummary"] is None
        assert body["updatedState"] == before

    def test_塔外のtickはHP回復のみで戦闘は起きない(
        self, api, db, guest, guest_player, rewind, fixed_rng
    ):
        character = guest_player.characters[0]
        character.hp = 10
        db.commit()

        rewind(guest_player, 3 * TICK_INTERVAL_SECONDS)
        body = api.post("/api/battle/tick").json()

        log_types = {entry["type"] for tick_logs in body["battleLogs"] for entry in tick_logs}
        assert log_types == {"recovery"}
        assert body["offlineSummary"]["totalGold"] == 0
        assert body["updatedState"]["characters"][0]["hp"] > 10


class TestScenario04オフライン復帰の一括計算:
    """必須シナリオ #4: 経過時間ぶんのtickの一括処理と上限クランプ"""

    def test_長時間の不在は簡易計算でまとめて処理される(
        self, api, guest, guest_player, rewind, fixed_rng
    ):
        _enter_tower(api)
        rewind(guest_player, 6 * 3600)  # 6時間 = 360tick

        body = api.post("/api/battle/tick").json()
        summary = body["offlineSummary"]

        assert summary["processedTicks"] == 360
        assert summary["calcMethod"] == "simplified"  # 100tick超は簡易計算
        assert summary["elapsedSeconds"] >= 6 * 3600
        assert summary["totalGold"] > 0
        assert summary["enemiesDefeated"] > 0

        # 集計値と保存後の所持金が一致する
        assert body["updatedState"]["player"]["gold"] == summary["totalGold"]

    def test_上限時間を超える不在は24時間ぶんでクランプされる(
        self, api, guest, guest_player, rewind, fixed_rng
    ):
        _enter_tower(api)
        rewind(guest_player, 30 * 3600)  # 30時間放置

        summary = api.post("/api/battle/tick").json()["offlineSummary"]

        assert summary["elapsedSeconds"] >= 30 * 3600
        assert summary["processedTicks"] == MAX_OFFLINE_TICKS  # 24時間 = 1440tick

    def test_一括処理と逐次処理でtickの結果が一致する(self, api, db, rewind):
        """ハイブリッドtick制: オンラインのポーリングとオフラインの一括計算で結果が変わらない"""

        def _run(batched: bool) -> dict:
            auth = api.post("/api/auth/guest").json()
            api.headers.update({"Authorization": f"Bearer {auth['accessToken']}"})
            player = db.query(Player).filter_by(user_id=auth["user"]["id"]).one()
            _enter_tower(api)

            random.seed(INTEGRATION_SEED)  # 双方の戦闘を同じ乱数列で回す
            if batched:
                rewind(player, 5 * TICK_INTERVAL_SECONDS)
                api.post("/api/battle/tick")
            else:
                for _ in range(5):
                    rewind(player, TICK_INTERVAL_SECONDS)
                    api.post("/api/battle/tick")

            state = api.get("/api/game/state").json()
            return {
                "gold": state["player"]["gold"],
                "floor": state["player"]["currentFloor"],
                "exp": state["characters"][0]["exp"],
                "hp": state["characters"][0]["hp"],
                "potions": state["potions"],
            }

        try:
            assert _run(batched=True) == _run(batched=False)
        finally:
            random.seed()

    def test_復帰処理の直後に再度tickしても二重加算されない(
        self, api, guest, guest_player, rewind, fixed_rng
    ):
        _enter_tower(api)
        rewind(guest_player, 2 * 3600)

        first = api.post("/api/battle/tick").json()
        gold_after_first = first["updatedState"]["player"]["gold"]

        second = api.post("/api/battle/tick").json()
        assert second["offlineSummary"] is None
        assert second["battleLogs"] == []
        assert second["updatedState"]["player"]["gold"] == gold_after_first
