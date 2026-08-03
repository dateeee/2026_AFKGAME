"""Token purpose separation and shop daily state unique constraint

製造完了ゲート（backend-review 2026-08-03）の指摘対応:
  - ISSUE-004: email_verification_tokens に purpose を追加し、メール確認と
    パスワードリセットのトークンを用途分離する（仕様: docs/tech/tech_auth.md §6）
  - ISSUE-015: shop_daily_states.player_id を一意にする（プレイヤーごとに1件。
    仕様: docs/tech/tech_shop.md §5）

Revision ID: c7d1a4f2b830
Revises: 36e28dd936bc
Create Date: 2026-08-03 10:20:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'c7d1a4f2b830'
down_revision: Union[str, Sequence[str], None] = '36e28dd936bc'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    # 既存行は全てメール確認用途として発行されたものなので、server_default で埋め戻す
    with op.batch_alter_table("email_verification_tokens") as batch_op:
        batch_op.add_column(
            sa.Column(
                "purpose",
                sa.String(length=20),
                nullable=False,
                server_default="verify_email",
            )
        )
    # 埋め戻しが済んだらDB側の既定値は外し、値の決定はアプリ側に一本化する
    with op.batch_alter_table("email_verification_tokens") as batch_op:
        batch_op.alter_column("purpose", server_default=None)

    with op.batch_alter_table("shop_daily_states") as batch_op:
        batch_op.create_unique_constraint(
            "uq_shop_daily_states_player_id", ["player_id"]
        )


def downgrade() -> None:
    """Downgrade schema."""
    with op.batch_alter_table("shop_daily_states") as batch_op:
        batch_op.drop_constraint("uq_shop_daily_states_player_id", type_="unique")

    with op.batch_alter_table("email_verification_tokens") as batch_op:
        batch_op.drop_column("purpose")
