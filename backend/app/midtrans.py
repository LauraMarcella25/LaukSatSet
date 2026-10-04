import base64
import hashlib
import hmac
import os
from datetime import datetime, timedelta

SERVER_KEY = os.getenv("MIDTRANS_SERVER_KEY", "")
IS_PRODUCTION = os.getenv("MIDTRANS_IS_PRODUCTION", "false").lower() == "true"
SNAP_URL = "https://app.midtrans.com/snap/v1/transactions" if IS_PRODUCTION else "https://app.sandbox.midtrans.com/snap/v1/transactions"


async def create_snap_transaction(order_id: str, gross_amount: int, customer: dict, items: list[dict]) -> dict:
    if not SERVER_KEY:
        return {
            "token": f"demo-{order_id}",
            "redirect_url": f"https://app.sandbox.midtrans.com/snap/v4/redirection/demo-{order_id}",
            "expires_at": datetime.utcnow() + timedelta(hours=24),
            "demo": True,
        }
    import httpx
    auth = base64.b64encode(f"{SERVER_KEY}:".encode()).decode()
    payload = {
        "transaction_details": {"order_id": order_id, "gross_amount": gross_amount},
        "customer_details": customer,
        "item_details": items,
        "credit_card": {"secure": True},
    }
    async with httpx.AsyncClient(timeout=20) as client:
        response = await client.post(SNAP_URL, json=payload, headers={"Authorization": f"Basic {auth}", "Accept": "application/json"})
        response.raise_for_status()
        data = response.json()
    return {**data, "expires_at": datetime.utcnow() + timedelta(hours=24), "demo": False}


def verify_signature(order_id: str, status_code: str, gross_amount: str, signature_key: str) -> bool:
    if not SERVER_KEY:
        return False
    expected = hashlib.sha512(f"{order_id}{status_code}{gross_amount}{SERVER_KEY}".encode()).hexdigest()
    return hmac.compare_digest(expected, signature_key)


def normalized_payment_status(transaction_status: str, fraud_status: str | None = None) -> str:
    if transaction_status == "capture":
        return "settlement" if fraud_status == "accept" else "pending"
    if transaction_status == "settlement":
        return "settlement"
    if transaction_status == "expire":
        return "kedaluwarsa"
    if transaction_status in {"deny", "cancel", "failure"}:
        return "gagal"
    return "pending"
