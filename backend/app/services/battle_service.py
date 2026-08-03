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
from app.master_data.enemies import get_enemy
from app.master_data.towers import get_tower, roll_encounter
from app.master_data.items import get_item
from app.config import (
    CRIT_MULTIPLIER,
    CRIT_RATE,
    DAMAGE_VARIANCE,
    FAST_CALC_THRESHOLD,
    OFFLINE_SAMPLE_TICKS,
    DEFAULT_POTION_THRESHOLD,
    DEFEAT_EXP_PENALTY,
    DEFENSE_FACTOR,
    RECOVERY_DEF_FACTOR,
    RECOVERY_HP_RATIO,
    TURNS_PER_TICK,
)
from app.rng import DEFAULT_RNG
from app.services.equipment_service import get_effective_stats, try_drop


#: `process_tick` の `potion_item` が省略されたことを表す番兵（None は「未所持」を意味するため）
_NOT_FETCHED = object()


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


def _calc_damage(
    atk: int, target_def: int, is_player: bool, rng: random.Random = DEFAULT_RNG
) -> tuple[int, bool]:
    """ダメージ計算。戻り値: (damage, is_crit)"""
    variance = rng.uniform(-DAMAGE_VARIANCE, DAMAGE_VARIANCE)
    raw = atk * (1 + variance) - target_def * DEFENSE_FACTOR
    is_crit = rng.random() < CRIT_RATE
    if is_crit:
        raw *= CRIT_MULTIPLIER
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


def _get_potion_item(player: Player, db: Session) -> InventoryItem | None:
    """HPポーションの所持レコードを引く。tick毎に引き直さないよう呼び出し元で保持する"""
    return db.query(InventoryItem).filter_by(
        player_id=player.id, item_id="hp_potion"
    ).first()


def _get_potion_count(player: Player, db: Session) -> int:
    item = _get_potion_item(player, db)
    return item.quantity if item else 0


def _use_potion(character: Character, effective_max_hp: int, item: InventoryItem | None) -> bool:
    """ポーションを使用。使用したらTrue"""
    if not item or item.quantity <= 0:
        return False
    potion = get_item("hp_potion")
    heal = math.floor(effective_max_hp * potion.heal_ratio)
    character.hp = min(effective_max_hp, character.hp + heal)
    item.quantity -= 1
    return True


def target_floor_cap(highest_floor: int, total_floors: int) -> int:
    """目標階の選択上限 = min(到達済み最高階 + 1, 総階数)。

    +1 は「未到達の次の階を1階ずつ開拓できるようにする」ため（systems/battle.md 目標階設定）。

    不変条件: `TowerData.total_floors` は常に非 None（型どおり）。総階数を持たない無限塔
    （深淵の塔、endgame.md §2.14）は Phase 5 で追加されるため、その際に本関数・
    `process_tick` の目標到達判定・`schemas.TowerInfo` をまとめて None 対応させる
    （known_issues.md）。片側だけ None 対応を持たせると判定が非対称になる。
    """
    return min(highest_floor + 1, total_floors)


def get_tower_highest_floor(player: Player, tower_id: str, db: Session) -> int:
    """指定塔の到達済み最高階。記録がなければ0（未挑戦）"""
    record = db.query(TowerClearRecord).filter_by(
        player_id=player.id, tower_id=tower_id
    ).first()
    if not record:
        return 0
    return record.highest_floor or 0


def _update_tower_record(player: Player, tower_id: str, floor: int, is_boss: bool, db: Session) -> int:
    """塔別クリア記録を更新。ボス討伐で cleared=True（次の塔の解放条件）。

    戻り値は**更新前**の到達済み最高階（上限追従の判定に使う）。
    """
    record = db.query(TowerClearRecord).filter_by(
        player_id=player.id, tower_id=tower_id
    ).first()
    if not record:
        record = TowerClearRecord(player_id=player.id, tower_id=tower_id)
        db.add(record)
    old_highest = record.highest_floor or 0
    if floor > old_highest:
        record.highest_floor = floor
    if is_boss:
        record.cleared = True
    return old_highest


def _follow_target_floor(
    player: Player, total_floors: int, old_highest: int, cleared_floor: int
) -> int | None:
    """上限追従: 目標階が上限と一致している状態で新しい階をクリアしたら目標階を +1 する。

    追従した場合は新しい目標階を返す。追従しない場合は None。
    目標階を上限より低く設定している場合は追従しない（プレイヤーが意図した周回階を維持する）。
    """
    if cleared_floor <= old_highest:
        return None  # 既踏の階の再クリア
    old_cap = target_floor_cap(old_highest, total_floors)
    new_cap = target_floor_cap(cleared_floor, total_floors)
    if player.target_floor != old_cap or new_cap <= old_cap:
        return None
    player.target_floor = new_cap
    return new_cap


def _recover_hp(character: Character, effective_max_hp: int, effective_def: int) -> int:
    """塔外HP自然回復。回復量を返す"""
    recovery = math.floor(
        effective_max_hp * RECOVERY_HP_RATIO + effective_def * RECOVERY_DEF_FACTOR
    )
    old_hp = character.hp
    character.hp = min(effective_max_hp, character.hp + recovery)
    return character.hp - old_hp


def process_tick(
    player: Player,
    character: Character,
    db: Session,
    rng: random.Random = DEFAULT_RNG,
    potion_item: InventoryItem | None | object = _NOT_FETCHED,
) -> TickResult:
    """1tick（3ターン）を処理。乱数は呼び出し元から受け取る（tech_rng.md §2）。

    `potion_item` は一括処理で毎tick引き直さないための持ち回り（ISSUE-110）。
    省略時のみ本関数が引く。
    """
    result = TickResult()
    tick_logs: list[dict] = []
    if potion_item is _NOT_FETCHED:
        potion_item = _get_potion_item(player, db)

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
            enemy_data = roll_encounter(
                player.current_tower_id, player.current_floor or 1, rng
            )
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
        threshold = DEFAULT_POTION_THRESHOLD
        if player.settings:
            threshold = player.settings.potion_threshold
        if character.hp <= effective_max_hp * threshold:
            if _use_potion(character, effective_max_hp, potion_item):
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
            # 撃破済み・場から除かれた敵は行動しない（tech_battle.md §5 #4・#5）。
            # 階クリア／周回リスタート／撤退は current_enemy_id・current_enemy_hp を
            # ともに None にするため、敵HPだけを見る判定では捕捉できない。
            if (
                character.hp <= 0
                or player.current_enemy_id is None
                or (player.current_enemy_hp is not None and player.current_enemy_hp <= 0)
            ):
                break

            if actor == "player":
                dmg, crit = _calc_damage(effective_atk, enemy_data.def_, True, rng)
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
                        enemy_data.is_boss, db, rng,
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
                    old_highest = _update_tower_record(
                        player, player.current_tower_id, current_floor,
                        enemy_data.is_boss, db,
                    )

                    # 上限追従: 目標階が上限と一致していたら自動で +1（オフライン中も適用）
                    followed = _follow_target_floor(
                        player, tower.total_floors, old_highest, current_floor,
                    )
                    if followed is not None:
                        tick_logs.append({"type": "target_floor_follow", "target_floor": followed})

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
                dmg, crit = _calc_damage(enemy_data.atk, effective_def, False, rng)
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
                    exp_penalty = math.floor(character.exp * DEFEAT_EXP_PENALTY)
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


def process_pending_ticks(
    player: Player,
    character: Character,
    pending_ticks: int,
    db: Session,
    rng: random.Random = DEFAULT_RNG,
) -> tuple[TickResult, str]:
    """未処理tickをまとめて処理し、(結果, 計算方式) を返す。

    tick数が FAST_CALC_THRESHOLD 以下ならフルシミュレーション、超える場合は
    OFFLINE_SAMPLE_TICKS 件のサンプル平均を残りtickへ外挿する簡易計算を使う。

    注意: 簡易計算の実装は tech_offline.md §4.1（乱数を使わない期待値計算）と乖離している
    （known_issues.md）。本関数はルーターからロジックを移設しただけで、算出方法は変えていない。
    """
    # ポーションの所持レコードはtickをまたいで同一。最大100tick分を引き直さない（ISSUE-110）
    potion_item = _get_potion_item(player, db)

    if pending_ticks <= FAST_CALC_THRESHOLD:
        accumulated = TickResult()
        for _ in range(pending_ticks):
            accumulated.accumulate(process_tick(player, character, db, rng, potion_item))
        return accumulated, "normal"

    sample_count = min(OFFLINE_SAMPLE_TICKS, pending_ticks)
    sample_result = TickResult()
    for _ in range(sample_count):
        sample_result.accumulate(process_tick(player, character, db, rng, potion_item))

    remaining = pending_ticks - sample_count
    if remaining > 0:
        multiplier = remaining / sample_count
        # 外挿分は run_gold に計上しない（塔外での一括受け取り扱い）。
        # 簡略計算そのものが tech_offline.md §4.1 と乖離しており（known_issues.md）、
        # 是正時に外挿の会計もあわせて見直す（ISSUE-106）
        player.gold += int(sample_result.total_gold * multiplier)
        character.exp += int(sample_result.total_exp * multiplier)
        extra_levels = _check_level_up(character)

        sample_result.total_gold += int(sample_result.total_gold * multiplier)
        sample_result.total_exp += int(sample_result.total_exp * multiplier)
        sample_result.enemies_defeated += int(sample_result.enemies_defeated * multiplier)
        sample_result.potions_used += int(sample_result.potions_used * multiplier)
        sample_result.levels_gained += extra_levels
        sample_result.floors_cleared += int(sample_result.floors_cleared * multiplier)

    return sample_result, "simplified"
