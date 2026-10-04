"""add addons and per-delivery addresses

Revision ID: f31c87dd9a10
Revises: e23b984cac39
"""
from alembic import op
import sqlalchemy as sa

revision = "f31c87dd9a10"
down_revision = "e23b984cac39"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("order_items") as batch:
        batch.add_column(sa.Column("sambal_quantity", sa.Integer(), nullable=False, server_default="0"))
        batch.add_column(sa.Column("cracker_quantity", sa.Integer(), nullable=False, server_default="0"))
    with op.batch_alter_table("delivery_schedules") as batch:
        batch.add_column(sa.Column("address_id", sa.Integer(), nullable=True))
        batch.create_index("ix_delivery_schedules_address_id", ["address_id"])
        batch.create_foreign_key("fk_delivery_schedules_address_id", "addresses", ["address_id"], ["id"])


def downgrade():
    with op.batch_alter_table("delivery_schedules") as batch:
        batch.drop_constraint("fk_delivery_schedules_address_id", type_="foreignkey")
        batch.drop_index("ix_delivery_schedules_address_id")
        batch.drop_column("address_id")
    with op.batch_alter_table("order_items") as batch:
        batch.drop_column("cracker_quantity")
        batch.drop_column("sambal_quantity")
