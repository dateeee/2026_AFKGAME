"""戦闘ログ・インベントリモデル"""

from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, String
from sqlalchemy.types import JSON
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.database import Base
from app.models.player import _new_uuid, _utcnow


class BattleLog(Base):
    __tablename__ = "battle_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    player_id: Mapped[str] = mapped_column(String(36), ForeignKey("players.id"), nullable=False)
    tick_number: Mapped[int] = mapped_column(Integer, nullable=False)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    entries: Mapped[dict] = mapped_column(JSON, nullable=False)

    player: Mapped["Player"] = relationship(back_populates="battle_logs")  # type: ignore[name-defined]


class InventoryItem(Base):
    __tablename__ = "inventory_items"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    player_id: Mapped[str] = mapped_column(String(36), ForeignKey("players.id"), nullable=False)
    item_id: Mapped[str] = mapped_column(String(50), nullable=False)
    quantity: Mapped[int] = mapped_column(Integer, default=0)

    player: Mapped["Player"] = relationship(back_populates="inventory_items")  # type: ignore[name-defined]
