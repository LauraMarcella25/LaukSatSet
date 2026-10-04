from datetime import date, datetime
from decimal import Decimal
from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Integer, Numeric, String, Text, UniqueConstraint, Index
from sqlalchemy import JSON
from sqlalchemy.orm import Mapped, mapped_column
from .database import Base


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[str] = mapped_column(String(180), unique=True, index=True)
    name: Mapped[str] = mapped_column(String(120))
    password_hash: Mapped[str] = mapped_column(String(180))
    role: Mapped[str] = mapped_column(String(24), index=True, default="pelanggan")
    phone: Mapped[str | None] = mapped_column(String(30))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class Address(Base):
    __tablename__ = "addresses"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    label: Mapped[str] = mapped_column(String(60))
    recipient: Mapped[str] = mapped_column(String(120))
    phone: Mapped[str] = mapped_column(String(30))
    line: Mapped[str] = mapped_column(Text)
    zone: Mapped[str] = mapped_column(String(80), default="Jakarta")
    street: Mapped[str | None] = mapped_column(Text)
    district: Mapped[str | None] = mapped_column(String(100))
    city: Mapped[str | None] = mapped_column(String(100))
    province: Mapped[str | None] = mapped_column(String(100))
    postal_code: Mapped[str | None] = mapped_column(String(10))
    latitude: Mapped[Decimal | None] = mapped_column(Numeric(10, 7))
    longitude: Mapped[Decimal | None] = mapped_column(Numeric(10, 7))
    distance_km: Mapped[Decimal | None] = mapped_column(Numeric(7, 2))


class Menu(Base):
    __tablename__ = "menus"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(120), index=True)
    description: Mapped[str] = mapped_column(Text)
    image_url: Mapped[str | None] = mapped_column(Text)
    category: Mapped[str] = mapped_column(String(60), default="Lauk utama", index=True)
    ingredients: Mapped[list] = mapped_column(JSON, default=list)
    allergens: Mapped[list] = mapped_column(JSON, default=list)
    portion_label: Mapped[str] = mapped_column(String(80), default="2 porsi")
    spicy_available: Mapped[bool] = mapped_column(Boolean, default=True)
    calories_per_portion: Mapped[int] = mapped_column(Integer, default=350)
    active: Mapped[bool] = mapped_column(Boolean, default=True, index=True)


class UserPreference(Base):
    __tablename__ = "user_preferences"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), unique=True, index=True)
    allergens: Mapped[list] = mapped_column(JSON, default=list)
    disliked_ingredients: Mapped[list] = mapped_column(JSON, default=list)
    calorie_target: Mapped[int | None] = mapped_column(Integer)
    activity: Mapped[str] = mapped_column(String(30), default="normal")
    fitness_goal: Mapped[str] = mapped_column(String(30), default="menjaga_berat")
    meals_per_day: Mapped[int] = mapped_column(Integer, default=3)


class MenuVariant(Base):
    __tablename__ = "menu_variants"
    __table_args__ = (UniqueConstraint("menu_id", "kind", name="uq_menu_variant_kind"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    menu_id: Mapped[int] = mapped_column(ForeignKey("menus.id"), index=True)
    kind: Mapped[str] = mapped_column(String(30))
    price: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    extra_portion_price: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=0)
    cook_minutes: Mapped[int] = mapped_column(Integer, default=15)
    equipment: Mapped[list] = mapped_column(JSON, default=list)
    instructions: Mapped[list] = mapped_column(JSON, default=list)
    step_minutes: Mapped[list] = mapped_column(JSON, default=list)
    doneness_guide: Mapped[str] = mapped_column(Text, default="Ikuti waktu dan petunjuk pada kemasan.")
    storage_guide: Mapped[str] = mapped_column(Text, default="Simpan sesuai petunjuk pada label.")
    recommended_use_days: Mapped[int] = mapped_column(Integer, default=3)
    active: Mapped[bool] = mapped_column(Boolean, default=True)


class Package(Base):
    __tablename__ = "packages"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(80), unique=True)
    min_packs: Mapped[int] = mapped_column(Integer)
    max_packs: Mapped[int] = mapped_column(Integer)
    base_packs: Mapped[int] = mapped_column(Integer)
    duration_days: Mapped[int] = mapped_column(Integer, default=1)
    discount_percent: Mapped[Decimal] = mapped_column(Numeric(5, 2), default=0)
    active: Mapped[bool] = mapped_column(Boolean, default=True)


class CapacitySlot(Base):
    __tablename__ = "capacity_slots"
    __table_args__ = (UniqueConstraint("delivery_date", "label", name="uq_slot_date_label"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    delivery_date: Mapped[date] = mapped_column(Date, index=True)
    label: Mapped[str] = mapped_column(String(80))
    capacity: Mapped[int] = mapped_column(Integer)
    reserved: Mapped[int] = mapped_column(Integer, default=0)
    cutoff_at: Mapped[datetime] = mapped_column(DateTime)
    shipping_fee: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=0)
    delivery_type: Mapped[str] = mapped_column(String(24), default="reguler")
    active: Mapped[bool] = mapped_column(Boolean, default=True)


class Order(Base):
    __tablename__ = "orders"
    id: Mapped[int] = mapped_column(primary_key=True)
    public_id: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    address_id: Mapped[int | None] = mapped_column(ForeignKey("addresses.id"))
    package_id: Mapped[int] = mapped_column(ForeignKey("packages.id"))
    delivery_method: Mapped[str] = mapped_column(String(20), default="diantar")
    status: Mapped[str] = mapped_column(String(40), default="menunggu_pembayaran", index=True)
    subtotal: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    addon_total: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=0)
    discount_total: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=0)
    shipping_total: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=0)
    total: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, index=True)


class OrderItem(Base):
    __tablename__ = "order_items"
    id: Mapped[int] = mapped_column(primary_key=True)
    order_id: Mapped[int] = mapped_column(ForeignKey("orders.id"), index=True)
    menu_id: Mapped[int] = mapped_column(ForeignKey("menus.id"))
    variant_id: Mapped[int] = mapped_column(ForeignKey("menu_variants.id"))
    menu_name_snapshot: Mapped[str] = mapped_column(String(120))
    variant_snapshot: Mapped[str] = mapped_column(String(30))
    unit_price_snapshot: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    quantity: Mapped[int] = mapped_column(Integer)
    portions: Mapped[int] = mapped_column(Integer)
    spicy_level: Mapped[str] = mapped_column(String(20), default="sedang")
    rice_quantity: Mapped[int] = mapped_column(Integer, default=0)
    sambal_quantity: Mapped[int] = mapped_column(Integer, default=0)
    cracker_quantity: Mapped[int] = mapped_column(Integer, default=0)
    note: Mapped[str | None] = mapped_column(Text)
    meal_day: Mapped[int] = mapped_column(Integer, default=1)
    meal_sequence: Mapped[int] = mapped_column(Integer, default=1)


class DeliverySchedule(Base):
    __tablename__ = "delivery_schedules"
    id: Mapped[int] = mapped_column(primary_key=True)
    order_id: Mapped[int] = mapped_column(ForeignKey("orders.id"), index=True)
    slot_id: Mapped[int] = mapped_column(ForeignKey("capacity_slots.id"), index=True)
    address_id: Mapped[int | None] = mapped_column(ForeignKey("addresses.id"), index=True)
    status: Mapped[str] = mapped_column(String(40), default="terjadwal", index=True)
    shipping_fee_snapshot: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    received_at: Mapped[datetime | None] = mapped_column(DateTime)
    courier_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), index=True)
    before_photo: Mapped[str | None] = mapped_column(Text)
    arrival_photo: Mapped[str | None] = mapped_column(Text)


class DeliveryItem(Base):
    __tablename__ = "delivery_items"
    __table_args__ = (UniqueConstraint("delivery_id", "order_item_id", name="uq_delivery_order_item"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    delivery_id: Mapped[int] = mapped_column(ForeignKey("delivery_schedules.id"), index=True)
    order_item_id: Mapped[int] = mapped_column(ForeignKey("order_items.id"))
    quantity: Mapped[int] = mapped_column(Integer)
    pantry_status: Mapped[str] = mapped_column(String(30), default="belum_diterima")
    recommended_use_at: Mapped[date | None] = mapped_column(Date)


class Payment(Base):
    __tablename__ = "payments"
    id: Mapped[int] = mapped_column(primary_key=True)
    order_id: Mapped[int] = mapped_column(ForeignKey("orders.id"), unique=True)
    provider_order_id: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    status: Mapped[str] = mapped_column(String(32), default="pending", index=True)
    gross_amount: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    snap_token: Mapped[str | None] = mapped_column(Text)
    redirect_url: Mapped[str | None] = mapped_column(Text)
    provider_payload: Mapped[dict] = mapped_column(JSON, default=dict)
    expires_at: Mapped[datetime | None] = mapped_column(DateTime)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class Subscription(Base):
    __tablename__ = "subscriptions"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    order_id: Mapped[int] = mapped_column(ForeignKey("orders.id"), unique=True)
    remaining_packs: Mapped[int] = mapped_column(Integer)
    status: Mapped[str] = mapped_column(String(30), default="aktif")


class ProductionBatch(Base):
    __tablename__ = "production_batches"
    id: Mapped[int] = mapped_column(primary_key=True)
    production_date: Mapped[date] = mapped_column(Date, index=True)
    delivery_id: Mapped[int | None] = mapped_column(ForeignKey("delivery_schedules.id"), index=True)
    menu_id: Mapped[int] = mapped_column(ForeignKey("menus.id"), index=True)
    variant_id: Mapped[int] = mapped_column(ForeignKey("menu_variants.id"))
    quantity: Mapped[int] = mapped_column(Integer)
    spicy_level: Mapped[str] = mapped_column(String(20))
    rice_quantity: Mapped[int] = mapped_column(Integer, default=0)
    sambal_quantity: Mapped[int] = mapped_column(Integer, default=0)
    cracker_quantity: Mapped[int] = mapped_column(Integer, default=0)
    notes: Mapped[list] = mapped_column(JSON, default=list)
    allergens: Mapped[list] = mapped_column(JSON, default=list)
    status: Mapped[str] = mapped_column(String(30), default="menunggu_produksi")


class InventoryItem(Base):
    __tablename__ = "inventory_items"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(120), unique=True)
    unit: Mapped[str] = mapped_column(String(30))
    stock: Mapped[Decimal] = mapped_column(Numeric(12, 3), default=0)
    min_stock: Mapped[Decimal] = mapped_column(Numeric(12, 3), default=0)
    purchase_price: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=0)
    supplier: Mapped[str | None] = mapped_column(String(120))
    use_by: Mapped[date | None] = mapped_column(Date)


class InventoryMovement(Base):
    __tablename__ = "inventory_movements"
    __table_args__ = (UniqueConstraint("idempotency_key", name="uq_inventory_movement_key"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    inventory_item_id: Mapped[int] = mapped_column(ForeignKey("inventory_items.id"), index=True)
    quantity: Mapped[Decimal] = mapped_column(Numeric(12, 3))
    movement_type: Mapped[str] = mapped_column(String(24))
    idempotency_key: Mapped[str] = mapped_column(String(120))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class Review(Base):
    __tablename__ = "reviews"
    __table_args__ = (UniqueConstraint("order_id", name="uq_review_order"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    order_id: Mapped[int] = mapped_column(ForeignKey("orders.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    taste: Mapped[int] = mapped_column(Integer)
    portion: Mapped[int] = mapped_column(Integer)
    packaging: Mapped[int] = mapped_column(Integer)
    delivery: Mapped[int] = mapped_column(Integer)
    comment: Mapped[str | None] = mapped_column(Text)


class Complaint(Base):
    __tablename__ = "complaints"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    delivery_id: Mapped[int] = mapped_column(ForeignKey("delivery_schedules.id"), index=True)
    description: Mapped[str] = mapped_column(Text)
    photo_data: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(30), default="baru")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, index=True)


class AuditEvent(Base):
    __tablename__ = "audit_events"
    id: Mapped[int] = mapped_column(primary_key=True)
    actor_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), index=True)
    entity_type: Mapped[str] = mapped_column(String(40), index=True)
    entity_id: Mapped[str] = mapped_column(String(80), index=True)
    action: Mapped[str] = mapped_column(String(80))
    before: Mapped[dict] = mapped_column(JSON, default=dict)
    after: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, index=True)

Index("ix_orders_user_created", Order.user_id, Order.created_at)
