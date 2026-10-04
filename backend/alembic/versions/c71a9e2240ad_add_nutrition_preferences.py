"""add nutrition and user preferences

Revision ID: c71a9e2240ad
Revises: f8a4606a8f3b
"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa

revision: str = "c71a9e2240ad"
down_revision: Union[str, None] = "f8a4606a8f3b"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("menus", sa.Column("calories_per_portion", sa.Integer(), nullable=False, server_default="350"))
    op.create_table(
        "user_preferences",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("allergens", sa.JSON(), nullable=False),
        sa.Column("disliked_ingredients", sa.JSON(), nullable=False),
        sa.Column("calorie_target", sa.Integer(), nullable=True),
        sa.Column("activity", sa.String(30), nullable=False),
        sa.Column("fitness_goal", sa.String(30), nullable=False),
        sa.Column("meals_per_day", sa.Integer(), nullable=False),
        sa.UniqueConstraint("user_id"),
    )
    op.create_index("ix_user_preferences_user_id", "user_preferences", ["user_id"], unique=True)


def downgrade() -> None:
    op.drop_index("ix_user_preferences_user_id", table_name="user_preferences")
    op.drop_table("user_preferences")
    op.drop_column("menus", "calories_per_portion")
