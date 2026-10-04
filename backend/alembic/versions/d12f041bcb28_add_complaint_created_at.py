"""add complaint created timestamp

Revision ID: d12f041bcb28
Revises: c05ea62d2f41
"""
from alembic import op
import sqlalchemy as sa

revision = "d12f041bcb28"
down_revision = "c05ea62d2f41"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("complaints") as batch:
        batch.add_column(sa.Column("created_at", sa.DateTime(), nullable=True))
    op.execute("UPDATE complaints SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL")
    with op.batch_alter_table("complaints") as batch:
        batch.alter_column("created_at", nullable=False)
        batch.create_index("ix_complaints_created_at", ["created_at"], unique=False)


def downgrade():
    with op.batch_alter_table("complaints") as batch:
        batch.drop_index("ix_complaints_created_at")
        batch.drop_column("created_at")
