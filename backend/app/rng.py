"""乱数源（tech_rng.md §2）

グローバル `random.*` の直呼びを禁止し、`random.Random` の**インスタンス**を
tick処理の入口（`routers/battle.py`）で1つ生成して引数で受け渡す。
戦闘・ドロップ・装備生成が同一インスタンスを共有し、乱数源を1本化する。

- 単体テストは `Random(42)` や戻り値を列で与えるスタブを渡すだけで決定的になる
  （`monkeypatch` でグローバルを差し替えるとプロセス全体に波及するため使わない）
- `BATTLE_RNG_SEED` を設定するとリクエストごとに同じ列を再生する（調査用）
"""

import random

from app.config import BATTLE_RNG_SEED

#: 引数で乱数源が渡されなかった場合の既定インスタンス。
#: 本番経路は必ず `new_rng()` の結果を渡すため、実運用では参照されない。
DEFAULT_RNG = random.Random()


def new_rng() -> random.Random:
    """1リクエストにつき1インスタンスを生成する（tech_rng.md §2）"""
    if BATTLE_RNG_SEED:
        return random.Random(BATTLE_RNG_SEED)
    return random.Random()
