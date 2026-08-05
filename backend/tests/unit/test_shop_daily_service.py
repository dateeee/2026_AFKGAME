"""単体テスト: 日替わりショップ（services/shop_daily_service.py）

仕様: tech/detail/tech_shop.md §7 分岐一覧（品揃えの生成）、§8 分岐一覧（購入・取得）の #7-#14 / #19
      数値の正: data/master/equipment.md §6.1、design/systems/economy.md §2.5「倉庫」

分岐観点:
  - 鮮度判定: 未生成 / リセット前 / リセット後 / リセット時刻ちょうど（境界は再生成側）
  - 解放帯（5F未満 / 5〜14F / 15F以上）と、レア→アンコモン→コモンの累積抽選の各枝
  - ベース抽選の同一カテゴリ除外、装備レベルの下限1
  - 付与数の下限 / 上限 / 単一値（乱数を消費しない）、ステータス値の1未満切り上げ
  - HP吸収を付与しない、5枠の内訳、固定価格テーブル
  - 購入: 売り切れ / ゴールド不足 / 所持枠上限 / 購入時の鮮度 / 途中失敗のロールバック
"""

import random
from datetime import datetime, timedelta, timezone

import pytest

from app.exceptions import AppError
from app.models.equipment import Equipment
from app.models.shop import ShopDailySlot, ShopDailyState
from app.services import shop_daily_service as sds

pytestmark = pytest.mark.unit


NOW = datetime(2026, 8, 2, 12, 0, 0, tzinfo=timezone.utc)
RESET = datetime(2026, 8, 3, 0, 0, 0, tzinfo=timezone.utc)


class ScriptedRandom(random.Random):
    """乱数を台本どおりに返す決定的スタブ（tech_rng.md: Random インスタンスを注入する）

    randoms   : rng.random() が順に返す値（尽きたら 0.0）
    randints  : rng.randint(a, b) が順に返す値（尽きたら下限 a）
    randranges: rng.randrange(n) が順に返す値（尽きたら 0）
    samples   : rng.sample(seq, k) が順に返す並び（尽きたら seq の先頭 k 件）
    """

    def __init__(self, randoms=(), randints=(), randranges=(), samples=()):
        super().__init__(0)
        self.randoms = list(randoms)
        self.randints = list(randints)
        self.randranges = list(randranges)
        self.samples = list(samples)
        self.randint_calls = 0

    def random(self):
        return self.randoms.pop(0) if self.randoms else 0.0

    def randint(self, a, b):
        self.randint_calls += 1
        return self.randints.pop(0) if self.randints else a

    def randrange(self, *args):
        return self.randranges.pop(0) if self.randranges else 0

    def sample(self, population, k, *, counts=None):
        if self.samples:
            return list(self.samples.pop(0))
        return list(population)[:k]


# 枠の確定値。装備レベル5 → 基礎値 floor(5×1.5+2)=9、コモンATK floor(9×1.0×1.0×0.7)=6
_SEED_SLOTS = [
    # (slot_index, category, base_id, rarity, level, stat_atk, price)
    (0, "weapon", "sword", "common", 5, 6, 500),
    (1, "weapon", "dagger", "common", 5, 6, 500),
    (2, "armor", "helm", "common", 5, 6, 400),
    (3, "armor", "shield", "common", 5, 6, 400),
    (4, "accessory", "ring", "common", 5, 6, 300),
]


def _seed_state(db, player, reset_at=RESET, sold=()) -> ShopDailyState:
    """確定値の5枠を持つ日替わり状態をDBへ直接作る（生成ロジックに依存させない）"""
    state = ShopDailyState(player_id=player.id, reset_at=reset_at)
    db.add(state)
    db.flush()
    for slot_index, category, base_id, rarity, level, stat_atk, price in _SEED_SLOTS:
        db.add(
            ShopDailySlot(
                shop_daily_state_id=state.id,
                slot_index=slot_index,
                category=category,
                base_id=base_id,
                rarity=rarity,
                level=level,
                stat_atk=stat_atk,
                stat_def=None,
                stat_hp=None,
                stat_spd=None,
                price=price,
                sold=slot_index in sold,
            )
        )
    db.commit()
    return state


def _slots(db, state) -> list[ShopDailySlot]:
    return (
        db.query(ShopDailySlot)
        .filter_by(shop_daily_state_id=state.id)
        .order_by(ShopDailySlot.slot_index)
        .all()
    )


def _utc(dt: datetime) -> datetime:
    """SQLite から読み戻した naive datetime を UTC として扱う"""
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def _fill_equipment(db, player, count: int) -> None:
    """所持枠を埋めるためのダミー装備"""
    for _ in range(count):
        db.add(
            Equipment(
                player_id=player.id,
                base_id="sword",
                slot="weapon",
                rarity="common",
                level=1,
                stat_atk=1,
            )
        )
    db.commit()


@pytest.fixture
def seeded(db, player) -> ShopDailyState:
    return _seed_state(db, player)


# ── §7 品揃えの生成 ──────────────────────────────────────────────


class Test鮮度判定:
    def test_状態が未生成なら5枠を生成してリセット時刻を保存する(self, db, player):
        # §7-1
        state = sds.ensure_fresh_lineup(db, player, now=NOW, rng=ScriptedRandom())
        assert _utc(state.reset_at) == RESET
        assert len(_slots(db, state)) == 5

    def test_リセット時刻より前なら保存済みの枠をそのまま使う(self, db, player):
        # §7-2 売り切れ状態も維持される
        _seed_state(db, player, sold=(0,))
        state = sds.ensure_fresh_lineup(
            db, player, now=RESET - timedelta(seconds=1), rng=ScriptedRandom()
        )
        assert _utc(state.reset_at) == RESET
        assert [s.base_id for s in _slots(db, state)] == [
            "sword", "dagger", "helm", "shield", "ring"
        ]
        assert _slots(db, state)[0].sold is True

    def test_リセット時刻を過ぎていれば全件削除して再生成する(self, db, player):
        # §7-3 リセット時刻が更新され、売り切れも解除される
        old = _seed_state(db, player, reset_at=datetime(2026, 8, 2, 0, 0, tzinfo=timezone.utc), sold=(0,))
        old_ids = {s.id for s in _slots(db, old)}
        state = sds.ensure_fresh_lineup(db, player, now=NOW, rng=ScriptedRandom())
        new = _slots(db, state)
        assert _utc(state.reset_at) == RESET
        assert len(new) == 5
        assert {s.id for s in new}.isdisjoint(old_ids)
        assert [s.sold for s in new] == [False] * 5

    def test_リセット時刻ちょうどなら再生成する(self, db, player):
        # §7-4 境界は再生成側（now >= reset_at）
        _seed_state(db, player, reset_at=NOW, sold=(0,))
        state = sds.ensure_fresh_lineup(db, player, now=NOW, rng=ScriptedRandom())
        assert _utc(state.reset_at) == RESET
        assert [s.sold for s in _slots(db, state)] == [False] * 5


class Test解放帯:
    @pytest.mark.parametrize("highest", [0, 4])  # 未到達の0F / 境界の1つ手前
    def test_5F未満はコモンのみから抽選する(self, highest):
        # §7-5
        assert sds.get_rarity_rates(highest) == pytest.approx({"common": 1.0})

    @pytest.mark.parametrize("highest", [5, 14])  # 帯の下端 / 上端
    def test_5F以上15F未満はコモンとアンコモンから抽選する(self, highest):
        # §7-6
        assert sds.get_rarity_rates(highest) == pytest.approx({"common": 0.70, "uncommon": 0.30})

    @pytest.mark.parametrize("highest", [15, 99])  # 帯の下端 / 十分に上
    def test_15F以上はレアまで抽選対象になる(self, highest):
        # §7-7
        assert sds.get_rarity_rates(highest) == pytest.approx(
            {"common": 0.55, "uncommon": 0.30, "rare": 0.15}
        )


class Testレアリティ抽選:
    def test_乱数が最上位の確率未満なら最上位レアリティを採る(self):
        # §7-8 15F以上: レア累積 0.15
        assert sds.roll_rarity(15, ScriptedRandom(randoms=[0.14])) == "rare"

    def test_乱数が中位の累積確率未満なら中位レアリティを採る(self):
        # §7-9 アンコモン累積 0.45（0.15 <= r < 0.45）
        assert sds.roll_rarity(15, ScriptedRandom(randoms=[0.20])) == "uncommon"

    def test_乱数が上位2つを超えコモンの累積未満ならコモンを採る(self):
        # §7-10 コモン累積 1.0（0.45 <= r < 1.0）の正常経路
        assert sds.roll_rarity(15, ScriptedRandom(randoms=[0.50])) == "common"

    def test_どの累積確率にも該当しなければコモンへフォールバックする(self):
        # §7-11 浮動小数誤差の受け皿（r が累積合計以上）
        assert sds.roll_rarity(15, ScriptedRandom(randoms=[1.0])) == "common"


class Testベース抽選:
    def test_カテゴリの1枠目は候補を除外せずに抽選する(self):
        # §7-12 武器のID昇順先頭は bow
        assert sds.pick_base("weapon", set(), ScriptedRandom(randranges=[0])).id == "bow"

    def test_カテゴリの2枠目は先行枠のベースを候補から除く(self):
        # §7-13 bow を除いた先頭は dagger
        assert sds.pick_base("weapon", {"bow"}, ScriptedRandom(randranges=[0])).id == "dagger"


class Test装備レベル:
    def test_最高到達階層が0なら装備レベルは1になる(self):
        # §7-14 下限クランプ
        assert sds.calc_equipment_level(0) == 1

    @pytest.mark.parametrize(("highest", "expected"), [(1, 1), (20, 20)])  # 下限そのもの / 通常
    def test_最高到達階層が1以上ならそのまま装備レベルになる(self, highest, expected):
        # §7-15
        assert sds.calc_equipment_level(highest) == expected


class Test付与ステータス数:
    def test_付与数の下限を引いたらその種数だけ付与する(self):
        # §7-16 コモンは1〜2種。下限1を引く
        stats = sds.roll_stats("common", 20, ScriptedRandom(randints=[1], samples=[["atk"]]))
        assert [k for k, v in stats.items() if v is not None] == ["stat_atk"]

    def test_付与数の上限を引いたらその種数だけ付与する(self):
        # §7-17 レアは2〜3種。上限3を引く
        stats = sds.roll_stats(
            "rare", 20, ScriptedRandom(randints=[3], samples=[["atk", "def", "hp"]])
        )
        assert sorted(k for k, v in stats.items() if v is not None) == [
            "stat_atk", "stat_def", "stat_hp"
        ]

    def test_付与数の範囲が単一値なら抽選せずその種数を付与する(self):
        # §7-18 アンコモンは2種固定 → randint を消費しない
        rng = ScriptedRandom(samples=[["atk", "def"]])
        stats = sds.roll_stats("uncommon", 20, rng)
        assert sorted(k for k, v in stats.items() if v is not None) == ["stat_atk", "stat_def"]
        assert rng.randint_calls == 0


class Testステータス値:
    def test_算出値が1未満なら1に切り上げる(self):
        # §7-19 LV1コモンSPD: floor(3 × 1.0 × 0.3 × 0.7) = 0 → 1
        stats = sds.roll_stats("common", 1, ScriptedRandom(randints=[1], samples=[["spd"]]))
        assert stats["stat_spd"] == 1

    def test_算出値が1以上ならfloorした値をそのまま採る(self):
        # §7-20 LV20の基礎値 floor(20×1.5+2)=32
        #       ATK floor(32×1.3×1.0×0.7)=29 / DEF floor(32×1.3×0.7×0.7)=20
        stats = sds.roll_stats("uncommon", 20, ScriptedRandom(samples=[["atk", "def"]]))
        assert stats["stat_atk"] == 29
        assert stats["stat_def"] == 20


class TestHP吸収:
    def test_生成したステータスにHP吸収は含まれない(self):
        # §7-21 ショップ装備はHP吸収を付与しない
        stats = sds.roll_stats("uncommon", 20, ScriptedRandom(samples=[["atk", "def"]]))
        assert set(stats) == {"stat_atk", "stat_def", "stat_hp", "stat_spd"}


class Test枠の生成ループ:
    def test_5枠が武器2防具2アクセサリー1の内訳で生成される(self):
        # §7-22 枠番号は昇順で固定
        slots = sds.generate_daily_slots(highest_floor=20, rng=ScriptedRandom())
        assert [s["slot_index"] for s in slots] == [0, 1, 2, 3, 4]
        assert [s["category"] for s in slots] == [
            "weapon", "weapon", "armor", "armor", "accessory"
        ]


class Test価格:
    @pytest.mark.parametrize(
        ("rarity", "category", "expected"),
        [
            ("common", "weapon", 500),
            ("common", "armor", 400),
            ("common", "accessory", 300),
            ("uncommon", "weapon", 1500),
            ("uncommon", "armor", 1200),
            ("uncommon", "accessory", 1000),
            ("rare", "weapon", 4000),
            ("rare", "armor", 3200),
            ("rare", "accessory", 2500),
        ],
    )
    def test_価格はレアリティとカテゴリの固定テーブルで決まる(self, rarity, category, expected):
        # §7-23 装備レベル・到達階層には依存しない
        assert sds.get_price(rarity, category) == expected


# ── §8 購入・取得（サービス層） ──────────────────────────────────


class Test購入:
    def test_未購入の枠なら購入処理が続行される(self, db, player, seeded):
        # §8-7
        equipment = sds.buy_daily(db, player, 0, now=NOW)
        assert equipment.base_id == "sword"
        assert equipment.slot == "weapon"
        assert db.query(Equipment).filter_by(player_id=player.id).count() == 1

    def test_売り切れの枠は購入できない(self, db, player):
        # §8-8
        _seed_state(db, player, sold=(0,))
        with pytest.raises(AppError) as exc:
            sds.buy_daily(db, player, 0, now=NOW)
        assert exc.value.code == "SHOP_ITEM_SOLD_OUT"
        assert exc.value.status_code == 400

    def test_所持ゴールドが価格ちょうどでも購入できる(self, db, player, seeded):
        # §8-9 境界（gold == price）は所持枠の判定へ進む
        player.gold = 500
        db.commit()
        sds.buy_daily(db, player, 0, now=NOW)
        assert player.gold == 0

    def test_所持ゴールドが価格未満なら購入できず状態も変わらない(self, db, player, seeded):
        # §8-10 境界の1つ手前
        player.gold = 499
        db.commit()
        with pytest.raises(AppError) as exc:
            sds.buy_daily(db, player, 0, now=NOW)
        assert exc.value.code == "SHOP_INSUFFICIENT_GOLD"
        assert exc.value.status_code == 400
        assert player.gold == 499
        assert _slots(db, seeded)[0].sold is False
        assert db.query(Equipment).filter_by(player_id=player.id).count() == 0

    def test_所持枠に空きがあれば減算と装備付与と売り切れ化が成立する(self, db, player, seeded):
        # §8-11 上限50枠に対して49件所持（残り1枠）
        _fill_equipment(db, player, 49)
        sds.buy_daily(db, player, 0, now=NOW)
        assert player.gold == 500  # 1000 - 500
        assert db.query(Equipment).filter_by(player_id=player.id).count() == 50
        assert _slots(db, seeded)[0].sold is True

    def test_所持枠が上限に達していれば購入できず状態も変わらない(self, db, player, seeded):
        # §8-12 残り0枠。ゴールドは足りている
        _fill_equipment(db, player, 50)
        with pytest.raises(AppError) as exc:
            sds.buy_daily(db, player, 0, now=NOW)
        assert exc.value.code == "SHOP_INVENTORY_FULL"
        assert exc.value.status_code == 400
        assert player.gold == 1000
        assert db.query(Equipment).filter_by(player_id=player.id).count() == 50
        assert _slots(db, seeded)[0].sold is False

    def test_リセット時刻前なら保存済みの枠に対して購入する(self, db, player, seeded):
        # §8-13
        equipment = sds.buy_daily(db, player, 0, now=RESET - timedelta(seconds=1))
        assert equipment.base_id == "sword"
        assert equipment.level == 5

    def test_リセット時刻を過ぎていれば再生成した枠に対して購入する(self, db, player):
        # §8-14 再生成後の1枠目（武器ID昇順の先頭 bow・到達20F・レア4000G）を買う
        _seed_state(db, player, reset_at=NOW)
        player.gold = 10_000
        player.highest_floor = 20
        db.commit()
        equipment = sds.buy_daily(db, player, 0, now=NOW, rng=ScriptedRandom())
        assert equipment.base_id == "bow"
        assert equipment.level == 20
        assert player.gold == 6_000  # 10000 - 4000（レア武器）

    def test_永続化の途中で失敗したら全件ロールバックする(self, db, player, seeded, monkeypatch):
        # §8-19 コミットで落として、ゴールド・装備・売り切れのいずれも残らないことを見る
        monkeypatch.setattr(db, "commit", lambda: (_ for _ in ()).throw(RuntimeError("boom")))
        with pytest.raises(RuntimeError):
            sds.buy_daily(db, player, 0, now=NOW)
        monkeypatch.undo()
        assert player.gold == 1000
        assert db.query(Equipment).filter_by(player_id=player.id).count() == 0
        assert _slots(db, seeded)[0].sold is False
