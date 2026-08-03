"""単体テスト: 戦闘ルーター（/api/battle/tick）

仕様: tech/tech_api.md（tick API）、tech/tech_offline.md（オフライン一括計算・簡易計算）
時刻依存のため last_tick_at を直接操作し、process_tick は差し替えて tick 数と結果を固定する。
"""

from datetime import datetime, timedelta, timezone

import pytest

from app.config import MAX_LOG_PER_RESPONSE, MAX_OFFLINE_HOURS, TICK_INTERVAL_SECONDS
from app.models.character import Character
from app.models.equipment import Equipment
from app.services import battle_service
from app.services.battle_service import TickResult

pytestmark = pytest.mark.unit


def _set_elapsed(db, player, seconds: float, *, aware: bool = False) -> None:
    """last_tick_at を「seconds 秒前」に設定する（aware=True でタイムゾーン付き）"""
    last = datetime.now(timezone.utc) - timedelta(seconds=seconds)
    player.last_tick_at = last if aware else last.replace(tzinfo=None)
    db.commit()


@pytest.fixture
def fake_tick(monkeypatch):
    """process_tick を差し替え、1tickあたりの結果を固定するファクトリ"""

    def _install(logs_per_tick: int = 1, **counters):
        state = {"calls": 0}

        def _fake(player, character, db):
            state["calls"] += 1
            result = TickResult(**counters)
            result.battle_logs = [
                [{"type": "attack", "tick": state["calls"]}] for _ in range(logs_per_tick)
            ]
            return result

        monkeypatch.setattr(battle_service, "process_tick", _fake)
        return state

    return _install


class TestTickAuth:
    def test_認証なしは401(self, client):
        res = client.post("/api/battle/tick", headers={"Authorization": ""})
        assert res.status_code == 401


class TestTickNoop:
    def test_1tick未満の経過では何も処理しない(self, db, player, client, fake_tick):
        state = fake_tick()
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS - 10)

        res = client.post("/api/battle/tick")

        assert res.status_code == 200
        body = res.json()
        assert body["battleLogs"] == []
        assert body["offlineSummary"] is None
        assert body["updatedState"]["player"]["gold"] == 1000
        assert state["calls"] == 0  # tick処理そのものが走らない

    def test_キャラクター不在なら処理しない(self, db, player, client, fake_tick):
        state = fake_tick()
        db.query(Character).filter_by(player_id=player.id).delete()
        db.commit()
        db.expire_all()
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS * 5)

        res = client.post("/api/battle/tick")

        assert res.status_code == 200
        assert res.json()["battleLogs"] == []
        assert res.json()["updatedState"]["characters"] == []
        assert state["calls"] == 0


class TestTickNormalCalc:
    def test_経過tick数だけフルシミュレーションする(self, db, player, client, fake_tick):
        state = fake_tick(total_gold=7, total_exp=3, enemies_defeated=1, floors_cleared=1)
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS * 3 + 5)

        res = client.post("/api/battle/tick")

        body = res.json()
        assert state["calls"] == 3
        assert len(body["battleLogs"]) == 3
        summary = body["offlineSummary"]
        assert summary["calcMethod"] == "normal"
        assert summary["processedTicks"] == 3
        assert (summary["totalGold"], summary["totalExp"]) == (21, 9)
        assert (summary["enemiesDefeated"], summary["floorsCleared"]) == (3, 3)
        assert summary["elapsedSeconds"] == TICK_INTERVAL_SECONDS * 3 + 5

    def test_タイムゾーン付きの最終tick時刻も扱える(self, db, player, client, fake_tick):
        state = fake_tick()
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS * 2 + 5, aware=True)

        res = client.post("/api/battle/tick")

        assert state["calls"] == 2
        assert res.json()["offlineSummary"]["processedTicks"] == 2

    def test_1tickだけならオフライン要約を返さない(self, db, player, client, fake_tick):
        fake_tick()
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS + 10)

        body = client.post("/api/battle/tick").json()

        assert len(body["battleLogs"]) == 1
        assert body["offlineSummary"] is None

    def test_最終tick時刻が更新される(self, db, player, client, fake_tick):
        fake_tick()
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS * 2)
        before = player.last_tick_at

        client.post("/api/battle/tick")

        assert player.last_tick_at > before.replace(tzinfo=timezone.utc)


class TestTickLogLimit:
    def test_上限を超えたログは新しいものだけ返す(self, db, player, client, fake_tick):
        ticks = MAX_LOG_PER_RESPONSE + 10
        fake_tick()
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS * ticks + 30)

        logs = client.post("/api/battle/tick").json()["battleLogs"]

        assert len(logs) == MAX_LOG_PER_RESPONSE
        assert logs[0][0]["tick"] == ticks - MAX_LOG_PER_RESPONSE + 1  # 古いログが落ちている
        assert logs[-1][0]["tick"] == ticks

    def test_上限以下のログはそのまま返す(self, db, player, client, fake_tick):
        fake_tick()
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS * 4)

        logs = client.post("/api/battle/tick").json()["battleLogs"]

        assert len(logs) == 4


class TestTickSimplifiedCalc:
    def test_閾値超過はサンプル平均で外挿する(self, db, player, client, fake_tick):
        state = fake_tick(total_gold=10, total_exp=50, enemies_defeated=1, potions_used=1, floors_cleared=1)
        ticks = 180  # FAST_CALC_THRESHOLD(100) 超過
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS * ticks + 10)

        summary = client.post("/api/battle/tick").json()["offlineSummary"]

        assert state["calls"] == 10  # サンプルは10tickのみ
        assert summary["calcMethod"] == "simplified"
        assert summary["processedTicks"] == ticks
        # サンプル10tick分 × 倍率(170/10 = 17)
        assert summary["totalGold"] == 100 + 100 * 17
        assert summary["totalExp"] == 500 + 500 * 17
        assert summary["enemiesDefeated"] == 10 + 10 * 17
        assert summary["potionsUsed"] == 10 + 10 * 17
        assert summary["floorsCleared"] == 10 + 10 * 17
        assert summary["levelsGained"] > 0  # 外挿EXPでレベルアップする
        assert player.gold == 1000 + 100 * 17

    def test_残tickがなければ外挿しない(self, db, player, client, fake_tick, monkeypatch):
        monkeypatch.setattr(battle_service, "FAST_CALC_THRESHOLD", 2)
        state = fake_tick(total_gold=10)
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS * 5 + 5)

        summary = client.post("/api/battle/tick").json()["offlineSummary"]

        assert state["calls"] == 5  # サンプル数 == 全tick数 → 倍率計算なし
        assert summary["calcMethod"] == "simplified"
        assert summary["processedTicks"] == 5
        assert summary["totalGold"] == 50
        assert player.gold == 1000  # 外挿による加算なし

    def test_オフライン上限時間で頭打ちになる(self, db, player, client, fake_tick):
        fake_tick()
        _set_elapsed(db, player, (MAX_OFFLINE_HOURS + 24) * 3600)

        summary = client.post("/api/battle/tick").json()["offlineSummary"]

        assert summary["processedTicks"] == MAX_OFFLINE_HOURS * 3600 // TICK_INTERVAL_SECONDS


class TestTickEquipmentResult:
    def test_ドロップと自動売却を返す(self, db, player, client, fake_tick):
        equip = Equipment(
            player_id=player.id, base_id="iron_sword", slot="weapon", rarity="rare",
            level=3, enhance_level=0, stat_atk=12, is_two_handed=False, locked=False,
        )
        db.add(equip)
        db.commit()
        sold = {"name": "折れた剣", "rarity": "common", "gold": 12}
        fake_tick(equipment_drops=[equip], equipment_auto_sold=[sold])
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS + 10)

        body = client.post("/api/battle/tick").json()

        assert [e["baseId"] for e in body["equipmentDrops"]] == ["iron_sword"]
        assert body["equipmentDrops"][0]["statAtk"] == 12
        assert body["equipmentAutoSold"] == [sold]

    def test_ドロップがなければ空配列(self, db, player, client, fake_tick):
        fake_tick()
        _set_elapsed(db, player, TICK_INTERVAL_SECONDS + 10)

        body = client.post("/api/battle/tick").json()

        assert body["equipmentDrops"] == []
        assert body["equipmentAutoSold"] == []
