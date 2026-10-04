from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.auth import hash_password
from app.database import Base, get_db
from app.main import app, seed
from app.models import User


def auth(client: TestClient, email: str) -> dict[str, str]:
    response = client.post("/auth/login", json={"email": email, "password": "demo123"})
    assert response.status_code == 200, response.text
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_checkout_to_pantry_cross_role_flow():
    test_engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    TestingSession = sessionmaker(bind=test_engine, expire_on_commit=False)
    Base.metadata.create_all(test_engine)
    with TestingSession() as db:
        seed(db)
        db.add(User(email="pengantar@lauksatset.id", name="Budi Pengantar", password_hash=hash_password("demo123"), role="pengantar", phone="081298765432"))
        db.commit()

    def override_db():
        with TestingSession() as db:
            yield db

    startup_handlers = list(app.router.on_startup)
    app.router.on_startup.clear()
    app.dependency_overrides[get_db] = override_db
    with TestClient(app) as client:
        customer = auth(client, "pelanggan@lauksatset.id")
        admin = auth(client, "admin@lauksatset.id")
        kitchen = auth(client, "dapur@lauksatset.id")
        courier = auth(client, "pengantar@lauksatset.id")

        catalog = client.get("/catalog").json()
        package = next(item for item in catalog["packages"] if item["duration_days"] == 1)
        variant = catalog["menus"][0]["variants"][0]
        addresses = client.get("/me/addresses", headers=customer).json()
        slots = client.get("/delivery-slots").json()
        # Gunakan tanggal yang lebih jauh agar cut-off tidak bergantung pada jam test dijalankan.
        slot = slots[min(4, len(slots) - 1)]

        checkout = client.post(
            "/checkout",
            headers=customer,
            json={
                "package_id": package["id"],
                "address_id": addresses[0]["id"],
                "slot_ids": [slot["id"]],
                "delivery_addresses": {str(slot["id"]): addresses[0]["id"]},
                "delivery_method": "diantar",
                "meals_per_day": 1,
                "items": [{
                    "variant_id": variant["id"],
                    "quantity": 1,
                    "portions": 2,
                    "spicy_level": "sedang",
                    "rice_quantity": 1,
                    "sambal_quantity": 1,
                    "cracker_quantity": 1,
                    "slot_id": slot["id"],
                    "meal_day": 1,
                    "meal_sequence": 1,
                }],
            },
        )
        assert checkout.status_code == 200, checkout.text
        order_id = checkout.json()["order_id"]

        order = client.get(f"/orders/{order_id}", headers=admin).json()
        delivery = order["deliveries"][0]
        queued_before_payment = client.get("/kitchen/production", headers=kitchen).json()
        queued_batch = next(item for item in queued_before_payment if item["delivery_id"] == delivery["id"])
        assert queued_batch["status"] == "menunggu_pembayaran"

        paid = client.post(f"/demo/payments/{order_id}/sukses", headers=admin)
        assert paid.status_code == 200, paid.text
        assert paid.json()["order_status"] == "menunggu_produksi"

        order = client.get(f"/orders/{order_id}", headers=admin).json()

        batches = client.get("/kitchen/production", headers=kitchen).json()
        batch = next(item for item in batches if item["delivery_id"] == delivery["id"])
        assert batch["status"] == "menunggu_produksi"
        assert batch["rice_quantity"] == 1
        assert batch["sambal_quantity"] == 1
        assert batch["cracker_quantity"] == 1
        admin_cannot_decide_production = client.post(f"/kitchen/production/{batch['id']}/status", headers=admin, json={"status": "diproduksi"})
        assert admin_cannot_decide_production.status_code == 403
        for status in ("diproduksi", "lolos_qc", "siap_dikirim"):
            changed = client.post(f"/kitchen/production/{batch['id']}/status", headers=kitchen, json={"status": status})
            assert changed.status_code == 200, changed.text

        courier_rows = client.get("/courier/deliveries", headers=courier).json()
        courier_delivery = next(item for item in courier_rows if item["id"] == delivery["id"])
        assert courier_delivery["status"] == "siap_dikirim"
        admin_cannot_release = client.post(f"/deliveries/{delivery['id']}/status", headers=admin, json={"status": "siap_dikirim"})
        assert admin_cannot_release.status_code == 403
        before = client.post(f"/courier/deliveries/{delivery['id']}/before", headers=courier, json={"photo_data": "b" * 40})
        assert before.status_code == 200, before.text
        arrival = client.post(f"/courier/deliveries/{delivery['id']}/arrival", headers=courier, json={"photo_data": "a" * 40})
        assert arrival.status_code == 200, arrival.text

        received = client.post(f"/me/deliveries/{delivery['id']}/receive", headers=customer)
        assert received.status_code == 200, received.text
        assert received.json()["status"] == "selesai"
        pantry = client.get("/me/pantry", headers=customer)
        assert pantry.status_code == 200, pantry.text
        assert any(item["status"] == "tersedia" and item["name"] == catalog["menus"][0]["name"] for item in pantry.json())
    app.dependency_overrides.pop(get_db, None)
    app.router.on_startup.extend(startup_handlers)
