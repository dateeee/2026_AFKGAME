"""単体テスト: 目標階の選択上限と上限追従

仕様: design/systems/battle.md「目標階設定」、tech/basic/tech_api.md `/api/tower/select`
分岐観点:
  - 上限 = min(到達済み最高階 + 1, 総階数)
  - 上限追従は「目標階 == 旧上限」かつ「新しい階をクリアした」場合のみ発生

不変条件: `TowerData.total_floors` は常に非 None。総階数を持たない無限塔（深淵の塔）は
Phase 5 で追加される（known_issues.md）ため、本工程では None 入力を扱わない。
"""

import pytest

from app.services.battle_service import (
    _follow_target_floor,
    get_tower_highest_floor,
    target_floor_cap,
)

pytestmark = pytest.mark.unit


class TestTargetFloorCap:
    @pytest.mark.parametrize(
        ("highest", "total", "expected"),
        [
            (0, 20, 1),    # 未挑戦 → 1階のみ
            (1, 20, 2),
            (12, 20, 13),  # 通常: highest + 1
            (19, 20, 20),  # 最上階の1つ手前
            (20, 20, 20),  # 総階数でクランプ（+1 しない）
            (25, 20, 20),  # 記録が総階数を超えていてもクランプ
        ],
    )
    def test_有限塔は総階数でクランプされる(self, highest, total, expected):
        assert target_floor_cap(highest, total) == expected


class TestGetTowerHighestFloor:
    def test_記録がなければ0(self, db, player):
        assert get_tower_highest_floor(player, "goblin_tower", db) == 0

    def test_記録があればその値(self, db, player, tower_record):
        tower_record("goblin_tower", highest_floor=7)
        assert get_tower_highest_floor(player, "goblin_tower", db) == 7

    def test_塔ごとに独立している(self, db, player, tower_record):
        tower_record("goblin_tower", highest_floor=20, cleared=True)
        tower_record("forest_tower", highest_floor=3)
        assert get_tower_highest_floor(player, "goblin_tower", db) == 20
        assert get_tower_highest_floor(player, "forest_tower", db) == 3


class TestFollowTargetFloor:
    def test_目標階が上限と一致していれば追従する(self, player):
        player.target_floor = 5  # 旧上限 = min(4+1, 20) = 5
        assert _follow_target_floor(player, 20, old_highest=4, cleared_floor=5) == 6
        assert player.target_floor == 6

    def test_目標階が上限より低ければ追従しない(self, player):
        player.target_floor = 3  # プレイヤーが意図した周回階
        assert _follow_target_floor(player, 20, old_highest=4, cleared_floor=5) is None
        assert player.target_floor == 3

    def test_既踏の階を再クリアしても追従しない(self, player):
        player.target_floor = 11  # 旧上限 = min(10+1, 20) = 11
        assert _follow_target_floor(player, 20, old_highest=10, cleared_floor=6) is None
        assert player.target_floor == 11

    def test_最上階クリア時は追従しない(self, player):
        player.target_floor = 20  # 旧上限 = min(19+1, 20) = 20、新上限も 20
        assert _follow_target_floor(player, 20, old_highest=19, cleared_floor=20) is None
        assert player.target_floor == 20

    def test_目標階が未設定なら追従しない(self, player):
        player.target_floor = None
        assert _follow_target_floor(player, 20, old_highest=4, cleared_floor=5) is None
        assert player.target_floor is None
