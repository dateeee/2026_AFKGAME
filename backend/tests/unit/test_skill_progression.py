"""単体テスト: スキル習得・セット変更・リセット（services/party_service.py, routers/skill.py）

仕様: tech/detail/tech_party.md §3（スキル習得）・§4（アクティブスキルセット変更）・§5（スキルリセット）、
      data/skills/（ID・必要SP・前提スキル・種別）、tech/detail/tech_party.md §7（エラーコード）
分岐観点:
  - 習得は キャラ所持 → スキルID実在 → 未習得 → 前提充足 → SP充足 の順に関門がある
  - セット変更は 件数・重複・種別（アクティブ限定）・習得済み で弾き、CDカウンターには触れない
  - リセットは 習得0件とゴールド不足で状態を一切変えず、成功時のみSPを全返却する

リクエスト／レスポンスは camelCase（CamelModel）。コスト = キャラLV × 50G（整数演算のみ）。

本工程で定義する実装の表層:
  models/character.py LearnedSkill（cooldown_remaining を保持）・ActiveSkillSlot、
  POST /api/skill/learn、PUT /api/skill/set-active、POST /api/skill/reset
"""

import pytest

from app.models.character import ActiveSkillSlot, LearnedSkill

from tests.helpers import error_code

pytestmark = pytest.mark.unit


@pytest.fixture
def learn(db, character):
    """習得済みスキルを直接作るファクトリ（習得APIの結果に依存させないため）"""

    def _learn(*skill_ids, cooldown=0):
        for skill_id in skill_ids:
            db.add(LearnedSkill(character_id=character.id, skill_id=skill_id, cooldown_remaining=cooldown))
        db.commit()

    return _learn


def _slots(db, character):
    rows = db.query(ActiveSkillSlot).filter_by(character_id=character.id).order_by(ActiveSkillSlot.slot_index).all()
    return [r.skill_id for r in rows]


def _learned(db, character):
    return {r.skill_id for r in db.query(LearnedSkill).filter_by(character_id=character.id).all()}


# ══════════════════════════════════════════════════════════════════
# §3 スキル習得（POST /api/skill/learn）
# ══════════════════════════════════════════════════════════════════


class TestSkillLearn:
    def test_所持キャラなら習得処理に進む(self, db, character, client):
        """分岐: tech_party.md §3 #1 — キャラ確認を通過する"""
        character.skill_points = 1
        db.commit()
        res = client.post("/api/skill/learn", json={"characterId": character.id, "skillId": "sword_1"})
        assert res.status_code == 200

    def test_未所持キャラIDは404になる(self, client):
        """分岐: tech_party.md §3 #2 — CHARACTER_NOT_FOUND"""
        res = client.post("/api/skill/learn", json={"characterId": "not-owned", "skillId": "sword_1"})
        assert res.status_code == 404
        assert error_code(res) == "CHARACTER_NOT_FOUND"

    def test_マスターに存在するスキルIDなら続行する(self, db, character, client):
        """分岐: tech_party.md §3 #3 — スキルID検証を通過する"""
        character.skill_points = 1
        db.commit()
        res = client.post("/api/skill/learn", json={"characterId": character.id, "skillId": "magic_1"})
        assert res.status_code == 200
        assert "magic_1" in _learned(db, character)

    def test_未知のスキルIDは422になる(self, db, character, client):
        """分岐: tech_party.md §3 #4 — SKILL_UNKNOWN（マスターデータの未知ID経路）"""
        character.skill_points = 1
        db.commit()
        res = client.post("/api/skill/learn", json={"characterId": character.id, "skillId": "no_such_skill"})
        assert res.status_code == 422
        assert error_code(res) == "SKILL_UNKNOWN"

    def test_既に習得済みなら400になる(self, db, character, client, learn):
        """分岐: tech_party.md §3 #5 — SKILL_ALREADY_LEARNED"""
        learn("sword_1")
        character.skill_points = 1
        db.commit()
        res = client.post("/api/skill/learn", json={"characterId": character.id, "skillId": "sword_1"})
        assert res.status_code == 400
        assert error_code(res) == "SKILL_ALREADY_LEARNED"
        assert character.skill_points == 1  # SPは減らない

    def test_未習得なら習得処理に進む(self, db, character, client, learn):
        """分岐: tech_party.md §3 #6 — 別スキルを習得済みでも対象が未習得なら通す"""
        learn("sword_1")
        character.skill_points = 1
        db.commit()
        res = client.post("/api/skill/learn", json={"characterId": character.id, "skillId": "sword_2"})
        assert res.status_code == 200

    @pytest.mark.parametrize(
        ("prereq", "skill_id"),
        [
            ((), "sword_1"),          # Tier1（前提なし）
            (("sword_1",), "sword_2"),  # 前提を習得済み
        ],
    )
    def test_前提を満たしていれば習得できる(self, db, character, client, learn, prereq, skill_id):
        """分岐: tech_party.md §3 #7 — 前提習得済み、またはTier1"""
        learn(*prereq)
        character.skill_points = 1
        db.commit()
        res = client.post("/api/skill/learn", json={"characterId": character.id, "skillId": skill_id})
        assert res.status_code == 200

    def test_前提スキルが未習得なら400になる(self, db, character, client):
        """分岐: tech_party.md §3 #8 — SKILL_PREREQUISITE_NOT_MET（sword_2 の前提は sword_1）"""
        character.skill_points = 3
        db.commit()
        res = client.post("/api/skill/learn", json={"characterId": character.id, "skillId": "sword_2"})
        assert res.status_code == 400
        assert error_code(res) == "SKILL_PREREQUISITE_NOT_MET"
        assert character.skill_points == 3

    def test_未使用SPが必要SPちょうどでも習得できる(self, db, character, client, learn):
        """分岐: tech_party.md §3 #9 — sword_p1 は2SP。境界（ちょうど）は習得側"""
        learn("sword_1", "sword_2")
        character.skill_points = 2
        db.commit()
        res = client.post("/api/skill/learn", json={"characterId": character.id, "skillId": "sword_p1"})
        assert res.status_code == 200
        assert character.skill_points == 0

    def test_未使用SPが必要SP未満なら400になる(self, db, character, client, learn):
        """分岐: tech_party.md §3 #10 — SKILL_INSUFFICIENT_SP"""
        learn("sword_1", "sword_2")
        character.skill_points = 1  # sword_p1 は2SP必要
        db.commit()
        res = client.post("/api/skill/learn", json={"characterId": character.id, "skillId": "sword_p1"})
        assert res.status_code == 400
        assert error_code(res) == "SKILL_INSUFFICIENT_SP"
        assert character.skill_points == 1


# ══════════════════════════════════════════════════════════════════
# §4 アクティブスキルセット変更（PUT /api/skill/set-active）
# ══════════════════════════════════════════════════════════════════


class TestSetActiveSkills:
    @pytest.mark.parametrize("count", [0, 2])  # 0件=全解除 / 上限ちょうど
    def test_0件から2件までは受理する(self, db, character, client, learn, count):
        """分岐: tech_party.md §4 #1 — 受理される件数の両端"""
        learn("sword_1", "magic_1")
        slots = ["sword_1", "magic_1"][:count]
        res = client.put("/api/skill/set-active", json={"characterId": character.id, "activeSlots": slots})
        assert res.status_code == 200
        assert _slots(db, character) == slots

    def test_3件以上は422になる(self, db, character, client, learn):
        """分岐: tech_party.md §4 #2 — スキーマ検証（セット枠は2つ）"""
        learn("sword_1", "magic_1", "heal_1")
        res = client.put(
            "/api/skill/set-active",
            json={"characterId": character.id, "activeSlots": ["sword_1", "magic_1", "heal_1"]},
        )
        assert res.status_code == 422
        assert _slots(db, character) == []

    def test_同一スキルを2枠に指定すると422になる(self, db, character, client, learn):
        """分岐: tech_party.md §4 #3 — SKILL_SLOT_DUPLICATED"""
        learn("sword_1")
        res = client.put(
            "/api/skill/set-active", json={"characterId": character.id, "activeSlots": ["sword_1", "sword_1"]}
        )
        assert res.status_code == 422
        assert error_code(res) == "SKILL_SLOT_DUPLICATED"

    def test_パッシブスキルを指定すると422になる(self, db, character, client, learn):
        """分岐: tech_party.md §4 #4 — SKILL_NOT_ACTIVE（magic_p1 はパッシブ）"""
        learn("magic_1", "magic_p1")
        res = client.put("/api/skill/set-active", json={"characterId": character.id, "activeSlots": ["magic_p1"]})
        assert res.status_code == 422
        assert error_code(res) == "SKILL_NOT_ACTIVE"

    def test_アクティブスキルのみなら受理する(self, db, character, client, learn):
        """分岐: tech_party.md §4 #5 — 種別チェックを通過する"""
        learn("sword_1", "magic_1")
        res = client.put(
            "/api/skill/set-active", json={"characterId": character.id, "activeSlots": ["sword_1", "magic_1"]}
        )
        assert res.status_code == 200

    def test_未習得のスキルを指定すると400になる(self, db, character, client, learn):
        """分岐: tech_party.md §4 #6 — SKILL_NOT_LEARNED"""
        learn("sword_1")
        res = client.put("/api/skill/set-active", json={"characterId": character.id, "activeSlots": ["magic_1"]})
        assert res.status_code == 400
        assert error_code(res) == "SKILL_NOT_LEARNED"

    def test_全て習得済みならセットを全置換する(self, db, character, client, learn):
        """分岐: tech_party.md §4 #7 — 旧セットは残さない"""
        learn("sword_1", "magic_1", "heal_1")
        client.put("/api/skill/set-active", json={"characterId": character.id, "activeSlots": ["sword_1", "magic_1"]})
        res = client.put("/api/skill/set-active", json={"characterId": character.id, "activeSlots": ["heal_1"]})
        assert res.status_code == 200
        assert _slots(db, character) == ["heal_1"]

    def test_セット変更してもCDカウンターは変化しない(self, db, character, client, learn):
        """分岐: tech_party.md §4 #8 — CDは習得スキルごとに保持する（未セット中も減算される）"""
        learn("sword_1", "magic_1", cooldown=2)
        client.put("/api/skill/set-active", json={"characterId": character.id, "activeSlots": ["sword_1"]})
        client.put("/api/skill/set-active", json={"characterId": character.id, "activeSlots": ["magic_1"]})
        remaining = {r.skill_id: r.cooldown_remaining for r in db.query(LearnedSkill).all()}
        assert remaining == {"sword_1": 2, "magic_1": 2}

    def test_重複がなければ受理する(self, db, character, client, learn):
        """分岐: tech_party.md §4 #9 — 別スキル2件は通す"""
        learn("sword_1", "heal_1")
        res = client.put(
            "/api/skill/set-active", json={"characterId": character.id, "activeSlots": ["sword_1", "heal_1"]}
        )
        assert res.status_code == 200
        assert _slots(db, character) == ["sword_1", "heal_1"]


# ══════════════════════════════════════════════════════════════════
# §5 スキルリセット（POST /api/skill/reset）
# ══════════════════════════════════════════════════════════════════


class TestSkillReset:
    def test_リセット対象が未所持キャラIDなら404になる(self, client):
        """分岐: tech_party.md §5 #1 — CHARACTER_NOT_FOUND"""
        res = client.post("/api/skill/reset", json={"characterId": "not-owned"})
        assert res.status_code == 404
        assert error_code(res) == "CHARACTER_NOT_FOUND"

    def test_所持キャラならリセット処理に進む(self, db, player, character, client, learn):
        """分岐: tech_party.md §5 #2 — キャラ確認を通過する"""
        learn("sword_1")
        res = client.post("/api/skill/reset", json={"characterId": character.id})
        assert res.status_code == 200

    def test_習得スキルが0件なら400になる(self, db, player, character, client):
        """分岐: tech_party.md §5 #3 — SKILL_NOTHING_TO_RESET。ゴールドは減算しない"""
        res = client.post("/api/skill/reset", json={"characterId": character.id})
        assert res.status_code == 400
        assert error_code(res) == "SKILL_NOTHING_TO_RESET"
        assert player.gold == 1000

    def test_習得スキルが1件以上ならリセット処理に進む(self, db, character, client, learn):
        """分岐: tech_party.md §5 #4 — 習得数チェックを通過する"""
        learn("sword_1")
        res = client.post("/api/skill/reset", json={"characterId": character.id})
        assert res.status_code == 200
        assert _learned(db, character) == set()

    def test_所持ゴールドがコスト未満なら400になる(self, db, player, character, client, learn):
        """分岐: tech_party.md §5 #5 — SKILL_INSUFFICIENT_GOLD。状態は変更しない"""
        learn("sword_1")
        player.gold = 49  # コスト = LV1 × 50 = 50G
        db.commit()
        res = client.post("/api/skill/reset", json={"characterId": character.id})
        assert res.status_code == 400
        assert error_code(res) == "SKILL_INSUFFICIENT_GOLD"
        assert player.gold == 49
        assert _learned(db, character) == {"sword_1"}

    def test_ゴールドがコストちょうどなら減算して残0になる(self, db, player, character, client, learn):
        """分岐: tech_party.md §5 #6 — 境界（ちょうど）は実行側"""
        learn("sword_1")
        player.gold = 50
        db.commit()
        res = client.post("/api/skill/reset", json={"characterId": character.id})
        assert res.status_code == 200
        assert player.gold == 0

    def test_成功すると消費SPが戻り習得とセットが全解除される(self, db, character, client, learn):
        """分岐: tech_party.md §5 #7 — sword_1(1SP) + sword_2(1SP) → SP+2"""
        learn("sword_1", "sword_2")
        db.add(ActiveSkillSlot(character_id=character.id, slot_index=0, skill_id="sword_1"))
        character.skill_points = 0
        db.commit()
        res = client.post("/api/skill/reset", json={"characterId": character.id})
        assert res.status_code == 200
        assert character.skill_points == 2
        assert _learned(db, character) == set()
        assert _slots(db, character) == []
