"""パーティ編成モデル"""

from sqlalchemy import ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.db.database import Base
from app.models.player import _new_uuid


class PartyMember(Base):
    __tablename__ = "party_members"
    __table_args__ = (
        UniqueConstraint("player_id", "slot_index", name="uq_party_members_player_slot"),
        # 同一キャラの重複編成をDB側でも防ぐ（サービス層の 422 PARTY_MEMBER_DUPLICATED の二重防御）
        UniqueConstraint("player_id", "character_id", name="uq_party_members_player_character"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    player_id: Mapped[str] = mapped_column(String(36), ForeignKey("players.id"), nullable=False)
    # 表示順のみに使う。行動順はSPD順とキャラID順が正（tech_battle.md §3.1）
    slot_index: Mapped[int] = mapped_column(Integer, nullable=False)
    character_id: Mapped[str] = mapped_column(String(36), ForeignKey("characters.id"), nullable=False)
