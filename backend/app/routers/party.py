"""パーティルーター"""

import logging

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.dependencies import get_current_player
from app.models.player import Player
from app.schemas.party import PartyEditRequest, PartyEditResponse
from app.services.party_service import edit_party

logger = logging.getLogger("afkgame.party")

router = APIRouter(prefix="/api/party", tags=["party"])


@router.put("/edit", response_model=PartyEditResponse)
def edit(
    req: PartyEditRequest,
    player: Player = Depends(get_current_player),
    db: Session = Depends(get_db),
) -> PartyEditResponse:
    """パーティ編成を変更する（塔外限定）"""
    member_ids = edit_party(player, req.member_ids, db)
    db.commit()
    logger.info("パーティ編成変更", extra={
        "player_id": str(player.id),
        "member_count": len(member_ids),
    })
    return PartyEditResponse(member_ids=member_ids)
