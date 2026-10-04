package com.lauksatset.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lauksatset.app.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.security.MessageDigest

data class AppUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val user: UserDto? = null,
    val profile: ProfileDto? = null,
    val preferences: PreferenceDto = PreferenceDto(),
    val catalog: CatalogResponse? = null,
    val slots: List<SlotDto> = emptyList(),
    val addresses: List<AddressDto> = emptyList(),
    val orders: List<OrderDto> = emptyList(),
    val adminSummary: AdminSummary? = null,
    val production: List<ProductionDto> = emptyList(),
    val checkout: CheckoutResponse? = null,
    val recommendation: RecommendationDto? = null,
    val recommendations: List<RecommendationDto> = emptyList(),
    val offlineMode: Boolean = false,
    val pantry: List<PantryItemDto> = emptyList(),
    val cart: List<CheckoutItem> = emptyList(),
    val cartPackageId: Int? = null,
    val cartSlotIds: List<Int> = emptyList(),
    val hasSavedMealTemplate: Boolean = false,
    val couriers: List<CourierDto> = emptyList(),
    val courierDeliveries: List<CourierDeliveryDto> = emptyList(),
    val archivedMenus: List<MenuDto> = emptyList(),
    val adminMessages: List<AdminMessageDto> = emptyList(),
    val adminUsers: List<AdminUserDto> = emptyList(),
    val publicSettings: PublicSettingsDto = PublicSettingsDto(),
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val provider = ApiProvider()
    private val prefs = application.getSharedPreferences("lauksatset_session", 0)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { runCatching { provider.api.publicSettings() }.onSuccess { _state.value = _state.value.copy(publicSettings = it) } }
        val token = prefs.getString("token", null)
        val role = prefs.getString("role", null)
        if (token != null && role != null) {
            provider.token = token.takeUnless { it == "offline-demo" }
            val user = UserDto(prefs.getInt("user_id", 0), prefs.getString("user_name", "Customer") ?: "Customer", role)
            _state.value = _state.value.copy(user = user, offlineMode = token == "offline-demo", preferences = readPreferences(), cart = readCart(), cartPackageId = prefs.getInt("cart_package", -1).takeIf { it > 0 }, cartSlotIds = readIntList("cart_slots"), hasSavedMealTemplate = prefs.contains("meal_template"))
            loadForRole(role)
        } else {
            _state.value = _state.value.copy(preferences = readPreferences(), cart = readCart(), cartPackageId = prefs.getInt("cart_package", -1).takeIf { it > 0 }, cartSlotIds = readIntList("cart_slots"), hasSavedMealTemplate = prefs.contains("meal_template"))
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun login(email: String, password: String, done: (String) -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.login(LoginRequest(email.trim().lowercase(), password)) }
            .onSuccess { result ->
                provider.token = result.access_token
                saveSession(result)
                _state.value = _state.value.copy(loading = false, user = result.user)
                loadForRole(result.user.role)
                done(result.user.role)
            }.onFailure {
                val demoUser = DemoData.userFor(email.trim())
                val localEmail = prefs.getString("local_email", null)
                val localUser = if (localEmail == email.trim().lowercase() && prefs.getString("local_password", null) == localHash(password)) UserDto(9001, prefs.getString("local_name", "Customer") ?: "Customer", "pelanggan") else null
                val fallbackUser = localUser ?: demoUser?.takeIf { password == "demo123" }
                if (fallbackUser != null) {
                    prefs.edit().putString("token", "offline-demo").putInt("user_id", fallbackUser.id).putString("user_name", fallbackUser.name).putString("role", fallbackUser.role).apply()
                    _state.value = _state.value.copy(loading = false, user = fallbackUser, offlineMode = true, error = null)
                    loadForRole(fallbackUser.role)
                    done(fallbackUser.role)
                } else _state.value = _state.value.copy(loading = false, error = "Login gagal. Untuk mode demo gunakan akun yang tertera dan kata sandi demo123.")
            }
    }

    fun register(name: String, email: String, phone: String, password: String, done: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.register(RegisterRequest(name.trim(), email.trim().lowercase(), phone.trim(), password)) }
            .onSuccess { result ->
                provider.token = result.access_token
                saveSession(result)
                _state.value = _state.value.copy(loading = false, user = result.user, profile = ProfileDto(result.user.id, result.user.name, email.trim(), phone.trim(), result.user.role))
                loadCustomer(); done()
            }.onFailure { error ->
                if (isNetworkFailure(error)) {
                    val localUser = UserDto(9001, name.trim(), "pelanggan")
                    prefs.edit().putString("token", "offline-demo").putInt("user_id", localUser.id).putString("user_name", localUser.name).putString("role", localUser.role).putString("local_email", email.trim().lowercase()).putString("local_password", localHash(password)).putString("local_name", name.trim()).putString("local_phone", phone.trim()).apply()
                    _state.value = _state.value.copy(loading = false, user = localUser, profile = ProfileDto(localUser.id, localUser.name, email.trim(), phone.trim(), localUser.role), offlineMode = true, error = null)
                    loadCustomer(); done()
                } else _state.value = _state.value.copy(loading = false, error = friendlyError(error))
            }
    }

    fun forgotPassword(email: String, done: (String?) -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.forgotPassword(ForgotPasswordRequest(email.trim().lowercase())) }.onSuccess { _state.value = _state.value.copy(loading = false); done(it["demo_code"]) }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun resetPassword(email: String, code: String, password: String, done: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.resetPassword(ResetPasswordRequest(email.trim().lowercase(), code, password)) }.onSuccess { _state.value = _state.value.copy(loading = false); done() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun changePassword(current: String, fresh: String, done: () -> Unit) = viewModelScope.launch {
        if (_state.value.offlineMode) { _state.value = _state.value.copy(error = "Perubahan kata sandi memerlukan server aktif."); return@launch }
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.changePassword(ChangePasswordRequest(current, fresh)) }.onSuccess { _state.value = _state.value.copy(loading = false); done() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun loadProfile() = viewModelScope.launch {
        if (_state.value.offlineMode) {
            val user = _state.value.user ?: return@launch
            _state.value = _state.value.copy(profile = ProfileDto(user.id, user.name, prefs.getString("local_email", null) ?: "pelanggan@lauksatset.id", prefs.getString("local_phone", null) ?: "081234567890", user.role))
            return@launch
        }
        runCatching { provider.api.profile() }.onSuccess { _state.value = _state.value.copy(profile = it) }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun savePreferences(preferences: PreferenceDto, done: (() -> Unit)? = null) = viewModelScope.launch {
        persistPreferences(preferences)
        if (_state.value.offlineMode) { _state.value = _state.value.copy(preferences = preferences); done?.invoke(); return@launch }
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.savePreferences(preferences) }.onSuccess { _state.value = _state.value.copy(loading = false, preferences = it); done?.invoke() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    private fun loadForRole(role: String) = viewModelScope.launch {
        when (role) {
            "pelanggan" -> loadCustomer()
            "admin" -> loadAdmin()
            "dapur" -> loadKitchen()
            "pengantar" -> loadCourier()
        }
    }

    fun loadCustomer() = viewModelScope.launch {
        if (_state.value.offlineMode) {
            _state.value = _state.value.copy(loading = false, catalog = DemoData.catalog, slots = DemoData.slots, addresses = DemoData.addresses, pantry = DemoData.pantry)
            return@launch
        }
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            val catalog = async { provider.api.catalog() }
            val slots = async { provider.api.slots() }
            val addresses = async { provider.api.addresses() }
            val orders = async { provider.api.orders() }
            val pantry = async { provider.api.pantry() }
            val preferences = async { provider.api.preferences() }
            listOf(catalog.await(), slots.await(), addresses.await(), orders.await(), pantry.await(), preferences.await())
        }.onSuccess { values ->
            @Suppress("UNCHECKED_CAST")
            val serverPreferences = values[5] as PreferenceDto; persistPreferences(serverPreferences)
            _state.value = _state.value.copy(loading = false, catalog = values[0] as CatalogResponse, slots = values[1] as List<SlotDto>, addresses = values[2] as List<AddressDto>, orders = values[3] as List<OrderDto>, pantry = values[4] as List<PantryItemDto>, preferences = serverPreferences)
        }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun loadOrders() = viewModelScope.launch {
        if (_state.value.offlineMode) return@launch
        runCatching { provider.api.orders() }.onSuccess { _state.value = _state.value.copy(orders = it) }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun refreshOrder(id: String) = viewModelScope.launch {
        if (_state.value.offlineMode) return@launch
        runCatching { provider.api.order(id) }.onSuccess { fresh -> _state.value = _state.value.copy(orders = _state.value.orders.map { if (it.id == id) fresh else it }) }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun cancelOrder(id: String) = customerOrderAction { provider.api.cancelOrder(id) }
    fun retryPayment(id: String, done: (String) -> Unit) = viewModelScope.launch { _state.value = _state.value.copy(loading = true, error = null); runCatching { provider.api.retryPayment(id) }.onSuccess { _state.value = _state.value.copy(loading = false); it["payment_url"]?.let(done); loadOrders() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) } }
    fun changeOrderAddress(id: String, addressId: Int) = customerOrderAction { provider.api.changeOrderAddress(id, IdRequest(address_id = addressId)) }
    fun rescheduleDelivery(id: Int, slotId: Int) = customerOrderAction { provider.api.rescheduleDelivery(id, IdRequest(slot_id = slotId)) }
    fun receiveDelivery(id: Int) = customerOrderAction { provider.api.receiveDelivery(id) }

    private fun customerOrderAction(action: suspend () -> OrderDto) = viewModelScope.launch {
        if (_state.value.offlineMode) { _state.value = _state.value.copy(error = "Aksi ini membutuhkan koneksi ke server."); return@launch }
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { action() }.onSuccess { fresh -> _state.value = _state.value.copy(loading = false, orders = _state.value.orders.map { if (it.id == fresh.id) fresh else it }); loadCustomer() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun reportDelivery(id: Int, description: String, photoData: String?) = viewModelScope.launch {
        if (_state.value.offlineMode) { _state.value = _state.value.copy(error = "Laporan tersimpan saat aplikasi kembali online."); return@launch }
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.reportDelivery(id, ComplaintRequest(description, photoData)) }.onSuccess { _state.value = _state.value.copy(loading = false); loadOrders() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun reviewOrder(id: String, rating: Int, comment: String) = viewModelScope.launch {
        if (_state.value.offlineMode) { _state.value = _state.value.copy(error = "Ulasan membutuhkan koneksi ke server."); return@launch }
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.reviewOrder(id, ReviewRequest(rating, rating, rating, rating, comment.ifBlank { null })) }.onSuccess { _state.value = _state.value.copy(loading = false) }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun sendFeedback(kind: String, message: String, done: () -> Unit = {}) = viewModelScope.launch {
        if (_state.value.offlineMode) { done(); return@launch }
        runCatching { provider.api.feedback(FeedbackRequest(kind, message)) }.onSuccess { done() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun loadCourier() = viewModelScope.launch {
        if (_state.value.offlineMode) {
            val status = prefs.getString("demo_delivery_status", "terjadwal") ?: "terjadwal"
            val before = prefs.getBoolean("demo_courier_before", false); val arrival = prefs.getBoolean("demo_courier_arrival", false)
            _state.value = _state.value.copy(loading = false, courierDeliveries = DemoData.courierDeliveries.mapIndexed { index, item -> if (index == 0) item.copy(status = status, has_before_photo = before, has_arrival_photo = arrival) else item })
            return@launch
        }
        _state.value = _state.value.copy(loading = true)
        runCatching { provider.api.courierDeliveries() }.onSuccess { _state.value = _state.value.copy(loading = false, courierDeliveries = it) }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun uploadCourierProof(id: Int, stage: String, data: String) = viewModelScope.launch {
        if (_state.value.offlineMode) {
            if (stage == "before") prefs.edit().putBoolean("demo_courier_before", true).putString("demo_delivery_status", "dalam_pengiriman").apply()
            else prefs.edit().putBoolean("demo_courier_arrival", true).apply()
            loadCourier(); return@launch
        }
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.courierProof(id, stage, CourierProofRequest(data)) }.onSuccess { loadCourier() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun assignCourier(deliveryId: Int, courierId: Int) = viewModelScope.launch {
        runCatching { provider.api.assignCourier(deliveryId, CourierAssignRequest(courierId)) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun checkout(request: CheckoutRequest, done: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null, checkout = null)
        if (_state.value.offlineMode) {
            val catalog = _state.value.catalog ?: DemoData.catalog
            val lines = request.items.mapNotNull { item -> catalog.menus.flatMap { it.variants }.firstOrNull { it.id == item.variant_id }?.let { it to item } }
            val subtotal = lines.sumOf { (variant, item) -> variant.price * item.quantity }
            val addons = lines.sumOf { (_, item) -> item.rice_quantity * 5000 + item.sambal_quantity * 3000 + item.cracker_quantity * 4000 }
            val pack = catalog.packages.first { it.id == request.package_id }
            val discount = ((subtotal + addons) * pack.discount_percent / 100).toInt()
            val shipping = if (request.delivery_method == "diantar") _state.value.slots.filter { it.id in request.slot_ids }.sumOf { it.shipping_fee } else 0
            val total = subtotal + addons - discount + shipping
            val id = "DEMO-${System.currentTimeMillis().toString().takeLast(8)}"
            val result = CheckoutResponse(id, "menunggu_pembayaran", "pending", total, PriceBreakdown(subtotal, addons, discount, shipping, total), "", true, "Transaksi simulasi offline")
            val orderItems = lines.map { (variant, item) -> OrderItemDto(name = catalog.menus.first { menu -> menu.variants.any { it.id == variant.id } }.name, variant = variant.kind, quantity = item.quantity, portions = item.portions, rice_quantity = item.rice_quantity, meal_day = item.meal_day, meal_sequence = item.meal_sequence, sambal_quantity = item.sambal_quantity, cracker_quantity = item.cracker_quantity) }
            val order = OrderDto(id, result.status, result.payment_status, total, java.time.LocalDateTime.now().toString(), orderItems, request.slot_ids.mapIndexed { index, slotId -> DeliveryDto(id = index + 1, status = "terjadwal", slot_id = slotId, date = _state.value.slots.firstOrNull { it.id == slotId }?.date, label = _state.value.slots.firstOrNull { it.id == slotId }?.label, shipping_fee = if (request.delivery_method == "diantar") _state.value.slots.firstOrNull { it.id == slotId }?.shipping_fee ?: 0 else 0, address = _state.value.addresses.firstOrNull { it.id == (request.delivery_addresses[slotId] ?: request.address_id) }) }, null, _state.value.addresses.firstOrNull { it.id == request.address_id }, request.delivery_method)
            _state.value = _state.value.copy(loading = false, checkout = result, orders = listOf(order) + _state.value.orders)
            clearCart()
            done(); return@launch
        }
        runCatching { provider.api.checkout(request) }
            .onSuccess { _state.value = _state.value.copy(loading = false, checkout = it); clearCart(); loadOrders(); done() }
            .onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun loadAdmin() = viewModelScope.launch {
        if (_state.value.offlineMode) {
            val deliveryStatus = prefs.getString("demo_delivery_status", "terjadwal") ?: "terjadwal"
            val demoOrders = DemoData.orders.map { order -> order.copy(status = if (deliveryStatus == "dalam_pengiriman") "dalam_pengiriman" else if (deliveryStatus == "siap_dikirim") "siap_dikirim" else order.status, deliveries = order.deliveries.map { it.copy(status = deliveryStatus, courier_name = "Budi Pengantar", courier_phone = "081298765432") }) }
            _state.value = _state.value.copy(loading = false, adminSummary = DemoData.adminSummary.copy(siap_kirim = if (deliveryStatus == "siap_dikirim") 1 else 0), catalog = DemoData.catalog, slots = DemoData.slots, orders = demoOrders, couriers = DemoData.couriers, adminUsers = DemoData.adminUsers)
            return@launch
        }
        _state.value = _state.value.copy(loading = true)
        runCatching {
            val summary = async { provider.api.adminSummary() }
            val catalog = async { provider.api.catalog() }
            val slots = async { provider.api.slots() }
            val orders = async { provider.api.orders() }
            val couriers = async { provider.api.couriers() }
            val archived = async { provider.api.archivedMenus() }
            val messages = async { provider.api.adminMessages() }
            val users = async { provider.api.adminUsers() }
            listOf(summary.await(), catalog.await(), slots.await(), orders.await(), couriers.await(), archived.await(), messages.await(), users.await())
        }.onSuccess { values ->
            @Suppress("UNCHECKED_CAST")
            _state.value = _state.value.copy(loading = false, adminSummary = values[0] as AdminSummary, catalog = values[1] as CatalogResponse, slots = values[2] as List<SlotDto>, orders = values[3] as List<OrderDto>, couriers = values[4] as List<CourierDto>, archivedMenus = values[5] as List<MenuDto>, adminMessages = values[6] as List<AdminMessageDto>, adminUsers = values[7] as List<AdminUserDto>)
        }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun loadKitchen() = viewModelScope.launch {
        if (_state.value.offlineMode) {
            val status = prefs.getString("demo_batch_status", "menunggu_produksi") ?: "menunggu_produksi"
            _state.value = _state.value.copy(loading = false, production = DemoData.production.map { it.copy(status = status) }, catalog = DemoData.catalog)
            return@launch
        }
        _state.value = _state.value.copy(loading = true)
        runCatching {
            val production = async { provider.api.production() }
            val catalog = async { provider.api.catalog() }
            production.await() to catalog.await()
        }.onSuccess { (production, catalog) -> _state.value = _state.value.copy(loading = false, production = production, catalog = catalog) }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun simulatePayment(id: String, result: String) = viewModelScope.launch {
        if (_state.value.offlineMode) {
            if (result == "sukses") prefs.edit().putString("demo_batch_status", "menunggu_produksi").apply()
            _state.value = _state.value.copy(orders = _state.value.orders.map { if (it.id == id) it.copy(status = if (result == "sukses") "menunggu_produksi" else "dibatalkan", payment_status = if (result == "sukses") "settlement" else "gagal") else it })
            return@launch
        }
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.demoPayment(id, result) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun toggleMenu(menu: MenuDto, active: Boolean) = viewModelScope.launch {
        if (_state.value.offlineMode) {
            if (!active) _state.value = _state.value.copy(catalog = _state.value.catalog?.copy(menus = _state.value.catalog!!.menus.filterNot { it.id == menu.id }), archivedMenus = _state.value.archivedMenus + menu.copy(active = false))
            else _state.value = _state.value.copy(catalog = _state.value.catalog?.copy(menus = _state.value.catalog!!.menus + menu.copy(active = true)), archivedMenus = _state.value.archivedMenus.filterNot { it.id == menu.id })
            return@launch
        }
        runCatching { provider.api.updateMenu(menu.id, MenuUpdateRequest(active = active)) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun editMenu(menu: MenuDto, request: MenuFullUpdateRequest) = viewModelScope.launch {
        if (_state.value.offlineMode) { val variants = menu.variants.map { old -> request.variants.firstOrNull { it.id == old.id }?.let { updated -> old.copy(price = updated.price, instructions = updated.instructions, step_minutes = updated.step_minutes, cook_minutes = updated.step_minutes.sum(), equipment = updated.equipment, storage_guide = updated.storage_guide, doneness_guide = updated.doneness_guide) } ?: old }; _state.value = _state.value.copy(catalog = _state.value.catalog?.copy(menus = _state.value.catalog!!.menus.map { if (it.id == menu.id) it.copy(name = request.name, description = request.description, ingredients = request.ingredients, allergens = request.allergens, portion_label = request.portion_label, calories_per_portion = request.calories_per_portion, category = request.category, image_url = request.image_url ?: it.image_url, variants = variants) else it })); return@launch }
        runCatching { provider.api.replaceMenu(menu.id, request) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun addMenu(request: MenuCreateRequest) = viewModelScope.launch {
        if (_state.value.offlineMode) { val catalog = _state.value.catalog ?: return@launch; val id = (catalog.menus.maxOfOrNull { it.id } ?: 0) + 1; val menu = MenuDto(id, request.name, request.description, request.ingredients, request.allergens, request.portion_label, listOf(VariantDto(id * 10 + 1, "siap_masak", request.ready_to_cook_price, request.step_minutes.sum(), request.instructions, request.storage_guide, request.doneness_guide, request.step_minutes, request.equipment), VariantDto(id * 10 + 2, "siap_makan", request.ready_to_eat_price, request.ready_to_eat_step_minutes.sum(), request.ready_to_eat_instructions, request.storage_guide, request.doneness_guide, request.ready_to_eat_step_minutes, request.equipment)), request.calories_per_portion, request.category, request.image_url); _state.value = _state.value.copy(catalog = catalog.copy(menus = catalog.menus + menu)); return@launch }
        runCatching { provider.api.createMenu(request) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun deleteMenu(menu: MenuDto) = viewModelScope.launch {
        if (_state.value.offlineMode) { _state.value = _state.value.copy(catalog = _state.value.catalog?.copy(menus = _state.value.catalog!!.menus.filterNot { it.id == menu.id }), archivedMenus = _state.value.archivedMenus + menu.copy(active = false)); return@launch }
        runCatching { provider.api.deleteMenu(menu.id) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun addSlot(request: SlotCreateRequest) = viewModelScope.launch {
        if (_state.value.offlineMode) { val id = (_state.value.slots.maxOfOrNull { it.id } ?: 0) + 1; _state.value = _state.value.copy(slots = _state.value.slots + SlotDto(id, request.date, request.label, request.capacity, 0, request.capacity, request.shipping_fee, "reguler", "${request.date}T00:00:00", true)); return@launch }
        runCatching { provider.api.createSlot(request) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun editSlot(slot: SlotDto, capacity: Int, fee: Int) = viewModelScope.launch {
        if (capacity < slot.reserved) { _state.value = _state.value.copy(error = "Kapasitas tidak boleh lebih kecil dari ${slot.reserved} pack yang sudah dipesan."); return@launch }
        if (_state.value.offlineMode) { _state.value = _state.value.copy(slots = _state.value.slots.map { if (it.id == slot.id) it.copy(capacity = capacity, remaining = capacity - it.reserved, shipping_fee = fee) else it }); return@launch }
        runCatching { provider.api.updateSlot(slot.id, SlotUpdateRequest(capacity = capacity, shipping_fee = fee)) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun deleteSlot(slot: SlotDto) = viewModelScope.launch {
        if (_state.value.offlineMode) { _state.value = _state.value.copy(slots = _state.value.slots.filterNot { it.id == slot.id }); return@launch }
        runCatching { provider.api.deleteSlot(slot.id) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun savePackage(existing: PackageDto?, request: PackageManageRequest) = viewModelScope.launch {
        if (_state.value.offlineMode) {
            val catalog = _state.value.catalog ?: return@launch
            val updated = PackageDto(existing?.id ?: ((catalog.packages.maxOfOrNull { it.id } ?: 0) + 1), request.name, request.min_packs, request.max_packs, request.base_packs, request.discount_percent, request.duration_days)
            _state.value = _state.value.copy(catalog = catalog.copy(packages = if (existing == null) catalog.packages + updated else catalog.packages.map { if (it.id == existing.id) updated else it }))
            return@launch
        }
        runCatching { if (existing == null) provider.api.createPackage(request) else provider.api.updatePackage(existing.id, request) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun deletePackage(pack: PackageDto) = viewModelScope.launch {
        if (_state.value.offlineMode) { _state.value = _state.value.copy(catalog = _state.value.catalog?.copy(packages = _state.value.catalog!!.packages.filterNot { it.id == pack.id })); return@launch }
        runCatching { provider.api.deletePackage(pack.id) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun adjustSlot(slot: SlotDto, delta: Int) = viewModelScope.launch {
        val requested = (slot.capacity + delta).coerceAtLeast(slot.reserved)
        if (_state.value.offlineMode) {
            _state.value = _state.value.copy(slots = _state.value.slots.map { if (it.id == slot.id) it.copy(capacity = requested, remaining = requested - it.reserved) else it })
            return@launch
        }
        runCatching { provider.api.updateSlot(slot.id, SlotUpdateRequest(capacity = requested)) }.onSuccess { loadAdmin() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun updateBatch(id: Int, status: String) = viewModelScope.launch {
        if (_state.value.offlineMode) {
            val editor = prefs.edit().putString("demo_batch_status", status)
            if (status == "siap_dikirim") editor.putString("demo_delivery_status", "siap_dikirim")
            editor.apply()
            _state.value = _state.value.copy(production = _state.value.production.map { if (it.id == id) it.copy(status = status) else it })
            return@launch
        }
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { provider.api.updateBatch(id, StatusRequest(status)) }.onSuccess { loadKitchen() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendlyError(it)) }
    }

    fun logout(done: () -> Unit) { provider.token = null; prefs.edit().remove("token").remove("user_id").remove("user_name").remove("role").apply(); _state.value = AppUiState(cart = readCart(), cartPackageId = prefs.getInt("cart_package", -1).takeIf { it > 0 }, cartSlotIds = readIntList("cart_slots"), hasSavedMealTemplate = prefs.contains("meal_template")); done() }

    fun recommend(request: RecommendationRequest) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null, recommendation = null)
        if (_state.value.offlineMode) {
            val options = DemoData.recommendations(request, _state.value.preferences)
            _state.value = _state.value.copy(loading = false, recommendations = options, recommendation = options.firstOrNull())
            return@launch
        }
        runCatching { provider.api.recommendationOptions(request) }
            .onSuccess { _state.value = _state.value.copy(loading = false, recommendations = it.recommendations, recommendation = it.recommendations.firstOrNull()) }
            .onFailure { val fallback = DemoData.recommendations(request, _state.value.preferences); _state.value = _state.value.copy(loading = false, recommendations = fallback, recommendation = fallback.firstOrNull(), error = if (fallback.isEmpty()) "Tidak ada menu yang aman untuk alergi dan pantangan yang tersimpan." else null) }
    }

    fun updatePantry(id: Int, status: String, note: String? = null) = viewModelScope.launch {
        if (_state.value.offlineMode) {
            _state.value = _state.value.copy(pantry = _state.value.pantry.map { if (it.id == id) it.copy(status = status) else it })
            return@launch
        }
        runCatching { provider.api.updatePantry(id, StatusRequest(status, note)) }
            .onSuccess { _state.value = _state.value.copy(pantry = provider.api.pantry()) }
            .onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun saveAddress(existing: AddressDto?, request: AddressRequest, done: (() -> Unit)? = null) = viewModelScope.launch {
        if (_state.value.offlineMode) { val distance = haversineKm(request.latitude, request.longitude, _state.value.publicSettings.kitchen_latitude, _state.value.publicSettings.kitchen_longitude); if (distance > _state.value.publicSettings.max_delivery_km) { _state.value = _state.value.copy(error = "Alamat berjarak ${"%.1f".format(distance)} km, di luar jangkauan."); return@launch }; val address = AddressDto(existing?.id ?: ((_state.value.addresses.maxOfOrNull { it.id } ?: 0) + 1), request.label, request.recipient, request.phone, request.line, request.zone, request.street, request.district, request.city, request.province, request.postal_code, request.latitude, request.longitude, distance); _state.value = _state.value.copy(addresses = if (existing == null) _state.value.addresses + address else _state.value.addresses.map { if (it.id == existing.id) address else it }); done?.invoke(); return@launch }
        runCatching { if (existing == null) provider.api.createAddress(request) else provider.api.updateAddress(existing.id, request) }.onSuccess { _state.value = _state.value.copy(addresses = provider.api.addresses()); done?.invoke() }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun deleteAddress(address: AddressDto) = viewModelScope.launch {
        if (_state.value.offlineMode) { _state.value = _state.value.copy(addresses = _state.value.addresses.filterNot { it.id == address.id }); return@launch }
        runCatching { provider.api.deleteAddress(address.id) }.onSuccess { _state.value = _state.value.copy(addresses = provider.api.addresses()) }.onFailure { _state.value = _state.value.copy(error = friendlyError(it)) }
    }

    fun setCartPackage(packageId: Int) {
        val duration = _state.value.catalog?.packages?.firstOrNull { it.id == packageId }?.duration_days ?: 1
        val cart = _state.value.cart.filter { it.meal_day in 1..duration }
        _state.value = _state.value.copy(cartPackageId = packageId, cart = cart)
        persistCart()
    }
    fun addCartItem(item: CheckoutItem) { _state.value = _state.value.copy(cart = _state.value.cart + item); persistCart() }
    fun updateCartItem(index: Int, item: CheckoutItem) { _state.value = _state.value.copy(cart = _state.value.cart.mapIndexed { i, current -> if (i == index) item else current }); persistCart() }
    fun replaceCart(items: List<CheckoutItem>) { _state.value = _state.value.copy(cart = items); persistCart() }
    fun removeCartItem(index: Int) { _state.value = _state.value.copy(cart = _state.value.cart.filterIndexed { i, _ -> i != index }); persistCart() }
    fun setCartSlots(ids: List<Int>) { val clean = ids.distinct(); _state.value = _state.value.copy(cartSlotIds = clean, cart = _state.value.cart.map { if (it.slot_id != null && it.slot_id !in clean) it.copy(slot_id = clean.firstOrNull()) else it }); persistCart() }
    fun replaceCartSlot(oldId: Int?, newId: Int) {
        val slots = if (oldId == null) (_state.value.cartSlotIds + newId).distinct() else _state.value.cartSlotIds.map { if (it == oldId) newId else it }.distinct()
        _state.value = _state.value.copy(cartSlotIds = slots, cart = _state.value.cart.map { item -> if (oldId == null || item.slot_id == oldId) item.copy(slot_id = newId) else item })
        persistCart()
    }
    fun clearCart() { _state.value = _state.value.copy(cart = emptyList(), cartPackageId = null, cartSlotIds = emptyList()); persistCart() }
    fun distributeCartAcrossSlots() {
        val slots = _state.value.cartSlotIds
        if (slots.isEmpty()) return
        val daySlots = _state.value.cart.map { it.meal_day }.distinct().sorted().mapIndexed { index, day -> day to slots[index % slots.size] }.toMap()
        _state.value = _state.value.copy(cart = _state.value.cart.map { it.copy(slot_id = daySlots[it.meal_day]) })
        persistCart()
    }
    fun assignCartDayToSlot(day: Int, slotId: Int) { _state.value = _state.value.copy(cart = _state.value.cart.map { if (it.meal_day == day) it.copy(slot_id = slotId) else it }); persistCart() }
    fun setCartPortions(portions: Int) { _state.value = _state.value.copy(cart = _state.value.cart.map { it.copy(portions = portions.coerceIn(1, 10)) }); persistCart() }
    fun setCartMealsPerDay(meals: Int) { _state.value = _state.value.copy(cart = _state.value.cart.filter { it.meal_sequence <= meals.coerceIn(1, 3) }); persistCart() }
    fun applyChoiceToAll(template: CheckoutItem, days: Int, mealsPerDay: Int, portions: Int) {
        val slot = _state.value.cartSlotIds.firstOrNull() ?: template.slot_id
        replaceCart((1..days).flatMap { day -> (1..mealsPerDay).map { meal -> template.copy(quantity = 1, portions = portions.coerceIn(1, 10), slot_id = slot, meal_day = day, meal_sequence = meal) } })
    }
    fun copyDayOneTemplate(mealsPerDay: Int) {
        val rows = _state.value.cart.filter { it.meal_day == 1 }.sortedBy { it.meal_sequence }
        if (rows.size != mealsPerDay) { _state.value = _state.value.copy(error = "Lengkapi semua pilihan Hari 1 sebelum menyalin susunan."); return }
        val array = JSONArray()
        rows.forEach { item -> array.put(JSONObject().put("variant", item.variant_id).put("quantity", 1).put("portions", item.portions).put("spicy", item.spicy_level).put("rice", item.rice_quantity).put("sambal", item.sambal_quantity).put("cracker", item.cracker_quantity).put("note", item.note).put("meal_sequence", item.meal_sequence)) }
        prefs.edit().putString("meal_template", array.toString()).apply()
        _state.value = _state.value.copy(hasSavedMealTemplate = true)
    }
    fun pasteSavedMealTemplate(days: Int, mealsPerDay: Int, portions: Int) {
        val raw = prefs.getString("meal_template", null) ?: run { _state.value = _state.value.copy(error = "Belum ada susunan menu yang disalin."); return }
        val saved = runCatching { val array = JSONArray(raw); List(array.length()) { index -> val row = array.getJSONObject(index); CheckoutItem(row.getInt("variant"), 1, portions.coerceIn(1, 10), row.optString("spicy", "sedang"), row.optInt("rice"), row.optString("note").takeIf { it.isNotBlank() && it != "null" }, _state.value.cartSlotIds.firstOrNull(), 1, row.optInt("meal_sequence", index + 1), row.optInt("sambal"), row.optInt("cracker")) } }.getOrElse { emptyList() }
        val activeVariants = _state.value.catalog?.menus?.flatMap { it.variants }?.map { it.id }?.toSet().orEmpty()
        if (saved.isEmpty() || saved.any { it.variant_id !in activeVariants }) { _state.value = _state.value.copy(error = "Susunan lama tidak dapat dipakai karena salah satu menu sudah tidak tersedia."); return }
        val byMeal = saved.associateBy { it.meal_sequence }
        val pasted = (1..days).flatMap { day -> (1..mealsPerDay).mapNotNull { meal -> byMeal[meal]?.copy(portions = portions.coerceIn(1, 10), meal_day = day, meal_sequence = meal) } }
        if (pasted.size != days * mealsPerDay) { _state.value = _state.value.copy(error = "Susunan tersimpan memiliki frekuensi makan yang berbeda. Salin ulang Hari 1 untuk frekuensi ini."); return }
        replaceCart(pasted)
    }
    fun addMenuDirect(menu: MenuDto, variant: VariantDto, portions: Int = 2) {
        val catalog = _state.value.catalog ?: return
        var pack = catalog.packages.firstOrNull { it.id == _state.value.cartPackageId } ?: catalog.packages.firstOrNull() ?: return
        if (_state.value.cartPackageId != pack.id) setCartPackage(pack.id)
        val meals = _state.value.preferences.meals_per_day.coerceIn(1, 3)
        var positions = (1..pack.duration_days).flatMap { day -> (1..meals).map { meal -> day to meal } }
        var next = positions.firstOrNull { position -> _state.value.cart.none { it.meal_day == position.first && it.meal_sequence == position.second } }
        if (next == null) {
            pack = catalog.packages.minByOrNull { it.duration_days } ?: return
            clearCart(); setCartPackage(pack.id); positions = listOf(1 to 1); next = positions.first()
        }
        val slot = _state.value.cartSlotIds.firstOrNull() ?: _state.value.slots.firstOrNull { it.available }?.id
        if (_state.value.cartSlotIds.isEmpty() && slot != null) setCartSlots(listOf(slot))
        addCartItem(CheckoutItem(variant.id, 1, portions.coerceIn(1, 10), "sedang", 0, null, slot, next.first, next.second))
    }

    fun buyMenuNow(menu: MenuDto, variant: VariantDto, portions: Int = 1) {
        val single = _state.value.catalog?.packages?.minByOrNull { it.base_packs } ?: return
        clearCart(); setCartPackage(single.id)
        val slot = _state.value.slots.firstOrNull { it.available }?.id
        if (slot != null) setCartSlots(listOf(slot))
        replaceCart(listOf(CheckoutItem(variant.id, 1, portions.coerceIn(1, 10), "sedang", 0, null, slot, 1, 1)))
    }

    private fun saveSession(result: LoginResponse) { prefs.edit().putString("token", result.access_token).putInt("user_id", result.user.id).putString("user_name", result.user.name).putString("role", result.user.role).apply() }
    private fun persistCart() { val array = JSONArray(); _state.value.cart.forEach { item -> array.put(JSONObject().put("variant", item.variant_id).put("quantity", item.quantity).put("portions", item.portions).put("spicy", item.spicy_level).put("rice", item.rice_quantity).put("sambal", item.sambal_quantity).put("cracker", item.cracker_quantity).put("note", item.note).put("slot", item.slot_id).put("meal_day", item.meal_day).put("meal_sequence", item.meal_sequence)) }; prefs.edit().putString("cart", array.toString()).putInt("cart_package", _state.value.cartPackageId ?: -1).putString("cart_slots", JSONArray(_state.value.cartSlotIds).toString()).apply() }
    private fun readCart(): List<CheckoutItem> = runCatching { val array = JSONArray(prefs.getString("cart", "[]")); List(array.length()) { index -> val row = array.getJSONObject(index); CheckoutItem(row.getInt("variant"), row.getInt("quantity"), row.getInt("portions"), row.getString("spicy"), row.getInt("rice"), row.optString("note").takeIf { it.isNotBlank() && it != "null" }, row.optInt("slot").takeIf { it > 0 }, row.optInt("meal_day", 1), row.optInt("meal_sequence", index + 1), row.optInt("sambal"), row.optInt("cracker")) } }.getOrDefault(emptyList())
    private fun readIntList(key: String): List<Int> = runCatching { val array = JSONArray(prefs.getString(key, "[]")); List(array.length()) { array.getInt(it) } }.getOrDefault(emptyList())
    private fun persistPreferences(value: PreferenceDto) { prefs.edit().putString("food_preferences", JSONObject().put("allergens", JSONArray(value.allergens)).put("disliked", JSONArray(value.disliked_ingredients)).put("calories", value.calorie_target).put("activity", value.activity).put("goal", value.fitness_goal).put("meals", value.meals_per_day).toString()).apply() }
    private fun readPreferences(): PreferenceDto = runCatching { val row = JSONObject(prefs.getString("food_preferences", "{}") ?: "{}"); fun strings(key: String): List<String> { val array = row.optJSONArray(key) ?: JSONArray(); return List(array.length()) { array.optString(it) } }; PreferenceDto(strings("allergens"), strings("disliked"), row.optInt("calories").takeIf { it > 0 }, row.optString("activity", "normal"), row.optString("goal", "menjaga_berat"), row.optInt("meals", 3).coerceIn(1, 8)) }.getOrDefault(PreferenceDto())

    private fun localHash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double { val earth = 6371.0; val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lon2 - lon1); val a = kotlin.math.sin(dp/2)*kotlin.math.sin(dp/2) + kotlin.math.cos(p1)*kotlin.math.cos(p2)*kotlin.math.sin(dl/2)*kotlin.math.sin(dl/2); return earth * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1-a)) }
    private fun isNetworkFailure(error: Throwable): Boolean = error.message?.contains("Unable to resolve host") == true || error.message?.contains("Failed to connect") == true || error.message?.contains("timeout", true) == true

    private fun friendlyError(error: Throwable): String = when {
        error is HttpException && error.code() == 401 -> "Sesi sudah kedaluwarsa. Silakan masuk kembali."
        error is HttpException && error.code() == 409 -> apiDetail(error) ?: "Data tersebut sudah digunakan."
        error is HttpException && error.code() == 422 -> apiDetail(error) ?: "Ada data yang belum benar. Periksa kolom yang ditandai."
        error.message?.contains("Unable to resolve host") == true || error.message?.contains("Failed to connect") == true -> "Tidak dapat terhubung ke server. Pastikan backend berjalan di port 8000."
        else -> "Permintaan belum berhasil. Periksa data dan coba lagi. (${error.message ?: "kesalahan tidak diketahui"})"
    }

    private fun apiDetail(error: HttpException): String? = runCatching {
        val detail = JSONObject(error.response()?.errorBody()?.string().orEmpty()).opt("detail")
        when (detail) { is String -> detail; is JSONArray -> (0 until detail.length()).joinToString(" · ") { index -> detail.optJSONObject(index)?.optString("msg")?.removePrefix("Value error, ").orEmpty() }; else -> null }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
