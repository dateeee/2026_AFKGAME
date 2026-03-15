"""プレイヤー関連モデル"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import BigInteger, Boolean, DateTime, Float, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.database import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _new_uuid() -> str:
    return str(uuid.uuid4())


class Player(Base):
    __tablename__ = "players"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    user_id: Mapped[str | None] = mapped_column(String(50), ForeignKey("users.id"), nullable=True)
    gold: Mapped[int] = mapped_column(BigInteger, default=0)
    current_tower_id: Mapped[str | None] = mapped_column(String(50), nullable=True)
    current_floor: Mapped[int | None] = mapped_column(Integer, nullable=True)
    target_floor: Mapped[int | None] = mapped_column(Integer, nullable=True)
    tower_mode: Mapped[str] = mapped_column(String(20), default="auto_repeat")
    hp_threshold: Mapped[float] = mapped_column(Float, default=0.3)
    current_enemy_id: Mapped[str | None] = mapped_column(String(50), nullable=True)
    current_enemy_hp: Mapped[int | None] = mapped_column(Integer, nullable=True)
    run_gold: Mapped[int] = mapped_column(BigInteger, default=0)
    highest_floor: Mapped[int] = mapped_column(Integer, default=0)
    last_tick_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    user: Mapped["User | None"] = relationship(back_populates="player")  # type: ignore[name-defined]
    settings: Mapped["PlayerSettings | None"] = relationship(back_populates="player", uselist=False)
    characters: Mapped[list["Character"]] = relationship(back_populates="player")  # type: ignore[name-defined]
    tower_clear_records: Mapped[list["TowerClearRecord"]] = relationship(back_populates="player")
    inventory_items: Mapped[list["InventoryItem"]] = relationship(back_populates="player")  # type: ignore[name-defined]
    battle_logs: Mapped[list["BattleLog"]] = relationship(back_populates="player")  # type: ignore[name-defined]
    equipment: Mapped[list["Equipment"]] = relationship(back_populates="player")  # type: ignore[name-defined]


class PlayerSettings(Base):
    __tablename__ = "player_settings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    player_id: Mapped[str] = mapped_column(String(36), ForeignKey("players.id"), nullable=False)
    potion_threshold: Mapped[float] = mapped_column(Float, default=0.3)
    battle_log_count: Mapped[int] = mapped_column(Integer, default=50)
    toast_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    auto_sell_rarity: Mapped[str | None] = mapped_column(String(20), nullable=True)

    player: Mapped["Player"] = relationship(back_populates="settings")


class TowerClearRecord(Base):
    __tablename__ = "tower_clear_records"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    player_id: Mapped[str] = mapped_column(String(36), ForeignKey("players.id"), nullable=False)
    tower_id: Mapped[str] = mapped_column(String(50), nullable=False)
    cleared: Mapped[bool] = mapped_column(Boolean, default=False)
    highest_floor: Mapped[int] = mapped_column(Integer, default=0)

    player: Mapped["Player"] = relationship(back_populates="tower_clear_records")
