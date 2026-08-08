"""スキルルーター"""

import logging

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.dependencies import get_current_player
from app.models.player import Player
from app.schemas.common import StatusResponse
from app.schemas.skill import SetActiveSkillsRequest, SkillLearnRequest, SkillResetRequest
from app.services.party_service import learn_skill, reset_skills, set_active_skills

logger = logging.getLogger("afkgame.skill")

router = APIRouter(prefix="/api/skill", tags=["skill"])


@router.post("/learn", response_model=StatusResponse)
def learn(
    req: SkillLearnRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> StatusResponse:
    """スキルを習得する（探索中も可）"""
    character = learn_skill(player, req.character_id, req.skill_id, db)
    db.commit()
    logger.info("スキル習得", extra={
        "player_id": str(player.id),
        "character_id": req.character_id,
        "skill_id": req.skill_id,
        "skill_points": character.skill_points,
    })
    return StatusResponse()


@router.put("/set-active", response_model=StatusResponse)
def set_active(
    req: SetActiveSkillsRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> StatusResponse:
    """アクティブスキルのセットを変更する（探索中も可）"""
    active_slots = set_active_skills(player, req.character_id, req.active_slots, db)
    db.commit()
    logger.info("スキルセット変更", extra={
        "player_id": str(player.id),
        "character_id": req.character_id,
        "slot_count": len(active_slots),
    })
    return StatusResponse()


@router.post("/reset", response_model=StatusResponse)
def reset(
    req: SkillResetRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> StatusResponse:
    """習得スキルを全リセットしSPを全返却する"""
    cost = reset_skills(player, req.character_id, db)
    db.commit()
    logger.info("スキルリセット", extra={
        "player_id": str(player.id),
        "character_id": req.character_id,
        "gold_cost": cost,
    })
    return StatusResponse()
