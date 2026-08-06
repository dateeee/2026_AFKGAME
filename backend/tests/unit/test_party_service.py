"""単体テスト: パーティ編成・キャラクター獲得・SP獲得（services/party_service.py, routers/party.py）

仕様: tech/detail/tech_party.md §1（パーティ編成変更）・§2（キャラクター獲得）・§6（SP獲得）、
      data/master/character.md §7.1（Phase 3 確定入手キャラの入手条件）
分岐観点:
  - 編成変更は「塔外のみ許可」「1〜4件」「重複なし」「全ID所持」の4関門
  - キャラ獲得は 未所持/既所持/条件なしの階 で分かれ、付与してもパーティは変えない
  - オフライン簡略計算の周回では初回クリアのみ付与する
  - SPはレベルアップ回数ぶんだけ増える

エラーコードの正: tech_party.md §7。リクエストは camelCase（CamelModel）。

本工程で定義する実装の表層:
  models/party.py PartyMember、party_service.grant_floor_character・
  grant_characters_for_floors・grant_skill_points、PUT /api/party/edit
"""

import pytest

from app.config import INITIAL_CHARACTER
from app.models.character import Character
from app.models.party import PartyMember
from app.services import party_service

from tests.helpers import error_code

pytestmark = pytest.mark.unit


@pytest.fixture
def roster(db, player, character):
    """所持キャラ4体（うち1体は conftest の初期キャラ）。編成の件数分岐に使う"""
    extra = [
        Character(player_id=player.id, **{**INITIAL_CHARACTER, "name": name, "type": ctype})
        for name, ctype in (("アカネ", "magic"), ("シロナ", "holy"), ("ハヤテ", "agile"))
    ]
    db.add_all(extra)
    db.commit()
    return [character, *extra]


def _member_ids(db, player):
    rows = db.query(PartyMember).filter_by(player_id=player.id).order_by(PartyMember.slot_index).all()
    return [r.character_id for r in rows]


# ══════════════════════════════════════════════════════════════════
# §1 パーティ編成変更（PUT /api/party/edit）
# ══════════════════════════════════════════════════════════════════


class TestPartyEdit:
    def test_塔外なら編成を更新する(self, db, player, client, roster):
        """分岐: tech_party.md §1 #1 — IDLE（塔外）は編集できる"""
        player.current_tower_id = None
        db.commit()
        res = client.put("/api/party/edit", json={"memberIds": [roster[1].id, roster[0].id]})
        assert res.status_code == 200
        assert _member_ids(db, player) == [roster[1].id, roster[0].id]  # 配列順が表示順

    def test_探索中は編成を変更できない(self, db, player, client, roster):
        """分岐: tech_party.md §1 #2 — 400 PARTY_LOCKED_IN_TOWER。編成は変更しない"""
        player.current_tower_id = "goblin_tower"
        player.current_floor = 3
        db.commit()
        res = client.put("/api/party/edit", json={"memberIds": [roster[0].id]})
        assert res.status_code == 400
        assert error_code(res) == "PARTY_LOCKED_IN_TOWER"
        assert _member_ids(db, player) == []

    @pytest.mark.parametrize("count", [1, 4])  # 下限ちょうど / 上限ちょうど
    def test_件数が1件から4件なら受理する(self, db, player, client, roster, count):
        """分岐: tech_party.md §1 #3 — 受理される件数の両端"""
        res = client.put("/api/party/edit", json={"memberIds": [c.id for c in roster[:count]]})
        assert res.status_code == 200
        assert len(_member_ids(db, player)) == count

    @pytest.mark.parametrize("count", [0, 5])  # 下限割れ / 上限超過
    def test_件数が0件または5件以上は422になる(self, db, player, client, roster, count):
        """分岐: tech_party.md §1 #4 — スキーマ検証。編成は変更しない"""
        ids = [c.id for c in roster[:count]] if count <= 4 else [c.id for c in roster] + [roster[0].id]
        res = client.put("/api/party/edit", json={"memberIds": ids})
        assert res.status_code == 422
        assert _member_ids(db, player) == []

    def test_配列内に同一キャラIDがあれば422になる(self, db, player, client, roster):
        """分岐: tech_party.md §1 #5 — PARTY_MEMBER_DUPLICATED"""
        res = client.put("/api/party/edit", json={"memberIds": [roster[0].id, roster[0].id]})
        assert res.status_code == 422
        assert error_code(res) == "PARTY_MEMBER_DUPLICATED"
        assert _member_ids(db, player) == []

    def test_重複がなければ受理する(self, db, player, client, roster):
        """分岐: tech_party.md §1 #6 — 全員が別IDなら通す"""
        res = client.put("/api/party/edit", json={"memberIds": [c.id for c in roster]})
        assert res.status_code == 200
        assert len(set(_member_ids(db, player))) == 4

    def test_未所持のキャラIDを含むと422になる(self, db, player, client, roster):
        """分岐: tech_party.md §1 #7 — PARTY_MEMBER_NOT_OWNED"""
        res = client.put("/api/party/edit", json={"memberIds": [roster[0].id, "not-owned-id"]})
        assert res.status_code == 422
        assert error_code(res) == "PARTY_MEMBER_NOT_OWNED"
        assert _member_ids(db, player) == []

    def test_全IDが所持キャラなら受理して全置換する(self, db, player, client, roster):
        """分岐: tech_party.md §1 #8 — 既存の編成は残さず置き換える"""
        client.put("/api/party/edit", json={"memberIds": [c.id for c in roster]})
        res = client.put("/api/party/edit", json={"memberIds": [roster[2].id]})
        assert res.status_code == 200
        assert _member_ids(db, player) == [roster[2].id]


# ══════════════════════════════════════════════════════════════════
# §2 キャラクター獲得（塔クリア報酬・tick処理内）
# ══════════════════════════════════════════════════════════════════


class TestCharacterAcquisition:
    def test_対象階をクリアし未所持ならキャラを付与する(self, db, player):
        """分岐: tech_party.md §2 #1 — 森の塔15F → アカネ。LV1・SP0・スキル未習得"""
        joined = party_service.grant_floor_character(player, "forest_tower", 15, db)
        assert joined is not None
        assert (joined.level, joined.exp, joined.skill_points) == (1, 0, 0)
        assert joined.hp == joined.max_hp

    def test_既所持なら付与しない(self, db, player):
        """分岐: tech_party.md §2 #2 — 周回・再クリアでは重複付与しない"""
        party_service.grant_floor_character(player, "forest_tower", 15, db)
        before = db.query(Character).filter_by(player_id=player.id).count()
        assert party_service.grant_floor_character(player, "forest_tower", 15, db) is None
        assert db.query(Character).filter_by(player_id=player.id).count() == before

    def test_入手条件のない階では判定しない(self, db, player):
        """分岐: tech_party.md §2 #3 — 森の塔14Fに入手条件はない"""
        before = db.query(Character).filter_by(player_id=player.id).count()
        assert party_service.grant_floor_character(player, "forest_tower", 14, db) is None
        assert db.query(Character).filter_by(player_id=player.id).count() == before

    def test_付与してもパーティは変更しない(self, db, player, character):
        """分岐: tech_party.md §2 #4 — 控えとして加入する（編成はプレイヤー操作）"""
        db.add(PartyMember(player_id=player.id, slot_index=0, character_id=character.id))
        db.commit()
        party_service.grant_floor_character(player, "forest_tower", 15, db)
        assert _member_ids(db, player) == [character.id]

    def test_オフライン簡略計算で初めてクリアした階のキャラを付与する(self, db, player):
        """分岐: tech_party.md §2 #5 — 付与し、オフラインサマリーに含める"""
        joined = party_service.grant_characters_for_floors(player, "forest_tower", [14, 15], db)
        assert [c.name for c in joined] == ["アカネ"]

    def test_オフラインの周回で再クリアしても付与しない(self, db, player):
        """分岐: tech_party.md §2 #6 — 2周目以降は対象外"""
        joined = party_service.grant_characters_for_floors(player, "forest_tower", [15, 15, 15], db)
        assert len(joined) == 1


# ══════════════════════════════════════════════════════════════════
# §6 SP獲得（戦闘処理・オフライン簡略計算内）
# ══════════════════════════════════════════════════════════════════


class TestSkillPointGain:
    def test_レベルアップ1回でSPが1増える(self, character):
        """分岐: tech_party.md §6 #1 — レベルアップ1回につき +1"""
        character.skill_points = 2
        party_service.grant_skill_points(character, 1)
        assert character.skill_points == 3

    def test_同一tickの複数レベルアップは上がった数だけ加算する(self, character):
        """分岐: tech_party.md §6 #2 — 3段上がれば +3"""
        character.skill_points = 2
        party_service.grant_skill_points(character, 3)
        assert character.skill_points == 5

    def test_レベルアップがなければSPは変化しない(self, character):
        """分岐: tech_party.md §6 #3 — 0回は加算しない"""
        character.skill_points = 2
        party_service.grant_skill_points(character, 0)
        assert character.skill_points == 2
