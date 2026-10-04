"""add package days and meal positions

Revision ID: e23b984cac39
Revises: d12f041bcb28
"""
from alembic import op
import sqlalchemy as sa

revision = "e23b984cac39"
down_revision = "d12f041bcb28"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("packages") as batch:
        batch.add_column(sa.Column("duration_days", sa.Integer(), nullable=False, server_default="1"))
    op.execute("UPDATE packages SET duration_days = CASE WHEN name = '3 Hari' THEN 3 WHEN name = '1 Minggu' THEN 7 WHEN name = '4 Minggu' THEN 28 ELSE 1 END")
    op.execute("UPDATE packages SET min_packs = duration_days, max_packs = CASE WHEN duration_days * 6 > 100 THEN 100 ELSE duration_days * 6 END, base_packs = CASE WHEN duration_days * 2 > 100 THEN 100 ELSE duration_days * 2 END")
    with op.batch_alter_table("order_items") as batch:
        batch.add_column(sa.Column("meal_day", sa.Integer(), nullable=False, server_default="1"))
        batch.add_column(sa.Column("meal_sequence", sa.Integer(), nullable=False, server_default="1"))


def downgrade():
    with op.batch_alter_table("order_items") as batch:
        batch.drop_column("meal_sequence")
        batch.drop_column("meal_day")
    with op.batch_alter_table("packages") as batch:
        batch.drop_column("duration_days")
