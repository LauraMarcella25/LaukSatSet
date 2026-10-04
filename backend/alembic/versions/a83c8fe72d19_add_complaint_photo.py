"""add complaint photo

Revision ID: a83c8fe72d19
Revises: c71a9e2240ad
"""
from alembic import op
import sqlalchemy as sa

revision = "a83c8fe72d19"
down_revision = "c71a9e2240ad"
branch_labels = None
depends_on = None

def upgrade():
    op.add_column("complaints", sa.Column("photo_data", sa.Text(), nullable=True))

def downgrade():
    op.drop_column("complaints", "photo_data")
