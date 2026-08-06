"""単体テスト: エンカウント敵数の抽選とEXP配分（master_data/towers.py・services/party_service.py）

仕様: tech/detail/tech_skill.md §8（エンカウント敵数・EXP配分）、
      tech/detail/tech_battle.md §3.1.5（パーティへのEXP配分）・§3.2（エンカウント抽選ロジック）
分岐観点:
  - 敵数は固定値か範囲指定か。範囲は下限・上限の両端を引く経路を持つ
  - ボス階は範囲指定にかかわらず1体固定
  - EXPは在籍パーティ全員へ全額（人数割りしない）。戦闘不能でも付与し、控えには付与しない

重み合計0のプール（マスターデータ不正）は tech_rng.md §5 #6 が持つ。

本工程で定義する実装の表層: towers.roll_enemy_count・party_service.grant_battle_exp
"""

import pytest

from app.config import INITIAL_CHARACTER
from app.master_data import towers as tw
from app.models.character import Character
from app.models.party import PartyMember
from app.services import party_service

from tests.unit.rng_stub import SeqRng

pytestmark = pytest.mark.unit


@pytest.fixture
def party_pair(db, player, character):
    """パーティ在籍2名 + 控え1名。control には EXP が入らないことを確かめる"""
    mage = Character(player_id=player.id, **{**INITIAL_CHARACTER, "name": "魔法使い", "type": "magic"})
    reserve = Character(player_id=player.id, **{**INITIAL_CHARACTER, "name": "控え", "type": "agile"})
    db.add_all([mage, reserve])
    db.flush()
    db.add_all(
        [
            PartyMember(player_id=player.id, slot_index=0, character_id=character.id),
            PartyMember(player_id=player.id, slot_index=1, character_id=mage.id),
        ]
    )
    db.commit()
    return character, mage, reserve


class TestEnemyCount:
    def test_固定値の階はその体数で出現する(self):
        """分岐: tech_skill.md §8 #1 — 最小=最大なら抽選の余地がない"""
        assert tw.roll_enemy_count(2, 2, is_boss=False, rng=SeqRng(0.0)) == 2

    def test_範囲指定で下限を引けば下限の体数になる(self):
        """分岐: tech_skill.md §8 #2 — 1-2体は各50%。前半区間"""
        assert tw.roll_enemy_count(1, 2, is_boss=False, rng=SeqRng(0.0)) == 1

    def test_範囲指定で上限を引けば上限の体数になる(self):
        """分岐: tech_skill.md §8 #3 — 後半区間"""
        assert tw.roll_enemy_count(1, 2, is_boss=False, rng=SeqRng(0.99)) == 2

    def test_ボス階は範囲指定でも1体固定になる(self):
        """分岐: tech_skill.md §8 #4 — 1体固定・重み100%"""
        assert tw.roll_enemy_count(1, 3, is_boss=True, rng=SeqRng(0.99)) == 1
        assert tw.get_tower("goblin_tower").floor_encounters[20] == [("goblin_king", 100)]


class TestExpDistribution:
    def test_パーティ在籍メンバー全員に全額付与する(self, db, player, party_pair):
        """分岐: tech_skill.md §8 #5 — 人数で分割しない"""
        hero, mage, _ = party_pair
        party_service.grant_battle_exp(player, 100, db)
        assert (hero.exp, mage.exp) == (100, 100)

    def test_戦闘不能のメンバーにも付与する(self, db, player, party_pair):
        """分岐: tech_skill.md §8 #6 — HP0でも在籍していれば対象"""
        _, mage, _ = party_pair
        mage.hp = 0
        db.commit()
        party_service.grant_battle_exp(player, 100, db)
        assert mage.exp == 100

    def test_パーティ外の控えキャラには付与しない(self, db, player, party_pair):
        """分岐: tech_skill.md §8 #7 — 訓練場による部分獲得は Phase 4"""
        _, _, reserve = party_pair
        party_service.grant_battle_exp(player, 100, db)
        assert reserve.exp == 0
