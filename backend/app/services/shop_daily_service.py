"""日替わりショップサービス — 品揃えの生成・24時間更新・購入

仕様: tech_shop.md。数値の正は config（economy.md / master/equipment.md 由来）。
乱数は `random.Random` のインスタンスを引数で受け取る（tech_rng.md §1）。
"""

import logging
import math
import random
from datetime import datetime, time, timedelta, timezone

from sqlalchemy.orm import Session

from app.config import (
    EQUIPMENT_STORAGE_LIMIT,
    RARITY_ORDER,
    RARITY_TIERS,
    SHOP_CATEGORY_SLOTS,
    SHOP_PRICES,
    SHOP_RARITY_RATES,
    SHOP_SLOT_CATEGORIES,
    SHOP_STAT_FACTOR,
    STAT_MODIFIERS,
)
from app.exceptions import AppError
from app.master_data.equipment import (
    EquipmentBase,
    calc_base_value,
    get_bases_by_category,
    get_equipment_base,
)
from app.models.equipment import Equipment
from app.models.player import Player
from app.models.shop import ShopDailySlot, ShopDailyState

logger = logging.getLogger("afkgame.shop")


# ── 時刻 ─────────────────────────────────────────────────────────


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _as_utc(dt: datetime) -> datetime:
    """DBから読み戻した naive datetime を UTC として扱う"""
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def next_reset_at(now: datetime) -> datetime:
    """次回リセット時刻 = 現在のUTC日付の翌日 00:00:00Z（tech_shop.md §2.1）"""
    utc_now = _as_utc(now).astimezone(timezone.utc)
    return datetime.combine(utc_now.date() + timedelta(days=1), time.min, tzinfo=timezone.utc)


def format_reset_at(dt: datetime) -> str:
    """API応答用のリセット時刻表記（tech_shop.md §6）"""
    return _as_utc(dt).astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


# ── 抽選 ─────────────────────────────────────────────────────────


def get_rarity_rates(highest_floor: int) -> dict[str, float]:
    """解放帯のレアリティ出現率を返す（tech_shop.md §2.3）"""
    threshold = max(t for t in SHOP_RARITY_RATES if highest_floor >= t)
    return dict(SHOP_RARITY_RATES[threshold])


def roll_rarity(highest_floor: int, rng: random.Random) -> str:
    """上位レアリティから累積し、`r < 累積値` を満たした最初のレアリティを採る"""
    rates = get_rarity_rates(highest_floor)
    r = rng.random()
    cumulative = 0.0
    for rarity in [x for x in reversed(RARITY_ORDER) if x in rates]:
        cumulative += rates[rarity]
        if r < cumulative:
            return rarity
    return "common"  # 浮動小数誤差の受け皿（tech_shop.md §2.3 手順4）


def pick_base(category: str, exclude_ids: set[str], rng: random.Random) -> EquipmentBase:
    """カテゴリのベース装備を1件選ぶ。同一カテゴリの先行枠で選ばれたものは除く"""
    candidates = [b for b in get_bases_by_category(category) if b.id not in exclude_ids]
    return candidates[rng.randrange(len(candidates))]


def calc_equipment_level(highest_floor: int) -> int:
    """装備レベル L = max(1, 最高到達階層)（tech_shop.md §3.1）"""
    return max(1, highest_floor)


def roll_stats(rarity: str, level: int, rng: random.Random) -> dict:
    """付与ステータスを決める。戻り値: {stat_atk, stat_def, stat_hp, stat_spd}

    付与数の範囲が単一値なら抽選せずその値を採る（乱数を消費しない）。
    """
    tier = RARITY_TIERS[rarity]
    stats_min, stats_max = tier["stats_min"], tier["stats_max"]
    count = stats_min if stats_min == stats_max else rng.randint(stats_min, stats_max)

    stat_keys = list(STAT_MODIFIERS)  # ATK → DEF → HP → SPD
    chosen = set(rng.sample(stat_keys, count))

    base_value = calc_base_value(level)
    multiplier = tier["multiplier"]
    return {
        f"stat_{key}": (
            max(1, math.floor(base_value * multiplier * STAT_MODIFIERS[key] * SHOP_STAT_FACTOR))
            if key in chosen
            else None
        )
        for key in stat_keys
    }


def get_price(rarity: str, category: str) -> int:
    """レアリティ×カテゴリの固定価格（tech_shop.md §3.2）"""
    return SHOP_PRICES[rarity][category]


def generate_daily_slots(highest_floor: int, rng: random.Random) -> list[dict]:
    """5枠分の内容を枠番号の昇順に生成する（tech_shop.md §2.2）"""
    level = calc_equipment_level(highest_floor)
    picked: dict[str, set[str]] = {category: set() for category in SHOP_CATEGORY_SLOTS}

    slots: list[dict] = []
    for slot_index, category in enumerate(SHOP_SLOT_CATEGORIES):
        rarity = roll_rarity(highest_floor, rng)
        base = pick_base(category, picked[category], rng)
        picked[category].add(base.id)
        slots.append({
            "slot_index": slot_index,
            "category": category,
            "base_id": base.id,
            "rarity": rarity,
            "level": level,
            "price": get_price(rarity, category),
            **roll_stats(rarity, level, rng),
        })
    return slots


# ── 品揃えの保持 ─────────────────────────────────────────────────


def get_slots(db: Session, state: ShopDailyState) -> list[ShopDailySlot]:
    """保存済みの枠を枠番号の昇順で返す"""
    return (
        db.query(ShopDailySlot)
        .filter_by(shop_daily_state_id=state.id)
        .order_by(ShopDailySlot.slot_index)
        .all()
    )


def ensure_fresh_lineup(
    db: Session,
    player: Player,
    now: datetime | None = None,
    rng: random.Random | None = None,
) -> ShopDailyState:
    """鮮度を判定し、必要なら品揃えを作り直す（tech_shop.md §2.1）"""
    now = _as_utc(now) if now is not None else _now()
    rng = rng if rng is not None else random.Random()

    state = db.query(ShopDailyState).filter_by(player_id=player.id).first()
    if state is not None and now < _as_utc(state.reset_at):
        return state

    if state is None:
        state = ShopDailyState(player_id=player.id, reset_at=next_reset_at(now))
        db.add(state)
        db.flush()
    else:
        for old_slot in get_slots(db, state):
            db.delete(old_slot)
        db.flush()  # 枠番号の一意制約に触れないよう、削除を先に確定させる
        state.reset_at = next_reset_at(now)

    for slot_data in generate_daily_slots(player.highest_floor, rng):
        db.add(ShopDailySlot(shop_daily_state_id=state.id, **slot_data))
    db.commit()

    logger.info(
        "日替わりショップを生成",
        extra={"player_id": str(player.id), "highest_floor": player.highest_floor},
    )
    return state


# ── 購入 ─────────────────────────────────────────────────────────


def buy_daily(
    db: Session,
    player: Player,
    slot_index: int,
    now: datetime | None = None,
    rng: random.Random | None = None,
) -> Equipment:
    """日替わり枠を1件購入して装備を付与する（tech_shop.md §4）"""
    now = _as_utc(now) if now is not None else _now()
    state = ensure_fresh_lineup(db, player, now=now, rng=rng)
    slot = (
        db.query(ShopDailySlot)
        .filter_by(shop_daily_state_id=state.id, slot_index=slot_index)
        .one()
    )

    if slot.sold:
        raise AppError("SHOP_ITEM_SOLD_OUT", "この商品は売り切れです", 400)
    if player.gold < slot.price:
        raise AppError("SHOP_INSUFFICIENT_GOLD", "ゴールドが足りません", 400)

    owned = db.query(Equipment).filter_by(player_id=player.id).count()
    if owned >= EQUIPMENT_STORAGE_LIMIT:
        raise AppError("SHOP_INVENTORY_FULL", "装備の所持枠が上限に達しています", 400)

    base = get_equipment_base(slot.base_id)
    equipment = Equipment(
        player_id=player.id,
        base_id=slot.base_id,
        slot=base.slot,
        rarity=slot.rarity,
        level=slot.level,
        enhance_level=0,
        stat_atk=slot.stat_atk,
        stat_def=slot.stat_def,
        stat_hp=slot.stat_hp,
        stat_spd=slot.stat_spd,
        lifesteal=None,
        is_two_handed=base.is_two_handed,
        locked=False,
    )

    try:
        player.gold -= slot.price
        db.add(equipment)
        slot.sold = True
        db.commit()
    except Exception:
        db.rollback()
        raise

    logger.info(
        "日替わりショップ購入",
        extra={
            "player_id": str(player.id),
            "slot_index": slot_index,
            "base_id": slot.base_id,
            "gold": player.gold,
        },
    )
    return equipment
