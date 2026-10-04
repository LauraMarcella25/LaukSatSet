from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP

RICE_PRICE = Decimal("5000")
SAMBAL_PRICE = Decimal("3000")
CRACKER_PRICE = Decimal("4000")


@dataclass(frozen=True)
class PriceLine:
    unit_price: Decimal
    quantity: int
    portions: int
    rice_quantity: int
    extra_portion_price: Decimal
    sambal_quantity: int = 0
    cracker_quantity: int = 0


def calculate(lines: list[PriceLine], discount_percent: Decimal, shipping_fees: list[Decimal]) -> dict[str, Decimal]:
    subtotal = sum((x.unit_price * x.quantity for x in lines), Decimal(0))
    portion_addon = sum((x.extra_portion_price * max(x.portions - 2, 0) * x.quantity for x in lines), Decimal(0))
    rice_addon = sum((RICE_PRICE * x.rice_quantity for x in lines), Decimal(0))
    sambal_addon = sum((SAMBAL_PRICE * x.sambal_quantity for x in lines), Decimal(0))
    cracker_addon = sum((CRACKER_PRICE * x.cracker_quantity for x in lines), Decimal(0))
    addon = portion_addon + rice_addon + sambal_addon + cracker_addon
    discount = ((subtotal + addon) * discount_percent / Decimal(100)).quantize(Decimal("1"), ROUND_HALF_UP)
    shipping = sum(shipping_fees, Decimal(0))
    return {"subtotal": subtotal, "addon_total": addon, "discount_total": discount, "shipping_total": shipping,
            "total": subtotal + addon - discount + shipping}
