"""装備サービス — 装着・売却・ロック・ステータス計算"""

import math

from sqlalchemy.orm import Session

from app.config import RARITY_ORDER, RARITY_TIERS, SELL_PRICE_BASE
from app.master_data.equipment import (
    EQUIPMENT_SLOTS,
    generate_equipment_drop,
    get_equipment_base,
)
from app.models.character import Character
from app.models.equipment import CharacterEquipSlot, Equipment
from app.models.player import Player


def create_equip_slots(character_id: str, db: Session) -> None:
    """キャラクター作成時に9スロット分のレコードを生成"""
    for slot in EQUIPMENT_SLOTS:
        db.add(CharacterEquipSlot(character_id=character_id, slot=slot))


def try_drop(
    player: Player,
    enemy_level: int,
    floor: int,
    is_boss: bool,
    db: Session,
) -> tuple[Equipment | None, dict | None]:
    """装備ドロップを試行。
    戻り値: (equipment_or_none, auto_sold_info_or_none)
    """
    drop = generate_equipment_drop(enemy_level, floor, is_boss)
    if drop is None:
        return None, None

    # オートセルチェック
    auto_sell_rarity = player.settings.auto_sell_rarity if player.settings else None
    if auto_sell_rarity and auto_sell_rarity in RARITY_ORDER:
        threshold_idx = RARITY_ORDER.index(auto_sell_rarity)
        drop_idx = RARITY_ORDER.index(drop["rarity"])
        if drop_idx <= threshold_idx:
            sell_price = calc_sell_price_from_dict(drop)
            player.gold += sell_price
            base = get_equipment_base(drop["base_id"])
            return None, {
                "name": base.name,
                "rarity": drop["rarity"],
                "gold": sell_price,
            }

    equip = Equipment(player_id=player.id, **drop)
    db.add(equip)
    db.flush()  # id を確定
    return equip, None


def equip_item(
    player: Player,
    character_id: str,
    slot: str,
    equipment_id: str | None,
    db: Session,
) -> None:
    """装備を装着/解除。equipment_id=None で解除。"""
    # キャラクター所有確認
    character = db.query(Character).filter_by(
        id=character_id, player_id=player.id
    ).first()
    if not character:
        raise ValueError("キャラクターが見つかりません")

    equip_slot = db.query(CharacterEquipSlot).filter_by(
        character_id=character_id, slot=slot
    ).first()
    if not equip_slot:
        raise ValueError(f"無効なスロット: {slot}")

    if equipment_id is None:
        # 解除
        equip_slot.equipment_id = None
        return

    # 装備所有確認
    equipment = db.query(Equipment).filter_by(
        id=equipment_id, player_id=player.id
    ).first()
    if not equipment:
        raise ValueError("装備が見つかりません")

    if equipment.slot != slot:
        raise ValueError(f"この装備はスロット '{slot}' に装着できません")

    # 両手武器の場合、盾を外す
    if slot == "weapon" and equipment.is_two_handed:
        shield_slot = db.query(CharacterEquipSlot).filter_by(
            character_id=character_id, slot="shield"
        ).first()
        if shield_slot:
            shield_slot.equipment_id = None

    # 盾装着時、現在の武器が両手武器なら拒否
    if slot == "shield":
        weapon_slot = db.query(CharacterEquipSlot).filter_by(
            character_id=character_id, slot="weapon"
        ).first()
        if weapon_slot and weapon_slot.equipment_id:
            weapon = db.get(Equipment, weapon_slot.equipment_id)
            if weapon and weapon.is_two_handed:
                raise ValueError("両手武器を装備中は盾を装着できません")

    # 他キャラが同じ装備を使っていたら外す
    other = db.query(CharacterEquipSlot).filter(
        CharacterEquipSlot.equipment_id == equipment_id,
        CharacterEquipSlot.character_id != character_id,
    ).first()
    if other:
        other.equipment_id = None

    equip_slot.equipment_id = equipment_id


def sell_items(
    player: Player,
    equipment_ids: list[str],
    db: Session,
) -> tuple[int, int]:
    """装備を売却。戻り値: (total_gold, items_sold)"""
    total_gold = 0
    sold = 0

    for eid in equipment_ids:
        equip = db.query(Equipment).filter_by(
            id=eid, player_id=player.id
        ).first()
        if not equip:
            continue
        if equip.locked:
            continue

        # 装着中チェック
        equipped = db.query(CharacterEquipSlot).filter_by(
            equipment_id=eid
        ).first()
        if equipped:
            continue

        price = calc_sell_price(equip)
        total_gold += price
        db.delete(equip)
        sold += 1

    player.gold += total_gold
    return total_gold, sold


def toggle_lock(player: Player, equipment_id: str, db: Session) -> bool:
    """ロック切替。新しいlocked状態を返す"""
    equip = db.query(Equipment).filter_by(
        id=equipment_id, player_id=player.id
    ).first()
    if not equip:
        raise ValueError("装備が見つかりません")
    equip.locked = not equip.locked
    return equip.locked


def calc_sell_price(equip: Equipment) -> int:
    multiplier = RARITY_TIERS[equip.rarity]["multiplier"]
    return math.floor(SELL_PRICE_BASE * multiplier * equip.level)


def calc_sell_price_from_dict(drop: dict) -> int:
    multiplier = RARITY_TIERS[drop["rarity"]]["multiplier"]
    return math.floor(SELL_PRICE_BASE * multiplier * drop["level"])


def get_effective_stats(character: Character, db: Session) -> dict:
    """装備込みの実効ステータスを返す"""
    stats = {
        "atk": character.base_atk,
        "def": character.base_def,
        "spd": character.base_spd,
        "hp_bonus": 0,
        "lifesteal": 0.0,
    }

    slots = db.query(CharacterEquipSlot).filter_by(
        character_id=character.id
    ).all()

    for slot in slots:
        if not slot.equipment_id:
            continue
        equip = slot.equipment
        if not equip:
            continue
        if equip.stat_atk:
            stats["atk"] += equip.stat_atk
        if equip.stat_def:
            stats["def"] += equip.stat_def
        if equip.stat_hp:
            stats["hp_bonus"] += equip.stat_hp
        if equip.stat_spd:
            stats["spd"] += equip.stat_spd
        if equip.lifesteal:
            stats["lifesteal"] += equip.lifesteal

    return stats


def get_equipped_map(character_id: str, db: Session) -> dict[str, str | None]:
    """スロット→装備IDのマッピングを返す"""
    slots = db.query(CharacterEquipSlot).filter_by(
        character_id=character_id
    ).all()
    return {s.slot: s.equipment_id for s in slots}
