"""装備モデル"""

from datetime import datetime

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.database import Base
from app.models.player import _new_uuid, _utcnow


class Equipment(Base):
    __tablename__ = "equipment"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    player_id: Mapped[str] = mapped_column(String(36), ForeignKey("players.id"), nullable=False)
    base_id: Mapped[str] = mapped_column(String(50), nullable=False)
    slot: Mapped[str] = mapped_column(String(20), nullable=False)
    rarity: Mapped[str] = mapped_column(String(20), nullable=False)
    level: Mapped[int] = mapped_column(Integer, nullable=False)
    enhance_level: Mapped[int] = mapped_column(Integer, default=0)
    stat_atk: Mapped[int | None] = mapped_column(Integer, nullable=True)
    stat_def: Mapped[int | None] = mapped_column(Integer, nullable=True)
    stat_hp: Mapped[int | None] = mapped_column(Integer, nullable=True)
    stat_spd: Mapped[int | None] = mapped_column(Integer, nullable=True)
    lifesteal: Mapped[float | None] = mapped_column(Float, nullable=True)
    is_two_handed: Mapped[bool] = mapped_column(Boolean, default=False)
    locked: Mapped[bool] = mapped_column(Boolean, default=False)
    acquired_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    player: Mapped["Player"] = relationship(back_populates="equipment")  # type: ignore[name-defined]


class CharacterEquipSlot(Base):
    __tablename__ = "character_equip_slots"

    character_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("characters.id"), primary_key=True
    )
    slot: Mapped[str] = mapped_column(String(20), primary_key=True)
    equipment_id: Mapped[str | None] = mapped_column(
        String(36), ForeignKey("equipment.id"), nullable=True
    )

    character: Mapped["Character"] = relationship(back_populates="equip_slots")  # type: ignore[name-defined]
    equipment: Mapped["Equipment | None"] = relationship()
