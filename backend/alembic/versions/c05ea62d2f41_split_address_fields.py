"""split address fields and coordinates

Revision ID: c05ea62d2f41
Revises: b94d9af31c20
"""
from alembic import op
import sqlalchemy as sa

revision = "c05ea62d2f41"
down_revision = "b94d9af31c20"
branch_labels = None
depends_on = None

def upgrade():
    with op.batch_alter_table("addresses") as batch:
        batch.add_column(sa.Column("street", sa.Text(), nullable=True))
        batch.add_column(sa.Column("district", sa.String(100), nullable=True))
        batch.add_column(sa.Column("city", sa.String(100), nullable=True))
        batch.add_column(sa.Column("province", sa.String(100), nullable=True))
        batch.add_column(sa.Column("postal_code", sa.String(10), nullable=True))
        batch.add_column(sa.Column("latitude", sa.Numeric(10, 7), nullable=True))
        batch.add_column(sa.Column("longitude", sa.Numeric(10, 7), nullable=True))
        batch.add_column(sa.Column("distance_km", sa.Numeric(7, 2), nullable=True))
    op.execute("UPDATE addresses SET street = line, city = zone")

def downgrade():
    with op.batch_alter_table("addresses") as batch:
        batch.drop_column("distance_km"); batch.drop_column("longitude"); batch.drop_column("latitude")
        batch.drop_column("postal_code"); batch.drop_column("province"); batch.drop_column("city"); batch.drop_column("district"); batch.drop_column("street")
