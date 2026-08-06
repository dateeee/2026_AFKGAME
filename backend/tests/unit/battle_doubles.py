"""Phase 3 戦闘テストの共通テストダブル（テストモジュールではない）

`conftest.py` には置かない: 使うのはスキル・オフラインの戦闘系テストだけで、
全テスト共通のフィクスチャではないため（test-list.md §4）。
乱数スタブは [rng_stub.py](rng_stub.py)（app に依存しないため分離している）。
"""

from app.services import skill_service as sk

from tests.unit.rng_stub import SeqRng

__all__ = ["SeqRng", "make_actor", "make_enemy"]


def make_actor(
    actor_id="ally1",
    *,
    hp=100,
    max_hp=100,
    atk=20,
    defense=10,
    spd=10,
    side="ally",
    learned=(),
    slots=(),
    cooldowns=None,
    statuses=None,
    buffs=None,
):
    """戦闘中アクター。テストが依存する値だけを明示的に渡す"""
    return sk.BattleActor(
        id=actor_id,
        name=actor_id,
        hp=hp,
        max_hp=max_hp,
        atk=atk,
        defense=defense,
        spd=spd,
        side=side,
        learned=list(learned),
        active_slots=list(slots),
        cooldowns=dict(cooldowns or {}),
        statuses=dict(statuses or {}),
        buffs=list(buffs or []),
    )


def make_enemy(enemy_id="enemy1", *, hp=100, max_hp=100, atk=15, defense=10, spd=10):
    return make_actor(enemy_id, hp=hp, max_hp=max_hp, atk=atk, defense=defense, spd=spd, side="enemy")
