"""パーティ関連スキーマ"""

from pydantic import Field

from app.config import PARTY_MAX_SIZE, PARTY_MIN_SIZE
from app.schemas import CamelModel


class PartyEditRequest(CamelModel):
    # 件数はスキーマ検証で弾く（tech_party.md §1 #4 → 422）
    member_ids: list[str] = Field(min_length=PARTY_MIN_SIZE, max_length=PARTY_MAX_SIZE)


class PartyEditResponse(CamelModel):
    """更新後の編成。配列順が表示順（tech_party.md §1）"""

    member_ids: list[str]
