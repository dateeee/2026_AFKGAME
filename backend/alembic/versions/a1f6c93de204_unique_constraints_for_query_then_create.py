"""Unique constraints for query-then-create tables

製造完了ゲート（backend-review 2026-08-04）の指摘対応:
  - ISSUE-108: query-then-create するテーブルに一意制約を付ける。
    二重リクエスト（ポーリングと復帰処理の同時実行等）で重複行が作られると
    `.first()` がどちらを返すか不定になり、進捗・所持数が欠落する。
    ShopDailyState.player_id（c7d1a4f2b830）と同じ扱いへ揃える。

対象:
  - players.user_id                       … 1ユーザー = 1プレイヤー
  - player_settings.player_id             … 1プレイヤー = 1設定
  - tower_clear_records(player_id, tower_id) … 塔ごとに1件
  - inventory_items(player_id, item_id)   … アイテムごとに1件

Revision ID: a1f6c93de204
Revises: c7d1a4f2b830
Create Date: 2026-08-04 09:40:00.000000

"""
from typing import Sequence, Union

from alembic import op


# revision identifiers, used by Alembic.
revision: str = 'a1f6c93de204'
down_revision: Union[str, Sequence[str], None] = 'c7d1a4f2b830'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    with op.batch_alter_table("players") as batch_op:
        batch_op.create_unique_constraint("uq_players_user_id", ["user_id"])

    with op.batch_alter_table("player_settings") as batch_op:
        batch_op.create_unique_constraint("uq_player_settings_player_id", ["player_id"])

    with op.batch_alter_table("tower_clear_records") as batch_op:
        batch_op.create_unique_constraint(
            "uq_tower_clear_records_player_tower", ["player_id", "tower_id"]
        )

    with op.batch_alter_table("inventory_items") as batch_op:
        batch_op.create_unique_constraint(
            "uq_inventory_items_player_item", ["player_id", "item_id"]
        )


def downgrade() -> None:
    """Downgrade schema."""
    with op.batch_alter_table("inventory_items") as batch_op:
        batch_op.drop_constraint("uq_inventory_items_player_item", type_="unique")

    with op.batch_alter_table("tower_clear_records") as batch_op:
        batch_op.drop_constraint("uq_tower_clear_records_player_tower", type_="unique")

    with op.batch_alter_table("player_settings") as batch_op:
        batch_op.drop_constraint("uq_player_settings_player_id", type_="unique")

    with op.batch_alter_table("players") as batch_op:
        batch_op.drop_constraint("uq_players_user_id", type_="unique")
