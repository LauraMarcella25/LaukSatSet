"""add courier and menu details

Revision ID: b94d9af31c20
Revises: a83c8fe72d19
"""
from alembic import op
import sqlalchemy as sa

revision = "b94d9af31c20"
down_revision = "a83c8fe72d19"
branch_labels = None
depends_on = None

def upgrade():
    op.add_column("menus", sa.Column("category", sa.String(60), nullable=False, server_default="Lauk utama"))
    op.create_index("ix_menus_category", "menus", ["category"])
    op.add_column("menu_variants", sa.Column("step_minutes", sa.JSON(), nullable=False, server_default="[]"))
    op.add_column("orders", sa.Column("delivery_method", sa.String(20), nullable=False, server_default="diantar"))
    with op.batch_alter_table("delivery_schedules") as batch:
        batch.add_column(sa.Column("courier_id", sa.Integer(), nullable=True))
        batch.add_column(sa.Column("before_photo", sa.Text(), nullable=True))
        batch.add_column(sa.Column("arrival_photo", sa.Text(), nullable=True))
        batch.create_index("ix_delivery_schedules_courier_id", ["courier_id"])
        batch.create_foreign_key("fk_delivery_courier", "users", ["courier_id"], ["id"])

def downgrade():
    with op.batch_alter_table("delivery_schedules") as batch:
        batch.drop_constraint("fk_delivery_courier", type_="foreignkey")
        batch.drop_index("ix_delivery_schedules_courier_id")
        batch.drop_column("arrival_photo"); batch.drop_column("before_photo"); batch.drop_column("courier_id")
    op.drop_column("orders", "delivery_method"); op.drop_column("menu_variants", "step_minutes")
    op.drop_index("ix_menus_category", table_name="menus"); op.drop_column("menus", "category")
