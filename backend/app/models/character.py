"""キャラクターモデル"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, UniqueConstraint
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


class LearnedSkill(Base):
    __tablename__ = "learned_skills"
    __table_args__ = (
        # 二重習得をDB側でも防ぐ（サービス層の 400 SKILL_ALREADY_LEARNED の二重防御）
        UniqueConstraint("character_id", "skill_id", name="uq_learned_skills_character_skill"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    character_id: Mapped[str] = mapped_column(String(36), ForeignKey("characters.id"), nullable=False)
    # スキルマスターのID。マスターはDBに持たないためFKは張らない
    skill_id: Mapped[str] = mapped_column(String(50), nullable=False)
    # CDは習得スキルごとに保持し、セット変更では変化しない（tech_party.md §4）
    cooldown_remaining: Mapped[int] = mapped_column(Integer, default=0)
    learned_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)


class ActiveSkillSlot(Base):
    __tablename__ = "active_skill_slots"
    __table_args__ = (
        UniqueConstraint("character_id", "slot_index", name="uq_active_skill_slots_character_slot"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    character_id: Mapped[str] = mapped_column(String(36), ForeignKey("characters.id"), nullable=False)
    slot_index: Mapped[int] = mapped_column(Integer, nullable=False)
    skill_id: Mapped[str] = mapped_column(String(50), nullable=False)
