"""link production batches to deliveries

Revision ID: b5814f72c9da
Revises: a42d19be70c1
"""

from alembic import op
import sqlalchemy as sa


revision = "b5814f72c9da"
down_revision = "a42d19be70c1"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("production_batches") as batch:
        batch.add_column(sa.Column("delivery_id", sa.Integer(), nullable=True))
        batch.create_index("ix_production_batches_delivery_id", ["delivery_id"], unique=False)
        batch.create_foreign_key("fk_production_batches_delivery_id", "delivery_schedules", ["delivery_id"], ["id"])


def downgrade() -> None:
    with op.batch_alter_table("production_batches") as batch:
        batch.drop_constraint("fk_production_batches_delivery_id", type_="foreignkey")
        batch.drop_index("ix_production_batches_delivery_id")
        batch.drop_column("delivery_id")
