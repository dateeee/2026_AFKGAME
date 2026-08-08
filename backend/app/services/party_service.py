"""パーティ・スキルサービス — 編成・キャラ獲得・SP/EXP付与・スキル習得/セット/リセット

処理仕様は tech_party.md §1〜§6、EXP配分は tech_skill.md §8。
エラーコードは tech_party.md §7。
"""

from sqlalchemy.orm import Session

from app.config import SKILL_POINTS_PER_LEVEL_UP, SKILL_RESET_COST_PER_LEVEL
from app.exceptions import AppError
from app.master_data.characters import FLOOR_CHARACTERS, calc_stats_for_level
from app.master_data.skills import ACTIVE_SKILL_IDS, SKILLS, get_skill
from app.models.character import ActiveSkillSlot, Character, LearnedSkill
from app.models.party import PartyMember
from app.models.player import Player


def _get_owned_character(player: Player, character_id: str, db: Session) -> Character:
    """所持キャラを引く。未所持・不存在は 404（tech_party.md §3 #2・§5 #1）"""
    character = db.query(Character).filter_by(id=character_id, player_id=player.id).first()
    if character is None:
        raise AppError("CHARACTER_NOT_FOUND", "キャラクターが見つかりません", 404)
    return character


def _learned_skill_ids(character_id: str, db: Session) -> set[str]:
    return {r.skill_id for r in db.query(LearnedSkill).filter_by(character_id=character_id).all()}


# ══════════════════════════════════════════════════════════════════
# §1 パーティ編成変更
# ══════════════════════════════════════════════════════════════════


def edit_party(player: Player, member_ids: list[str], db: Session) -> list[str]:
    """パーティを全置換する。戻り値は更新後の編成（配列順が表示順）"""
    # 探索中は編成を固定する（tech_state.md §4）
    if player.current_tower_id is not None:
        raise AppError("PARTY_LOCKED_IN_TOWER", "探索中はパーティを変更できません", 400)

    requested = set(member_ids)
    if len(requested) != len(member_ids):
        raise AppError("PARTY_MEMBER_DUPLICATED", "同じキャラクターを重複して編成できません", 422)

    owned = {
        row[0]
        for row in db.query(Character.id).filter(
            Character.player_id == player.id, Character.id.in_(member_ids)
        )
    }
    if not requested <= owned:
        raise AppError("PARTY_MEMBER_NOT_OWNED", "所持していないキャラクターは編成できません", 422)

    db.query(PartyMember).filter_by(player_id=player.id).delete()
    db.flush()  # 一意制約（player_id, slot_index）が旧行と衝突しないよう先にDELETEを流す
    for slot_index, character_id in enumerate(member_ids):
        db.add(PartyMember(player_id=player.id, slot_index=slot_index, character_id=character_id))
    return member_ids


# ══════════════════════════════════════════════════════════════════
# §2 キャラクター獲得（塔クリア報酬・tick処理内）
# ══════════════════════════════════════════════════════════════════


def grant_floor_character(player: Player, tower_id: str, floor: int, db: Session) -> Character | None:
    """対象階の確定入手キャラを未所持なら付与する。付与しなかった場合は None

    加入時は LV1・EXP0・SP0・スキル未習得・HP=maxHP。パーティへは自動編入しない（控え加入）。
    """
    unlock = FLOOR_CHARACTERS.get((tower_id, floor))
    if unlock is None:
        return None

    # `characters` はマスターID列を持たないため、所持判定は名前で行う
    if db.query(Character).filter_by(player_id=player.id, name=unlock.name).first():
        return None

    stats = calc_stats_for_level(unlock.type, 1)
    character = Character(
        player_id=player.id,
        name=unlock.name,
        type=unlock.type,
        level=1,
        exp=0,
        skill_points=0,
        hp=stats["max_hp"],
        **stats,
    )
    db.add(character)
    db.flush()  # id を確定（呼び出し元がサマリーへ載せる）
    return character


def grant_characters_for_floors(
    player: Player, tower_id: str, floors: list[int], db: Session
) -> list[Character]:
    """オフライン簡略計算でクリアした階をまとめて判定する（周回の再クリアでは付与しない）"""
    joined: list[Character] = []
    for floor in floors:
        character = grant_floor_character(player, tower_id, floor, db)
        if character is not None:
            joined.append(character)
    return joined


# ══════════════════════════════════════════════════════════════════
# §3 スキル習得
# ══════════════════════════════════════════════════════════════════


def learn_skill(player: Player, character_id: str, skill_id: str, db: Session) -> Character:
    """スキルを習得しSPを減算する"""
    character = _get_owned_character(player, character_id, db)

    skill = get_skill(skill_id)
    if skill is None:
        raise AppError("SKILL_UNKNOWN", "存在しないスキルです", 422)

    learned = _learned_skill_ids(character.id, db)
    if skill_id in learned:
        raise AppError("SKILL_ALREADY_LEARNED", "すでに習得しています", 400)

    # 前提が複数ある場合はいずれか1つの習得で充足する（heal_3 のみ）
    if skill.prerequisites and not learned & set(skill.prerequisites):
        raise AppError("SKILL_PREREQUISITE_NOT_MET", "前提スキルを習得していません", 400)

    if character.skill_points < skill.sp_cost:
        raise AppError("SKILL_INSUFFICIENT_SP", "スキルポイントが足りません", 400)

    db.add(LearnedSkill(character_id=character.id, skill_id=skill_id))
    character.skill_points -= skill.sp_cost
    return character


# ══════════════════════════════════════════════════════════════════
# §4 アクティブスキルセット変更
# ══════════════════════════════════════════════════════════════════


def set_active_skills(
    player: Player, character_id: str, active_slots: list[str], db: Session
) -> list[str]:
    """セット枠を全置換する。CDカウンターは習得スキル側が持つため変化しない"""
    character = _get_owned_character(player, character_id, db)

    requested = set(active_slots)
    if len(requested) != len(active_slots):
        raise AppError("SKILL_SLOT_DUPLICATED", "同じスキルを複数の枠に設定できません", 422)

    # パッシブと未知IDはどちらもセット枠に入らない
    if not requested <= ACTIVE_SKILL_IDS:
        raise AppError("SKILL_NOT_ACTIVE", "アクティブスキルではありません", 422)

    if not requested <= _learned_skill_ids(character.id, db):
        raise AppError("SKILL_NOT_LEARNED", "習得していないスキルです", 400)

    db.query(ActiveSkillSlot).filter_by(character_id=character.id).delete()
    db.flush()  # 一意制約（character_id, slot_index）が旧行と衝突しないよう先にDELETEを流す
    for slot_index, skill_id in enumerate(active_slots):
        db.add(ActiveSkillSlot(character_id=character.id, slot_index=slot_index, skill_id=skill_id))
    return active_slots


# ══════════════════════════════════════════════════════════════════
# §5 スキルリセット
# ══════════════════════════════════════════════════════════════════


def reset_skills(player: Player, character_id: str, db: Session) -> int:
    """習得・セットを全解除してSPを全返却する。戻り値は消費したゴールド"""
    character = _get_owned_character(player, character_id, db)

    learned = db.query(LearnedSkill).filter_by(character_id=character.id).all()
    if not learned:
        raise AppError("SKILL_NOTHING_TO_RESET", "習得しているスキルがありません", 400)

    cost = character.level * SKILL_RESET_COST_PER_LEVEL
    if player.gold < cost:
        raise AppError("SKILL_INSUFFICIENT_GOLD", "ゴールドが足りません", 400)

    player.gold -= cost
    refunded = sum(SKILLS[row.skill_id].sp_cost for row in learned)
    for row in learned:
        db.delete(row)
    db.query(ActiveSkillSlot).filter_by(character_id=character.id).delete()
    character.skill_points += refunded
    return cost


# ══════════════════════════════════════════════════════════════════
# §6 SP獲得 / EXP配分（tech_skill.md §8）
# ══════════════════════════════════════════════════════════════════


def grant_skill_points(character: Character, level_ups: int) -> None:
    """レベルアップ回数ぶんのSPを加算する"""
    character.skill_points += level_ups * SKILL_POINTS_PER_LEVEL_UP


def grant_battle_exp(player: Player, exp: int, db: Session) -> None:
    """在籍パーティ全員へ全額付与する（人数で分割しない。戦闘不能でも付与し、控えには付与しない）"""
    members = (
        db.query(Character)
        .join(PartyMember, PartyMember.character_id == Character.id)
        .filter(PartyMember.player_id == player.id)
        .all()
    )
    for character in members:
        character.exp += exp
