"""戦闘サービス — Phase 1 コアエンジン + Phase 2 装備統合"""

import math
import random
from dataclasses import dataclass, field

from sqlalchemy.orm import Session

from app.models.player import Player, TowerClearRecord
from app.models.character import Character
from app.models.equipment import Equipment
from app.models.item import InventoryItem
from app.master_data.characters import calc_stats_for_level, required_exp
from app.master_data.enemies import EnemyData, get_enemy
from app.master_data.towers import get_tower, roll_encounter
from app.master_data.items import get_item
from app.config import TURNS_PER_TICK
from app.services.equipment_service import get_effective_stats, try_drop


@dataclass
class TickResult:
    battle_logs: list[list[dict]] = field(default_factory=list)
    total_gold: int = 0
    total_exp: int = 0
    enemies_defeated: int = 0
    potions_used: int = 0
    levels_gained: int = 0
    floors_cleared: int = 0
    defeated: bool = False
    equipment_drops: list[Equipment] = field(default_factory=list)
    equipment_auto_sold: list[dict] = field(default_factory=list)

    def accumulate(self, other: "TickResult") -> None:
        self.battle_logs.extend(other.battle_logs)
        self.total_gold += other.total_gold
        self.total_exp += other.total_exp
        self.enemies_defeated += other.enemies_defeated
        self.potions_used += other.potions_used
        self.levels_gained += other.levels_gained
        self.floors_cleared += other.floors_cleared
        self.equipment_drops.extend(other.equipment_drops)
        self.equipment_auto_sold.extend(other.equipment_auto_sold)
        if other.defeated:
            self.defeated = True


def _calc_damage(atk: int, target_def: int, is_player: bool) -> tuple[int, bool]:
    """ダメージ計算。戻り値: (damage, is_crit)"""
    variance = random.uniform(-0.1, 0.1)
    raw = atk * (1 + variance) - target_def * 0.5
    is_crit = random.random() < 0.05
    if is_crit:
        raw *= 1.5
    min_dmg = 1 if is_player else 0
    damage = max(min_dmg, math.floor(raw))
    return damage, is_crit


def _check_level_up(character: Character) -> int:
    """レベルアップ判定。レベルアップ回数を返す"""
    levels = 0
    while character.exp >= required_exp(character.level):
        character.exp -= required_exp(character.level)
        character.level += 1
        levels += 1
        stats = calc_stats_for_level(character.type, character.level)
        character.max_hp = stats["max_hp"]
        character.base_atk = stats["base_atk"]
        character.base_def = stats["base_def"]
        character.base_spd = stats["base_spd"]
        character.hp = character.max_hp  # レベルアップで全回復
    return levels


def _get_potion_count(player: Player, db: Session) -> int:
    item = db.query(InventoryItem).filter_by(
        player_id=player.id, item_id="hp_potion"
    ).first()
    return item.quantity if item else 0


def _use_potion(player: Player, character: Character, effective_max_hp: int, db: Session) -> bool:
    """ポーションを使用。使用したらTrue"""
    item = db.query(InventoryItem).filter_by(
        player_id=player.id, item_id="hp_potion"
    ).first()
    if not item or item.quantity <= 0:
        return False
    potion = get_item("hp_potion")
    heal = math.floor(effective_max_hp * potion.heal_ratio)
    character.hp = min(effective_max_hp, character.hp + heal)
    item.quantity -= 1
    return True


def _update_tower_record(player: Player, tower_id: str, floor: int, is_boss: bool, db: Session) -> None:
    """塔別クリア記録を更新。ボス討伐で cleared=True（次の塔の解放条件）"""
    record = db.query(TowerClearRecord).filter_by(
        player_id=player.id, tower_id=tower_id
    ).first()
    if not record:
        record = TowerClearRecord(player_id=player.id, tower_id=tower_id)
        db.add(record)
    if floor > (record.highest_floor or 0):
        record.highest_floor = floor
    if is_boss:
        record.cleared = True


def _recover_hp(character: Character, effective_max_hp: int, effective_def: int) -> int:
    """塔外HP自然回復。回復量を返す"""
    recovery = math.floor(effective_max_hp * 0.02 + effective_def * 0.5)
    old_hp = character.hp
    character.hp = min(effective_max_hp, character.hp + recovery)
    return character.hp - old_hp


def process_tick(player: Player, character: Character, db: Session) -> TickResult:
    """1tick（3ターン）を処理"""
    result = TickResult()
    tick_logs: list[dict] = []

    # 装備込み実効ステータス
    eff = get_effective_stats(character, db)
    effective_atk = eff["atk"]
    effective_def = eff["def"]
    effective_spd = eff["spd"]
    effective_max_hp = character.max_hp + eff["hp_bonus"]
    total_lifesteal = eff["lifesteal"]

    # 塔外: HP回復のみ
    if not player.current_tower_id:
        if character.hp < effective_max_hp:
            healed = _recover_hp(character, effective_max_hp, effective_def)
            if healed > 0:
                tick_logs.append({
                    "type": "recovery",
                    "actor": character.name,
                    "amount": healed,
                    "hp": character.hp,
                    "max_hp": effective_max_hp,
                })
        if tick_logs:
            result.battle_logs.append(tick_logs)
        return result

    tower = get_tower(player.current_tower_id)

    for turn in range(TURNS_PER_TICK):
        # 敵がいなければロール
        if not player.current_enemy_id or player.current_enemy_hp is None or player.current_enemy_hp <= 0:
            enemy_data = roll_encounter(player.current_tower_id, player.current_floor or 1)
            player.current_enemy_id = enemy_data.id
            player.current_enemy_hp = enemy_data.hp
            tick_logs.append({
                "type": "encounter",
                "floor": player.current_floor,
                "enemy": enemy_data.name,
                "enemy_hp": enemy_data.hp,
            })

        enemy_data = get_enemy(player.current_enemy_id)

        # ポーション自動使用チェック
        threshold = 0.5  # デフォルト
        if player.settings:
            threshold = player.settings.potion_threshold
        if character.hp <= effective_max_hp * threshold:
            if _use_potion(player, character, effective_max_hp, db):
                result.potions_used += 1
                tick_logs.append({
                    "type": "potion",
                    "actor": character.name,
                    "hp": character.hp,
                    "max_hp": effective_max_hp,
                })

        # ターン処理: SPD順
        player_first = effective_spd >= enemy_data.spd  # 同速はプレイヤー先行

        actors = []
        if player_first:
            actors = ["player", "enemy"]
        else:
            actors = ["enemy", "player"]

        for actor in actors:
            if character.hp <= 0 or (player.current_enemy_hp is not None and player.current_enemy_hp <= 0):
                break

            if actor == "player":
                dmg, crit = _calc_damage(effective_atk, enemy_data.def_, is_player=True)
                player.current_enemy_hp = max(0, (player.current_enemy_hp or 0) - dmg)
                tick_logs.append({
                    "type": "attack",
                    "actor": character.name,
                    "target": enemy_data.name,
                    "damage": dmg,
                    "critical": crit,
                    "target_hp": player.current_enemy_hp,
                })

                # HP吸収
                if total_lifesteal > 0 and dmg > 0:
                    heal = math.floor(dmg * total_lifesteal)
                    if heal > 0:
                        character.hp = min(effective_max_hp, character.hp + heal)
                        tick_logs.append({
                            "type": "lifesteal",
                            "actor": character.name,
                            "amount": heal,
                            "hp": character.hp,
                            "max_hp": effective_max_hp,
                        })

                # 敵撃破
                if player.current_enemy_hp <= 0:
                    result.enemies_defeated += 1
                    gold_earned = enemy_data.gold
                    exp_earned = enemy_data.exp
                    player.gold += gold_earned
                    player.run_gold += gold_earned
                    character.exp += exp_earned
                    result.total_gold += gold_earned
                    result.total_exp += exp_earned

                    tick_logs.append({
                        "type": "defeat",
                        "target": enemy_data.name,
                        "gold": gold_earned,
                        "exp": exp_earned,
                    })

                    # 装備ドロップ
                    dropped, auto_sold = try_drop(
                        player, enemy_data.level,
                        player.current_floor or 1,
                        enemy_data.is_boss, db,
                    )
                    if dropped:
                        result.equipment_drops.append(dropped)
                        tick_logs.append({
                            "type": "equipment_drop",
                            "name": dropped.base_id,
                            "rarity": dropped.rarity,
                            "slot": dropped.slot,
                        })
                    if auto_sold:
                        result.equipment_auto_sold.append(auto_sold)
                        tick_logs.append({
                            "type": "equipment_auto_sold",
                            "name": auto_sold["name"],
                            "rarity": auto_sold["rarity"],
                            "gold": auto_sold["gold"],
                        })

                    # レベルアップ判定
                    lvls = _check_level_up(character)
                    if lvls > 0:
                        result.levels_gained += lvls
                        # レベルアップ後にステータス再計算
                        eff = get_effective_stats(character, db)
                        effective_atk = eff["atk"]
                        effective_def = eff["def"]
                        effective_spd = eff["spd"]
                        effective_max_hp = character.max_hp + eff["hp_bonus"]
                        total_lifesteal = eff["lifesteal"]
                        tick_logs.append({
                            "type": "level_up",
                            "actor": character.name,
                            "level": character.level,
                            "stats": {
                                "max_hp": effective_max_hp,
                                "atk": effective_atk,
                                "def": effective_def,
                                "spd": effective_spd,
                            },
                        })

                    # フロアクリア → 次フロアへ
                    result.floors_cleared += 1
                    current_floor = player.current_floor or 1
                    if current_floor > (player.highest_floor or 0):
                        player.highest_floor = current_floor
                    _update_tower_record(
                        player, player.current_tower_id, current_floor,
                        enemy_data.is_boss, db,
                    )

                    next_floor = current_floor + 1

                    if next_floor > tower.total_floors or next_floor > (player.target_floor or tower.total_floors):
                        # 目標到達
                        tick_logs.append({"type": "tower_target_reached", "floor": current_floor})
                        if player.tower_mode == "auto_repeat":
                            player.current_floor = 1
                            player.current_enemy_id = None
                            player.current_enemy_hp = None
                            player.run_gold = 0
                            tick_logs.append({"type": "tower_restart"})
                        else:
                            # stop_on_clear
                            player.current_tower_id = None
                            player.current_floor = None
                            player.target_floor = None
                            player.current_enemy_id = None
                            player.current_enemy_hp = None
                            player.run_gold = 0
                            tick_logs.append({"type": "tower_exit"})
                            break
                    else:
                        # 退却条件チェック
                        if player.hp_threshold > 0 and character.hp < effective_max_hp * player.hp_threshold:
                            # HP閾値による退却。自動周回モードなら1階から再スタート（game_spec §2.2）
                            tick_logs.append({"type": "retreat_hp", "hp": character.hp, "max_hp": effective_max_hp})
                            if player.tower_mode == "auto_repeat":
                                player.current_floor = 1
                                player.current_enemy_id = None
                                player.current_enemy_hp = None
                                player.run_gold = 0
                                tick_logs.append({"type": "tower_restart"})
                                continue
                            player.current_tower_id = None
                            player.current_floor = None
                            player.target_floor = None
                            player.current_enemy_id = None
                            player.current_enemy_hp = None
                            player.run_gold = 0
                            break
                        player.current_floor = next_floor
                        player.current_enemy_id = None
                        player.current_enemy_hp = None

            else:  # enemy
                dmg, crit = _calc_damage(enemy_data.atk, effective_def, is_player=False)
                character.hp = max(0, character.hp - dmg)
                tick_logs.append({
                    "type": "attack",
                    "actor": enemy_data.name,
                    "target": character.name,
                    "damage": dmg,
                    "critical": crit,
                    "target_hp": character.hp,
                })

                # プレイヤー全滅
                if character.hp <= 0:
                    result.defeated = True
                    # ペナルティ: 現在レベル内の蓄積EXPの50%失、走行Gold失（game_spec §2.2 全滅時の処理）
                    exp_penalty = math.floor(character.exp * 0.5)
                    character.exp = character.exp - exp_penalty
                    gold_penalty = player.run_gold
                    player.gold = max(0, player.gold - gold_penalty)
                    result.total_gold -= gold_penalty

                    tick_logs.append({
                        "type": "player_defeated",
                        "exp_lost": exp_penalty,
                        "gold_lost": gold_penalty,
                    })

                    # 塔退出、HP=1で復活
                    player.current_tower_id = None
                    player.current_floor = None
                    player.target_floor = None
                    player.current_enemy_id = None
                    player.current_enemy_hp = None
                    player.run_gold = 0
                    character.hp = 1
                    break

        # 塔を出た場合はターンループも終了
        if not player.current_tower_id:
            break

    if tick_logs:
        result.battle_logs.append(tick_logs)
    return result
