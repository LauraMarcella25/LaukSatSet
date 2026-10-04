import os
import uuid
import math
from datetime import date, datetime, timedelta
from decimal import Decimal
from typing import Literal
from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, field_validator, model_validator
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from .auth import create_token, current_user, hash_password, require_roles
from .database import Base, SessionLocal, engine, get_db
from .midtrans import create_snap_transaction, normalized_payment_status, verify_signature
from .models import Address, AuditEvent, CapacitySlot, Complaint, DeliveryItem, DeliverySchedule, InventoryItem, Menu, MenuVariant, Order, OrderItem, Package, Payment, ProductionBatch, Review, Subscription, User, UserPreference
from .pricing import PriceLine, calculate
from .recommendations import gemini_recommend, rule_recommend

app = FastAPI(title="LaukSatSet API", version="0.1.0", description="API pemesanan lauk dan operasi dapur")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
PASSWORD_RESETS: dict[str, tuple[str, datetime]] = {}
SERVICE_ZONES = {"Jakarta Selatan", "Jakarta Pusat", "Jakarta Barat", "Jakarta Timur", "Depok", "Tangerang"}
KITCHEN_LATITUDE = float(os.getenv("KITCHEN_LATITUDE", "-6.2445"))
KITCHEN_LONGITUDE = float(os.getenv("KITCHEN_LONGITUDE", "106.8006"))
MAX_DELIVERY_KM = float(os.getenv("MAX_DELIVERY_KM", "25"))
DELIVERY_FEE_PER_KM = int(os.getenv("DELIVERY_FEE_PER_KM", "2000"))
DELIVERY_FEE_PER_EXTRA_PACK = int(os.getenv("DELIVERY_FEE_PER_EXTRA_PACK", "1000"))
COURIER_PROOF_RETENTION_MONTHS = max(1, int(os.getenv("COURIER_PROOF_RETENTION_MONTHS", "6")))

ORDER_TRANSITIONS = {
    "menunggu_pembayaran": {"dibatalkan"}, "dibayar": {"dikonfirmasi"},
    "dikonfirmasi": {"menunggu_produksi"}, "menunggu_produksi": {"diproduksi"},
    "diproduksi": {"lolos_qc", "bermasalah"}, "lolos_qc": {"siap_dikirim"},
    "siap_dikirim": {"dalam_pengiriman"}, "dalam_pengiriman": {"selesai", "bermasalah"},
    "bermasalah": {"dibatalkan", "dikonfirmasi"}, "selesai": set(), "dibatalkan": set(),
}


class LoginIn(BaseModel):
    email: str
    password: str


class RegisterIn(BaseModel):
    name: str = Field(min_length=2, max_length=120)
    email: str = Field(min_length=5, max_length=180)
    phone: str = Field(min_length=8, max_length=30)
    password: str = Field(min_length=6, max_length=120)

    @field_validator("name")
    @classmethod
    def valid_name(cls, value: str):
        if any(char.isdigit() for char in value): raise ValueError("Nama tidak boleh berisi angka")
        return value.strip()

    @field_validator("phone")
    @classmethod
    def valid_phone(cls, value: str):
        normalized = value.removeprefix("+")
        if not normalized.isdigit(): raise ValueError("Nomor WhatsApp hanya boleh berisi angka")
        return value


class ForgotPasswordIn(BaseModel):
    email: str


class ResetPasswordIn(BaseModel):
    email: str
    code: str = Field(min_length=6, max_length=6)
    new_password: str = Field(min_length=6, max_length=120)


class ChangePasswordIn(BaseModel):
    current_password: str
    new_password: str = Field(min_length=6, max_length=120)


class AddressIn(BaseModel):
    label: Literal["Rumah", "Kantor", "Kos"]
    recipient: str = Field(min_length=2, max_length=120)
    phone: str = Field(min_length=8, max_length=30)
    line: str = Field(min_length=5, max_length=1000)
    zone: str = Field(min_length=2, max_length=80)
    street: str = Field(min_length=5, max_length=1000)
    district: str = Field(min_length=2, max_length=100)
    city: Literal["Jakarta Selatan", "Jakarta Pusat", "Jakarta Barat", "Jakarta Timur", "Depok", "Tangerang"]
    province: Literal["DKI Jakarta", "Jawa Barat", "Banten"]
    postal_code: str = Field(min_length=5, max_length=10)
    latitude: float
    longitude: float

    @field_validator("recipient")
    @classmethod
    def recipient_without_digits(cls, value: str):
        if any(char.isdigit() for char in value): raise ValueError("Nama penerima tidak boleh berisi angka")
        return value.strip()

    @field_validator("postal_code")
    @classmethod
    def numeric_postal_code(cls, value: str):
        if not value.isdigit(): raise ValueError("Kode pos harus berupa angka")
        return value

    @model_validator(mode="after")
    def matching_zone(self):
        if self.zone != self.city: raise ValueError("Zona harus sama dengan kota yang dipilih")
        return self


class ItemIn(BaseModel):
    variant_id: int
    quantity: int = Field(ge=1, le=20)
    portions: int = Field(ge=1, le=10)
    spicy_level: str = "sedang"
    rice_quantity: int = Field(default=0, ge=0, le=20)
    sambal_quantity: int = Field(default=0, ge=0, le=20)
    cracker_quantity: int = Field(default=0, ge=0, le=20)
    note: str | None = Field(default=None, max_length=500)
    slot_id: int | None = None
    meal_day: int = Field(default=1, ge=1, le=31)
    meal_sequence: int = Field(default=1, ge=1, le=6)


class CheckoutIn(BaseModel):
    package_id: int
    address_id: int | None = None
    slot_ids: list[int] = Field(min_length=1, max_length=12)
    # Paket empat minggu dapat berisi 28 hari × 3 waktu makan.
    items: list[ItemIn] = Field(min_length=1, max_length=100)
    delivery_method: Literal["diantar", "ambil_sendiri"] = "diantar"
    meals_per_day: int = Field(default=1, ge=1, le=6)
    delivery_addresses: dict[int, int] = Field(default_factory=dict)

    @model_validator(mode="after")
    def one_delivery_schedule_per_day(self):
        schedules_by_day: dict[int, set[int]] = {}
        for item in self.items:
            if item.slot_id is not None:
                schedules_by_day.setdefault(item.meal_day, set()).add(item.slot_id)
        if any(len(slots) > 1 for slots in schedules_by_day.values()):
            raise ValueError("Semua makanan pada hari yang sama harus memakai satu jadwal pengiriman")
        return self


class StatusIn(BaseModel):
    status: str
    note: str | None = Field(default=None, max_length=2000)


class ReviewIn(BaseModel):
    taste: int = Field(ge=1, le=5)
    portion: int = Field(ge=1, le=5)
    packaging: int = Field(ge=1, le=5)
    delivery: int = Field(ge=1, le=5)
    comment: str | None = Field(default=None, max_length=1000)


class ComplaintIn(BaseModel):
    description: str = Field(min_length=5, max_length=2000)
    photo_data: str | None = Field(default=None, max_length=7_000_000)


class OrderAddressIn(BaseModel):
    address_id: int


class DeliveryRescheduleIn(BaseModel):
    slot_id: int


class WebhookIn(BaseModel):
    order_id: str
    status_code: str
    gross_amount: str
    signature_key: str
    transaction_status: str
    fraud_status: str | None = None


class MenuUpdateIn(BaseModel):
    name: str | None = Field(default=None, min_length=2, max_length=120)
    description: str | None = Field(default=None, min_length=3, max_length=1000)
    active: bool | None = None
    variant_id: int | None = None
    price: int | None = Field(default=None, ge=0)
    category: str | None = Field(default=None, min_length=2, max_length=60)
    image_url: str | None = Field(default=None, max_length=7_000_000)
    instructions: list[str] | None = None
    step_minutes: list[int] | None = None


class MenuCreateIn(BaseModel):
    name: str = Field(min_length=2, max_length=120)
    description: str = Field(min_length=3, max_length=1000)
    ingredients: list[str] = Field(default_factory=list)
    allergens: list[str] = Field(default_factory=list)
    portion_label: str = Field(default="2 porsi", max_length=80)
    ready_to_cook_price: int = Field(ge=1000)
    ready_to_eat_price: int = Field(ge=1000)
    calories_per_portion: int = Field(default=350, ge=50, le=3000)
    category: str = Field(default="Lauk utama", min_length=2, max_length=60)
    image_url: str | None = Field(default=None, max_length=7_000_000)
    instructions: list[str] = Field(default_factory=lambda: ["Panaskan lauk sesuai petunjuk."])
    step_minutes: list[int] = Field(default_factory=lambda: [10])
    ready_to_eat_instructions: list[str] = Field(default_factory=lambda: ["Panaskan sesuai petunjuk pada kemasan."])
    ready_to_eat_step_minutes: list[int] = Field(default_factory=lambda: [5])
    equipment: list[str] = Field(default_factory=list)
    storage_guide: str = Field(default="Simpan sesuai petunjuk pada label.", min_length=3, max_length=1000)
    doneness_guide: str = Field(default="Pastikan panas merata.", min_length=3, max_length=1000)


class VariantAdminIn(BaseModel):
    id: int
    price: int = Field(ge=1000)
    instructions: list[str] = Field(min_length=1)
    step_minutes: list[int] = Field(min_length=1)
    equipment: list[str] = Field(default_factory=list)
    storage_guide: str = Field(min_length=3, max_length=1000)
    doneness_guide: str = Field(min_length=3, max_length=1000)


class MenuFullUpdateIn(BaseModel):
    name: str = Field(min_length=2, max_length=120)
    description: str = Field(min_length=3, max_length=1000)
    ingredients: list[str]
    allergens: list[str]
    portion_label: str = Field(min_length=2, max_length=80)
    calories_per_portion: int = Field(ge=50, le=3000)
    category: str = Field(min_length=2, max_length=60)
    image_url: str | None = Field(default=None, max_length=7_000_000)
    variants: list[VariantAdminIn] = Field(min_length=1)


class CourierProofIn(BaseModel):
    photo_data: str = Field(min_length=20, max_length=7_000_000)


class CourierAssignIn(BaseModel):
    courier_id: int


class FeedbackIn(BaseModel):
    kind: Literal["masalah", "saran", "lainnya"] = "saran"
    message: str = Field(min_length=5, max_length=2000)


class SlotUpdateIn(BaseModel):
    capacity: int | None = Field(default=None, ge=0)
    shipping_fee: int | None = Field(default=None, ge=0)
    active: bool | None = None


class SlotCreateIn(BaseModel):
    date: date
    label: str = Field(min_length=3, max_length=80)
    capacity: int = Field(ge=1, le=10000)
    shipping_fee: int = Field(ge=0)


class PackageIn(BaseModel):
    name: str = Field(min_length=2, max_length=80)
    min_packs: int = Field(ge=1, le=100)
    max_packs: int = Field(ge=1, le=100)
    base_packs: int = Field(ge=1, le=100)
    duration_days: int = Field(ge=1, le=31)
    discount_percent: Decimal = Field(ge=0, le=90)


class MovementIn(BaseModel):
    quantity: Decimal
    movement_type: Literal["masuk", "pemakaian", "waste", "koreksi"]
    idempotency_key: str = Field(min_length=4, max_length=120)


class RecommendationIn(BaseModel):
    budget: int | None = Field(default=None, ge=10000, le=2_000_000)
    max_minutes: int | None = Field(default=None, ge=1, le=180)
    preference: str = Field(default="", max_length=200)
    question: str = Field(default="Enaknya makan apa hari ini?", max_length=500)
    people: int = Field(default=1, ge=1, le=20)


class PreferenceIn(BaseModel):
    allergens: list[str] = Field(default_factory=list, max_length=30)
    disliked_ingredients: list[str] = Field(default_factory=list, max_length=30)
    calorie_target: int | None = Field(default=None, ge=800, le=6000)
    activity: Literal["ringan", "normal", "aktif", "gym"] = "normal"
    fitness_goal: Literal["turun_berat", "menjaga_berat", "naik_massa_otot"] = "menjaga_berat"
    meals_per_day: int = Field(default=3, ge=1, le=8)


def money(value):
    return int(Decimal(value))


def delivery_distance(latitude: float, longitude: float) -> Decimal:
    earth = 6371.0
    p1, p2 = math.radians(KITCHEN_LATITUDE), math.radians(latitude)
    dp, dl = math.radians(latitude - KITCHEN_LATITUDE), math.radians(longitude - KITCHEN_LONGITUDE)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return Decimal(str(round(earth * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a)), 2)))


def distance_surcharge(distance: Decimal | None) -> Decimal:
    if distance is None: return Decimal(0)
    return Decimal(max(0, math.ceil(float(distance) - 5)) * DELIVERY_FEE_PER_KM)


def seed(db: Session):
    if db.scalar(select(func.count(User.id))):
        return
    users = [
        User(email="pelanggan@lauksatset.id", name="Rani", password_hash=hash_password("demo123"), role="pelanggan", phone="081234567890"),
        User(email="admin@lauksatset.id", name="Admin LaukSatSet", password_hash=hash_password("demo123"), role="admin"),
        User(email="dapur@lauksatset.id", name="Tim Dapur", password_hash=hash_password("demo123"), role="dapur"),
    ]
    db.add_all(users); db.flush()
    db.add(Address(user_id=users[0].id, label="Rumah", recipient="Rani", phone="081234567890", line="Jl. Melati No. 12, Jakarta Selatan", zone="Jakarta Selatan"))
    menus = [
        Menu(name="Ayam Woku", description="Ayam berbumbu woku harum dengan kemangi.", ingredients=["ayam", "kemangi", "cabai", "serai"], allergens=[], portion_label="2 porsi", calories_per_portion=410),
        Menu(name="Dori Sambal Matah", description="Ikan dori lembut dengan sambal matah segar.", ingredients=["ikan dori", "bawang", "serai", "cabai"], allergens=["ikan"], portion_label="2 porsi", calories_per_portion=360),
        Menu(name="Semur Tahu Jamur", description="Semur gurih manis berbahan tahu dan jamur.", ingredients=["tahu", "jamur", "kecap"], allergens=["kedelai"], portion_label="2 porsi", calories_per_portion=320),
    ]
    db.add_all(menus); db.flush()
    prices = [(menus[0], 42000), (menus[1], 46000), (menus[2], 34000)]
    for menu, price in prices:
        db.add_all([
            MenuVariant(menu_id=menu.id, kind="siap_masak", price=price, extra_portion_price=14000, cook_minutes=18, equipment=["pisau", "talenan", "wajan", "spatula"], instructions=["Cairkan kemasan semalaman di chiller. Siapkan pisau dan talenan bersih untuk memotong bahan tambahan bila diperlukan.", "Panaskan wajan dengan 1 sdm minyak selama 1 menit menggunakan api sedang.", "Buka kemasan, masukkan lauk beserta bumbu, lalu aduk perlahan agar bumbu tidak gosong.", "Masak 12–14 menit. Balik atau aduk setiap 3 menit dan tambahkan 2 sdm air jika bumbu terlalu kering.", "Periksa bagian tengah lauk, pastikan panas merata dan tidak mentah sebelum disajikan."], step_minutes=[2, 1, 2, 12, 1], doneness_guide="Kuah mendidih dan bagian tengah lauk panas merata.", storage_guide="Simpan beku. Cairkan di chiller sebelum dimasak.", recommended_use_days=7),
            MenuVariant(menu_id=menu.id, kind="siap_makan", price=price + 5000, extra_portion_price=16000, cook_minutes=5, equipment=["panci atau microwave"], instructions=["Buka kemasan sesuai tanda.", "Panaskan hingga seluruh bagian panas merata.", "Sajikan segera."], doneness_guide="Uap terlihat dan bagian tengah lauk panas merata.", storage_guide="Simpan dingin pada suhu chiller.", recommended_use_days=3),
        ])
    db.add_all([
        Package(name="1 Hari", min_packs=1, max_packs=6, base_packs=2, duration_days=1, discount_percent=0),
        Package(name="3 Hari", min_packs=3, max_packs=18, base_packs=6, duration_days=3, discount_percent=2),
        Package(name="1 Minggu", min_packs=7, max_packs=42, base_packs=14, duration_days=7, discount_percent=5),
        Package(name="4 Minggu", min_packs=28, max_packs=100, base_packs=56, duration_days=28, discount_percent=8),
    ])
    for offset in (1, 3, 5, 8, 15, 22, 29):
        delivery_date = date.today() + timedelta(days=offset)
        for label in ("09.00–12.00", "14.00–17.00"):
            db.add(CapacitySlot(delivery_date=delivery_date, label=label, capacity=30, reserved=0, cutoff_at=datetime.combine(delivery_date - timedelta(days=1), datetime.min.time()).replace(hour=16), shipping_fee=12000, delivery_type="reguler"))
    db.add_all([InventoryItem(name="Ayam fillet", unit="kg", stock=20, min_stock=5, purchase_price=55000, supplier="Pasar Segar"), InventoryItem(name="Beras", unit="kg", stock=12, min_stock=4, purchase_price=16000, supplier="Toko Pangan")])
    db.commit()


@app.on_event("startup")
def startup():
    Base.metadata.create_all(engine)
    with SessionLocal() as db:
        seed(db)
        cutoff = date.today() - timedelta(days=COURIER_PROOF_RETENTION_MONTHS * 30)
        old_slot_ids = db.scalars(select(CapacitySlot.id).where(CapacitySlot.delivery_date < cutoff)).all()
        if old_slot_ids:
            old_deliveries = db.scalars(select(DeliverySchedule).where(DeliverySchedule.slot_id.in_(old_slot_ids))).all()
            for delivery in old_deliveries:
                delivery.before_photo = None
                delivery.arrival_photo = None
            db.commit()
        if not db.scalar(select(User).where(User.email == "pengantar@lauksatset.id")):
            db.add(User(email="pengantar@lauksatset.id", name="Budi Pengantar", password_hash=hash_password("demo123"), role="pengantar", phone="081298765432")); db.commit()
        dates = db.scalars(select(CapacitySlot.delivery_date).distinct()).all()
        for delivery_date in dates:
            if delivery_date > date.today() and not db.scalar(select(CapacitySlot).where(CapacitySlot.delivery_date == delivery_date, CapacitySlot.label == "14.00–17.00")):
                db.add(CapacitySlot(delivery_date=delivery_date, label="14.00–17.00", capacity=30, reserved=0, cutoff_at=datetime.combine(delivery_date - timedelta(days=1), datetime.min.time()).replace(hour=16), shipping_fee=12000, delivery_type="reguler"))
        db.commit()


@app.get("/health")
def health():
    return {"status": "sehat", "midtrans": "sandbox_terkonfigurasi" if os.getenv("MIDTRANS_SERVER_KEY") else "mode_demo_tanpa_kredensial"}


@app.get("/settings/public")
def public_settings():
    return {"support_whatsapp": os.getenv("SUPPORT_WHATSAPP", ""), "max_delivery_km": MAX_DELIVERY_KM,
            "delivery_fee_per_km": DELIVERY_FEE_PER_KM, "delivery_fee_per_extra_pack": DELIVERY_FEE_PER_EXTRA_PACK,
            "kitchen_latitude": KITCHEN_LATITUDE, "kitchen_longitude": KITCHEN_LONGITUDE,
            "kitchen_address": os.getenv("KITCHEN_ADDRESS", "Dapur LaukSatSet, Jakarta Selatan"),
            "recommendation_engine": "Gemini" if os.getenv("GEMINI_API_KEY") else "Rekomendasi lokal"}


@app.post("/auth/login")
def login(body: LoginIn, db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(User.email == body.email.lower()))
    if not user or user.password_hash != hash_password(body.password):
        raise HTTPException(401, "Email atau kata sandi salah")
    return {"access_token": create_token(user), "token_type": "bearer", "user": {"id": user.id, "name": user.name, "role": user.role}}


@app.post("/auth/register")
def register(body: RegisterIn, db: Session = Depends(get_db)):
    email = body.email.strip().lower()
    if "@" not in email:
        raise HTTPException(422, "Format email tidak valid")
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(409, "Email sudah terdaftar")
    user = User(email=email, name=body.name.strip(), phone=body.phone.strip(), password_hash=hash_password(body.password), role="pelanggan")
    db.add(user); db.commit(); db.refresh(user)
    return {"access_token": create_token(user), "token_type": "bearer", "user": {"id": user.id, "name": user.name, "role": user.role}}


@app.post("/auth/forgot-password")
def forgot_password(body: ForgotPasswordIn, db: Session = Depends(get_db)):
    email = body.email.strip().lower(); user = db.scalar(select(User).where(User.email == email))
    code = str(int(uuid.uuid4().hex[:8], 16) % 1_000_000).zfill(6)
    if user: PASSWORD_RESETS[email] = (code, datetime.utcnow() + timedelta(minutes=15))
    response = {"message": "Jika akun ditemukan, kode pemulihan dikirim ke kontak terdaftar."}
    if user and not os.getenv("WHATSAPP_PROVIDER_TOKEN"): response["demo_code"] = code
    return response


@app.post("/auth/reset-password")
def reset_password(body: ResetPasswordIn, db: Session = Depends(get_db)):
    email = body.email.strip().lower(); saved = PASSWORD_RESETS.get(email)
    if not saved or saved[0] != body.code or saved[1] < datetime.utcnow(): raise HTTPException(400, "Kode salah atau kedaluwarsa")
    user = db.scalar(select(User).where(User.email == email))
    if not user: raise HTTPException(404)
    user.password_hash = hash_password(body.new_password); PASSWORD_RESETS.pop(email, None); db.commit()
    return {"status": "kata_sandi_diperbarui"}


@app.get("/me")
def me(user: User = Depends(current_user)):
    return {"id": user.id, "name": user.name, "email": user.email, "phone": user.phone, "role": user.role}


@app.post("/me/password")
def change_password(body: ChangePasswordIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if user.password_hash != hash_password(body.current_password): raise HTTPException(401, "Kata sandi lama salah")
    user.password_hash = hash_password(body.new_password); db.commit(); return {"status": "kata_sandi_diperbarui"}


@app.get("/me/preferences")
def get_preferences(user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    row = db.scalar(select(UserPreference).where(UserPreference.user_id == user.id))
    if not row: return PreferenceIn().model_dump()
    return {key: getattr(row, key) for key in PreferenceIn.model_fields}


@app.put("/me/preferences")
def put_preferences(body: PreferenceIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    row = db.scalar(select(UserPreference).where(UserPreference.user_id == user.id))
    if not row: row = UserPreference(user_id=user.id); db.add(row)
    for key, value in body.model_dump().items(): setattr(row, key, value)
    db.commit(); return body.model_dump()


@app.get("/catalog")
def catalog(db: Session = Depends(get_db)):
    menus = db.scalars(select(Menu).where(Menu.active.is_(True)).order_by(Menu.name)).all()
    variants = db.scalars(select(MenuVariant).where(MenuVariant.active.is_(True))).all()
    packages = db.scalars(select(Package).where(Package.active.is_(True)).order_by(Package.base_packs)).all()
    return {
        "menus": [{"id": m.id, "name": m.name, "description": m.description, "ingredients": m.ingredients, "allergens": m.allergens, "portion_label": m.portion_label, "calories_per_portion": m.calories_per_portion, "category": m.category, "image_url": m.image_url,
                   "variants": [{"id": v.id, "kind": v.kind, "price": money(v.price), "cook_minutes": v.cook_minutes, "instructions": v.instructions, "step_minutes": v.step_minutes, "equipment": v.equipment, "storage_guide": v.storage_guide, "doneness_guide": v.doneness_guide} for v in variants if v.menu_id == m.id]} for m in menus],
        "packages": [{"id": p.id, "name": p.name, "min_packs": p.min_packs, "max_packs": p.max_packs, "base_packs": p.base_packs, "duration_days": p.duration_days, "discount_percent": float(p.discount_percent)} for p in packages],
    }


def menu_conflicts_with_preferences(menu: dict, avoided_values: list[str]) -> bool:
    """Match saved restrictions against both exact and compound ingredient names."""
    avoided = [" ".join(value.casefold().replace("-", " ").split()) for value in avoided_values if value.strip()]
    menu_terms = [" ".join(value.casefold().replace("-", " ").split()) for value in [*menu.get("allergens", []), *menu.get("ingredients", [])] if value.strip()]
    return any(blocked == term or blocked in term or term in blocked for blocked in avoided for term in menu_terms)


@app.post("/recommendations")
async def recommendations(body: RecommendationIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    data = catalog(db)
    preference_row = db.scalar(select(UserPreference).where(UserPreference.user_id == user.id))
    if preference_row:
        avoided = [*preference_row.allergens, *preference_row.disliked_ingredients]
        data["menus"] = [menu for menu in data["menus"] if not menu_conflicts_with_preferences(menu, avoided)]
    if not data["menus"]:
        raise HTTPException(404, "Tidak ada menu yang sesuai pengaturan alergi dan pantangan")
    context = f"{body.preference} aktivitas {preference_row.activity} tujuan {preference_row.fitness_goal}" if preference_row else body.preference
    target_per_meal = int(preference_row.calorie_target / preference_row.meals_per_day) if preference_row and preference_row.calorie_target else None
    fallback = rule_recommend(data["menus"], body.budget, body.max_minutes, context, body.question, body.people, target_per_meal)
    result = await gemini_recommend(data["menus"], fallback, body.budget, body.max_minutes, context, body.question, body.people, target_per_meal)
    result["people"] = body.people
    result["disclaimer"] = "Rekomendasi tidak menentukan keamanan alergi. Periksa bahan dan alergen yang dikonfirmasi admin."
    return result


@app.post("/recommendations/options")
async def recommendation_options(body: RecommendationIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    remaining = list(catalog(db)["menus"])
    preference_row = db.scalar(select(UserPreference).where(UserPreference.user_id == user.id))
    if preference_row:
        avoided = [*preference_row.allergens, *preference_row.disliked_ingredients]
        remaining = [menu for menu in remaining if not menu_conflicts_with_preferences(menu, avoided)]
    if not remaining: raise HTTPException(404, "Belum ada menu aktif untuk direkomendasikan")
    results = []
    context = f"{body.preference} aktivitas {preference_row.activity} tujuan {preference_row.fitness_goal}" if preference_row else body.preference
    target_per_meal = int(preference_row.calorie_target / preference_row.meals_per_day) if preference_row and preference_row.calorie_target else None
    fallback = rule_recommend(remaining, body.budget, body.max_minutes, context, body.question, body.people, target_per_meal)
    first = await gemini_recommend(remaining, fallback, body.budget, body.max_minutes, context, body.question, body.people, target_per_meal)
    first.update({"people": body.people, "disclaimer": "Periksa bahan dan alergen sebelum memesan."}); results.append(first)
    remaining = [menu for menu in remaining if menu["id"] != first["menu_id"]]
    while remaining and len(results) < 3:
        result = rule_recommend(remaining, body.budget, body.max_minutes, context, f"{body.question} pilihan {len(results) + 1}", body.people, target_per_meal)
        result.update({"people": body.people, "disclaimer": "Periksa bahan dan alergen sebelum memesan."}); results.append(result)
        remaining = [menu for menu in remaining if menu["id"] != result["menu_id"]]
    return {"recommendations": results}


@app.get("/delivery-slots")
def slots(db: Session = Depends(get_db)):
    rows = db.scalars(select(CapacitySlot).where(CapacitySlot.active.is_(True), CapacitySlot.delivery_date > date.today()).order_by(CapacitySlot.delivery_date, CapacitySlot.label)).all()
    return [{"id": s.id, "date": s.delivery_date.isoformat(), "label": s.label, "capacity": s.capacity, "reserved": s.reserved, "remaining": s.capacity - s.reserved, "shipping_fee": money(s.shipping_fee), "type": s.delivery_type, "cutoff_at": s.cutoff_at.isoformat(), "available": s.reserved < s.capacity and datetime.utcnow() < s.cutoff_at} for s in rows]


@app.get("/me/addresses")
def addresses(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return db.scalars(select(Address).where(Address.user_id == user.id)).all()


@app.post("/me/addresses")
def create_address(body: AddressIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    if body.zone not in SERVICE_ZONES: raise HTTPException(422, "Alamat berada di luar jangkauan maksimal 25 km")
    distance = delivery_distance(body.latitude, body.longitude)
    if distance > Decimal(str(MAX_DELIVERY_KM)): raise HTTPException(422, f"Alamat berjarak {distance} km, di luar jangkauan maksimal {MAX_DELIVERY_KM:g} km")
    address = Address(user_id=user.id, **body.model_dump(), distance_km=distance); db.add(address); db.commit(); db.refresh(address); return address


@app.patch("/me/addresses/{address_id}")
def update_address(address_id: int, body: AddressIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    if body.zone not in SERVICE_ZONES: raise HTTPException(422, "Alamat berada di luar jangkauan maksimal 25 km")
    address = db.scalar(select(Address).where(Address.id == address_id, Address.user_id == user.id))
    if not address: raise HTTPException(404, "Alamat tidak ditemukan")
    distance = delivery_distance(body.latitude, body.longitude)
    if distance > Decimal(str(MAX_DELIVERY_KM)): raise HTTPException(422, f"Alamat berjarak {distance} km, di luar jangkauan maksimal {MAX_DELIVERY_KM:g} km")
    for key, value in body.model_dump().items(): setattr(address, key, value)
    address.distance_km = distance
    db.commit(); db.refresh(address); return address


@app.delete("/me/addresses/{address_id}")
def delete_address(address_id: int, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    address = db.scalar(select(Address).where(Address.id == address_id, Address.user_id == user.id))
    if not address: raise HTTPException(404, "Alamat tidak ditemukan")
    used_by_order = db.scalar(select(func.count(Order.id)).where(Order.address_id == address.id))
    used_by_delivery = db.scalar(select(func.count(DeliverySchedule.id)).where(DeliverySchedule.address_id == address.id))
    if used_by_order or used_by_delivery: raise HTTPException(409, "Alamat sudah digunakan pada pesanan dan tidak dapat dihapus")
    db.delete(address); db.commit(); return {"status": "dihapus"}


def ensure_production_for_order(order: Order, db: Session, initial_status: str = "menunggu_pembayaran") -> None:
    """Create the kitchen queue as soon as an order exists, once per delivery/menu variant."""
    deliveries = db.scalars(select(DeliverySchedule).where(DeliverySchedule.order_id == order.id)).all()
    for delivery in deliveries:
        slot = db.get(CapacitySlot, delivery.slot_id)
        production_date = slot.delivery_date - timedelta(days=1) if slot else date.today()
        allocations = db.scalars(select(DeliveryItem).where(DeliveryItem.delivery_id == delivery.id)).all()
        grouped: dict[tuple[int, int, str], dict] = {}
        for allocation in allocations:
            item = db.get(OrderItem, allocation.order_item_id)
            if not item:
                continue
            menu = db.get(Menu, item.menu_id)
            key = (item.menu_id, item.variant_id, item.spicy_level)
            row = grouped.setdefault(key, {
                "quantity": 0, "rice": 0, "sambal": 0, "cracker": 0, "notes": [],
                "allergens": menu.allergens if menu else [],
            })
            row["quantity"] += allocation.quantity
            row["rice"] += min(item.rice_quantity, allocation.quantity)
            row["sambal"] += min(item.sambal_quantity, allocation.quantity)
            row["cracker"] += min(item.cracker_quantity, allocation.quantity)
            if item.note and item.note not in row["notes"]:
                row["notes"].append(item.note)
        for (menu_id, variant_id, spicy_level), row in grouped.items():
            exists = db.scalar(select(ProductionBatch).where(
                ProductionBatch.delivery_id == delivery.id,
                ProductionBatch.menu_id == menu_id,
                ProductionBatch.variant_id == variant_id,
                ProductionBatch.spicy_level == spicy_level,
            ))
            if exists:
                exists.production_date = production_date
                exists.quantity = row["quantity"]
                exists.rice_quantity = row["rice"]
                exists.sambal_quantity = row["sambal"]
                exists.cracker_quantity = row["cracker"]
                exists.notes = row["notes"]
                exists.allergens = row["allergens"]
                continue
            db.add(ProductionBatch(
                delivery_id=delivery.id, production_date=production_date, menu_id=menu_id, variant_id=variant_id,
                quantity=row["quantity"], spicy_level=spicy_level, rice_quantity=row["rice"],
                sambal_quantity=row["sambal"], cracker_quantity=row["cracker"], notes=row["notes"],
                allergens=row["allergens"], status=initial_status,
            ))


def set_order_production_state(order: Order, db: Session, status: str) -> None:
    delivery_ids = db.scalars(select(DeliverySchedule.id).where(DeliverySchedule.order_id == order.id)).all()
    batches = db.scalars(select(ProductionBatch).where(ProductionBatch.delivery_id.in_(delivery_ids))).all() if delivery_ids else []
    for batch in batches:
        batch.status = status


def sync_order_from_production(order: Order, db: Session) -> None:
    delivery_ids = db.scalars(select(DeliverySchedule.id).where(DeliverySchedule.order_id == order.id)).all()
    batches = db.scalars(select(ProductionBatch).where(ProductionBatch.delivery_id.in_(delivery_ids))).all() if delivery_ids else []
    if not batches:
        return
    states = {batch.status for batch in batches}
    if "bermasalah" in states:
        order.status = "bermasalah"
    elif states == {"siap_dikirim"}:
        order.status = "siap_dikirim"
    elif states <= {"lolos_qc", "siap_dikirim"}:
        order.status = "lolos_qc"
    elif states & {"diproduksi", "lolos_qc", "siap_dikirim"}:
        order.status = "diproduksi"
    elif states == {"menunggu_produksi"}:
        order.status = "menunggu_produksi"


def assign_available_courier(delivery: DeliverySchedule, actor_id: int, db: Session) -> None:
    """Assign the least-loaded courier when kitchen releases an unassigned delivery."""
    if delivery.courier_id:
        return
    order = db.get(Order, delivery.order_id)
    if not order or order.delivery_method != "diantar":
        return
    couriers = db.scalars(select(User).where(User.role == "pengantar").order_by(User.id)).all()
    if not couriers:
        return
    active_states = {"terjadwal", "siap_dikirim", "dalam_pengiriman"}
    courier = min(couriers, key=lambda item: db.scalar(select(func.count(DeliverySchedule.id)).where(
        DeliverySchedule.courier_id == item.id, DeliverySchedule.status.in_(active_states)
    )) or 0)
    delivery.courier_id = courier.id
    db.add(AuditEvent(actor_id=actor_id, entity_type="delivery", entity_id=str(delivery.id), action="tetapkan_pengantar_otomatis", after={"courier_id": courier.id}))


@app.post("/checkout")
async def checkout(body: CheckoutIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    package = db.get(Package, body.package_id)
    if not package or not package.active:
        raise HTTPException(400, "Paket tidak tersedia")
    total_packs = sum(i.quantity for i in body.items)
    expected_packs = package.duration_days * body.meals_per_day
    if total_packs != expected_packs or total_packs < package.min_packs or total_packs > package.max_packs:
        raise HTTPException(422, f"Paket {package.name} dengan {body.meals_per_day} kali makan per hari harus berisi tepat {expected_packs} lauk")
    meal_positions = [(item.meal_day, item.meal_sequence) for item in body.items for _ in range(item.quantity)]
    expected_positions = {(day, meal) for day in range(1, package.duration_days + 1) for meal in range(1, body.meals_per_day + 1)}
    if len(meal_positions) != len(set(meal_positions)) or set(meal_positions) != expected_positions:
        raise HTTPException(422, "Setiap kolom hari dan waktu makan harus memiliki tepat satu lauk")
    if body.delivery_method == "diantar" and body.address_id is None:
        raise HTTPException(422, "Alamat wajib dipilih untuk pesanan yang diantar")
    selected_address = db.scalar(select(Address).where(Address.id == body.address_id, Address.user_id == user.id)) if body.address_id else None
    if body.address_id and not selected_address: raise HTTPException(403, "Alamat bukan milik akun ini")
    addresses_by_slot: dict[int, Address] = {}
    if body.delivery_method == "diantar":
        for slot_id in body.slot_ids:
            address_id = body.delivery_addresses.get(slot_id, body.address_id)
            address = db.scalar(select(Address).where(Address.id == address_id, Address.user_id == user.id)) if address_id else None
            if not address: raise HTTPException(422, "Setiap pengiriman harus memiliki alamat yang valid")
            addresses_by_slot[slot_id] = address
    slots_locked = db.scalars(select(CapacitySlot).where(CapacitySlot.id.in_(body.slot_ids)).with_for_update()).all()
    if len(slots_locked) != len(set(body.slot_ids)):
        raise HTTPException(400, "Slot pengiriman tidak valid")
    selected_slot_ids = set(body.slot_ids)
    if any(item.slot_id is not None and item.slot_id not in selected_slot_ids for item in body.items):
        raise HTTPException(422, "Tanggal pada salah satu lauk tidak termasuk jadwal yang dipilih")
    allocations: list[dict[int, int]] = []
    slot_counts = {slot_id: 0 for slot_id in body.slot_ids}
    day_default_slots: dict[int, int] = {}
    for request_item in body.items:
        item_allocation: dict[int, int] = {}
        for _ in range(request_item.quantity):
            slot_id = request_item.slot_id or day_default_slots.setdefault(request_item.meal_day, body.slot_ids[len(day_default_slots) % len(body.slot_ids)])
            item_allocation[slot_id] = item_allocation.get(slot_id, 0) + 1
            slot_counts[slot_id] += 1
        allocations.append(item_allocation)
    if any(count == 0 for count in slot_counts.values()):
        raise HTTPException(422, "Setiap jadwal harus memiliki minimal satu pack")
    for slot in slots_locked:
        if slot.delivery_date <= date.today():
            raise HTTPException(409, "Pengiriman paling cepat dapat dipilih mulai besok")
        if not slot.active or datetime.utcnow() >= slot.cutoff_at:
            raise HTTPException(409, "Cut-off salah satu slot sudah lewat")
        if slot.reserved + slot_counts[slot.id] > slot.capacity:
            raise HTTPException(409, f"Slot {slot.label} pada {slot.delivery_date} sudah penuh")
    variant_ids = [i.variant_id for i in body.items]
    variants = {v.id: v for v in db.scalars(select(MenuVariant).where(MenuVariant.id.in_(variant_ids), MenuVariant.active.is_(True))).all()}
    if len(variants) != len(set(variant_ids)):
        raise HTTPException(409, "Salah satu menu tidak tersedia")
    menus = {m.id: m for m in db.scalars(select(Menu).where(Menu.id.in_([v.menu_id for v in variants.values()]), Menu.active.is_(True))).all()}
    lines = [PriceLine(variants[i.variant_id].price, i.quantity, i.portions, i.rice_quantity, variants[i.variant_id].extra_portion_price, i.sambal_quantity, i.cracker_quantity) for i in body.items]
    shipping_by_slot = {s.id: s.shipping_fee + distance_surcharge(addresses_by_slot[s.id].distance_km if s.id in addresses_by_slot else None) + Decimal(max(0, slot_counts[s.id] - 1) * DELIVERY_FEE_PER_EXTRA_PACK) for s in slots_locked}
    totals = calculate(lines, package.discount_percent, list(shipping_by_slot.values()) if body.delivery_method == "diantar" else [])
    public_id = f"LSS-{datetime.utcnow():%Y%m%d}-{uuid.uuid4().hex[:8].upper()}"
    order = Order(public_id=public_id, user_id=user.id, address_id=body.address_id if body.delivery_method == "diantar" else None, package_id=package.id, delivery_method=body.delivery_method, status="menunggu_pembayaran", **totals)
    db.add(order); db.flush()
    order_items = []
    for request_item in body.items:
        variant, menu = variants[request_item.variant_id], menus[variants[request_item.variant_id].menu_id]
        item = OrderItem(order_id=order.id, menu_id=menu.id, variant_id=variant.id, menu_name_snapshot=menu.name, variant_snapshot=variant.kind, unit_price_snapshot=variant.price,
                         quantity=request_item.quantity, portions=request_item.portions, spicy_level=request_item.spicy_level, rice_quantity=request_item.rice_quantity, sambal_quantity=request_item.sambal_quantity, cracker_quantity=request_item.cracker_quantity, note=request_item.note,
                         meal_day=request_item.meal_day, meal_sequence=request_item.meal_sequence)
        db.add(item); order_items.append(item)
    db.flush()
    for slot in slots_locked:
        slot.reserved += slot_counts[slot.id]
        delivery = DeliverySchedule(order_id=order.id, slot_id=slot.id, address_id=addresses_by_slot.get(slot.id).id if slot.id in addresses_by_slot else None, shipping_fee_snapshot=shipping_by_slot[slot.id] if body.delivery_method == "diantar" else 0)
        db.add(delivery); db.flush()
        for item, item_allocation in zip(order_items, allocations):
            take = item_allocation.get(slot.id, 0)
            if take > 0:
                db.add(DeliveryItem(delivery_id=delivery.id, order_item_id=item.id, quantity=take))
    db.flush()
    ensure_production_for_order(order, db)
    snap = await create_snap_transaction(public_id, money(order.total), {"first_name": user.name, "email": user.email, "phone": user.phone or ""},
                                         [{"id": str(i.id), "price": money(i.unit_price_snapshot), "quantity": i.quantity, "name": i.menu_name_snapshot} for i in order_items])
    payment = Payment(order_id=order.id, provider_order_id=public_id, status="pending", gross_amount=order.total, snap_token=snap["token"], redirect_url=snap["redirect_url"], expires_at=snap["expires_at"])
    db.add_all([payment, AuditEvent(actor_id=user.id, entity_type="order", entity_id=public_id, action="checkout_dibuat", after={"total": money(order.total)})])
    if package.base_packs > 1:
        db.add(Subscription(user_id=user.id, order_id=order.id, remaining_packs=total_packs))
    db.commit()
    return {"order_id": public_id, "status": order.status, "payment_status": payment.status, "total": money(order.total), "breakdown": {k: money(v) for k, v in totals.items()}, "snap_redirect_url": payment.redirect_url, "demo_mode": snap["demo"], "label": "Transaksi simulasi — Midtrans Sandbox"}


def serialize_order(order: Order, db: Session):
    payment = db.scalar(select(Payment).where(Payment.order_id == order.id))
    items = db.scalars(select(OrderItem).where(OrderItem.order_id == order.id)).all()
    deliveries = db.scalars(select(DeliverySchedule).where(DeliverySchedule.order_id == order.id)).all()
    address = db.get(Address, order.address_id) if order.address_id else None
    delivery_rows = []
    for delivery in deliveries:
        slot = db.get(CapacitySlot, delivery.slot_id)
        allocations = db.execute(select(DeliveryItem, OrderItem).join(OrderItem, OrderItem.id == DeliveryItem.order_item_id).where(DeliveryItem.delivery_id == delivery.id)).all()
        courier = db.get(User, delivery.courier_id) if delivery.courier_id else None
        delivery_address = db.get(Address, delivery.address_id) if delivery.address_id else address
        delivery_rows.append({"id": delivery.id, "status": delivery.status, "slot_id": delivery.slot_id,
                              "date": slot.delivery_date.isoformat() if slot else None, "label": slot.label if slot else None,
                              "shipping_fee": money(delivery.shipping_fee_snapshot),
                              "items": [{"name": item.menu_name_snapshot, "quantity": allocation.quantity} for allocation, item in allocations],
                              "courier_name": courier.name if courier else None, "courier_phone": courier.phone if courier else None,
                              "address": ({"id": delivery_address.id, "label": delivery_address.label, "recipient": delivery_address.recipient, "phone": delivery_address.phone, "line": delivery_address.line, "zone": delivery_address.zone} if delivery_address else None)})
    return {"id": order.public_id, "status": order.status, "payment_status": payment.status if payment else None, "total": money(order.total), "created_at": order.created_at.isoformat(),
            "items": [{"name": i.menu_name_snapshot, "variant": i.variant_snapshot, "quantity": i.quantity, "portions": i.portions, "rice_quantity": i.rice_quantity, "sambal_quantity": i.sambal_quantity, "cracker_quantity": i.cracker_quantity, "meal_day": i.meal_day, "meal_sequence": i.meal_sequence} for i in items],
            "deliveries": delivery_rows, "payment_url": payment.redirect_url if payment else None,
            "address": ({"id": address.id, "label": address.label, "recipient": address.recipient, "phone": address.phone, "line": address.line, "zone": address.zone,
                         "street": address.street, "district": address.district, "city": address.city, "province": address.province, "postal_code": address.postal_code,
                         "latitude": float(address.latitude) if address.latitude is not None else None, "longitude": float(address.longitude) if address.longitude is not None else None,
                         "distance_km": float(address.distance_km) if address.distance_km is not None else None} if address else None), "delivery_method": order.delivery_method}


def customer_order(public_id: str, user: User, db: Session, lock: bool = False):
    query = select(Order).where(Order.public_id == public_id, Order.user_id == user.id)
    if lock: query = query.with_for_update()
    order = db.scalar(query)
    if not order: raise HTTPException(404, "Pesanan tidak ditemukan")
    return order


@app.post("/orders/{public_id}/cancel")
def cancel_order(public_id: str, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    order = customer_order(public_id, user, db, True)
    if order.status != "menunggu_pembayaran": raise HTTPException(409, "Pesanan hanya dapat dibatalkan sebelum pembayaran")
    deliveries = db.scalars(select(DeliverySchedule).where(DeliverySchedule.order_id == order.id)).all()
    for delivery in deliveries:
        slot = db.get(CapacitySlot, delivery.slot_id)
        quantity = db.scalar(select(func.coalesce(func.sum(DeliveryItem.quantity), 0)).where(DeliveryItem.delivery_id == delivery.id)) or 0
        if slot: slot.reserved = max(0, slot.reserved - int(quantity))
    order.status = "dibatalkan"
    set_order_production_state(order, db, "dibatalkan")
    db.add(AuditEvent(actor_id=user.id, entity_type="order", entity_id=public_id, action="dibatalkan_customer"))
    db.commit(); return serialize_order(order, db)


@app.patch("/orders/{public_id}/address")
def change_order_address(public_id: str, body: OrderAddressIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    order = customer_order(public_id, user, db, True)
    if order.status not in {"menunggu_pembayaran", "dibayar", "dikonfirmasi"}: raise HTTPException(409, "Alamat tidak dapat diubah setelah produksi dimulai")
    address = db.scalar(select(Address).where(Address.id == body.address_id, Address.user_id == user.id))
    if not address: raise HTTPException(404, "Alamat tidak ditemukan")
    order.address_id = address.id
    for delivery in db.scalars(select(DeliverySchedule).where(DeliverySchedule.order_id == order.id)).all():
        if delivery.status == "terjadwal": delivery.address_id = address.id
    db.add(AuditEvent(actor_id=user.id, entity_type="order", entity_id=public_id, action="ubah_alamat", after={"address_id": address.id}))
    db.commit(); return serialize_order(order, db)


@app.patch("/deliveries/{delivery_id}/reschedule")
def reschedule_delivery(delivery_id: int, body: DeliveryRescheduleIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    delivery = db.get(DeliverySchedule, delivery_id)
    order = db.get(Order, delivery.order_id) if delivery else None
    if not order or order.user_id != user.id: raise HTTPException(404, "Pengiriman tidak ditemukan")
    if order.status not in {"menunggu_pembayaran", "dibayar", "dikonfirmasi"}: raise HTTPException(409, "Jadwal tidak dapat diubah setelah produksi dimulai")
    old_slot, new_slot = db.get(CapacitySlot, delivery.slot_id), db.get(CapacitySlot, body.slot_id)
    quantity = int(db.scalar(select(func.coalesce(func.sum(DeliveryItem.quantity), 0)).where(DeliveryItem.delivery_id == delivery.id)) or 0)
    if not new_slot or not new_slot.active or new_slot.cutoff_at <= datetime.utcnow(): raise HTTPException(409, "Jadwal baru sudah ditutup")
    if new_slot.capacity - new_slot.reserved < quantity: raise HTTPException(409, "Kapasitas jadwal baru tidak mencukupi")
    if old_slot: old_slot.reserved = max(0, old_slot.reserved - quantity)
    new_slot.reserved += quantity; delivery.slot_id = new_slot.id; delivery.shipping_fee_snapshot = new_slot.shipping_fee
    for batch in db.scalars(select(ProductionBatch).where(ProductionBatch.delivery_id == delivery.id)).all():
        batch.production_date = new_slot.delivery_date - timedelta(days=1)
    db.add(AuditEvent(actor_id=user.id, entity_type="delivery", entity_id=str(delivery.id), action="ubah_jadwal", after={"slot_id": new_slot.id}))
    db.commit(); return serialize_order(order, db)


@app.get("/orders")
def my_orders(user: User = Depends(current_user), db: Session = Depends(get_db)):
    query = select(Order).order_by(Order.created_at.desc())
    if user.role == "pelanggan": query = query.where(Order.user_id == user.id)
    elif user.role not in {"admin", "dapur"}: raise HTTPException(403)
    return [serialize_order(o, db) for o in db.scalars(query).all()]


@app.get("/orders/{public_id}")
def order_detail(public_id: str, user: User = Depends(current_user), db: Session = Depends(get_db)):
    order = db.scalar(select(Order).where(Order.public_id == public_id))
    if not order or (user.role == "pelanggan" and order.user_id != user.id): raise HTTPException(404, "Pesanan tidak ditemukan")
    return serialize_order(order, db)


@app.post("/orders/{public_id}/retry-payment")
async def retry_payment(public_id: str, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    order = customer_order(public_id, user, db, True)
    payment = db.scalar(select(Payment).where(Payment.order_id == order.id))
    if not payment or payment.status not in {"gagal", "kedaluwarsa"}: raise HTTPException(409, "Pembayaran belum dapat diulang")
    items = db.scalars(select(OrderItem).where(OrderItem.order_id == order.id)).all()
    snap = await create_snap_transaction(public_id, money(order.total), {"first_name": user.name, "email": user.email, "phone": user.phone or ""}, [{"id": str(i.id), "price": money(i.unit_price_snapshot), "quantity": i.quantity, "name": i.menu_name_snapshot} for i in items])
    payment.status = "pending"; payment.snap_token = snap["token"]; payment.redirect_url = snap["redirect_url"]; payment.expires_at = snap["expires_at"]; payment.updated_at = datetime.utcnow(); order.status = "menunggu_pembayaran"
    set_order_production_state(order, db, "menunggu_pembayaran")
    db.add(AuditEvent(actor_id=user.id, entity_type="payment", entity_id=public_id, action="bayar_ulang")); db.commit()
    return {"payment_url": payment.redirect_url, "payment_status": payment.status}


@app.post("/payments/webhook")
def payment_webhook(body: WebhookIn, db: Session = Depends(get_db)):
    if not verify_signature(body.order_id, body.status_code, body.gross_amount, body.signature_key): raise HTTPException(401, "Signature Midtrans tidak valid")
    payment = db.scalar(select(Payment).where(Payment.provider_order_id == body.order_id).with_for_update())
    if not payment or Decimal(body.gross_amount) != payment.gross_amount: raise HTTPException(400, "Order atau nominal tidak sesuai")
    new_status = normalized_payment_status(body.transaction_status, body.fraud_status)
    if payment.status == new_status: return {"status": "duplikat_diabaikan"}
    before = payment.status; payment.status = new_status; payment.provider_payload = body.model_dump(); payment.updated_at = datetime.utcnow()
    order = db.get(Order, payment.order_id)
    if new_status == "settlement" and order.status == "menunggu_pembayaran":
        order.status = "menunggu_produksi"
        set_order_production_state(order, db, "menunggu_produksi")
    elif new_status in {"gagal", "kedaluwarsa"} and order.status == "menunggu_pembayaran":
        order.status = "dibatalkan"
        set_order_production_state(order, db, "dibatalkan")
    db.add(AuditEvent(entity_type="payment", entity_id=body.order_id, action="webhook_midtrans", before={"status": before}, after={"status": new_status}))
    db.commit(); return {"status": "diproses"}


@app.post("/demo/payments/{public_id}/{result}")
def demo_payment(public_id: str, result: Literal["sukses", "gagal", "kedaluwarsa"], user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    if os.getenv("MIDTRANS_SERVER_KEY"): raise HTTPException(404)
    payment = db.scalar(select(Payment).where(Payment.provider_order_id == public_id))
    if not payment: raise HTTPException(404, "Pembayaran tidak ditemukan")
    payment.status = "settlement" if result == "sukses" else ("kedaluwarsa" if result == "kedaluwarsa" else "gagal")
    order = db.get(Order, payment.order_id)
    order.status = "menunggu_produksi" if result == "sukses" else "dibatalkan"
    set_order_production_state(order, db, "menunggu_produksi" if result == "sukses" else "dibatalkan")
    db.add(AuditEvent(actor_id=user.id, entity_type="payment", entity_id=public_id, action=f"simulasi_{result}", after={"status": payment.status}))
    db.commit(); return {"order_id": public_id, "payment_status": payment.status, "order_status": order.status, "label": "Simulasi lokal, bukan bukti transaksi Midtrans"}


@app.post("/orders/{public_id}/status")
def update_order_status(public_id: str, body: StatusIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    order = db.scalar(select(Order).where(Order.public_id == public_id).with_for_update())
    if not order: raise HTTPException(404)
    if body.status in {"dikonfirmasi", "menunggu_produksi", "diproduksi", "lolos_qc", "siap_dikirim", "dalam_pengiriman", "selesai"}:
        raise HTTPException(403, "Status operasional berubah otomatis dari pembayaran, dapur, dan pengantar")
    if body.status not in ORDER_TRANSITIONS.get(order.status, set()): raise HTTPException(409, f"Transisi {order.status} ke {body.status} tidak diizinkan")
    before = order.status; order.status = body.status
    db.add(AuditEvent(actor_id=user.id, entity_type="order", entity_id=public_id, action="ubah_status", before={"status": before}, after={"status": body.status}))
    db.commit(); return {"id": public_id, "status": order.status}


@app.get("/admin/summary")
def admin_summary(user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    counts = dict(db.execute(select(Order.status, func.count(Order.id)).group_by(Order.status)).all())
    revenue = db.scalar(select(func.coalesce(func.sum(Order.total), 0)).join(Payment, Payment.order_id == Order.id).where(Payment.status == "settlement"))
    low_stock = db.scalar(select(func.count(InventoryItem.id)).where(InventoryItem.stock <= InventoryItem.min_stock))
    return {"pesanan_masuk": sum(counts.values()), "perlu_verifikasi": counts.get("menunggu_pembayaran", 0), "perlu_diproduksi": counts.get("menunggu_produksi", 0), "siap_kirim": counts.get("siap_dikirim", 0), "pendapatan_terkonfirmasi": money(revenue), "stok_menipis": low_stock, "demo_mode": not bool(os.getenv("MIDTRANS_SERVER_KEY")), "catatan": "Margin dan laba estimasi bergantung pada kelengkapan input HPP, kemasan, ongkir, biaya pembayaran, diskon, waste, dan biaya tetap."}


@app.get("/kitchen/production")
def production(user: User = Depends(require_roles("admin", "dapur")), db: Session = Depends(get_db)):
    batches = db.scalars(select(ProductionBatch).order_by(ProductionBatch.production_date)).all()
    result = []
    for batch in batches:
        menu = db.get(Menu, batch.menu_id)
        delivery = db.get(DeliverySchedule, batch.delivery_id) if batch.delivery_id else None
        slot = db.get(CapacitySlot, delivery.slot_id) if delivery else None
        delivery_date = slot.delivery_date if slot else batch.production_date + timedelta(days=1)
        if not slot:
            slot = db.scalar(select(CapacitySlot).where(CapacitySlot.delivery_date == delivery_date, CapacitySlot.active.is_(True)).order_by(CapacitySlot.label))
        start_label = (slot.label.split("–")[0] if slot else "09.00").replace(".", ":")
        try: hour, minute = (int(x) for x in start_label.split(":")[:2])
        except (ValueError, TypeError): hour, minute = 9, 0
        deliver_at = datetime.combine(delivery_date, datetime.min.time()).replace(hour=hour, minute=minute)
        row = {column.name: getattr(batch, column.name) for column in ProductionBatch.__table__.columns}
        row.update({"menu_name": menu.name if menu else "Menu produksi", "deliver_at": deliver_at.isoformat(), "finish_by": (deliver_at - timedelta(minutes=30)).isoformat()})
        result.append(row)
    return sorted(result, key=lambda row: (row["deliver_at"], row["menu_name"], row["id"]))


@app.post("/kitchen/production/{batch_id}/status")
def update_batch(batch_id: int, body: StatusIn, user: User = Depends(require_roles("dapur")), db: Session = Depends(get_db)):
    transitions = {"menunggu_produksi": {"diproduksi"}, "diproduksi": {"lolos_qc", "bermasalah"}, "lolos_qc": {"siap_dikirim"}, "bermasalah": {"diproduksi"}, "siap_dikirim": set()}
    batch = db.get(ProductionBatch, batch_id)
    if not batch: raise HTTPException(404, "Batch tidak ditemukan")
    if body.status not in transitions.get(batch.status, set()): raise HTTPException(409, "Transisi batch tidak diizinkan")
    before = batch.status; batch.status = body.status
    db.add(AuditEvent(actor_id=user.id, entity_type="production_batch", entity_id=str(batch.id), action="ubah_status", before={"status": before}, after={"status": body.status}))
    db.flush()
    linked_delivery = db.get(DeliverySchedule, batch.delivery_id) if batch.delivery_id else None
    if body.status == "siap_dikirim":
        if linked_delivery:
            deliveries = [linked_delivery]
        else:
            slot_ids = db.scalars(select(CapacitySlot.id).where(CapacitySlot.delivery_date == batch.production_date + timedelta(days=1))).all()
            deliveries = db.scalars(select(DeliverySchedule).where(DeliverySchedule.slot_id.in_(slot_ids))).all() if slot_ids else []
        for delivery in deliveries:
            required_batches = db.scalars(select(ProductionBatch).where(ProductionBatch.delivery_id == delivery.id)).all()
            if not required_batches:
                delivery_items = db.scalars(select(DeliveryItem).where(DeliveryItem.delivery_id == delivery.id)).all()
                required_batches = []
                for delivery_item in delivery_items:
                    order_item = db.get(OrderItem, delivery_item.order_item_id)
                    if order_item:
                        required = db.scalar(select(ProductionBatch).where(
                            ProductionBatch.production_date == batch.production_date,
                            ProductionBatch.menu_id == order_item.menu_id,
                            ProductionBatch.variant_id == order_item.variant_id,
                            ProductionBatch.spicy_level == order_item.spicy_level,
                        ))
                        if required:
                            required_batches.append(required)
            if required_batches and all(required.status == "siap_dikirim" for required in required_batches):
                if delivery.status == "terjadwal":
                    delivery.status = "siap_dikirim"
                    assign_available_courier(delivery, user.id, db)
                    db.add(AuditEvent(actor_id=user.id, entity_type="delivery", entity_id=str(delivery.id), action="siap_dari_dapur", before={"status": "terjadwal"}, after={"status": "siap_dikirim"}))
    if linked_delivery:
        order = db.get(Order, linked_delivery.order_id)
        if order:
            sync_order_from_production(order, db)
    db.commit(); return {"id": batch.id, "status": batch.status}


@app.patch("/admin/menus/{menu_id}")
def update_menu(menu_id: int, body: MenuUpdateIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    menu = db.get(Menu, menu_id)
    if not menu: raise HTTPException(404, "Menu tidak ditemukan")
    before = {"name": menu.name, "active": menu.active}
    if body.name is not None: menu.name = body.name
    if body.description is not None: menu.description = body.description
    if body.category is not None: menu.category = body.category
    if body.image_url is not None: menu.image_url = body.image_url
    if body.active is not None: menu.active = body.active
    if body.variant_id is not None and body.price is not None:
        variant = db.scalar(select(MenuVariant).where(MenuVariant.id == body.variant_id, MenuVariant.menu_id == menu.id))
        if not variant: raise HTTPException(400, "Varian tidak dimiliki menu")
        variant.price = body.price
        if body.instructions is not None: variant.instructions = body.instructions
        if body.step_minutes is not None: variant.step_minutes = body.step_minutes
    db.add(AuditEvent(actor_id=user.id, entity_type="menu", entity_id=str(menu.id), action="ubah_menu", before=before, after=body.model_dump(exclude_none=True)))
    db.commit(); return {"id": menu.id, "name": menu.name, "active": menu.active}


@app.post("/admin/menus")
def create_menu(body: MenuCreateIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    if len(body.instructions) != len(body.step_minutes) or len(body.ready_to_eat_instructions) != len(body.ready_to_eat_step_minutes): raise HTTPException(422, "Setiap langkah memasak harus memiliki timer")
    menu = Menu(name=body.name.strip(), description=body.description.strip(), ingredients=body.ingredients, allergens=body.allergens, portion_label=body.portion_label, calories_per_portion=body.calories_per_portion, category=body.category, image_url=body.image_url)
    db.add(menu); db.flush()
    db.add_all([
        MenuVariant(menu_id=menu.id, kind="siap_masak", price=body.ready_to_cook_price, extra_portion_price=0, cook_minutes=sum(body.step_minutes), equipment=body.equipment, instructions=body.instructions, step_minutes=body.step_minutes, storage_guide=body.storage_guide, doneness_guide=body.doneness_guide),
        MenuVariant(menu_id=menu.id, kind="siap_makan", price=body.ready_to_eat_price, extra_portion_price=0, cook_minutes=sum(body.ready_to_eat_step_minutes), equipment=body.equipment, instructions=body.ready_to_eat_instructions, step_minutes=body.ready_to_eat_step_minutes, storage_guide=body.storage_guide, doneness_guide=body.doneness_guide),
    ])
    db.add(AuditEvent(actor_id=user.id, entity_type="menu", entity_id=str(menu.id), action="tambah_menu", after={"name": menu.name}))
    db.commit(); return {"id": menu.id, "name": menu.name}


@app.put("/admin/menus/{menu_id}")
def replace_menu(menu_id: int, body: MenuFullUpdateIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    menu = db.get(Menu, menu_id)
    if not menu: raise HTTPException(404, "Menu tidak ditemukan")
    variants = {v.id: v for v in db.scalars(select(MenuVariant).where(MenuVariant.menu_id == menu.id)).all()}
    if set(variants) != {item.id for item in body.variants}: raise HTTPException(422, "Seluruh varian menu harus disertakan")
    for item in body.variants:
        if len(item.instructions) != len(item.step_minutes): raise HTTPException(422, "Setiap langkah harus memiliki timer")
    before = {"name": menu.name, "category": menu.category}
    menu.name = body.name.strip(); menu.description = body.description.strip(); menu.ingredients = body.ingredients; menu.allergens = body.allergens
    menu.portion_label = body.portion_label; menu.calories_per_portion = body.calories_per_portion; menu.category = body.category
    if body.image_url is not None: menu.image_url = body.image_url
    for item in body.variants:
        variant = variants[item.id]; variant.price = item.price; variant.instructions = item.instructions; variant.step_minutes = item.step_minutes
        variant.cook_minutes = sum(item.step_minutes); variant.equipment = item.equipment; variant.storage_guide = item.storage_guide; variant.doneness_guide = item.doneness_guide
    db.add(AuditEvent(actor_id=user.id, entity_type="menu", entity_id=str(menu.id), action="ubah_menu_lengkap", before=before, after={"name": menu.name, "category": menu.category}))
    db.commit(); return {"id": menu.id, "name": menu.name, "active": menu.active}


@app.delete("/admin/menus/{menu_id}")
def delete_menu(menu_id: int, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    menu = db.get(Menu, menu_id)
    if not menu: raise HTTPException(404, "Menu tidak ditemukan")
    menu.active = False
    db.add(AuditEvent(actor_id=user.id, entity_type="menu", entity_id=str(menu.id), action="arsipkan_menu", before={"active": True}, after={"active": False}))
    db.commit(); return {"id": menu.id, "active": False}


@app.get("/admin/menus/archived")
def archived_menus(user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    menus = db.scalars(select(Menu).where(Menu.active.is_(False)).order_by(Menu.name)).all()
    result = []
    for menu in menus:
        variants = db.scalars(select(MenuVariant).where(MenuVariant.menu_id == menu.id)).all()
        result.append({"id": menu.id, "name": menu.name, "description": menu.description, "ingredients": menu.ingredients, "allergens": menu.allergens,
                       "portion_label": menu.portion_label, "calories_per_portion": menu.calories_per_portion, "category": menu.category, "image_url": menu.image_url, "active": False,
                       "variants": [{"id": v.id, "kind": v.kind, "price": money(v.price), "cook_minutes": v.cook_minutes, "instructions": v.instructions, "step_minutes": v.step_minutes, "equipment": v.equipment, "storage_guide": v.storage_guide, "doneness_guide": v.doneness_guide} for v in variants]})
    return result


@app.patch("/admin/slots/{slot_id}")
def update_slot(slot_id: int, body: SlotUpdateIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    slot = db.get(CapacitySlot, slot_id)
    if not slot: raise HTTPException(404, "Slot tidak ditemukan")
    if body.capacity is not None and body.capacity < slot.reserved: raise HTTPException(409, "Kapasitas tidak boleh lebih kecil dari reservasi")
    before = {"capacity": slot.capacity, "shipping_fee": money(slot.shipping_fee), "active": slot.active}
    if body.capacity is not None: slot.capacity = body.capacity
    if body.shipping_fee is not None: slot.shipping_fee = body.shipping_fee
    if body.active is not None: slot.active = body.active
    db.add(AuditEvent(actor_id=user.id, entity_type="capacity_slot", entity_id=str(slot.id), action="ubah_slot", before=before, after=body.model_dump(exclude_none=True)))
    db.commit(); return {"id": slot.id, "capacity": slot.capacity, "reserved": slot.reserved, "shipping_fee": money(slot.shipping_fee), "active": slot.active}


@app.post("/admin/slots")
def create_slot(body: SlotCreateIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    if body.date <= date.today(): raise HTTPException(422, "Tanggal pengiriman paling cepat adalah besok")
    if db.scalar(select(CapacitySlot).where(CapacitySlot.delivery_date == body.date, CapacitySlot.label == body.label)): raise HTTPException(409, "Slot pada tanggal dan jam tersebut sudah ada")
    cutoff = datetime.combine(body.date - timedelta(days=1), datetime.min.time()).replace(hour=16)
    slot = CapacitySlot(delivery_date=body.date, label=body.label, capacity=body.capacity, reserved=0, cutoff_at=cutoff, shipping_fee=body.shipping_fee, delivery_type="reguler")
    db.add(slot); db.flush(); db.add(AuditEvent(actor_id=user.id, entity_type="capacity_slot", entity_id=str(slot.id), action="tambah_slot", after={"date": body.date.isoformat(), "label": body.label})); db.commit()
    return {"id": slot.id}


@app.delete("/admin/slots/{slot_id}")
def delete_slot(slot_id: int, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    slot = db.get(CapacitySlot, slot_id)
    if not slot: raise HTTPException(404, "Slot tidak ditemukan")
    if slot.reserved > 0: raise HTTPException(409, "Slot yang sudah memiliki pesanan tidak dapat dihapus")
    slot.active = False; db.add(AuditEvent(actor_id=user.id, entity_type="capacity_slot", entity_id=str(slot.id), action="arsipkan_slot", after={"active": False})); db.commit()
    return {"id": slot.id, "active": False}


@app.post("/admin/packages")
def create_package(body: PackageIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    if body.min_packs > body.max_packs or not body.min_packs <= body.base_packs <= body.max_packs: raise HTTPException(422, "Jumlah pack dasar harus berada dalam batas minimum dan maksimum")
    if db.scalar(select(Package).where(Package.name == body.name)): raise HTTPException(409, "Nama paket sudah digunakan")
    package = Package(**body.model_dump()); db.add(package); db.flush(); db.add(AuditEvent(actor_id=user.id, entity_type="package", entity_id=str(package.id), action="tambah_paket", after=body.model_dump(mode="json"))); db.commit()
    return {"id": package.id}


@app.patch("/admin/packages/{package_id}")
def update_package(package_id: int, body: PackageIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    package = db.get(Package, package_id)
    if not package: raise HTTPException(404, "Paket tidak ditemukan")
    if body.min_packs > body.max_packs or not body.min_packs <= body.base_packs <= body.max_packs: raise HTTPException(422, "Jumlah pack dasar harus berada dalam batas minimum dan maksimum")
    before = {"name": package.name, "base_packs": package.base_packs}
    for key, value in body.model_dump().items(): setattr(package, key, value)
    db.add(AuditEvent(actor_id=user.id, entity_type="package", entity_id=str(package.id), action="ubah_paket", before=before, after=body.model_dump(mode="json"))); db.commit()
    return {"id": package.id}


@app.delete("/admin/packages/{package_id}")
def delete_package(package_id: int, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    package = db.get(Package, package_id)
    if not package: raise HTTPException(404, "Paket tidak ditemukan")
    package.active = False; db.add(AuditEvent(actor_id=user.id, entity_type="package", entity_id=str(package.id), action="arsipkan_paket", after={"active": False})); db.commit()
    return {"id": package.id, "active": False}


@app.get("/admin/inventory")
def inventory(user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    rows = db.scalars(select(InventoryItem).order_by(InventoryItem.name)).all()
    return [{"id": x.id, "name": x.name, "unit": x.unit, "stock": float(x.stock), "min_stock": float(x.min_stock), "purchase_price": money(x.purchase_price), "supplier": x.supplier, "use_by": x.use_by.isoformat() if x.use_by else None, "low": x.stock <= x.min_stock} for x in rows]


@app.post("/admin/inventory/{item_id}/movement")
def inventory_movement(item_id: int, body: MovementIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    from .models import InventoryMovement
    existing = db.scalar(select(InventoryMovement).where(InventoryMovement.idempotency_key == body.idempotency_key))
    if existing: return {"status": "duplikat_diabaikan", "movement_id": existing.id}
    item = db.get(InventoryItem, item_id)
    if not item: raise HTTPException(404, "Bahan tidak ditemukan")
    delta = body.quantity if body.movement_type in {"masuk", "koreksi"} else -abs(body.quantity)
    if item.stock + delta < 0: raise HTTPException(409, "Stok tidak cukup")
    item.stock += delta
    movement = InventoryMovement(inventory_item_id=item.id, quantity=delta, movement_type=body.movement_type, idempotency_key=body.idempotency_key)
    db.add_all([movement, AuditEvent(actor_id=user.id, entity_type="inventory", entity_id=str(item.id), action=body.movement_type, after={"quantity": str(delta), "stock": str(item.stock)})]); db.commit()
    return {"status": "tersimpan", "stock": float(item.stock)}


@app.post("/orders/{public_id}/review")
def review(public_id: str, body: ReviewIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    order = db.scalar(select(Order).where(Order.public_id == public_id, Order.user_id == user.id))
    if not order or order.status != "selesai": raise HTTPException(409, "Ulasan hanya tersedia untuk pesanan selesai")
    if db.scalar(select(Review).where(Review.order_id == order.id)): raise HTTPException(409, "Pesanan sudah diulas")
    item = Review(order_id=order.id, user_id=user.id, **body.model_dump()); db.add(item); db.commit(); return {"status": "ulasan_tersimpan"}


@app.post("/deliveries/{delivery_id}/problem")
def complaint(delivery_id: int, body: ComplaintIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    delivery = db.get(DeliverySchedule, delivery_id)
    order = db.get(Order, delivery.order_id) if delivery else None
    if not order or order.user_id != user.id: raise HTTPException(404)
    complaint_row = Complaint(user_id=user.id, delivery_id=delivery_id, description=body.description, photo_data=body.photo_data); db.add(complaint_row); delivery.status = "bermasalah"; db.commit()
    return {"status": "masalah_dicatat"}


@app.post("/me/deliveries/{delivery_id}/receive")
def receive_delivery(delivery_id: int, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    delivery = db.get(DeliverySchedule, delivery_id)
    order = db.get(Order, delivery.order_id) if delivery else None
    if not order or order.user_id != user.id: raise HTTPException(404, "Pengiriman tidak ditemukan")
    if delivery.status not in {"dalam_pengiriman", "siap_dikirim"}: raise HTTPException(409, "Pengiriman belum dapat dikonfirmasi")
    delivery.status = "diterima"; delivery.received_at = datetime.utcnow()
    for delivery_item in db.scalars(select(DeliveryItem).where(DeliveryItem.delivery_id == delivery.id)).all():
        order_item = db.get(OrderItem, delivery_item.order_item_id); variant = db.get(MenuVariant, order_item.variant_id)
        delivery_item.pantry_status = "tersedia"; delivery_item.recommended_use_at = date.today() + timedelta(days=variant.recommended_use_days)
    if all(x.status == "diterima" for x in db.scalars(select(DeliverySchedule).where(DeliverySchedule.order_id == order.id)).all()): order.status = "selesai"
    db.add(AuditEvent(actor_id=user.id, entity_type="delivery", entity_id=str(delivery.id), action="diterima_customer"))
    db.commit(); return serialize_order(order, db)


@app.post("/deliveries/{delivery_id}/status")
def update_delivery(delivery_id: int, body: StatusIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    transitions = {"terjadwal": set(), "siap_dikirim": set(), "dalam_pengiriman": {"bermasalah"}, "bermasalah": set(), "diterima": set()}
    delivery = db.get(DeliverySchedule, delivery_id)
    if not delivery: raise HTTPException(404, "Pengiriman tidak ditemukan")
    if body.status == "siap_dikirim": raise HTTPException(403, "Status siap dikirim hanya dapat ditetapkan oleh dapur setelah QC")
    if body.status not in transitions.get(delivery.status, set()): raise HTTPException(409, "Transisi pengiriman tidak diizinkan")
    before = delivery.status; delivery.status = body.status
    if body.status == "siap_dikirim":
        order = db.get(Order, delivery.order_id)
        order_deliveries = db.scalars(select(DeliverySchedule).where(DeliverySchedule.order_id == delivery.order_id)).all()
        if order and all(item.status in {"siap_dikirim", "dalam_pengiriman", "diterima"} for item in order_deliveries):
            order.status = "siap_dikirim"
    if body.status == "diterima":
        delivery.received_at = datetime.utcnow()
        for delivery_item in db.scalars(select(DeliveryItem).where(DeliveryItem.delivery_id == delivery.id)).all():
            order_item = db.get(OrderItem, delivery_item.order_item_id)
            variant = db.get(MenuVariant, order_item.variant_id)
            delivery_item.pantry_status = "tersedia"
            delivery_item.recommended_use_at = date.today() + timedelta(days=variant.recommended_use_days)
    db.add(AuditEvent(actor_id=user.id, entity_type="delivery", entity_id=str(delivery.id), action="ubah_status", before={"status": before}, after={"status": body.status}))
    db.commit(); return {"id": delivery.id, "status": delivery.status}


@app.get("/admin/couriers")
def couriers(user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    rows = db.scalars(select(User).where(User.role == "pengantar").order_by(User.name)).all()
    result = []
    for courier in rows:
        deliveries = db.scalars(select(DeliverySchedule).where(DeliverySchedule.courier_id == courier.id).order_by(DeliverySchedule.id.desc())).all()
        history = []
        for delivery in deliveries:
            order, slot = db.get(Order, delivery.order_id), db.get(CapacitySlot, delivery.slot_id)
            history.append({"delivery_id": delivery.id, "order_id": order.public_id if order else "-", "status": delivery.status,
                            "date": slot.delivery_date.isoformat() if slot else None, "label": slot.label if slot else None,
                            "has_before_photo": bool(delivery.before_photo), "has_arrival_photo": bool(delivery.arrival_photo)})
        result.append({"id": courier.id, "name": courier.name, "phone": courier.phone or "", "active_count": sum(x.status != "diterima" for x in deliveries),
                       "completed_count": sum(x.status == "diterima" for x in deliveries), "deliveries": history})
    return result


@app.get("/admin/users")
def admin_users(user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    rows = db.scalars(select(User).where(User.role.in_(["pelanggan", "pengantar"])).order_by(User.role, User.name)).all()
    result = []
    for account in rows:
        order_count = db.scalar(select(func.count(Order.id)).where(Order.user_id == account.id)) if account.role == "pelanggan" else 0
        delivery_count = db.scalar(select(func.count(DeliverySchedule.id)).where(DeliverySchedule.courier_id == account.id)) if account.role == "pengantar" else 0
        result.append({"id": account.id, "name": account.name, "email": account.email, "phone": account.phone or "", "role": account.role,
                       "order_count": int(order_count or 0), "delivery_count": int(delivery_count or 0), "created_at": account.created_at.isoformat()})
    return result


@app.get("/admin/messages")
def admin_messages(user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    result = []
    for complaint in db.scalars(select(Complaint).order_by(Complaint.id.desc())).all():
        customer = db.get(User, complaint.user_id); delivery = db.get(DeliverySchedule, complaint.delivery_id)
        order = db.get(Order, delivery.order_id) if delivery else None
        result.append({"id": f"complaint-{complaint.id}", "kind": "masalah_pengiriman", "message": complaint.description,
                       "customer": customer.name if customer else "Customer", "order_id": order.public_id if order else None,
                       "created_at": complaint.created_at.isoformat(), "photo_data": complaint.photo_data, "status": complaint.status})
    feedback_rows = db.scalars(select(AuditEvent).where(AuditEvent.entity_type == "feedback").order_by(AuditEvent.created_at.desc())).all()
    for item in feedback_rows:
        customer = db.get(User, item.actor_id) if item.actor_id else None
        result.append({"id": f"feedback-{item.id}", "kind": item.action, "message": item.after.get("message", ""),
                       "customer": customer.name if customer else "Customer", "order_id": None, "created_at": item.created_at.isoformat(), "photo_data": None, "status": "baru"})
    return result


@app.post("/admin/deliveries/{delivery_id}/assign")
def assign_courier(delivery_id: int, body: CourierAssignIn, user: User = Depends(require_roles("admin")), db: Session = Depends(get_db)):
    delivery, courier = db.get(DeliverySchedule, delivery_id), db.get(User, body.courier_id)
    if not delivery: raise HTTPException(404, "Pengiriman tidak ditemukan")
    if not courier or courier.role != "pengantar": raise HTTPException(422, "Akun pengantar tidak valid")
    delivery.courier_id = courier.id
    db.add(AuditEvent(actor_id=user.id, entity_type="delivery", entity_id=str(delivery.id), action="tetapkan_pengantar", after={"courier_id": courier.id}))
    db.commit(); return {"id": delivery.id, "courier_name": courier.name, "courier_phone": courier.phone}


@app.get("/courier/deliveries")
def courier_deliveries(user: User = Depends(require_roles("pengantar")), db: Session = Depends(get_db)):
    deliveries = db.scalars(select(DeliverySchedule).where(DeliverySchedule.courier_id == user.id).order_by(DeliverySchedule.id.desc())).all()
    result = []
    for delivery in deliveries:
        order, slot = db.get(Order, delivery.order_id), db.get(CapacitySlot, delivery.slot_id)
        address = db.get(Address, delivery.address_id) if delivery.address_id else (db.get(Address, order.address_id) if order and order.address_id else None)
        result.append({"id": delivery.id, "status": delivery.status, "slot_id": delivery.slot_id, "date": slot.delivery_date.isoformat() if slot else None, "label": slot.label if slot else None,
                       "order_id": order.public_id if order else "-", "recipient": address.recipient if address else "Ambil sendiri", "phone": address.phone if address else "", "address": f"{address.line}, {address.zone}" if address else "LaukSatSet",
                       "has_before_photo": bool(delivery.before_photo), "has_arrival_photo": bool(delivery.arrival_photo)})
    return result


@app.post("/courier/deliveries/{delivery_id}/{stage}")
def courier_proof(delivery_id: int, stage: Literal["before", "arrival"], body: CourierProofIn, user: User = Depends(require_roles("pengantar")), db: Session = Depends(get_db)):
    delivery = db.scalar(select(DeliverySchedule).where(DeliverySchedule.id == delivery_id, DeliverySchedule.courier_id == user.id))
    if not delivery: raise HTTPException(404, "Pengiriman tidak ditemukan")
    if stage == "before":
        if delivery.status != "siap_dikirim": raise HTTPException(409, "Pesanan belum dinyatakan siap oleh dapur")
        delivery.before_photo = body.photo_data
        delivery.status = "dalam_pengiriman"
        order = db.get(Order, delivery.order_id)
        if order and order.status == "siap_dikirim": order.status = "dalam_pengiriman"
    else:
        if not delivery.before_photo: raise HTTPException(409, "Unggah foto barang sebelum berangkat terlebih dahulu")
        delivery.arrival_photo = body.photo_data
    db.add(AuditEvent(actor_id=user.id, entity_type="delivery", entity_id=str(delivery.id), action=f"foto_{stage}"))
    db.commit(); return {"id": delivery.id, "status": delivery.status, "stage": stage}


@app.post("/me/feedback")
def customer_feedback(body: FeedbackIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    db.add(AuditEvent(actor_id=user.id, entity_type="feedback", entity_id=str(user.id), action=body.kind, after={"message": body.message}))
    db.commit(); return {"status": "feedback_tersimpan"}


@app.get("/me/pantry")
def pantry(user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    rows = db.execute(select(DeliveryItem, OrderItem, MenuVariant, DeliverySchedule, Order)
                      .join(OrderItem, OrderItem.id == DeliveryItem.order_item_id)
                      .join(MenuVariant, MenuVariant.id == OrderItem.variant_id)
                      .join(DeliverySchedule, DeliverySchedule.id == DeliveryItem.delivery_id)
                      .join(Order, Order.id == DeliverySchedule.order_id)
                      .where(Order.user_id == user.id, DeliveryItem.pantry_status != "belum_diterima")
                      .order_by(DeliveryItem.recommended_use_at)).all()
    return [{"id": item.id, "menu_id": order_item.menu_id, "name": order_item.menu_name_snapshot, "variant": order_item.variant_snapshot, "quantity": item.quantity,
             "status": item.pantry_status, "received_at": delivery.received_at.isoformat() if delivery.received_at else None,
             "recommended_use_at": item.recommended_use_at.isoformat() if item.recommended_use_at else None,
             "storage_guide": variant.storage_guide, "cook_minutes": variant.cook_minutes} for item, order_item, variant, delivery, order in rows]


@app.post("/me/pantry/{item_id}/status")
def pantry_status(item_id: int, body: StatusIn, user: User = Depends(require_roles("pelanggan")), db: Session = Depends(get_db)):
    if body.status not in {"sudah_dimasak", "bermasalah"}: raise HTTPException(422, "Status stok tidak valid")
    row = db.execute(select(DeliveryItem, DeliverySchedule, Order).join(DeliverySchedule, DeliverySchedule.id == DeliveryItem.delivery_id).join(Order, Order.id == DeliverySchedule.order_id).where(DeliveryItem.id == item_id, Order.user_id == user.id)).first()
    if not row: raise HTTPException(404, "Stok lauk tidak ditemukan")
    item, delivery, order = row
    if item.pantry_status not in {"tersedia", "bermasalah", "sudah_dimasak"}: raise HTTPException(409, "Status stok tidak dapat diubah")
    item.pantry_status = body.status
    if body.status == "bermasalah": db.add(Complaint(user_id=user.id, delivery_id=delivery.id, description=body.note or "Masalah dilaporkan dari Stok Lauk Saya"))
    db.add(AuditEvent(actor_id=user.id, entity_type="pantry_item", entity_id=str(item.id), action="ubah_status", after={"status": body.status, "note": body.note}))
    db.commit(); return {"id": item.id, "status": item.pantry_status}
