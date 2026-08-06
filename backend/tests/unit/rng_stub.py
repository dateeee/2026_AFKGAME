"""乱数スタブ（テストモジュールではない）

`random.Random` のうちテストで使う API だけを実装する。seed 固定と違い
「どの枝を通るか」がテストの字面で分かる（patterns.md「外部要因の固定」）。
"""


class SeqRng:
    """`random()` が与えた列を順に返す（尽きたら最後の値を返し続ける）"""

    def __init__(self, *values: float):
        self.values = list(values) or [1.0]
        self.calls = 0

    def random(self) -> float:
        value = self.values[min(self.calls, len(self.values) - 1)]
        self.calls += 1
        return value

    def choice(self, seq):
        return seq[int(self.random() * len(seq))]

    def choices(self, population, weights=None, k=1):
        return [self.choice(population) for _ in range(k)]

    def randint(self, a: int, b: int) -> int:
        return a + int(self.random() * (b - a + 1))
