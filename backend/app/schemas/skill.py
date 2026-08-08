"""スキル関連スキーマ"""

from pydantic import Field

from app.config import ACTIVE_SKILL_SLOT_COUNT
from app.schemas import CamelModel


class SkillLearnRequest(CamelModel):
    character_id: str
    skill_id: str


class SetActiveSkillsRequest(CamelModel):
    character_id: str
    # 0件（全解除）〜2件。件数超過はスキーマ検証で弾く（tech_party.md §4 #2 → 422）
    active_slots: list[str] = Field(max_length=ACTIVE_SKILL_SLOT_COUNT)


class SkillResetRequest(CamelModel):
    character_id: str
