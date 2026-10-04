package com.lauksatset.app.data

import java.time.LocalDate

object DemoData {
    val catalog = CatalogResponse(
        menus = listOf(
            MenuDto(1, "Ayam Woku", "Ayam berbumbu woku harum dengan kemangi.", listOf("ayam", "kemangi", "cabai", "serai"), emptyList(), "2 porsi", listOf(
                VariantDto(11, "siap_masak", 42000, 18, listOf("Cairkan kemasan semalaman di chiller. Iris kemangi dan cabai tambahan bila ingin lebih segar.", "Panaskan wajan dengan 1 sdm minyak selama 1 menit memakai api sedang.", "Buka kemasan, masukkan ayam beserta bumbu, lalu aduk perlahan agar bumbu tidak gosong.", "Masak 12–14 menit. Balik potongan ayam setiap 3 menit dan tambahkan 2 sdm air bila bumbu terlalu kering.", "Masukkan kemangi pada menit terakhir. Pastikan bagian tengah ayam tidak merah lalu sajikan."), "Simpan beku dan cairkan di chiller.", "Kuah mendidih dan ayam panas merata.", listOf(2, 1, 3, 12, 1), listOf("wajan", "spatula", "pisau")),
                VariantDto(12, "siap_makan", 47000, 5, listOf("Buka kemasan sesuai tanda.", "Panaskan hingga seluruh bagian panas.", "Sajikan segera."), "Simpan di chiller.", "Uap terlihat dan bagian tengah panas.")), 410),
            MenuDto(2, "Dori Sambal Matah", "Ikan dori lembut dengan sambal matah segar.", listOf("ikan dori", "bawang", "serai", "cabai"), listOf("ikan"), "2 porsi", listOf(
                VariantDto(21, "siap_masak", 46000, 15, listOf("Cairkan dori di chiller. Iris tipis bawang merah, cabai, dan serai untuk sambal matah.", "Keringkan permukaan dori, lalu panaskan wajan dengan sedikit minyak selama 1 menit.", "Masak dori 4–5 menit pada tiap sisi. Balik satu kali agar fillet tidak hancur.", "Campur irisan sambal matah dengan minyak panas dan sedikit garam.", "Letakkan sambal setelah dori matang dan sajikan segera."), "Simpan beku dan cairkan perlahan di chiller.", "Daging ikan tidak transparan dan mudah terurai.", listOf(3, 1, 9, 2, 1), listOf("pisau", "talenan", "wajan", "spatula")),
                VariantDto(22, "siap_makan", 51000, 5, listOf("Buka segel dan pisahkan sambal matah.", "Panaskan dori 4 menit sampai bagian tengah panas.", "Tambahkan sambal matah setelah lauk panas lalu sajikan."), "Simpan di chiller.", "Bagian tengah lauk panas merata.", listOf(1, 4, 1), listOf("piring", "microwave atau panci"))), 360),
            MenuDto(3, "Semur Tahu Jamur", "Semur gurih manis berbahan tahu dan jamur.", listOf("tahu", "jamur", "kecap"), listOf("kedelai"), "2 porsi", listOf(
                VariantDto(31, "siap_masak", 34000, 12, listOf("Cairkan kemasan di chiller. Potong tahu menjadi dua bila ingin ukuran lebih kecil.", "Panaskan wajan, lalu tuang tahu, jamur, bumbu, dan 3 sdm air.", "Masak dengan api sedang selama 8 menit dan aduk perlahan setiap 2 menit.", "Kecilkan api sampai kuah mengental lalu koreksi rasa."), "Simpan beku dan jangan bekukan kembali setelah cair.", "Kuah mendidih, mengental, dan jamur lunak.", listOf(2, 1, 8, 1), listOf("pisau", "talenan", "wajan", "spatula")),
                VariantDto(32, "siap_makan", 39000, 4, listOf("Buka sedikit sudut kemasan.", "Panaskan selama 4 menit.", "Aduk perlahan dan sajikan saat seluruh bagian panas."), "Simpan di chiller.", "Seluruh bagian panas merata.", listOf(1, 4, 1), listOf("piring", "microwave atau panci"))) , 320)
        ),
        packages = listOf(PackageDto(1, "1 Hari", 1, 6, 2, 0.0, 1), PackageDto(2, "3 Hari", 3, 18, 6, 2.0, 3), PackageDto(3, "1 Minggu", 7, 42, 14, 5.0, 7), PackageDto(4, "4 Minggu", 28, 100, 56, 8.0, 28))
    )
    val slots = listOf(1, 3, 5, 8, 15, 22, 29).flatMapIndexed { index, day -> listOf(SlotDto(index * 2 + 1, LocalDate.now().plusDays(day.toLong()).toString(), "09.00–12.00", 30, 0, 30, 12000, "reguler", LocalDate.now().plusDays((day - 1).toLong()).toString() + "T16:00:00", true), SlotDto(index * 2 + 2, LocalDate.now().plusDays(day.toLong()).toString(), "14.00–17.00", 30, 0, 30, 12000, "reguler", LocalDate.now().plusDays((day - 1).toLong()).toString() + "T16:00:00", true)) }
    val addresses = listOf(AddressDto(1, "Rumah", "Rani", "081234567890", "Jl. Melati No. 12, Kec. Kebayoran Baru, Jakarta Selatan, DKI Jakarta 12120", "Jakarta Selatan", "Jl. Melati No. 12", "Kebayoran Baru", "Jakarta Selatan", "DKI Jakarta", "12120", -6.2448, 106.7991, 0.2))
    val adminSummary = AdminSummary(1, 1, 0, 0, 0, 1, true, "Mode demo offline. Omzet hanya bertambah setelah pembayaran simulasi dikonfirmasi.")
    val orders = listOf(OrderDto("LSS-DEMO-001", "menunggu_pembayaran", "pending", 141000, LocalDate.now().toString() + "T09:00:00", listOf(OrderItemDto("Ayam Woku", "siap_masak", 2, 2, 1), OrderItemDto("Semur Tahu Jamur", "siap_makan", 1, 1, 0)), listOf(DeliveryDto(1, "terjadwal", 1, LocalDate.now().plusDays(1).toString(), "09.00–12.00", listOf(DeliveryItemSummaryDto("Ayam Woku", 2), DeliveryItemSummaryDto("Semur Tahu Jamur", 1)))), null, addresses.first()))
    val production = listOf(ProductionDto(1, LocalDate.now().plusDays(1).toString(), 1, 11, 6, "sedang", 2, listOf("Pisahkan 1 pack tanpa cabai"), emptyList(), "menunggu_produksi"))
    val pantry = listOf(
        PantryItemDto(1, 1, "Ayam Woku", "siap_masak", 1, "tersedia", LocalDate.now().minusDays(1).toString(), LocalDate.now().plusDays(5).toString(), "Simpan beku dan cairkan di chiller sebelum dimasak.", 18),
        PantryItemDto(2, 3, "Semur Tahu Jamur", "siap_makan", 1, "tersedia", LocalDate.now().toString(), LocalDate.now().plusDays(3).toString(), "Simpan di chiller.", 4)
    )
    val couriers = listOf(CourierDto(4, "Budi Pengantar", "081298765432", 1, 4, listOf(CourierHistoryDto(1, "LSS-DEMO-001", "terjadwal", LocalDate.now().plusDays(1).toString(), "09.00–12.00", false, false))))
    val courierDeliveries = listOf(
        CourierDeliveryDto(1, "terjadwal", 1, LocalDate.now().plusDays(1).toString(), "09.00–12.00", "LSS-DEMO-001", "Rani", "081234567890", addresses.first().line, false, false),
        CourierDeliveryDto(2, "siap_dikirim", 3, LocalDate.now().plusDays(1).toString(), "09.00–12.00", "LSS-DEMO-014", "Dewi", "081355501201", "Jl. Wijaya II No. 8, Jakarta Selatan", false, false),
        CourierDeliveryDto(3, "siap_dikirim", 4, LocalDate.now().plusDays(1).toString(), "14.00–17.00", "LSS-DEMO-018", "Ardi", "081277703311", "Jl. Tebet Barat No. 21, Jakarta Selatan", false, false),
        CourierDeliveryDto(4, "dalam_pengiriman", 5, LocalDate.now().toString(), "14.00–17.00", "LSS-DEMO-020", "Maya", "081211190808", "Jl. Kemang Raya No. 5, Jakarta Selatan", true, false),
        CourierDeliveryDto(5, "terjadwal", 6, LocalDate.now().plusDays(2).toString(), "09.00–12.00", "LSS-DEMO-024", "Nadia", "085700112233", "Jl. Fatmawati No. 44, Jakarta Selatan", false, false),
        CourierDeliveryDto(6, "siap_dikirim", 7, LocalDate.now().plusDays(2).toString(), "14.00–17.00", "LSS-DEMO-026", "Raka", "082233445566", "Jl. Cilandak KKO No. 17, Jakarta Selatan", false, false),
        CourierDeliveryDto(7, "diterima", 8, LocalDate.now().minusDays(1).toString(), "09.00–12.00", "LSS-DEMO-008", "Sinta", "081388776655", "Jl. Panglima Polim No. 12, Jakarta Selatan", true, true),
        CourierDeliveryDto(8, "terjadwal", 9, LocalDate.now().plusDays(3).toString(), "09.00–12.00", "LSS-DEMO-031", "Fajar", "081299887766", "Jl. Radio Dalam No. 9, Jakarta Selatan", false, false)
    )
    val adminUsers = listOf(
        AdminUserDto(1, "Rani", "pelanggan@lauksatset.id", "081234567890", "pelanggan", 1, 0, LocalDate.now().minusMonths(2).toString()),
        AdminUserDto(4, "Budi Pengantar", "pengantar@lauksatset.id", "081298765432", "pengantar", 0, 5, LocalDate.now().minusMonths(3).toString())
    )

    fun userFor(email: String): UserDto? = when (email.lowercase()) {
        "pelanggan@lauksatset.id" -> UserDto(1, "Rani", "pelanggan")
        "admin@lauksatset.id" -> UserDto(2, "Admin LaukSatSet", "admin")
        "dapur@lauksatset.id" -> UserDto(3, "Tim Dapur", "dapur")
        "pengantar@lauksatset.id" -> UserDto(4, "Budi Pengantar", "pengantar")
        else -> null
    }

    private fun safeMenus(preferences: PreferenceDto): List<MenuDto> {
        val avoided = (preferences.allergens + preferences.disliked_ingredients).map { it.trim().lowercase() }.filter { it.isNotBlank() }
        return catalog.menus.filter { menu ->
            val menuTerms = (menu.allergens + menu.ingredients).map { it.trim().lowercase() }
            avoided.none { blocked -> menuTerms.any { term -> blocked in term || term in blocked } }
        }
    }

    fun recommendation(request: RecommendationRequest, preferences: PreferenceDto = PreferenceDto()): RecommendationDto? {
        val words = "${request.preference} ${request.question}".lowercase()
        val menus = safeMenus(preferences)
        val menu = when {
            "ikan" in words || "segar" in words -> menus.firstOrNull { "ikan" in it.allergens || it.ingredients.any { ingredient -> "ikan" in ingredient } }
            "vegetarian" in words || "tahu" in words || "hemat" in words -> menus.firstOrNull { it.ingredients.any { ingredient -> "tahu" in ingredient } }
            else -> null
        } ?: menus.firstOrNull() ?: return null
        val available = menu.variants.filter { (request.budget == null || it.price <= request.budget) && (request.max_minutes == null || it.cook_minutes <= request.max_minutes) }
        val variant = available.minByOrNull { it.cook_minutes } ?: menu.variants.minBy { it.price }
        return RecommendationDto(menu.id, menu.name, variant.id, variant.kind, "Cocok dengan preferensi, anggaran, waktu memasak, dan jumlah orang yang kamu berikan.", "aturan offline", "Periksa bahan dan alergen yang dikonfirmasi admin sebelum memesan.", request.people)
    }

    fun recommendations(request: RecommendationRequest, preferences: PreferenceDto = PreferenceDto()): List<RecommendationDto> {
        val first = recommendation(request, preferences) ?: return emptyList()
        val remaining = safeMenus(preferences).filterNot { it.id == first.menu_id }.map { menu ->
            val variant = menu.variants.filter { (request.budget == null || it.price <= request.budget) && (request.max_minutes == null || it.cook_minutes <= request.max_minutes) }.minByOrNull { it.cook_minutes } ?: menu.variants.minBy { it.price }
            RecommendationDto(menu.id, menu.name, variant.id, variant.kind, "Alternatif yang tetap menyesuaikan anggaran, waktu, dan kebutuhan ${request.people} orang.", "aturan offline", "Periksa bahan dan alergen sebelum memesan.", request.people)
        }
        return (listOf(first) + remaining).take(3)
    }
}
