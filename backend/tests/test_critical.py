import hashlib
from decimal import Decimal
from app.midtrans import normalized_payment_status
from app.pricing import PriceLine, calculate
from app.recommendations import rule_recommend
from app.main import AddressIn, CheckoutIn, ItemIn, PackageIn, delivery_distance, distance_surcharge, menu_conflicts_with_preferences
from pydantic import ValidationError
import pytest
from datetime import date, datetime, timedelta
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool
from app.database import Base
from app.main import CourierProofIn, StatusIn, courier_proof, update_batch
from fastapi import HTTPException
from app.models import CapacitySlot, DeliveryItem, DeliverySchedule, Menu, MenuVariant, Order, OrderItem, ProductionBatch, User


def test_server_pricing_includes_addons_discount_and_each_delivery():
    result = calculate([PriceLine(Decimal("40000"), 3, 3, 2, Decimal("12000"), sambal_quantity=1, cracker_quantity=2)], Decimal("5"), [Decimal("10000"), Decimal("10000")])
    assert result == {"subtotal": Decimal("120000"), "addon_total": Decimal("57000"), "discount_total": Decimal("8850"), "shipping_total": Decimal("20000"), "total": Decimal("188150")}


def test_four_week_three_meals_accepts_all_84_positions():
    items = [ItemIn(variant_id=1, quantity=1, portions=1, meal_day=day, meal_sequence=meal) for day in range(1, 29) for meal in range(1, 4)]
    request = CheckoutIn(package_id=4, address_id=1, slot_ids=[1, 2, 3, 4], items=items, meals_per_day=3)
    assert len(request.items) == 84


def test_allergy_matching_blocks_compound_ingredient_names():
    menu = {"allergens": ["susu sapi"], "ingredients": ["krim susu", "ayam"]}
    assert menu_conflicts_with_preferences(menu, ["susu"])
    assert menu_conflicts_with_preferences(menu, ["ayam fillet"])
    assert not menu_conflicts_with_preferences(menu, ["ikan"])


def test_package_discount_cannot_make_product_free():
    with pytest.raises(ValidationError):
        PackageIn(name="Gratis", min_packs=1, max_packs=2, base_packs=1, duration_days=1, discount_percent=100)


def test_same_day_cannot_use_multiple_delivery_schedules():
    with pytest.raises(ValidationError, match="hari yang sama"):
        CheckoutIn(package_id=1, address_id=1, slot_ids=[1, 2], meals_per_day=2, items=[
            ItemIn(variant_id=1, quantity=1, portions=2, meal_day=1, meal_sequence=1, slot_id=1),
            ItemIn(variant_id=2, quantity=1, portions=2, meal_day=1, meal_sequence=2, slot_id=2),
        ])


def test_payment_status_is_conservative():
    assert normalized_payment_status("pending") == "pending"
    assert normalized_payment_status("capture", "challenge") == "pending"
    assert normalized_payment_status("capture", "accept") == "settlement"
    assert normalized_payment_status("expire") == "kedaluwarsa"


def test_signature_formula_matches_midtrans_contract(monkeypatch):
    import app.midtrans as midtrans
    monkeypatch.setattr(midtrans, "SERVER_KEY", "server-key")
    raw = "ORDER-120000.00server-key"
    signature = hashlib.sha512(raw.encode()).hexdigest()
    assert midtrans.verify_signature("ORDER-1", "200", "00.00", signature)


def test_rule_recommendation_uses_budget_and_time():
    menus = [{"id": 1, "name": "Ayam", "description": "gurih", "ingredients": ["ayam"], "variants": [{"id": 11, "kind": "siap_makan", "price": 45000, "cook_minutes": 5}]},
             {"id": 2, "name": "Dori", "description": "ikan", "ingredients": ["ikan"], "variants": [{"id": 21, "kind": "siap_masak", "price": 60000, "cook_minutes": 20}]}]
    result = rule_recommend(menus, 50000, 10, "ayam gurih", "makan cepat", 2)
    assert result["menu_id"] == 1
    assert result["source"] == "aturan"


def test_address_validation_and_distance_fee():
    address = AddressIn(label="Rumah", recipient="Rani Putri", phone="081234567890", line="Jl. Melati 12", zone="Jakarta Selatan", street="Jl. Melati 12", district="Kebayoran Baru", city="Jakarta Selatan", province="DKI Jakarta", postal_code="12120", latitude=-6.2445, longitude=106.8006)
    assert delivery_distance(address.latitude, address.longitude) == Decimal("0.0")
    assert distance_surcharge(Decimal("7.1")) == Decimal("6000")


def test_address_rejects_invalid_name_and_zone():
    with pytest.raises(ValidationError):
        AddressIn(label="Rumah", recipient="Rani2", phone="081234567890", line="Jl. Melati 12", zone="Depok", street="Jl. Melati 12", district="Kebayoran Baru", city="Jakarta Selatan", province="DKI Jakarta", postal_code="12A20", latitude=-6.2445, longitude=106.8006)


def test_kitchen_ready_releases_only_matching_delivery():
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    with Session(engine) as db:
        kitchen = User(email="dapur@test.id", name="Dapur", password_hash="x", role="dapur")
        customer = User(email="customer@test.id", name="Customer", password_hash="x", role="pelanggan")
        menu_a = Menu(name="Ayam Kecap", description="A")
        menu_b = Menu(name="Ikan Bakar", description="B")
        db.add_all([kitchen, customer, menu_a, menu_b]); db.flush()
        variant_a = MenuVariant(menu_id=menu_a.id, kind="siap_masak", price=40000)
        variant_b = MenuVariant(menu_id=menu_b.id, kind="siap_masak", price=45000)
        db.add_all([variant_a, variant_b]); db.flush()
        delivery_date = date.today() + timedelta(days=2)
        slot = CapacitySlot(delivery_date=delivery_date, label="09.00–12.00", capacity=20, reserved=2, cutoff_at=datetime.utcnow(), shipping_fee=12000)
        db.add(slot); db.flush()
        deliveries = []
        batches = []
        for index, (menu, variant) in enumerate(((menu_a, variant_a), (menu_b, variant_b)), start=1):
            order = Order(public_id=f"ORD-{index}", user_id=customer.id, package_id=1, status="diproduksi", subtotal=40000, total=52000)
            db.add(order); db.flush()
            item = OrderItem(order_id=order.id, menu_id=menu.id, variant_id=variant.id, menu_name_snapshot=menu.name, variant_snapshot=variant.kind, unit_price_snapshot=variant.price, quantity=1, portions=1, spicy_level="sedang")
            delivery = DeliverySchedule(order_id=order.id, slot_id=slot.id, status="terjadwal", shipping_fee_snapshot=12000, courier_id=kitchen.id)
            db.add_all([item, delivery]); db.flush()
            db.add(DeliveryItem(delivery_id=delivery.id, order_item_id=item.id, quantity=1))
            batch = ProductionBatch(delivery_id=delivery.id, production_date=delivery_date - timedelta(days=1), menu_id=menu.id, variant_id=variant.id, quantity=1, spicy_level="sedang", status="lolos_qc")
            db.add(batch); deliveries.append(delivery); batches.append(batch)
        db.commit()

        update_batch(batches[0].id, StatusIn(status="siap_dikirim"), kitchen, db)
        db.refresh(deliveries[0]); db.refresh(deliveries[1])
        assert deliveries[0].status == "siap_dikirim"
        assert deliveries[1].status == "terjadwal"
        with pytest.raises(HTTPException, match="belum dinyatakan siap"):
            courier_proof(deliveries[1].id, "before", CourierProofIn(photo_data="foto-bukti-pengiriman"), kitchen, db)
        released = courier_proof(deliveries[0].id, "before", CourierProofIn(photo_data="foto-bukti-pengiriman"), kitchen, db)
        assert released["status"] == "dalam_pengiriman"
