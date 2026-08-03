"""キャラクターモデル"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.database import Base
from app.models.player import _new_uuid, _utcnow


class Character(Base):
    __tablename__ = "characters"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    player_id: Mapped[str] = mapped_column(String(36), ForeignKey("players.id"), nullable=False)
    name: Mapped[str] = mapped_column(String(50), nullable=False)
    type: Mapped[str] = mapped_column(String(20), default="melee")
    level: Mapped[int] = mapped_column(Integer, default=1)
    exp: Mapped[int] = mapped_column(BigInteger, default=0)
    hp: Mapped[int] = mapped_column(Integer, nullable=False)
    max_hp: Mapped[int] = mapped_column(Integer, nullable=False)
    base_atk: Mapped[int] = mapped_column(Integer, nullable=False)
    base_def: Mapped[int] = mapped_column(Integer, nullable=False)
    base_spd: Mapped[int] = mapped_column(Integer, nullable=False)
    limit_break: Mapped[int] = mapped_column(Integer, default=0)
    skill_points: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    player: Mapped["Player"] = relationship(back_populates="characters")  # type: ignore[name-defined]
    equip_slots: Mapped[list["CharacterEquipSlot"]] = relationship(back_populates="character")  # type: ignore[name-defined]
