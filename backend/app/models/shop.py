"""日替わりショップモデル（tech_shop.md §5）"""

from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.database import Base
from app.models.player import _new_uuid


class ShopDailyState(Base):
    """日替わりショップの更新サイクル（プレイヤーごとに1件）"""

    __tablename__ = "shop_daily_states"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    player_id: Mapped[str] = mapped_column(String(36), ForeignKey("players.id"), nullable=False)
    reset_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class ShopDailySlot(Base):
    """日替わりショップの1枠（状態1件につき5件）

    装備スロットと両手武器フラグは base_id からマスターデータで一意に定まるため保存しない。
    """

    __tablename__ = "shop_daily_slots"
    __table_args__ = (
        UniqueConstraint("shop_daily_state_id", "slot_index", name="uq_shop_daily_slot_index"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_new_uuid)
    shop_daily_state_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("shop_daily_states.id"), nullable=False
    )
    slot_index: Mapped[int] = mapped_column(Integer, nullable=False)
    category: Mapped[str] = mapped_column(String(20), nullable=False)
    base_id: Mapped[str] = mapped_column(String(50), nullable=False)
    rarity: Mapped[str] = mapped_column(String(20), nullable=False)
    level: Mapped[int] = mapped_column(Integer, nullable=False)
    stat_atk: Mapped[int | None] = mapped_column(Integer, nullable=True)
    stat_def: Mapped[int | None] = mapped_column(Integer, nullable=True)
    stat_hp: Mapped[int | None] = mapped_column(Integer, nullable=True)
    stat_spd: Mapped[int | None] = mapped_column(Integer, nullable=True)
    price: Mapped[int] = mapped_column(Integer, nullable=False)
    sold: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)

    state: Mapped["ShopDailyState"] = relationship()
