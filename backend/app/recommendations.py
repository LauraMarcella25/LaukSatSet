import json
import os
import httpx

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-flash-latest")


def rule_recommend(menus: list[dict], budget: int | None, max_minutes: int | None, preference: str, question: str, people: int = 1, target_calories: int | None = None) -> dict:
    words = f"{preference} {question}".lower()
    scored = []
    for menu in menus:
        variants = menu.get("variants", [])
        affordable = [v for v in variants if budget is None or v["price"] <= budget]
        candidates = affordable or variants
        fastest = min(candidates, key=lambda x: x["cook_minutes"], default=None)
        score = 0
        reasons = []
        if fastest and max_minutes is not None and fastest["cook_minutes"] <= max_minutes:
            score += 3; reasons.append(f"siap dalam sekitar {fastest['cook_minutes']} menit")
        if fastest and budget is not None and fastest["price"] <= budget:
            score += 3; reasons.append("sesuai anggaran")
        haystack = " ".join([menu["name"], menu["description"], *menu.get("ingredients", [])]).lower()
        for token in ["ayam", "ikan", "tahu", "jamur", "pedas", "gurih", "manis", "ringan"]:
            if token in words and token in haystack:
                score += 4; reasons.append(f"cocok dengan preferensi {token}")
        if "tidak pedas" in words and "cabai" not in haystack:
            score += 2; reasons.append("lebih mudah dipilih tanpa rasa pedas")
        calories = menu.get("calories_per_portion")
        if calories and target_calories and abs(calories - target_calories) <= max(100, int(target_calories * 0.25)):
            score += 3; reasons.append(f"sekitar {calories} kkal per porsi, dekat target makanmu")
        if calories and ("gym" in words or "massa otot" in words) and any(x in haystack for x in ["ayam", "ikan", "tahu"]):
            score += 2; reasons.append("memiliki sumber protein untuk mendukung pola makan aktif")
        variety = sum(ord(c) for c in words) % max(len(menus), 1)
        menu_index = next((i for i, candidate in enumerate(menus) if candidate["id"] == menu["id"]), 0)
        tie_distance = (menu_index - variety) % max(len(menus), 1)
        scored.append((score, tie_distance, menu, fastest, reasons))
    scored.sort(key=lambda item: (-item[0], item[1], item[3]["price"] if item[3] else 10**9))
    _, _, menu, variant, reasons = scored[0]
    reasons.append(f"untuk {people} orang")
    return {"menu_id": menu["id"], "menu_name": menu["name"], "variant_id": variant["id"] if variant else None,
            "variant": variant["kind"] if variant else None, "reason": "; ".join(reasons) if reasons else "pilihan yang tersedia dengan harga paling ringan", "source": "aturan"}


async def gemini_recommend(menus: list[dict], fallback: dict, budget: int | None, max_minutes: int | None, preference: str, question: str, people: int = 1, target_calories: int | None = None) -> dict:
    if not GEMINI_API_KEY:
        return fallback
    allowed = [{"id": m["id"], "nama": m["name"], "deskripsi": m["description"], "bahan": m.get("ingredients", []), "kalori_per_porsi": m.get("calories_per_portion"),
                "varian": [{"id": v["id"], "jenis": v["kind"], "harga": v["price"], "menit": v["cook_minutes"]} for v in m.get("variants", [])]} for m in menus]
    prompt = (
        "Pilih tepat satu menu LaukSatSet dari daftar yang tersedia. Jangan membuat menu, harga, bahan, atau klaim alergi baru. "
        "Jelaskan alasan singkat dalam bahasa Indonesia. Informasi alergi tetap harus diverifikasi pengguna.\n"
        f"Anggaran: {budget or 'tidak ditentukan'}; waktu maksimal: {max_minutes or 'tidak ditentukan'} menit; "
        f"jumlah orang: {people}; target kalori per makan: {target_calories or 'tidak ditentukan'}; preferensi: {preference}; pertanyaan: {question}; menu: {json.dumps(allowed, ensure_ascii=False)}"
    )
    schema = {"type": "object", "properties": {"menu_id": {"type": "integer"}, "variant_id": {"type": "integer"}, "reason": {"type": "string"}}, "required": ["menu_id", "variant_id", "reason"], "additionalProperties": False}
    payload = {"contents": [{"parts": [{"text": prompt}]}], "generationConfig": {"responseMimeType": "application/json", "responseSchema": schema, "temperature": 0.2}}
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent"
    try:
        async with httpx.AsyncClient(timeout=20) as client:
            response = await client.post(url, json=payload, headers={"x-goog-api-key": GEMINI_API_KEY})
            response.raise_for_status()
        raw = response.json()["candidates"][0]["content"]["parts"][0]["text"]
        choice = json.loads(raw)
        menu = next((m for m in menus if m["id"] == choice["menu_id"]), None)
        variant = next((v for m in menus for v in m.get("variants", []) if v["id"] == choice["variant_id"] and m["id"] == choice["menu_id"]), None)
        if not menu or not variant:
            return fallback
        return {"menu_id": menu["id"], "menu_name": menu["name"], "variant_id": variant["id"], "variant": variant["kind"], "reason": choice["reason"], "source": "gemini"}
    except (httpx.HTTPError, KeyError, ValueError, TypeError, json.JSONDecodeError):
        return fallback
