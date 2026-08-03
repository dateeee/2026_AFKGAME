"""単体テスト: 乱数源（app/rng.py）

仕様: tech/tech_rng.md §2（インスタンス注入）・§5（分岐一覧）。
グローバル `random.*` を直呼びしないことで、テストのシード固定がプロセス全体へ
波及せず、並行リクエスト間でも乱数が干渉しないことを担保する。
"""

import random

import pytest

from app import rng as rng_module
from app.services import battle_service as bs

pytestmark = pytest.mark.unit


class TestNewRng:
    def test_シード未設定なら毎回別のインスタンスになる(self, monkeypatch):
        monkeypatch.setattr(rng_module, "BATTLE_RNG_SEED", "")
        first, second = rng_module.new_rng(), rng_module.new_rng()
        assert first is not second
        assert isinstance(first, random.Random)

    def test_シードを設定すると同じ列を再生する(self, monkeypatch):
        """調査用のシード固定（tech_rng.md §2）"""
        monkeypatch.setattr(rng_module, "BATTLE_RNG_SEED", "20260804")
        first = [rng_module.new_rng().random() for _ in range(3)]
        second = [rng_module.new_rng().random() for _ in range(3)]
        assert first == second


class TestRngInjection:
    """別インスタンスの乱数は互いに干渉しない（tech_rng.md §5 の観点8）

    §5 は Phase 3〜 の観点（重み合計0のプール・生存者0体のターゲット選択）を含む
    通し番号のため、`分岐:` マーカーでは参照しない（全行の対応が揃ってから移行する）。
    """

    def test_同じシードを渡せば結果が再現する(self):
        a = bs._calc_damage(100, 0, True, random.Random(42))
        b = bs._calc_damage(100, 0, True, random.Random(42))
        assert a == b

    def test_別シードのインスタンスは独立している(self):
        left = random.Random(1)
        right = random.Random(2)
        # left を先に消費しても right の列は変わらない
        before = random.Random(2).random()
        bs._calc_damage(100, 0, True, left)
        assert right.random() == before
