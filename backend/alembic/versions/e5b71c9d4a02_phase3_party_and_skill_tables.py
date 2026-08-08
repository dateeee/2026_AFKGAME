"""Phase3 party and skill tables

Phase 3: パーティ編成（PartyMember）・習得スキル（LearnedSkill）・
アクティブスキル枠（ActiveSkillSlot）を追加する。
仕様: docs/tech/detail/tech_party.md §1・§3・§4、
定義書: docs/tech/basic/tech_db/player.md §5〜§7、
ER図: docs/diagrams/er_diagram/player.md

Revision ID: e5b71c9d4a02
Revises: a1f6c93de204
Create Date: 2026-08-08 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'e5b71c9d4a02'
down_revision: Union[str, Sequence[str], None] = 'a1f6c93de204'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.create_table('party_members',
    sa.Column('id', sa.String(length=36), nullable=False),
    sa.Column('player_id', sa.String(length=36), nullable=False),
    sa.Column('slot_index', sa.Integer(), nullable=False),
    sa.Column('character_id', sa.String(length=36), nullable=False),
    sa.ForeignKeyConstraint(['player_id'], ['players.id'], ),
    sa.ForeignKeyConstraint(['character_id'], ['characters.id'], ),
    sa.PrimaryKeyConstraint('id'),
    sa.UniqueConstraint('player_id', 'slot_index', name='uq_party_members_player_slot'),
    sa.UniqueConstraint('player_id', 'character_id', name='uq_party_members_player_character')
    )
    op.create_table('learned_skills',
    sa.Column('id', sa.String(length=36), nullable=False),
    sa.Column('character_id', sa.String(length=36), nullable=False),
    # skill_id はスキルマスターのID。マスターをDBに持たないためFKは張らない
    sa.Column('skill_id', sa.String(length=50), nullable=False),
    sa.Column('cooldown_remaining', sa.Integer(), nullable=False),
    sa.Column('learned_at', sa.DateTime(timezone=True), nullable=False),
    sa.ForeignKeyConstraint(['character_id'], ['characters.id'], ),
    sa.PrimaryKeyConstraint('id'),
    sa.UniqueConstraint('character_id', 'skill_id', name='uq_learned_skills_character_skill')
    )
    op.create_table('active_skill_slots',
    sa.Column('id', sa.String(length=36), nullable=False),
    sa.Column('character_id', sa.String(length=36), nullable=False),
    sa.Column('slot_index', sa.Integer(), nullable=False),
    sa.Column('skill_id', sa.String(length=50), nullable=False),
    sa.ForeignKeyConstraint(['character_id'], ['characters.id'], ),
    sa.PrimaryKeyConstraint('id'),
    sa.UniqueConstraint('character_id', 'slot_index', name='uq_active_skill_slots_character_slot')
    )


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_table('active_skill_slots')
    op.drop_table('learned_skills')
    op.drop_table('party_members')
