"""単体テスト: オフラインの処理方式選択と簡略計算（services/offline_service.py）

仕様: tech/detail/tech_offline.md §2（処理方式の切り替え）・§4（簡略計算アルゴリズム）・
      §4.1（期待値計算式）・§5（分岐一覧）
分岐観点:
  - 未処理tick数による処理方式の切り替え（100 / 101 の境界）
  - 周回ループの全滅判定・ポーション在庫・レベルアップ・上限追従
  - 打ち切り時と完走時で残tickの扱いが変わる
  - 周回数 0 / 1 / 2以上

簡略計算は乱数を使わず期待値で確定計算する（§4.1）。1周回の期待値は `estimate_lap(level)`
としてテストから与え、周回ループそのものの分岐だけを検証する。
tick数の算定・24時間キャップは tech_tick.md §5、全滅ペナルティの適用順は tech_state.md §3 が持つ。

本工程で定義する実装の表層:
  offline_service.resolve_calc_mode・LapEstimate・OfflineState・run_simplified
"""

import pytest

from app.services import offline_service as off

pytestmark = pytest.mark.unit


def make_lap(*, ticks=10, gross_damage=100, heal=20, exp=50, gold=30, potion_uses=1):
    """1周回の期待値（§4.1「周回の解決」）"""
    return off.LapEstimate(
        ticks=ticks, gross_damage=gross_damage, heal=heal, exp=exp, gold=gold, potion_uses=potion_uses
    )


def make_state(
    *,
    pending_ticks=20,
    party_hp_total=200,
    potion_count=5,
    potion_heal=50,
    exp_to_next_level=1000,
    target_floor=3,
    highest_floor=4,
    total_floors=20,
):
    return off.OfflineState(
        pending_ticks=pending_ticks,
        party_hp_total=party_hp_total,
        potion_count=potion_count,
        potion_heal=potion_heal,
        exp_to_next_level=exp_to_next_level,
        target_floor=target_floor,
        highest_floor=highest_floor,
        total_floors=total_floors,
    )


class TestCalcMode:
    @pytest.mark.parametrize("pending", [1, 100])  # 下限 / 境界ちょうど
    def test_未処理tickが100以下なら正規シミュレーションを選ぶ(self, pending):
        """分岐: tech_offline.md §5 #1 — 1tickずつ実行する（個別ログも生成する）"""
        assert off.resolve_calc_mode(pending) == "normal"

    @pytest.mark.parametrize("pending", [101, 1440])  # 境界の1つ上 / 24時間ぶん
    def test_未処理tickが101以上なら簡略計算を選ぶ(self, pending):
        """分岐: tech_offline.md §5 #2 — 期待値で一括処理する"""
        assert off.resolve_calc_mode(pending) == "simplified"


class TestWipeDecision:
    def test_純被ダメがパーティ合計HPを超えたら全滅で打ち切る(self):
        """分岐: tech_offline.md §5 #3 — 全滅ペナルティを適用してから打ち切る"""
        # 純被ダメ = 400 − 20 − (1回 × 50) = 330 > 合計HP 200
        state = make_state(pending_ticks=100)
        result = off.run_simplified(state, lambda level: make_lap(gross_damage=400))
        assert result.wiped is True
        assert result.total_gold == 0  # ゴールドロスト（塔内取得分を失う）

    def test_純被ダメがパーティ合計HP以下なら周回を継続する(self):
        """分岐: tech_offline.md §5 #4 — 純被ダメ 30 ≤ 合計HP 200"""
        state = make_state(pending_ticks=20)
        result = off.run_simplified(state, lambda level: make_lap())
        assert result.wiped is False
        assert result.laps == 2


class TestPotionStock:
    def test_在庫があれば消費して回復に算入する(self):
        """分岐: tech_offline.md §5 #5 — 消費数/周回を所持数から減算する"""
        # 240 − 20 − 50 = 170 ≤ 200 で継続する
        state = make_state(pending_ticks=20, potion_count=5)
        result = off.run_simplified(state, lambda level: make_lap(gross_damage=240))
        assert result.potions_used == 2  # 1個/周 × 2周
        assert result.wiped is False

    def test_在庫が尽きたら以降の周回は回復なしで全滅判定する(self):
        """分岐: tech_offline.md §5 #6 — 2周目は 240 − 20 = 220 > 200 で全滅"""
        state = make_state(pending_ticks=20, potion_count=1)
        result = off.run_simplified(state, lambda level: make_lap(gross_damage=240))
        assert result.potions_used == 1
        assert (result.laps, result.wiped) == (2, True)


class TestLevelUp:
    def test_必要EXPに到達したら以降の周回を新ステータスで計算する(self):
        """分岐: tech_offline.md §5 #7 — レベルアップを反映し SP も加算する"""
        laps = {1: make_lap(ticks=10), 2: make_lap(ticks=5)}  # LV2で1周が速くなる
        state = make_state(pending_ticks=15, exp_to_next_level=50)
        result = off.run_simplified(state, lambda level: laps[level])
        assert result.levels_gained == 1
        assert result.skill_points_gained == 1
        assert result.ticks_consumed == 15  # 1周目10tick + 2周目5tick（新ステータス）

    def test_必要EXPに未到達ならステータス据え置きで継続する(self):
        """分岐: tech_offline.md §5 #8 — レベルアップなし"""
        state = make_state(pending_ticks=20, exp_to_next_level=1000)
        result = off.run_simplified(state, lambda level: make_lap())
        assert (result.levels_gained, result.skill_points_gained) == (0, 0)
        assert result.ticks_consumed == 20  # 1周10tick据え置きのまま2周


class TestTargetFloorFollow:
    def test_目標階が上限と一致していれば新しい階のクリアで追従する(self):
        """分岐: tech_offline.md §5 #9 — 上限 = min(highest+1, total) = 5 と一致"""
        state = make_state(pending_ticks=10, target_floor=5, highest_floor=4, total_floors=20)
        result = off.run_simplified(state, lambda level: make_lap())
        assert result.target_floor == 6

    def test_目標階が上限より低ければ追従しない(self):
        """分岐: tech_offline.md §5 #10 — プレイヤーが意図した周回階を維持する"""
        state = make_state(pending_ticks=10, target_floor=3, highest_floor=4, total_floors=20)
        result = off.run_simplified(state, lambda level: make_lap())
        assert result.target_floor == 3


class TestRemainingTicks:
    def test_全滅で打ち切ったら残tickは破棄して塔外待機に回す(self):
        """分岐: tech_offline.md §5 #11 — 残tickは消化せずHP自然回復のみ適用する"""
        state = make_state(pending_ticks=200)
        result = off.run_simplified(state, lambda level: make_lap(gross_damage=400))
        assert result.ticks_consumed == 10  # 全滅した1周ぶんだけ消化
        assert result.idle_ticks == 190     # 残りは塔外待機（HP自然回復の対象）

    def test_目標階の周回で消化しきったら全tickを消化して終了する(self):
        """分岐: tech_offline.md §5 #12 — 残tickも塔外待機ぶんも残らない"""
        state = make_state(pending_ticks=20)
        result = off.run_simplified(state, lambda level: make_lap())
        assert (result.ticks_consumed, result.idle_ticks) == (20, 0)


class TestLapLoop:
    def test_消化すべきtickがなければ簡略計算は呼び出されない(self):
        """分岐: tech_offline.md §5 #13 — 0周。処理方式選択の段階で除外される"""
        assert off.resolve_calc_mode(0) == "none"

    def test_1周で消化しきればその周回の結果のみ反映する(self):
        """分岐: tech_offline.md §5 #14 — 1周"""
        state = make_state(pending_ticks=10)
        result = off.run_simplified(state, lambda level: make_lap())
        assert result.laps == 1
        assert (result.total_gold, result.total_exp) == (30, 50)

    def test_2周以上なら周回ごとに報酬とEXPとポーション消費を累積する(self):
        """分岐: tech_offline.md §5 #15 — 2周ぶんが加算される"""
        state = make_state(pending_ticks=30)
        result = off.run_simplified(state, lambda level: make_lap())
        assert result.laps == 3
        assert (result.total_gold, result.total_exp, result.potions_used) == (90, 150, 3)
