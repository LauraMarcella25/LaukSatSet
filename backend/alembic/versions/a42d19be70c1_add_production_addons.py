"""add production addon quantities

Revision ID: a42d19be70c1
Revises: f31c87dd9a10
"""

from alembic import op
import sqlalchemy as sa


revision = "a42d19be70c1"
down_revision = "f31c87dd9a10"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("production_batches") as batch:
        batch.add_column(sa.Column("sambal_quantity", sa.Integer(), nullable=False, server_default="0"))
        batch.add_column(sa.Column("cracker_quantity", sa.Integer(), nullable=False, server_default="0"))


def downgrade() -> None:
    with op.batch_alter_table("production_batches") as batch:
        batch.drop_column("cracker_quantity")
        batch.drop_column("sambal_quantity")
