"""単体テスト: 装備ルーター（/api/equipment/*）

仕様: tech/basic/tech_api.md 装備操作、design/systems/equipment.md「装備スロット（9スロット）」「売却価格」
分岐観点:
  - /list: 所持ゼロ / 所持あり / 他プレイヤーの装備を含めない / 未認証
  - /equip: 正常系（装着・解除）/ サービスの ValueError を400へ変換
  - /sell: 売却成立 / 対象なし
  - /lock: 切替成功 / サービスの ValueError を404（EQUIP_NOT_FOUND）へ変換
"""

import pytest

from app.models.equipment import CharacterEquipSlot, Equipment
from app.models.player import Player
from app.services.equipment_service import create_equip_slots
from tests.helpers import error_message

pytestmark = pytest.mark.unit


@pytest.fixture
def equip_slots(db, character):
    """標準キャラクターに9スロットを用意する"""
    create_equip_slots(character.id, db)
    db.commit()
    return character


@pytest.fixture
def make_equipment(db, player):
    def _make(**overrides) -> Equipment:
        data = {
            "player_id": player.id,
            "base_id": "helm",
            "slot": "head",
            "rarity": "common",
            "level": 1,
            "enhance_level": 0,
            "stat_atk": None,
            "stat_def": None,
            "stat_hp": None,
            "stat_spd": None,
            "lifesteal": None,
            "is_two_handed": False,
            "locked": False,
        }
        data.update(overrides)
        equip = Equipment(**data)
        db.add(equip)
        db.commit()
        return equip

    return _make


class TestListEquipment:
    def test_所持装備が無ければ空配列(self, client):
        res = client.get("/api/equipment/list")
        assert res.status_code == 200
        assert res.json() == []

    def test_所持装備をキャメルケースで返す(self, client, make_equipment):
        equip = make_equipment(
            base_id="greatsword", slot="weapon", rarity="epic", level=12,
            stat_atk=30, lifesteal=0.04, is_two_handed=True, locked=True,
        )
        body = client.get("/api/equipment/list").json()
        assert len(body) == 1
        item = body[0]
        assert item["id"] == equip.id
        assert item["baseId"] == "greatsword"
        assert item["rarity"] == "epic"
        assert item["statAtk"] == 30
        assert item["statDef"] is None
        assert item["isTwoHanded"] is True
        assert item["locked"] is True
        assert "acquiredAt" in item

    def test_他プレイヤーの装備は含まれない(self, client, db, make_equipment):
        make_equipment()
        other = Player(id="other-player", gold=0)
        db.add(other)
        db.flush()
        db.add(Equipment(
            player_id=other.id, base_id="helm", slot="head",
            rarity="rare", level=5, enhance_level=0,
        ))
        db.commit()

        body = client.get("/api/equipment/list").json()
        assert [i["rarity"] for i in body] == ["common"]

    def test_認証なしは401(self, client):
        res = client.get("/api/equipment/list", headers={"Authorization": ""})
        assert res.status_code == 401


class TestEquip:
    def test_装備を装着できる(self, client, db, equip_slots, make_equipment):
        equip = make_equipment()
        res = client.post("/api/equipment/equip", json={
            "characterId": equip_slots.id, "slot": "head", "equipmentId": equip.id,
        })
        assert res.status_code == 200
        assert res.json() == {"status": "ok"}

        slot = db.query(CharacterEquipSlot).filter_by(
            character_id=equip_slots.id, slot="head"
        ).first()
        assert slot.equipment_id == equip.id

    def test_装備を解除できる(self, client, db, equip_slots, make_equipment):
        equip = make_equipment()
        client.post("/api/equipment/equip", json={
            "characterId": equip_slots.id, "slot": "head", "equipmentId": equip.id,
        })
        res = client.post("/api/equipment/equip", json={
            "characterId": equip_slots.id, "slot": "head",
        })
        assert res.status_code == 200

        slot = db.query(CharacterEquipSlot).filter_by(
            character_id=equip_slots.id, slot="head"
        ).first()
        assert slot.equipment_id is None

    def test_存在しないキャラクターは400(self, client):
        res = client.post("/api/equipment/equip", json={
            "characterId": "no-such-character", "slot": "head",
        })
        assert res.status_code == 400
        assert error_message(res) == "キャラクターが見つかりません"

    def test_スロットが一致しない装備は400(self, client, equip_slots, make_equipment):
        equip = make_equipment()  # slot=head
        res = client.post("/api/equipment/equip", json={
            "characterId": equip_slots.id, "slot": "weapon", "equipmentId": equip.id,
        })
        assert res.status_code == 400
        assert error_message(res) == "この装備はスロット 'weapon' に装着できません"


class TestSell:
    def test_売却でゴールドが増える(self, client, db, player, make_equipment):
        a = make_equipment(rarity="common", level=10)  # 50G
        b = make_equipment(rarity="rare", level=5)     # 40G
        res = client.post("/api/equipment/sell", json={"equipmentIds": [a.id, b.id]})

        assert res.status_code == 200
        assert res.json() == {"goldEarned": 90, "itemsSold": 2}
        db.refresh(player)
        assert player.gold == 1090
        assert db.query(Equipment).count() == 0

    def test_売却対象が無ければ0件を返す(self, client, db, player):
        res = client.post("/api/equipment/sell", json={"equipmentIds": []})
        assert res.status_code == 200
        assert res.json() == {"goldEarned": 0, "itemsSold": 0}
        db.refresh(player)
        assert player.gold == 1000


class TestLock:
    def test_ロック状態を切り替えられる(self, client, db, make_equipment):
        equip = make_equipment(locked=False)

        assert client.post("/api/equipment/lock", json={"equipmentId": equip.id}).json() == {
            "locked": True
        }
        assert client.post("/api/equipment/lock", json={"equipmentId": equip.id}).json() == {
            "locked": False
        }
        db.refresh(equip)
        assert equip.locked is False

    def test_存在しない装備は404(self, client):
        res = client.post("/api/equipment/lock", json={"equipmentId": "no-such-equipment"})
        assert res.status_code == 404
        assert error_message(res) == "装備が見つかりません"
