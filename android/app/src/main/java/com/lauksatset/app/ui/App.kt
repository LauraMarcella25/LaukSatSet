package com.lauksatset.app.ui

import android.content.Intent
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Base64
import android.graphics.BitmapFactory
import java.io.IOException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.lauksatset.app.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.navDeepLink
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lauksatset.app.AppUiState
import com.lauksatset.app.AppViewModel
import com.lauksatset.app.data.*
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.Duration
import java.time.ZoneId
import com.lauksatset.app.KitchenReminderReceiver
import com.lauksatset.app.MainActivity
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.SelectableDates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

private fun rupiah(value: Int): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply { maximumFractionDigits = 0 }.format(value)
private fun label(value: String): String = value.replace('_', ' ').replaceFirstChar { it.uppercase() }
private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"))

private fun cookingStepMinutes(variant: VariantDto?): List<Int> {
    if (variant == null) return emptyList()
    val timerWords = listOf("panas", "masak", "rebus", "goreng", "kukus", "panggang", "tumis", "bakar", "microwave", "didih", "menit", "api", "diamkan", "tunggu")
    val fallback = ((variant.cook_minutes.takeIf { it > 0 } ?: 1) / variant.instructions.size.coerceAtLeast(1)).coerceAtLeast(1)
    return variant.instructions.mapIndexed { index, instruction ->
        if (timerWords.none { it in instruction.lowercase() }) 0 else variant.step_minutes.getOrNull(index)?.takeIf { it > 0 } ?: fallback
    }
}

@SuppressLint("MissingPermission")
private fun lastKnownCoordinates(context: Context): Pair<Double, Double>? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { it.latitude to it.longitude }
}

@Composable
fun LaukSatSetApp(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val startDestination = remember { when (state.user?.role) { "admin" -> "admin"; "dapur" -> "kitchen"; "pengantar" -> "courier"; "pelanggan" -> "home"; else -> "login" } }
    LaunchedEffect(state.error) { val message = state.error; if (message != null) { delay(4_000); if (state.error == message) vm.clearError() } }
    Box(Modifier.fillMaxSize().background(Cream)) {
        NavHost(navController = nav, startDestination = startDestination) {
            composable("login") { LoginScreen(state, vm, { nav.navigate("register") }) { role -> nav.navigate(if (role == "admin") "admin" else if (role == "dapur") "kitchen" else if (role == "pengantar") "courier" else "home") { popUpTo("login") { inclusive = true } } } }
            composable("register") { RegisterScreen(state, vm, nav::popBackStack) { nav.navigate("onboarding") { popUpTo("login") { inclusive = true } } } }
            composable("onboarding") { OnboardingScreen(state, nav, vm) }
            composable("preferences") { PreferencesScreen(state, nav, vm) }
            composable("home") { CustomerHome(state, nav, vm) }
            composable("profile") { ProfileScreen(state, nav, vm) }
            composable("builder") { BuilderScreen(state, nav, vm) }
            composable("builder-step2") { BuilderScreen(state, nav, vm, 2) }
            composable("cart") { BuilderScreen(state, nav, vm, 3) }
            composable("menus") { AllMenusScreen(state, nav, vm) }
            composable("orders", deepLinks = listOf(navDeepLink { uriPattern = "lauksatset://orders" })) { OrdersScreen(state, nav, vm) }
            composable("order/{orderId}") { entry -> OrderDetailScreen(state, entry.arguments?.getString("orderId").orEmpty(), nav, vm) }
            composable("payment") { PaymentScreen(state, nav, vm) }
            composable("recommend") { RecommendationScreen(state, nav, vm) }
            composable("pantry") { PantryScreen(state, nav, vm) }
            composable("cook/{menuId}") { entry -> CookScreen(state, entry.arguments?.getString("menuId")?.toIntOrNull(), nav, vm) }
            composable("pantry-cook/{menuId}") { entry -> CookScreen(state, entry.arguments?.getString("menuId")?.toIntOrNull(), nav, vm, fromPantry = true) }
            composable("admin", deepLinks = listOf(navDeepLink { uriPattern = "lauksatset://admin" })) { AdminScreen(state, nav, vm) { vm.logout { nav.navigate("login") { popUpTo("admin") { inclusive = true } } } } }
            composable("admin-product/{menuId}") { entry -> AdminProductEditorScreen(state, entry.arguments?.getString("menuId")?.toIntOrNull() ?: 0, nav, vm) }
            composable("kitchen", deepLinks = listOf(navDeepLink { uriPattern = "lauksatset://kitchen" })) { KitchenScreen(state, nav, vm) }
            composable("kitchen-profile") { KitchenProfileScreen(state, nav, vm) { vm.logout { nav.navigate("login") { popUpTo("kitchen") { inclusive = true } } } } }
            composable("courier", deepLinks = listOf(navDeepLink { uriPattern = "lauksatset://courier" })) { CourierScreen(state, nav, vm) }
            composable("courier-profile") { CourierProfileScreen(state, nav, vm) { vm.logout { nav.navigate("login") { popUpTo("courier") { inclusive = true } } } } }
        }
        state.error?.let { ErrorBanner(it, vm::clearError, Modifier.align(Alignment.TopCenter)) }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Orange)
    }
}

@Composable private fun ErrorBanner(message: String, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.padding(12.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp), shadowElevation = 4.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
            TextButton(onClick = dismiss) { Text("Tutup") }
        }
    }
}

@Composable private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("lauk", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Brown, letterSpacing = (-1).sp)
        Text("satset", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Terracotta, letterSpacing = (-1).sp)
        Text(".", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Olive)
    }
}

@Composable private fun BackButton(click: () -> Unit) { IconButton(onClick = click) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Kembali") } }

@Composable private fun LoginScreen(state: AppUiState, vm: AppViewModel, register: () -> Unit, onSuccess: (String) -> Unit) {
    var email by remember { mutableStateOf("pelanggan@lauksatset.id") }
    var password by remember { mutableStateOf("demo123") }
    var forgot by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 24.dp, vertical = 36.dp), verticalArrangement = Arrangement.Center) {
        BrandMark(); Spacer(Modifier.height(28.dp))
        Text("Lauk siap saat harimu padat.", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, color = LeafDark)
        Spacer(Modifier.height(8.dp)); Text("Atur menu, porsi, dan jadwal kirim dalam satu tempat.", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Kata sandi") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(18.dp))
        Button(onClick = { vm.login(email, password, onSuccess) }, Modifier.fillMaxWidth().height(54.dp), enabled = !state.loading && email.isNotBlank() && password.isNotBlank(), shape = RoundedCornerShape(14.dp)) { Text("Masuk", fontWeight = FontWeight.Bold) }
        TextButton(onClick = { forgot = true }, Modifier.align(Alignment.End)) { Text("Lupa kata sandi?") }
        TextButton(onClick = register, Modifier.align(Alignment.CenterHorizontally)) { Text("Belum punya akun? Daftar") }
        Spacer(Modifier.height(20.dp)); Text("Akun demo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("pelanggan@lauksatset.id · admin@lauksatset.id · dapur@lauksatset.id · pengantar@lauksatset.id", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        Text("Kata sandi: demo123 · otomatis memakai mode offline bila server tidak tersedia", color = Muted, fontSize = 13.sp)
    }
    if (forgot) ForgotPasswordDialog(email, state.loading, { forgot = false }, vm)
}

@Composable private fun ForgotPasswordDialog(initialEmail: String, loading: Boolean, close: () -> Unit, vm: AppViewModel) { var email by remember { mutableStateOf(initialEmail) }; var code by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var requested by remember { mutableStateOf(false) }; var hint by remember { mutableStateOf<String?>(null) }; AlertDialog(onDismissRequest = close, title = { Text("Pulihkan kata sandi") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(email, { email = it }, label = { Text("Email akun") }); if (requested) { Text(hint ?: "Kode telah dikirim ke kontak terdaftar.", color = Olive); OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Kode 6 digit") }); OutlinedTextField(password, { password = it }, label = { Text("Kata sandi baru") }, visualTransformation = PasswordVisualTransformation()) } } }, confirmButton = { Button(onClick = { if (!requested) vm.forgotPassword(email) { demo -> requested = true; hint = demo?.let { "Kode demo: $it" } } else vm.resetPassword(email, code, password, close) }, enabled = !loading && email.contains("@") && (!requested || (code.length == 6 && password.length >= 6))) { Text(if (requested) "Simpan kata sandi" else "Kirim kode") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } }) }

@Composable private fun RegisterScreen(state: AppUiState, vm: AppViewModel, back: () -> Unit, done: () -> Unit) {
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        BackButton(back)
        BrandMark(); Spacer(Modifier.height(20.dp)); Text("Buat akun customer", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Simpan alamat dan pantau jadwal kirimanmu.", color = Muted); Spacer(Modifier.height(18.dp))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nama lengkap") }, singleLine = true, isError = name.any(Char::isDigit), supportingText = { if (name.any(Char::isDigit)) Text("Nama tidak boleh berisi angka") })
        Spacer(Modifier.height(10.dp)); OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, isError = email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches(), supportingText = { if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) Text("Format email belum benar") })
        Spacer(Modifier.height(10.dp)); OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Nomor WhatsApp") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), isError = phone.isNotBlank() && (phone.removePrefix("+").any { !it.isDigit() } || phone.length < 8), supportingText = { if (phone.isNotBlank() && (phone.removePrefix("+").any { !it.isDigit() } || phone.length < 8)) Text("Gunakan minimal 8 angka") })
        Spacer(Modifier.height(10.dp)); OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Kata sandi") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, isError = password.isNotBlank() && password.length < 6, supportingText = { if (password.isNotBlank() && password.length < 6) Text("Kata sandi minimal 6 karakter") })
        Spacer(Modifier.height(18.dp)); Button(onClick = { vm.register(name, email, phone, password, done) }, Modifier.fillMaxWidth().height(52.dp), enabled = !state.loading && name.length >= 2 && name.none(Char::isDigit) && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() && phone.removePrefix("+").all(Char::isDigit) && phone.length >= 8 && password.length >= 6) { Text("Daftar") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun OnboardingScreen(state: AppUiState, nav: NavController, vm: AppViewModel) {
    var step by remember { mutableIntStateOf(1) }
    val context = LocalContext.current
    var labelText by remember { mutableStateOf("Rumah") }; var recipient by remember { mutableStateOf(state.user?.name.orEmpty()) }; var phone by remember { mutableStateOf(state.profile?.phone.orEmpty()) }
    var street by remember { mutableStateOf("") }; var district by remember { mutableStateOf("") }; var city by remember { mutableStateOf("Jakarta Selatan") }; var province by remember { mutableStateOf("DKI Jakarta") }; var postal by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }; var longitude by remember { mutableStateOf<Double?>(null) }
    fun captureLocation() { lastKnownCoordinates(context)?.let { (lat, lng) -> latitude = lat; longitude = lng } }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> if (result.values.any { it }) captureLocation() }
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BrandMark(); WizardProgress(step, 2, if (step == 1) "Alamat pertama" else "Preferensi makan")
        if (step == 1) {
            Text("Ke mana lauk dikirim?", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Alamat ini masih bisa diubah dari Profil.", color = Muted)
            FlowChoices(listOf("Rumah", "Kantor", "Kos"), labelText, { it }) { labelText = it }
            OutlinedTextField(recipient, { recipient = it.filterNot(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Nama penerima") }); OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, Modifier.fillMaxWidth(), label = { Text("Nomor telepon") }); OutlinedTextField(street, { street = it }, Modifier.fillMaxWidth(), label = { Text("Jalan, nomor, RT/RW") }, minLines = 2); OutlinedTextField(district, { district = it }, Modifier.fillMaxWidth(), label = { Text("Kecamatan") })
            Text("Kota", fontWeight = FontWeight.Bold); FlowChoices(listOf("Jakarta Selatan", "Jakarta Pusat", "Jakarta Barat", "Jakarta Timur", "Depok", "Tangerang"), city, { it }) { city = it }
            Text("Provinsi", fontWeight = FontWeight.Bold); FlowChoices(listOf("DKI Jakarta", "Jawa Barat", "Banten"), province, { it }) { province = it }
            OutlinedTextField(postal, { postal = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = { Text("Kode pos") })
            OutlinedButton(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) captureLocation() else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, Modifier.fillMaxWidth()) { Text(if (latitude == null) "Gunakan lokasi saat ini" else "Lokasi tersimpan · perbarui") }
            latitude?.let { Text("Koordinat: ${"%.5f".format(it)}, ${"%.5f".format(longitude)}", color = Olive, fontSize = 13.sp) }
            Button(onClick = { val line = "$street, Kec. $district, $city, $province $postal"; vm.saveAddress(null, AddressRequest(labelText, recipient, phone, line, city, street, district, city, province, postal, latitude!!, longitude!!)) { step = 2 } }, Modifier.fillMaxWidth(), enabled = labelText.length >= 2 && recipient.length >= 2 && phone.length >= 8 && street.length >= 5 && district.length >= 2 && postal.length == 5 && latitude != null && longitude != null && !state.loading) { Text("Lanjut ke preferensi") }
        } else PreferenceFields(state.preferences, state.loading, { step = 1 }) { preferences -> vm.savePreferences(preferences) { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun PreferencesScreen(state: AppUiState, nav: NavController, vm: AppViewModel) {
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Preferensi makan", fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(nav::popBackStack) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).padding(20.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) { PreferenceFields(state.preferences, state.loading, { nav.popBackStack() }) { vm.savePreferences(it) { nav.popBackStack() } } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PreferenceFields(initial: PreferenceDto, loading: Boolean, back: () -> Unit, save: (PreferenceDto) -> Unit) {
    val commonAllergens = listOf("ikan", "kedelai", "susu", "telur", "kacang", "gluten")
    val allergens = remember(initial) { mutableStateListOf<String>().apply { addAll(initial.allergens) } }
    var dislikes by remember(initial) { mutableStateOf(initial.disliked_ingredients.joinToString(", ")) }; var calories by remember(initial) { mutableStateOf(initial.calorie_target?.toString().orEmpty()) }; var activity by remember(initial) { mutableStateOf(initial.activity) }; var goal by remember(initial) { mutableStateOf(initial.fitness_goal) }; var meals by remember(initial) { mutableIntStateOf(initial.meals_per_day) }
    Text("Atur sesuai kebiasaanmu", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Data ini menyaring rekomendasi. Informasi kalori adalah estimasi per porsi, bukan saran medis.", color = Muted)
    Text("Alergi atau pantangan", fontWeight = FontWeight.Bold); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { commonAllergens.forEach { item -> FilterChip(selected = item in allergens, onClick = { if (item in allergens) allergens.remove(item) else allergens.add(item) }, label = { Text(label(item)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Terracotta, selectedLabelColor = WarmWhite)) } }
    OutlinedTextField(dislikes, { dislikes = it }, Modifier.fillMaxWidth(), label = { Text("Bahan yang tidak disukai, pisahkan koma") })
    Text("Aktivitas harian", fontWeight = FontWeight.Bold); FlowChoices(listOf("ringan", "normal", "aktif", "gym"), activity, { label(it) }) { activity = it }
    Text("Tujuan", fontWeight = FontWeight.Bold); FlowChoices(listOf("turun_berat", "menjaga_berat", "naik_massa_otot"), goal, { label(it) }) { goal = it }
    OutlinedTextField(calories, { calories = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Target kalori harian (opsional)") }, suffix = { Text("kkal") }); Counter("Frekuensi makan per hari", meals, 1, 8) { meals = it }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = back, Modifier.weight(1f)) { Text("Kembali") }; Button(onClick = { save(PreferenceDto(allergens.toList(), dislikes.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }, calories.toIntOrNull(), activity, goal, meals)) }, Modifier.weight(1f), enabled = !loading && (calories.isBlank() || (calories.toIntOrNull() ?: 0) in 800..6000)) { Text("Simpan") } }
}

@Composable private fun WizardProgress(step: Int, total: Int, title: String) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(total) { index -> Box(Modifier.weight(1f).height(5.dp).background(if (index < step) Terracotta else Beige, RoundedCornerShape(4.dp))) } }; Text("Langkah $step dari $total · $title", color = Terracotta, fontWeight = FontWeight.Bold, fontSize = 13.sp) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CustomerHome(state: AppUiState, nav: NavController, vm: AppViewModel) {
    var search by remember { mutableStateOf("") }
    val visibleMenus = state.catalog?.menus.orEmpty().filter { search.isBlank() || it.name.contains(search, true) || it.ingredients.any { ingredient -> ingredient.contains(search, true) } }
    val context = LocalContext.current
    val todaySlots = state.slots.filter { it.date == LocalDate.now().toString() }.map { it.id }.toSet()
    val todayOrders = state.orders.filter { order -> order.deliveries.any { it.slot_id in todaySlots } }
    LaunchedEffect(todayOrders.map { it.id }) { if (todayOrders.isNotEmpty()) notifyDelivery(context, todayOrders.flatMap { it.items }.joinToString { "${it.quantity}× ${it.name}" }) }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { BrandMark() }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream), actions = { Surface(Modifier.padding(end = 10.dp).size(42.dp).clickable { nav.navigate("profile") }, color = Terracotta, shape = RoundedCornerShape(21.dp)) { Box(contentAlignment = Alignment.Center) { Text(state.user?.name?.trim()?.firstOrNull()?.uppercase() ?: "P", color = WarmWhite, fontWeight = FontWeight.Black, fontSize = 18.sp) } } }) },
        bottomBar = { CustomerNav("beranda", nav) }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(bottom = 28.dp)) {
            if (state.offlineMode) item { Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), color = Beige, shape = RoundedCornerShape(14.dp)) { Text("Mode demo offline · login dan fitur utama tetap dapat dicoba tanpa backend", Modifier.padding(12.dp), color = LeafDark, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
            if (todayOrders.isNotEmpty()) item { Surface(Modifier.padding(16.dp).fillMaxWidth(), color = Olive, shape = RoundedCornerShape(10.dp)) { Column(Modifier.padding(16.dp)) { Text("Kirimanmu datang hari ini", color = WarmWhite, fontWeight = FontWeight.Bold); Text(todayOrders.flatMap { it.items }.joinToString { "${it.quantity}× ${it.name}" }, color = WarmWhite, fontSize = 13.sp) } } }
            if (state.cart.isNotEmpty()) item { Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clickable { nav.navigate("builder") }, color = Brown, shape = RoundedCornerShape(10.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Keranjang tersimpan", color = WarmWhite, fontWeight = FontWeight.Bold); Text("${state.cart.sumOf { it.quantity }} pack · lanjutkan kapan saja", color = Beige, fontSize = 13.sp) }; Text("Lanjut →", color = WarmWhite, fontWeight = FontWeight.Bold) } } }
            item {
                Box(Modifier.fillMaxWidth().height(310.dp).clickable { state.catalog?.menus?.firstOrNull()?.let { nav.navigate("cook/${it.id}") } }) {
                    Image(painterResource(R.drawable.food_chicken), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Brown.copy(alpha = .94f)))))
                    Text("Foto ilustrasi", color = WarmWhite, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Brown.copy(alpha = .6f), RoundedCornerShape(4.dp)).padding(5.dp))
                    Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                        Text("DARI DAPUR, UNTUK HARI INI", color = Beige, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text("Lauk enak.\nHari lebih ringan.", color = WarmWhite, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp)); Text("Siap masak atau siap makan. Kamu pilih.", color = WarmWhite, fontSize = 14.sp)
                    }
                }
                Surface(color = Beige) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Makan teratur, tanpa repot", fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Text("Pilih lauk & jadwal kirimmu", color = Muted, fontSize = 12.sp) }
                    Button(onClick = { nav.navigate("builder") }, shape = RoundedCornerShape(8.dp)) { Text("Susun paket") }
                } }
                SectionTitle("Favorit pelanggan", "Tiga pilihan populer minggu ini")
                OutlinedTextField(search, { search = it }, Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp).fillMaxWidth(), label = { Text("Cari lauk atau bahan") }, leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) }, singleLine = true, shape = RoundedCornerShape(10.dp))
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(visibleMenus.take(3)) { menu ->
                        Surface(Modifier.width(220.dp).clickable { nav.navigate("cook/${menu.id}") }, color = Beige, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(10.dp)) { FoodPhoto(menu, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))); Spacer(Modifier.height(10.dp)); Text(menu.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text("Mulai ${rupiah(menu.variants.minOfOrNull { it.price } ?: 0)}", color = Terracotta, fontSize = 14.sp, fontWeight = FontWeight.Medium) } }
                    }
                }
            }
            item {
                Surface(Modifier.padding(20.dp).fillMaxWidth().clickable { nav.navigate("recommend") }, color = WarmWhite, border = BorderStroke(1.dp, Beige), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Hari ini makan apa?", fontWeight = FontWeight.SemiBold, fontSize = 17.sp); Text("Cari lauk yang pas dengan seleramu.", color = Muted, fontSize = 13.sp) }
                        Text("→", color = Terracotta, fontSize = 24.sp)
                    }
                }
                SectionTitle("Paket untuk harimu", "Mulai dari sekali makan sampai stok mingguan")
            }
            item { OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${state.publicSettings.support_whatsapp.filter(Char::isDigit)}?text=Halo%20LaukSatSet,%20saya%20ingin%20bertanya"))) }, Modifier.padding(horizontal = 20.dp).fillMaxWidth(), enabled = state.publicSettings.support_whatsapp.isNotBlank()) { Text(if (state.publicSettings.support_whatsapp.isBlank()) "Kontak bantuan belum diatur" else "Tanya LaukSatSet lewat WhatsApp") } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.catalog?.packages.orEmpty()) { selectedPackage -> PackCard(selectedPackage) { vm.setCartPackage(selectedPackage.id); nav.navigate("builder") } }
                }
            }
            item { Surface(Modifier.padding(20.dp).fillMaxWidth().clickable { nav.navigate("menus") }, color = Olive, shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Lihat semua lauk", color = WarmWhite, fontSize = 19.sp, fontWeight = FontWeight.Black); Text("Cari berdasarkan nama, bahan, atau kategori", color = WarmWhite.copy(alpha = .85f), fontSize = 13.sp) }; Text("→", color = WarmWhite, fontSize = 26.sp) } } }
            if (visibleMenus.isEmpty() && search.isNotBlank()) item { Text("Menu ‘$search’ belum ditemukan.", Modifier.padding(20.dp), color = Muted) }
            if (state.catalog?.menus.isNullOrEmpty() && !state.loading) item { EmptyState("Menu belum tersedia", "Coba muat ulang setelah admin mengaktifkan menu.") { vm.loadCustomer() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun AllMenusScreen(state: AppUiState, nav: NavController, vm: AppViewModel) {
    var search by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Semua") }
    val menus = state.catalog?.menus.orEmpty().filter { (category == "Semua" || it.category == category) && (search.isBlank() || it.name.contains(search, true) || it.ingredients.any { ingredient -> ingredient.contains(search, true) }) }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Semua lauk", fontWeight = FontWeight.Black) }, navigationIcon = { BackButton(nav::popBackStack) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(search, { search = it }, Modifier.padding(16.dp).fillMaxWidth(), label = { Text("Cari lauk atau bahan") }, leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) }, singleLine = true); FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (listOf("Semua") + state.catalog?.menus.orEmpty().map { it.category }.distinct()).forEach { item -> FilterChip(selected = item == category, onClick = { category = item }, label = { Text(item) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Terracotta, selectedLabelColor = WarmWhite)) } } }
            items(menus) { MenuCard(it) { nav.navigate("cook/${it.id}") } }
            if (menus.isEmpty()) item { EmptyState("Lauk tidak ditemukan", "Coba kata kunci atau kategori lain.") { search = ""; category = "Semua" } }
        }
    }
}

private fun notifyDelivery(context: Context, detail: String) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(NotificationChannel("delivery", "Pengingat pengiriman", NotificationManager.IMPORTANCE_DEFAULT))
    if (android.os.Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        val open = PendingIntent.getActivity(context, 101, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("lauksatset://orders")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(101, NotificationCompat.Builder(context, "delivery").setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("LaukSatSet datang hari ini").setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(detail)).setContentIntent(open).setAutoCancel(true).build())
    }
}

private fun notifyAdminReady(context: Context, detail: String) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(NotificationChannel("admin_ready", "Pesanan siap dikirim", NotificationManager.IMPORTANCE_HIGH))
    if (android.os.Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        val open = PendingIntent.getActivity(context, 102, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("lauksatset://admin")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(detail.hashCode(), NotificationCompat.Builder(context, "admin_ready").setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Pesanan siap dikirim").setContentText(detail).setContentIntent(open).setAutoCancel(true).build())
    }
}

private fun notifyCourierReady(context: Context, detail: String) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(NotificationChannel("courier_ready", "Tugas siap diantar", NotificationManager.IMPORTANCE_HIGH))
    if (android.os.Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        val open = PendingIntent.getActivity(context, 103, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("lauksatset://courier")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(detail.hashCode(), NotificationCompat.Builder(context, "courier_ready").setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Pesanan siap diantar").setContentText(detail).setContentIntent(open).setAutoCancel(true).build())
    }
}

@Composable private fun PackCard(pack: PackageDto, click: () -> Unit) {
    Surface(Modifier.width(172.dp).clickable(onClick = click), shape = RoundedCornerShape(10.dp), color = WarmWhite, border = BorderStroke(1.dp, Beige)) {
        Column(Modifier.padding(14.dp)) { Text(pack.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1); Text("${pack.duration_days} hari", color = Leaf, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("Pilih 1–3× makan/hari", color = Muted, fontSize = 11.sp); Spacer(Modifier.height(8.dp)); Text(if (pack.discount_percent > 0) "Hemat ${pack.discount_percent.toInt()}%" else "Coba dulu", color = Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun MenuCard(menu: MenuDto, click: () -> Unit) {
    Surface(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clickable(onClick = click), shape = RoundedCornerShape(10.dp), color = WarmWhite, border = BorderStroke(1.dp, Beige)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FoodPhoto(menu, Modifier.size(90.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                Text(menu.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp); Text(menu.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(5.dp)); Text("Mulai ${rupiah(menu.variants.minOfOrNull { it.price } ?: 0)} · ${menu.portion_label}", color = Leaf, fontWeight = FontWeight.Bold, fontSize = 13.sp); if (menu.calories_per_portion > 0) Text("±${menu.calories_per_portion} kkal per porsi", color = Olive, fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun FoodPhoto(menu: MenuDto, modifier: Modifier = Modifier) {
    val resource = when {
        menu.name.contains("ayam", ignoreCase = true) -> R.drawable.food_chicken
        menu.name.contains("dori", ignoreCase = true) || menu.allergens.any { it.contains("ikan", ignoreCase = true) } -> R.drawable.food_fish
        else -> R.drawable.food_tofu
    }
    Box(modifier) {
        val custom = remember(menu.image_url) { menu.image_url?.let { runCatching { val bytes = Base64.decode(it.substringAfter("base64,"), Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() } }
        if (custom != null) Image(custom, "Foto ${menu.name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Image(painterResource(resource), "Ilustrasi ${menu.name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text("Foto ilustrasi", color = WarmWhite, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomEnd).background(Brown.copy(alpha = .65f)).padding(horizontal = 5.dp, vertical = 2.dp))
    }
}

@Composable private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)) { Text(title, fontWeight = FontWeight.Black, fontSize = 21.sp, color = LeafDark); Text(subtitle, color = Muted, fontSize = 13.sp) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DateFilter(label: String, selectedDate: String?, allowAll: Boolean = false, futureOnly: Boolean = false, onDateSelected: (String?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val readable = selectedDate?.let { runCatching { LocalDate.parse(it).format(dateFormatter) }.getOrDefault(it) } ?: "Semua tanggal"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.Bold, color = Brown)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { showPicker = true }, colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = WarmWhite), shape = RoundedCornerShape(8.dp)) { Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(readable) }
            if (allowAll && selectedDate != null) TextButton(onClick = { onDateSelected(null) }) { Text("Semua", color = Terracotta, fontWeight = FontWeight.Bold) }
        }
    }
    if (showPicker) {
        val initialMillis = selectedDate?.let { runCatching { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() }
        val todayStart = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis, selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = !futureOnly || utcTimeMillis > todayStart
            override fun isSelectableYear(year: Int): Boolean = !futureOnly || year >= LocalDate.now().year
        })
        DatePickerDialog(onDismissRequest = { showPicker = false }, confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis -> onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                showPicker = false
            }) { Text("Pilih", color = Terracotta, fontWeight = FontWeight.Bold) }
        }, dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Batal", color = Brown) } }) {
            DatePicker(state = pickerState, colors = DatePickerDefaults.colors(selectedDayContainerColor = Terracotta, todayDateBorderColor = Terracotta, todayContentColor = Terracotta))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ProfileScreen(state: AppUiState, nav: NavController, vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadProfile() }
    val profile = state.profile
    var addAddress by remember { mutableStateOf(false) }; var editAddress by remember { mutableStateOf<AddressDto?>(null) }; var deleteAddress by remember { mutableStateOf<AddressDto?>(null) }; var feedback by remember { mutableStateOf(false) }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Profil saya", fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(nav::popBackStack) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Surface(shape = RoundedCornerShape(12.dp), color = Beige) { Column(Modifier.fillMaxWidth().padding(20.dp)) { Text(profile?.name ?: state.user?.name.orEmpty(), fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(profile?.email.orEmpty(), color = Muted); Text(profile?.phone.orEmpty(), color = Muted) } } }
            item { ChoiceSection("Preferensi & kebiasaan") { Text("Aktivitas: ${label(state.preferences.activity)} · tujuan ${label(state.preferences.fitness_goal)}", color = Muted); Text("Target ${state.preferences.calorie_target?.let { "$it kkal/hari" } ?: "belum diatur"} · ${state.preferences.meals_per_day}× makan", color = Muted); if (state.preferences.allergens.isNotEmpty()) Text("Dihindari: ${state.preferences.allergens.joinToString()}", color = Terracotta); OutlinedButton(onClick = { nav.navigate("preferences") }) { Text("Ubah preferensi") } } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Alamat pengiriman", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Button(onClick = { addAddress = true }) { Text("Tambah") } } }
            if (state.addresses.isEmpty()) item { Text("Belum ada alamat. Tambahkan alamat sebelum checkout.", color = Muted) }
            items(state.addresses) { address -> ChoiceSection(address.label) { Text(address.recipient); Text(address.phone, color = Muted); Text(address.line, color = Muted); Text(address.zone, color = Olive); Row { OutlinedButton(onClick = { editAddress = address }) { Text("Edit") }; TextButton(onClick = { deleteAddress = address }) { Text("Hapus", color = Terracotta) } } } }
            item { OutlinedButton(onClick = { feedback = true }, Modifier.fillMaxWidth()) { Text("Kirim masalah atau saran") } }
            item { OutlinedButton(onClick = { vm.logout { nav.navigate("login") { popUpTo(0) } } }, Modifier.fillMaxWidth()) { Text("Keluar dari akun") } }
        }
    }
    if (addAddress) AddressDialog(null, { addAddress = false }) { vm.saveAddress(null, it); addAddress = false }
    editAddress?.let { address -> AddressDialog(address, { editAddress = null }) { vm.saveAddress(address, it); editAddress = null } }
    deleteAddress?.let { address -> AlertDialog(onDismissRequest = { deleteAddress = null }, title = { Text("Hapus alamat ${address.label}?") }, text = { Text("Alamat yang sudah dipakai pada pesanan lama tetap disimpan untuk riwayat dan mungkin tidak dapat dihapus.") }, confirmButton = { Button(onClick = { vm.deleteAddress(address); deleteAddress = null }) { Text("Hapus") } }, dismissButton = { TextButton(onClick = { deleteAddress = null }) { Text("Batal") } }) }
    if (feedback) FeedbackDialog({ feedback = false }) { kind, message -> vm.sendFeedback(kind, message) { feedback = false } }
}

@Composable private fun AddressDialog(existing: AddressDto?, close: () -> Unit, save: (AddressRequest) -> Unit) {
    val context = LocalContext.current
    var labelText by remember { mutableStateOf(existing?.label ?: "Rumah") }; var recipient by remember { mutableStateOf(existing?.recipient.orEmpty()) }; var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }; var street by remember { mutableStateOf(existing?.street ?: existing?.line.orEmpty()) }; var district by remember { mutableStateOf(existing?.district.orEmpty()) }; var city by remember { mutableStateOf(existing?.city ?: existing?.zone ?: "Jakarta Selatan") }; var province by remember { mutableStateOf(existing?.province ?: "DKI Jakarta") }; var postal by remember { mutableStateOf(existing?.postal_code.orEmpty()) }; var latitude by remember { mutableStateOf(existing?.latitude) }; var longitude by remember { mutableStateOf(existing?.longitude) }
    fun captureLocation() { lastKnownCoordinates(context)?.let { (lat, lng) -> latitude = lat; longitude = lng } }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> if (result.values.any { it }) captureLocation() }
    AlertDialog(onDismissRequest = close, title = { Text(if (existing == null) "Tambah alamat" else "Edit alamat") }, text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Label alamat"); FlowChoices(listOf("Rumah", "Kantor", "Kos"), labelText, { it }) { labelText = it }; OutlinedTextField(recipient, { recipient = it }, label = { Text("Nama penerima") }, placeholder = { Text("Contoh: Rani Putri") }, isError = recipient.isNotBlank() && (recipient.length < 2 || recipient.any(Char::isDigit)), supportingText = { if (recipient.isNotBlank() && (recipient.length < 2 || recipient.any(Char::isDigit))) Text("Nama minimal 2 huruf dan tidak boleh berisi angka") }); OutlinedTextField(phone, { phone = it }, label = { Text("Nomor WhatsApp") }, isError = phone.isNotBlank() && (phone.removePrefix("+").any { !it.isDigit() } || phone.length < 8), supportingText = { if (phone.isNotBlank() && (phone.removePrefix("+").any { !it.isDigit() } || phone.length < 8)) Text("Gunakan minimal 8 angka") }); OutlinedTextField(street, { street = it }, label = { Text("Jalan, nomor, RT/RW") }, minLines = 2, isError = street.isNotBlank() && street.length < 5, supportingText = { if (street.isNotBlank() && street.length < 5) Text("Alamat jalan terlalu pendek") }); OutlinedTextField(district, { district = it }, label = { Text("Kecamatan") }, isError = district.isNotBlank() && district.length < 2); Text("Kota"); FlowChoices(listOf("Jakarta Selatan", "Jakarta Pusat", "Jakarta Barat", "Jakarta Timur", "Depok", "Tangerang"), city, { it }) { city = it }; Text("Provinsi"); FlowChoices(listOf("DKI Jakarta", "Jawa Barat", "Banten"), province, { it }) { province = it }; OutlinedTextField(postal, { postal = it }, label = { Text("Kode pos") }, isError = postal.isNotBlank() && (postal.length != 5 || postal.any { !it.isDigit() }), supportingText = { if (postal.isNotBlank() && (postal.length != 5 || postal.any { !it.isDigit() })) Text("Kode pos harus tepat 5 angka") }); OutlinedButton(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) captureLocation() else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, Modifier.fillMaxWidth()) { Text(if (latitude == null) "Gunakan lokasi saat ini" else "Perbarui lokasi") }; if (latitude == null) Text("Lokasi diperlukan untuk memeriksa jangkauan dan ongkir.", color = Terracotta, fontSize = 12.sp) else Text("Lokasi terpasang · jarak dihitung otomatis", color = Olive, fontSize = 13.sp) } }, confirmButton = { Button(onClick = { val line = "$street, Kec. $district, $city, $province $postal"; save(AddressRequest(labelText, recipient, phone, line, city, street, district, city, province, postal, latitude!!, longitude!!)) }, enabled = recipient.length >= 2 && recipient.none(Char::isDigit) && phone.removePrefix("+").all(Char::isDigit) && phone.length >= 8 && street.length >= 5 && district.length >= 2 && postal.length == 5 && postal.all(Char::isDigit) && latitude != null && longitude != null) { Text("Simpan") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } })
}

@Composable private fun FeedbackDialog(close: () -> Unit, send: (String, String) -> Unit) { var kind by remember { mutableStateOf("saran") }; var message by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, title = { Text("Masalah atau saran") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { FlowChoices(listOf("masalah", "saran", "lainnya"), kind, { label(it) }) { kind = it }; OutlinedTextField(message, { message = it }, label = { Text("Tulis pesan") }, minLines = 4) } }, confirmButton = { Button(onClick = { send(kind, message) }, enabled = message.length >= 5) { Text("Kirim") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } }) }

@Composable private fun CustomerNav(active: String, nav: NavController) {
    NavigationBar(containerColor = WarmWhite, tonalElevation = 0.dp) {
        listOf("beranda" to "Beranda", "recommend" to "Saran", "pantry" to "Stok", "orders" to "Pesanan").forEach { (route, title) ->
            NavigationBarItem(selected = active == route, onClick = { if (active != route) nav.navigate(if (route == "beranda") "home" else route) { launchSingleTop = true; popUpTo("home") } }, icon = { Icon(painterResource(when (route) { "beranda" -> R.drawable.ic_home; "recommend" -> R.drawable.ic_search; "pantry" -> R.drawable.ic_stock; else -> R.drawable.ic_orders }), null, Modifier.size(23.dp)) }, label = { Text(title) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = WarmWhite, selectedTextColor = Terracotta, indicatorColor = Terracotta, unselectedIconColor = Brown, unselectedTextColor = Brown))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PantryScreen(state: AppUiState, nav: NavController, vm: AppViewModel) {
    var pantryDate by remember { mutableStateOf<String?>(null) }
    var pantryStatus by remember { mutableStateOf("semua") }
    var confirmAction by remember { mutableStateOf<Pair<PantryItemDto, String>?>(null) }
    var storageItem by remember { mutableStateOf<PantryItemDto?>(null) }
    var issueItem by remember { mutableStateOf<PantryItemDto?>(null) }
    val visiblePantry = state.pantry.filter { (pantryDate == null || it.recommended_use_at == pantryDate) && (pantryStatus == "semua" || it.status == pantryStatus) }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Stok Lauk Saya", fontWeight = FontWeight.Black) }, navigationIcon = { BackButton(nav::popBackStack) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = { CustomerNav("pantry", nav) }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Lauk yang sudah diterima", fontSize = 25.sp, fontWeight = FontWeight.Black, color = LeafDark); Text("Tanggal anjuran penggunaan bukan jaminan keamanan pangan. Periksa kondisi makanan sebelum digunakan.", color = Muted, fontSize = 13.sp) }
            item { ChoiceSection("Filter stok") { DateFilter("Anjuran penggunaan", pantryDate, allowAll = true) { pantryDate = it }; Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("semua", "tersedia", "sudah_dimasak", "bermasalah").forEach { status -> FilterChip(selected = pantryStatus == status, onClick = { pantryStatus = status }, label = { Text(label(status), maxLines = 1) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Terracotta, selectedLabelColor = WarmWhite)) } } } }
            if (visiblePantry.isEmpty()) item { EmptyState(if (state.pantry.isEmpty()) "Stok masih kosong" else "Tidak ada lauk di tanggal ini", if (state.pantry.isEmpty()) "Lauk akan masuk setelah pengiriman ditandai diterima." else "Pilih tanggal lain atau tampilkan semua tanggal.") { if (state.pantry.isEmpty()) nav.navigate("orders") else pantryDate = null } }
            items(visiblePantry) { item ->
                Surface(shape = RoundedCornerShape(16.dp), color = if (item.status == "tersedia") Beige else WarmWhite, border = BorderStroke(1.dp, if (item.status == "bermasalah") Terracotta else Olive.copy(alpha = .4f))) { Column(Modifier.padding(18.dp)) {
                    Row { Column(Modifier.weight(1f)) { Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.Black); Text("${label(item.variant)} · ${item.quantity} pack", color = Leaf, fontWeight = FontWeight.Bold) }; Text(label(item.status), color = if (item.status == "tersedia") Orange else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp)); Text("Anjuran digunakan sebelum ${item.recommended_use_at ?: "lihat label"}", color = Muted, fontSize = 13.sp); Text(item.storage_guide, color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { nav.navigate("pantry-cook/${item.menu_id}") }, colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = WarmWhite)) { Text("Masak") }; OutlinedButton(onClick = { storageItem = item }) { Text("Cara simpan") } }
                    Row { if (item.status == "tersedia") TextButton(onClick = { confirmAction = item to "sudah_dimasak" }) { Text("Tandai sudah dimasak") }; TextButton(onClick = { issueItem = item }) { Text("Laporkan masalah", color = MaterialTheme.colorScheme.error) } }
                } }
            }
        }
    }
    storageItem?.let { item -> AlertDialog(onDismissRequest = { storageItem = null }, title = { Text("Cara menyimpan ${item.name}") }, text = { Text(item.storage_guide) }, confirmButton = { TextButton(onClick = { storageItem = null }) { Text("Mengerti") } }) }
    confirmAction?.let { (item, status) -> AlertDialog(onDismissRequest = { confirmAction = null }, title = { Text(if (status == "sudah_dimasak") "Sudah dimasak?" else "Laporkan masalah?") }, text = { Text(if (status == "sudah_dimasak") "${item.name} akan dikeluarkan dari stok yang tersedia." else "Status ${item.name} akan diubah menjadi bermasalah dan laporan akan dikirim.") }, confirmButton = { Button(onClick = { vm.updatePantry(item.id, status); confirmAction = null }) { Text("Ya, lanjutkan") } }, dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Batal") } }) }
    issueItem?.let { item -> var note by remember(item.id) { mutableStateOf("") }; AlertDialog(onDismissRequest = { issueItem = null }, title = { Text("Masalah pada ${item.name}") }, text = { OutlinedTextField(note, { note = it }, label = { Text("Jelaskan kondisi atau masalah") }, minLines = 4) }, confirmButton = { Button(onClick = { vm.updatePantry(item.id, "bermasalah", note); issueItem = null }, enabled = note.length >= 5) { Text("Kirim laporan") } }, dismissButton = { TextButton(onClick = { issueItem = null }) { Text("Batal") } }) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun RecommendationScreen(state: AppUiState, nav: NavController, vm: AppViewModel) {
    var question by remember { mutableStateOf("Enaknya makan apa hari ini?") }
    var preference by remember { mutableStateOf("gurih, tidak terlalu pedas") }
    var budget by remember { mutableStateOf("50000") }
    var minutes by remember { mutableStateOf("15") }
    var people by remember { mutableIntStateOf(2) }
    val context = LocalContext.current
    var scannedPhoto by remember { mutableStateOf<Uri?>(null) }
    var imageLabels by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val selectedIngredients = remember { mutableStateListOf<String>() }
    val ingredientOptions = remember(state.catalog) { state.catalog?.menus.orEmpty().flatMap { it.ingredients }.map(String::trim).filter(String::isNotBlank).distinctBy(String::lowercase).sorted() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scannedPhoto = uri
            imageLabels = emptyList()
            selectedIngredients.clear()
            scanError = null
            scanning = true
            try {
                val image = InputImage.fromFilePath(context, uri)
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        imageLabels = labels.filter { it.confidence >= 0.35f }.take(8).map { it.text to it.confidence }
                        val labelWords = imageLabels.map { it.first.lowercase() }
                        selectedIngredients.addAll(ingredientOptions.filter { ingredient ->
                            ingredientLabelAliases(ingredient).any { alias -> labelWords.any { label -> alias in label } }
                        })
                        if (imageLabels.isEmpty()) scanError = "Bahan belum dikenali dari foto ini. Pilih bahan secara manual di bawah."
                        scanning = false
                    }
                    .addOnFailureListener {
                        scanError = "Foto gagal dianalisis. Coba foto lain atau pilih bahan secara manual."
                        scanning = false
                    }
                    .addOnCompleteListener { labeler.close() }
            } catch (_: IOException) {
                scanError = "Foto tidak dapat dibuka. Pilih gambar lain dari galeri."
                scanning = false
            } catch (_: Exception) {
                scanError = "Foto gagal dianalisis. Coba lagi."
                scanning = false
            }
        }
    }
    val menusFromIngredients = remember(state.catalog, state.preferences, selectedIngredients.toList()) {
        val avoided = (state.preferences.allergens + state.preferences.disliked_ingredients).map { it.trim().lowercase() }.filter(String::isNotBlank)
        state.catalog?.menus.orEmpty().filter { menu ->
            val menuTerms = (menu.ingredients + menu.allergens).map { it.trim().lowercase() }
            avoided.none { blocked -> menuTerms.any { term -> blocked in term || term in blocked } } &&
                selectedIngredients.any { chosen -> menu.ingredients.any { it.equals(chosen, ignoreCase = true) } }
        }
    }
    val quickIdeas = listOf("Cepat & praktis" to "makan cepat", "Hemat" to "hemat", "Tanpa ayam" to "ikan atau tahu", "Tidak pedas" to "tidak pedas")
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Rekomendasi lauk", fontWeight = FontWeight.Black) }, navigationIcon = { BackButton(nav::popBackStack) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = { CustomerNav("recommend", nav) }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Ceritakan yang kamu inginkan", fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black, color = LeafDark); Text("Pilihan hanya diambil dari menu LaukSatSet yang sedang tersedia.", color = Muted); Text("Mesin rekomendasi: ${state.publicSettings.recommendation_engine}", color = Olive, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            item {
                ChoiceSection("Punya bahan di rumah?") {
                    Text("Pilih foto bahan. ML Kit mengenali isi foto langsung di perangkat; fotomu tidak diunggah.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { imagePicker.launch("image/*") }, enabled = !scanning, colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = WarmWhite)) {
                        Text(if (scanning) "Mengenali bahan…" else if (scannedPhoto == null) "Pilih foto bahan" else "Pilih foto lain")
                    }
                    scannedPhoto?.let { Text("Foto dipilih · ${it.lastPathSegment ?: "gambar"}", color = Olive, fontSize = 12.sp) }
                    if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Terracotta)
                    scanError?.let { Text(it, color = Terracotta, fontSize = 13.sp) }
                    if (imageLabels.isNotEmpty()) {
                        Text("Hasil pengenalan · periksa sebelum memilih", fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            imageLabels.forEach { (text, confidence) -> AssistChip(onClick = {}, label = { Text("$text · ${(confidence * 100).toInt()}%") }) }
                        }
                        if (selectedIngredients.isEmpty()) Text("Belum ada bahan menu yang cocok otomatis. Pilih bahan yang benar dari daftar.", color = Muted, fontSize = 12.sp)
                    }
                    Text("Bahan yang tersedia di menu", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ingredientOptions.forEach { ingredient ->
                            FilterChip(selected = ingredient in selectedIngredients, onClick = { if (ingredient in selectedIngredients) selectedIngredients.remove(ingredient) else selectedIngredients.add(ingredient) }, label = { Text(label(ingredient)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Terracotta, selectedLabelColor = WarmWhite))
                        }
                    }
                    if (selectedIngredients.isNotEmpty()) {
                        Text("Menu yang cocok", fontWeight = FontWeight.Bold)
                        if (menusFromIngredients.isEmpty()) Text("Belum ada menu yang cocok dengan bahan dan preferensimu.", color = Muted, fontSize = 13.sp)
                        menusFromIngredients.forEach { menu ->
                            val variant = menu.variants.firstOrNull()
                            Surface(Modifier.fillMaxWidth(), color = Beige, shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(menu.name, fontWeight = FontWeight.Bold, color = Brown); Text("Bahan: ${menu.ingredients.joinToString()}", color = Muted, fontSize = 12.sp); variant?.let { Text(rupiah(it.price), color = Olive, fontWeight = FontWeight.Bold) } }
                                    TextButton(onClick = { if (variant != null) { vm.addMenuDirect(menu, variant, people); nav.navigate("cart") } }, enabled = variant != null) { Text("Pilih", color = Terracotta) }
                                }
                            }
                        }
                    }
                    Text("Hasil pengenalan adalah perkiraan, bukan pemeriksaan keamanan atau kandungan alergen.", color = Muted, fontSize = 11.sp)
                }
            }
            item { Text("Pilih kebutuhanmu", fontWeight = FontWeight.SemiBold); FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { quickIdeas.forEach { (title, value) -> Surface(Modifier.fillMaxWidth(.48f).clickable { preference = value; question = "Rekomendasikan lauk $value" }, color = if (preference == value) Terracotta else Beige, shape = RoundedCornerShape(10.dp)) { Text(title, Modifier.padding(16.dp), color = if (preference == value) WarmWhite else Brown, fontWeight = FontWeight.Medium) } } } }
            item { OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth(), label = { Text("Pertanyaan") }, shape = RoundedCornerShape(14.dp), minLines = 2) }
            item { OutlinedTextField(preference, { preference = it }, Modifier.fillMaxWidth(), label = { Text("Selera atau bahan favorit") }, shape = RoundedCornerShape(14.dp)) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(budget, { budget = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Anggaran") }, prefix = { Text("Rp") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Waktu") }, suffix = { Text("mnt") }, shape = RoundedCornerShape(14.dp)) } }
            item { ChoiceSection("Untuk berapa orang?") { Counter("Jumlah orang", people, 1, 20) { people = it } } }
            item { Button(onClick = { vm.recommend(RecommendationRequest(budget.toIntOrNull(), minutes.toIntOrNull(), preference, question, people)) }, Modifier.fillMaxWidth().height(52.dp), enabled = question.isNotBlank() && !state.loading, colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = WarmWhite), shape = RoundedCornerShape(14.dp)) { Text("Carikan lauk", fontWeight = FontWeight.Black) } }
            if (state.recommendations.isNotEmpty()) item { Text("Tiga pilihan untukmu", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
            items(state.recommendations) { result ->
                Surface(shape = RoundedCornerShape(16.dp), color = if (result == state.recommendations.first()) LeafDark else Beige) { Column(Modifier.padding(20.dp).fillMaxWidth()) { val dark = result == state.recommendations.first(); Text(if (result.source == "gemini") "Pilihan utama · Gemini" else if (dark) "Pilihan utama" else "Pilihan alternatif", color = if (dark) Beige else Terracotta, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(result.menu_name, color = if (dark) WarmWhite else Brown, fontSize = 24.sp, fontWeight = FontWeight.Black); Text("${label(result.variant ?: "varian tersedia")} · untuk ${result.people} orang", color = if (dark) Beige else Olive, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(result.reason, color = if (dark) WarmWhite else Brown); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { val foundMenu = state.catalog?.menus?.firstOrNull { it.id == result.menu_id }; val foundVariant = foundMenu?.variants?.firstOrNull { it.id == result.variant_id } ?: foundMenu?.variants?.firstOrNull(); if (foundMenu != null && foundVariant != null) { vm.addMenuDirect(foundMenu, foundVariant, result.people); nav.navigate("cart") } }, colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = WarmWhite)) { Text("Masuk keranjang") }; TextButton(onClick = { nav.navigate("cook/${result.menu_id}") }) { Text("Detail", color = if (dark) WarmWhite else Brown) } } } }
            }
        }
    }
}

private fun ingredientLabelAliases(ingredient: String): List<String> {
    val normalized = ingredient.lowercase()
    val aliases = when {
        "ayam" in normalized -> listOf("chicken", "poultry")
        "ikan" in normalized || "dori" in normalized -> listOf("fish", "seafood")
        "tahu" in normalized -> listOf("tofu", "soybean", "soy")
        "jamur" in normalized -> listOf("mushroom", "fungus")
        "cabai" in normalized || "sambal" in normalized -> listOf("chili", "pepper")
        "bawang" in normalized -> listOf("onion", "garlic", "shallot")
        "kemangi" in normalized -> listOf("basil", "herb")
        "serai" in normalized -> listOf("lemongrass")
        "kecap" in normalized -> listOf("soy sauce", "sauce")
        else -> emptyList()
    }
    return listOf(normalized) + aliases
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BuilderScreen(state: AppUiState, nav: NavController, vm: AppViewModel, initialStep: Int = 1) {
    val catalog = state.catalog; val selections = state.cart; val selectedSlots = state.cartSlotIds
    val pack = catalog?.packages?.firstOrNull { it.id == state.cartPackageId } ?: catalog?.packages?.firstOrNull()
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var mealsPerDay by rememberSaveable { mutableIntStateOf((selections.maxOfOrNull { it.meal_sequence } ?: state.preferences.meals_per_day).coerceIn(1, 3)) }
    var people by rememberSaveable { mutableIntStateOf((selections.firstOrNull()?.portions ?: 1).coerceIn(1, 10)) }
    var editingMeal by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pendingMealChange by remember { mutableStateOf<CheckoutItem?>(null) }
    var confirmCheckout by remember { mutableStateOf(false) }
    var deliveryMethod by rememberSaveable { mutableStateOf("diantar") }; var removeIndex by remember { mutableStateOf<Int?>(null) }
    var activeScheduleIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedAddressId by rememberSaveable { mutableStateOf(state.addresses.firstOrNull()?.id) }
    var addressPickerSlotId by remember { mutableStateOf<Int?>(null) }
    var templateSaved by remember { mutableStateOf(false) }
    val slotAddresses = rememberSaveable(saver = listSaver<SnapshotStateMap<Int, Int>, Int>(
        save = { map -> map.entries.flatMap { listOf(it.key, it.value) } },
        restore = { values -> mutableStateMapOf<Int, Int>().apply { values.chunked(2).forEach { pair -> if (pair.size == 2) put(pair[0], pair[1]) } } }
    )) { mutableStateMapOf() }
    val requiredCount = (pack?.duration_days ?: 1) * mealsPerDay
    val selectedCount = selections.sumOf { it.quantity }; val packageDays = pack?.duration_days ?: 1
    val maxDeliveries = when { packageDays <= 3 -> packageDays; packageDays <= 7 -> 2; else -> kotlin.math.ceil(packageDays / 7.0).toInt().coerceAtMost(12) }
    val mealGridValid = selectedCount == requiredCount && selections.map { it.meal_day to it.meal_sequence }.toSet().size == requiredCount
    val selectedAddress = state.addresses.firstOrNull { it.id == selectedAddressId }
    val activeSlot = state.slots.firstOrNull { it.id == selectedSlots.getOrNull(activeScheduleIndex.coerceAtMost((selectedSlots.size - 1).coerceAtLeast(0))) }
    val shippingEstimate = if (deliveryMethod == "diantar") state.slots.filter { it.id in selectedSlots }.sumOf { slot -> val packs = selections.filter { it.slot_id == slot.id }.sumOf { it.quantity }; val address = state.addresses.firstOrNull { it.id == (slotAddresses[slot.id] ?: selectedAddressId) }; val distanceFee = kotlin.math.ceil(((address?.distance_km ?: 0.0) - 5.0).coerceAtLeast(0.0)).toInt() * state.publicSettings.delivery_fee_per_km; slot.shipping_fee + distanceFee + (packs - 1).coerceAtLeast(0) * state.publicSettings.delivery_fee_per_extra_pack } else 0
    val estimated = selections.sumOf { line -> (catalog?.menus?.flatMap { it.variants }?.firstOrNull { it.id == line.variant_id }?.price ?: 0) * line.quantity + line.rice_quantity * 5000 + line.sambal_quantity * 3000 + line.cracker_quantity * 4000 } + shippingEstimate
    val capacityValid = selectedSlots.all { id -> selections.filter { it.slot_id == id }.sumOf { it.quantity } <= (state.slots.firstOrNull { it.id == id }?.remaining ?: 0) }
    val dayScheduleValid = selections.groupBy { it.meal_day }.values.all { dayItems -> dayItems.map { it.slot_id }.toSet().size == 1 }
    val scheduleValid = (deliveryMethod == "ambil_sendiri" || (selectedAddressId != null && selectedSlots.all { (slotAddresses[it] ?: selectedAddressId) != null })) && selectedSlots.isNotEmpty() && selections.all { it.slot_id in selectedSlots } && selectedSlots.all { id -> selections.any { it.slot_id == id } } && dayScheduleValid && capacityValid
    val titles = listOf("Pilih paket", "Pilih lauk", "Keranjang", "Pengiriman", "Ringkasan")
    LaunchedEffect(pack?.id, state.slots) { if (state.cartPackageId == null && pack != null) vm.setCartPackage(pack.id); if (selectedSlots.isEmpty()) state.slots.firstOrNull { it.available }?.let { vm.setCartSlots(listOf(it.id)) } }
    LaunchedEffect(state.addresses) { if (selectedAddressId == null) selectedAddressId = state.addresses.firstOrNull()?.id }
    LaunchedEffect(selectedSlots, selectedAddressId) { if (activeScheduleIndex > selectedSlots.lastIndex) activeScheduleIndex = selectedSlots.lastIndex.coerceAtLeast(0); selectedAddressId?.let { address -> selectedSlots.forEach { slotAddresses.putIfAbsent(it, address) } }; slotAddresses.keys.retainAll(selectedSlots.toSet()) }
    Scaffold(containerColor = Cream, topBar = { Column(Modifier.background(Cream).padding(horizontal = 16.dp, vertical = 8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { BackButton { if (step > 1) step-- else nav.popBackStack() }; Text("Susun paket", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; WizardProgress(step, 5, titles[step - 1]) } }, bottomBar = { Surface(color = WarmWhite, shadowElevation = 6.dp) { Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { if (step > 1) OutlinedButton(onClick = { step-- }, Modifier.weight(1f)) { Text("Sebelumnya") }; val enabled = when (step) { 1 -> pack != null; 2 -> mealGridValid; 3 -> mealGridValid; 4 -> scheduleValid && mealGridValid; else -> scheduleValid && mealGridValid && !state.loading }; Button(onClick = { if (step < 5) step++ else confirmCheckout = true }, Modifier.weight(1f), enabled = enabled) { Text(if (step == 5) "Periksa & bayar" else "Lanjut") } } } }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (step == 1) {
                item { Text("Pilih ritme makanmu", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Tentukan durasi paket, jumlah orang, dan berapa kali makan setiap hari.", color = Muted) }
                items(catalog?.packages.orEmpty()) { itemPack -> Surface(Modifier.fillMaxWidth().clickable { vm.setCartPackage(itemPack.id) }, color = if (pack?.id == itemPack.id) Terracotta else WarmWhite, border = BorderStroke(1.dp, if (pack?.id == itemPack.id) Terracotta else Beige), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(18.dp)) { Column(Modifier.weight(1f)) { Text(itemPack.name, color = if (pack?.id == itemPack.id) WarmWhite else Brown, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("${itemPack.duration_days} hari", color = if (pack?.id == itemPack.id) Beige else Muted) }; if (itemPack.discount_percent > 0) Text("Hemat ${itemPack.discount_percent.toInt()}%", color = if (pack?.id == itemPack.id) WarmWhite else Terracotta, fontWeight = FontWeight.Bold) } } }
                item { ChoiceSection("Kebutuhan makan") { Counter("Untuk berapa orang", people, 1, 10) { value -> people = value; vm.setCartPortions(value) }; Counter("Makan per hari", mealsPerDay, 1, 3) { value -> mealsPerDay = value; vm.setCartMealsPerDay(value) }; Text("$people orang · $mealsPerDay kali makan per hari · total $requiredCount pilihan lauk.", color = Terracotta, fontWeight = FontWeight.Bold); Text("Setiap waktu makan boleh memakai lauk berbeda. Jumlah porsi otomatis mengikuti jumlah orang dan masih bisa disesuaikan per lauk.", color = Muted, fontSize = 12.sp) } }
            }
            if (step == 2) {
                item { Text("Pilih lauk per waktu makan", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("$selectedCount dari $requiredCount kolom sudah diisi. Tekan tiap kartu untuk memilih lauk berbeda.", color = Terracotta, fontWeight = FontWeight.Bold) }
                item { ChoiceSection("Isi lebih cepat") { Text("Simpan susunan Hari 1 agar bisa ditempel lagi pada pesanan berikutnya.", color = Muted, fontSize = 13.sp); if (selections.isNotEmpty()) Button(onClick = { vm.applyChoiceToAll(selections.first(), packageDays, mealsPerDay, people) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = WarmWhite)) { Text("Pakai pilihan pertama untuk semua") }; OutlinedButton(onClick = { vm.copyDayOneTemplate(mealsPerDay); templateSaved = true }, Modifier.fillMaxWidth(), enabled = selections.count { it.meal_day == 1 } == mealsPerDay) { Text(if (templateSaved) "Susunan Hari 1 tersimpan ✓" else "Salin susunan Hari 1") }; Button(onClick = { vm.pasteSavedMealTemplate(packageDays, mealsPerDay, people) }, Modifier.fillMaxWidth(), enabled = state.hasSavedMealTemplate, colors = ButtonDefaults.buttonColors(containerColor = Olive, contentColor = WarmWhite)) { Text("Tempel susunan tersimpan") } } }
                (1..(pack?.duration_days ?: 1)).forEach { day ->
                    item { Text("Hari $day", fontSize = 20.sp, fontWeight = FontWeight.Black, color = LeafDark) }
                    items((1..mealsPerDay).toList().chunked(2)) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { meal ->
                                val chosen = selections.firstOrNull { it.meal_day == day && it.meal_sequence == meal }
                                val chosenMenu = catalog?.menus?.firstOrNull { menuItem -> menuItem.variants.any { it.id == chosen?.variant_id } }
                                val chosenVariant = chosenMenu?.variants?.firstOrNull { it.id == chosen?.variant_id }
                                Surface(Modifier.weight(1f).clickable { editingMeal = day to meal }, color = if (chosen == null) WarmWhite else Beige, border = BorderStroke(1.dp, if (chosen == null) Beige else Terracotta), shape = RoundedCornerShape(14.dp)) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Makan $meal", color = Terracotta, fontWeight = FontWeight.Black)
                                        if (chosenMenu == null) { Text("+ Pilih lauk", color = Muted); Text("Belum diisi", color = Muted, fontSize = 12.sp) }
                                        else { FoodPhoto(chosenMenu, Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(8.dp))); Text(chosenMenu.name, fontWeight = FontWeight.Bold, maxLines = 2); Text("${label(chosenVariant?.kind ?: "")} · ${rupiah(chosenVariant?.price ?: 0)}", color = Olive, fontSize = 12.sp); Text("Ganti pilihan", color = Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (step == 3) {
                item { Text("Keranjangmu", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Semua waktu makan ditampilkan terpisah agar mudah diperiksa.", color = Muted) }
                if (selections.isEmpty()) item { EmptyState("Keranjang kosong", "Kembali dan isi setiap kolom waktu makan.", "Pilih lauk") { step = 2 } }
                (1..packageDays).forEach { day ->
                    item { Text("Hari $day", fontSize = 20.sp, fontWeight = FontWeight.Black, color = LeafDark) }
                    items(selections.filter { it.meal_day == day }.sortedBy { it.meal_sequence }) { line -> val index = selections.indexOf(line); val foundMenu = catalog?.menus?.firstOrNull { m -> m.variants.any { it.id == line.variant_id } }; val foundVariant = foundMenu?.variants?.firstOrNull { it.id == line.variant_id }; Surface(Modifier.fillMaxWidth(), color = WarmWhite, border = BorderStroke(1.dp, Beige), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { foundMenu?.let { FoodPhoto(it, Modifier.size(76.dp).clip(RoundedCornerShape(10.dp))) }; Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Makan ${line.meal_sequence}", color = Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(foundMenu?.name ?: "Lauk", fontWeight = FontWeight.Black, fontSize = 17.sp); Text("${label(foundVariant?.kind ?: "")} · ${line.portions} porsi", color = Olive, fontSize = 12.sp); val addons = listOfNotNull(if (line.rice_quantity > 0) "Nasi" else null, if (line.sambal_quantity > 0) "Sambal" else null, if (line.cracker_quantity > 0) "Kerupuk" else null); if (addons.isNotEmpty()) Text(addons.joinToString(), color = Muted, fontSize = 11.sp); Row { TextButton(onClick = { editingMeal = line.meal_day to line.meal_sequence }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Ganti") }; TextButton(onClick = { removeIndex = index }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Hapus", color = Terracotta) } } } } } }
                }
                item { Text("Total $selectedCount/$requiredCount pilihan", fontWeight = FontWeight.Bold, color = if (mealGridValid) Olive else Terracotta) }
            }
            if (step == 4) {
                item {
                    Text(if (deliveryMethod == "ambil_sendiri") "Atur pengambilan" else "Atur pengiriman", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(if (packageDays >= 28) "Paket 4 minggu dapat dibagi menjadi $maxDeliveries jadwal mingguan agar kualitas lauk tetap terjaga." else "Paket ini dapat dibagi ke maksimal $maxDeliveries jadwal.", color = Muted)
                }
                item { ChoiceSection("Cara menerima") { FlowChoices(listOf("diantar", "ambil_sendiri"), deliveryMethod, { if (it == "diantar") "Diantar" else "Ambil sendiri" }) { deliveryMethod = it }; Text(if (deliveryMethod == "diantar") "Pilih satu tujuan utama. Alamat berbeda per jadwal tetap tersedia bila dibutuhkan." else "Pilih rentang jam pengambilan. Lokasi: ${state.publicSettings.kitchen_address}.", color = Muted, fontSize = 13.sp); if (deliveryMethod == "ambil_sendiri") OutlinedButton(onClick = { val settings = state.publicSettings; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${settings.kitchen_latitude},${settings.kitchen_longitude}"))) }, Modifier.fillMaxWidth()) { Text("Lihat lokasi pengambilan di Maps") } } }
                item { ChoiceSection(if (deliveryMethod == "diantar") "Jadwal pengiriman" else "Jadwal pengambilan") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { selectedSlots.forEachIndexed { index, id -> val slot = state.slots.firstOrNull { it.id == id }; FilterChip(selected = index == activeScheduleIndex, onClick = { activeScheduleIndex = index }, label = { Text("${index + 1}. ${slot?.date?.let { raw -> runCatching { LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("dd MMM")) }.getOrDefault(raw) } ?: "Jadwal"}") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Terracotta, selectedLabelColor = WarmWhite)) } }
                    DateFilter("Tanggal jadwal ${activeScheduleIndex + 1}", activeSlot?.date, futureOnly = true) { newDate -> newDate?.let { date -> state.slots.firstOrNull { it.date == date && it.available }?.let { replacement -> val oldId = activeSlot?.id; oldId?.let { previous -> slotAddresses.remove(previous)?.let { address -> slotAddresses[replacement.id] = address } }; vm.replaceCartSlot(oldId, replacement.id) } } }
                    val sameDaySlots = state.slots.filter { it.date == activeSlot?.date && it.available }
                    FlowChoices(sameDaySlots, activeSlot, { "${it.label} · ${if (deliveryMethod == "diantar") rupiah(it.shipping_fee) else "Gratis"}" }) { replacement -> val oldId = activeSlot?.id; oldId?.let { previous -> slotAddresses.remove(previous)?.let { address -> slotAddresses[replacement.id] = address } }; vm.replaceCartSlot(oldId, replacement.id) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedSlots.size < maxDeliveries) OutlinedButton(onClick = { val dates = selectedSlots.mapNotNull { id -> state.slots.firstOrNull { candidate -> candidate.id == id }?.date }; val next = state.slots.firstOrNull { it.available && it.id !in selectedSlots && it.date !in dates }; if (next != null) { vm.setCartSlots(selectedSlots + next.id); activeScheduleIndex = selectedSlots.size } }, Modifier.weight(1f)) { Text("+ Tambah jadwal") }
                        if (selectedSlots.size > 1) OutlinedButton(onClick = vm::distributeCartAcrossSlots, Modifier.weight(1f)) { Text("Bagi otomatis") }
                    }
                    Text("Tanggal, jam, lauk, dan alamat jadwal aktif selalu diperbarui bersama.", color = Muted, fontSize = 12.sp)
                } }
                if (deliveryMethod == "diantar") {
                    item { ChoiceSection("Tujuan pengiriman") { Text("Semua jadwal pada pesanan ini memakai satu alamat.", color = Muted, fontSize = 12.sp); selectedAddress?.let { Text(it.label, fontWeight = FontWeight.Bold); Text("${it.recipient} · ${it.phone}\n${it.line}", color = Muted, fontSize = 13.sp) } ?: Text("Belum ada alamat yang dipilih.", color = Terracotta); Button(onClick = { addressPickerSlotId = 0 }, Modifier.fillMaxWidth(), enabled = state.addresses.isNotEmpty()) { Text("Ubah alamat") }; TextButton(onClick = { nav.navigate("profile") }, Modifier.fillMaxWidth()) { Text(if (state.addresses.isEmpty()) "Tambah alamat baru" else "Kelola alamat tersimpan") } } }
                }
                items((1..packageDays).toList()) { day ->
                    val dayItems = selections.filter { it.meal_day == day }
                    val activeSlotId = dayItems.mapNotNull { it.slot_id }.distinct().singleOrNull()
                    ChoiceSection("Hari $day · ${dayItems.size} kali makan") { val options = state.slots.filter { it.id in selectedSlots }; FlowChoices(options, options.firstOrNull { it.id == activeSlotId }, { "${LocalDate.parse(it.date).format(dateFormatter)} · ${it.label}" }) { vm.assignCartDayToSlot(day, it.id) }; if (activeSlotId == null) Text("Pilih satu jadwal untuk seluruh lauk pada hari ini.", color = Terracotta, fontSize = 12.sp) }
                }
                if (!scheduleValid) item { Text(if (!capacityValid) "Jumlah lauk melebihi kapasitas salah satu jadwal." else if (!dayScheduleValid) "Semua makanan pada hari yang sama harus memakai satu jadwal." else "Pilih minimal satu jadwal, bagi setiap hari ke jadwal, dan lengkapi alamat tujuan.", color = Terracotta, fontWeight = FontWeight.Bold) }
            }
            if (step == 5) {
                item { Text("Cek pesananmu", fontSize = 28.sp, fontWeight = FontWeight.Black, color = LeafDark); Text("Periksa paket, lauk, jadwal, tujuan, dan preferensi sebelum membayar.", color = Muted) }
                item { ChoiceSection("Ringkasan paket") { Text(pack?.name ?: "Paket", fontSize = 20.sp, fontWeight = FontWeight.Black); Text("$packageDays hari · $people orang · $mealsPerDay kali makan per hari", color = Olive, fontWeight = FontWeight.Bold); Text("${selections.size} pilihan lauk · ${selectedSlots.size} jadwal ${if (deliveryMethod == "diantar") "pengiriman" else "pengambilan"}", color = Muted) } }
                if (deliveryMethod == "diantar") item { ChoiceSection("Alamat tujuan") { selectedAddress?.let { Text(it.label, fontWeight = FontWeight.Bold); Text("${it.recipient} · ${it.phone}\n${it.line}", color = Muted) }; OutlinedButton(onClick = { addressPickerSlotId = 0 }, Modifier.fillMaxWidth()) { Text("Ubah alamat") } } }
                items(selectedSlots) { slotId ->
                    val slot = state.slots.firstOrNull { it.id == slotId }
                    val address = state.addresses.firstOrNull { it.id == (slotAddresses[slotId] ?: selectedAddressId) }
                    ChoiceSection(if (deliveryMethod == "diantar") "Pengiriman · ${slot?.date ?: "-"}" else "Pengambilan · ${slot?.date ?: "-"}") {
                        Text(slot?.label ?: "-", color = Terracotta, fontWeight = FontWeight.Bold)
                        if (deliveryMethod == "diantar") Text("${address?.recipient ?: "Alamat belum dipilih"} · ${address?.phone.orEmpty()}\n${address?.line.orEmpty()}", color = Muted)
                        else { Text("Lokasi pengambilan", fontWeight = FontWeight.Bold); Text(state.publicSettings.kitchen_address, color = Muted); OutlinedButton(onClick = { val settings = state.publicSettings; val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${settings.kitchen_latitude},${settings.kitchen_longitude}"); context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }, Modifier.fillMaxWidth()) { Text("Buka lokasi di Google Maps") } }
                        selections.filter { it.slot_id == slotId }.sortedWith(compareBy<CheckoutItem> { it.meal_day }.thenBy { it.meal_sequence }).forEach { line -> val found = catalog?.menus?.firstOrNull { m -> m.variants.any { it.id == line.variant_id } }; val extras = listOfNotNull(line.rice_quantity.takeIf { it > 0 }?.let { "nasi" }, line.sambal_quantity.takeIf { it > 0 }?.let { "sambal" }, line.cracker_quantity.takeIf { it > 0 }?.let { "kerupuk" }); Text("Hari ${line.meal_day} · Makan ${line.meal_sequence}: ${found?.name ?: "Lauk"} · ${line.portions} porsi${if (extras.isEmpty()) "" else " · ${extras.joinToString()}"}", fontSize = 13.sp) }
                    }
                }
                item { ChoiceSection("Catatan preferensi") { Text("Aktivitas ${label(state.preferences.activity)} · tujuan ${label(state.preferences.fitness_goal)}", fontWeight = FontWeight.Medium); if (state.preferences.calorie_target != null) Text("Target ${state.preferences.calorie_target} kkal/hari", color = Muted); if (state.preferences.allergens.isNotEmpty()) Text("Alergi yang disimpan: ${state.preferences.allergens.joinToString()}", color = Terracotta, fontWeight = FontWeight.Bold) else Text("Tidak ada alergi yang disimpan.", color = Muted); if (state.preferences.disliked_ingredients.isNotEmpty()) Text("Dihindari: ${state.preferences.disliked_ingredients.joinToString()}", color = Muted); TextButton(onClick = { nav.navigate("preferences") }) { Text("Ubah preferensi") } } }
                item { Surface(color = Brown, shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(20.dp)) { PriceRow("Lauk & add-on", selections.sumOf { line -> (catalog?.menus?.flatMap { it.variants }?.firstOrNull { it.id == line.variant_id }?.price ?: 0) * line.quantity + line.rice_quantity * 5000 + line.sambal_quantity * 3000 + line.cracker_quantity * 4000 }); PriceRow(if (deliveryMethod == "diantar") "Ongkir" else "Biaya pengambilan", shippingEstimate); HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Beige); Text("Perkiraan ${rupiah(estimated)}", color = WarmWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Diskon paket dihitung ulang oleh server.", color = Beige, fontSize = 12.sp) } } }
            }
        }
    }
    editingMeal?.let { (day, mealNumber) ->
        val current = selections.firstOrNull { it.meal_day == day && it.meal_sequence == mealNumber }
        MealPickerDialog(day, mealNumber, people, catalog?.menus.orEmpty(), current, { editingMeal = null }) { choice -> if (step == 3 && current != null) pendingMealChange = choice else { vm.replaceCart((selections.filterNot { it.meal_day == day && it.meal_sequence == mealNumber } + choice.copy(slot_id = choice.slot_id ?: selectedSlots.firstOrNull())).sortedWith(compareBy<CheckoutItem> { it.meal_day }.thenBy { it.meal_sequence })); editingMeal = null } }
    }
    pendingMealChange?.let { choice -> AlertDialog(onDismissRequest = { pendingMealChange = null }, title = { Text("Simpan perubahan lauk?") }, text = { Text("Pilihan Hari ${choice.meal_day} · Makan ${choice.meal_sequence} akan diganti. Periksa varian dan add-on sebelum menyimpan.") }, confirmButton = { Button(onClick = { vm.replaceCart((selections.filterNot { it.meal_day == choice.meal_day && it.meal_sequence == choice.meal_sequence } + choice.copy(slot_id = choice.slot_id ?: selectedSlots.firstOrNull())).sortedWith(compareBy<CheckoutItem> { it.meal_day }.thenBy { it.meal_sequence })); pendingMealChange = null; editingMeal = null }) { Text("Ya, simpan") } }, dismissButton = { TextButton(onClick = { pendingMealChange = null }) { Text("Periksa lagi") } }) }
    addressPickerSlotId?.let { targetSlotId -> AlertDialog(onDismissRequest = { addressPickerSlotId = null }, title = { Text(if (targetSlotId == 0) "Ubah alamat utama" else "Pilih alamat hari pengiriman") }, text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { state.addresses.forEach { address -> val selectedId = if (targetSlotId == 0) selectedAddressId else (slotAddresses[targetSlotId] ?: selectedAddressId); Surface(Modifier.fillMaxWidth().clickable { if (targetSlotId == 0) { selectedAddressId = address.id; selectedSlots.forEach { slotAddresses[it] = address.id } } else slotAddresses[targetSlotId] = address.id; addressPickerSlotId = null }, color = if (selectedId == address.id) Terracotta else Beige, shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(14.dp)) { Text(address.label, color = if (selectedId == address.id) WarmWhite else Brown, fontWeight = FontWeight.Bold); Text("${address.recipient} · ${address.phone}\n${address.line}", color = if (selectedId == address.id) WarmWhite else Muted, fontSize = 12.sp) } } }; TextButton(onClick = { addressPickerSlotId = null; nav.navigate("profile") }, Modifier.fillMaxWidth()) { Text("Tambah atau edit alamat") } } }, confirmButton = {}, dismissButton = { TextButton(onClick = { addressPickerSlotId = null }) { Text("Batal") } }) }
    if (confirmCheckout) AlertDialog(onDismissRequest = { confirmCheckout = false }, title = { Text("Pesanan sudah benar?") }, text = { Text("${selections.size} pilihan lauk · ${selectedSlots.size} jadwal · ${if (deliveryMethod == "diantar") "diantar ke alamat yang dipilih" else "diambil di ${state.publicSettings.kitchen_address}"}. Setelah pembayaran, perubahan mengikuti batas waktu produksi.") }, confirmButton = { Button(onClick = { val p = pack ?: return@Button; confirmCheckout = false; vm.checkout(CheckoutRequest(p.id, if (deliveryMethod == "diantar") selectedAddressId else null, selectedSlots, selections, deliveryMethod, mealsPerDay, slotAddresses.toMap())) { nav.navigate("payment") } }) { Text("Benar, lanjut bayar") } }, dismissButton = { TextButton(onClick = { confirmCheckout = false }) { Text("Periksa lagi") } })
    removeIndex?.let { index -> AlertDialog(onDismissRequest = { removeIndex = null }, title = { Text("Hapus lauk?") }, text = { Text("Lauk ini akan dikeluarkan dari keranjang.") }, confirmButton = { Button(onClick = { vm.removeCartItem(index); removeIndex = null }) { Text("Hapus") } }, dismissButton = { TextButton(onClick = { removeIndex = null }) { Text("Batal") } }) }
}

@Composable private fun MealPickerDialog(day: Int, mealNumber: Int, defaultPortions: Int, menus: List<MenuDto>, current: CheckoutItem?, close: () -> Unit, save: (CheckoutItem) -> Unit) {
    var menu by remember(day, mealNumber) { mutableStateOf(menus.firstOrNull { candidate -> candidate.variants.any { it.id == current?.variant_id } } ?: menus.firstOrNull()) }
    var variant by remember(menu, current) { mutableStateOf(menu?.variants?.firstOrNull { it.id == current?.variant_id } ?: menu?.variants?.firstOrNull()) }
    var portions by remember(current, defaultPortions) { mutableIntStateOf(current?.portions ?: defaultPortions) }; var rice by remember(current) { mutableIntStateOf(current?.rice_quantity ?: 0) }; var sambal by remember(current) { mutableIntStateOf(current?.sambal_quantity ?: 0) }; var crackers by remember(current) { mutableIntStateOf(current?.cracker_quantity ?: 0) }; var spicy by remember(current) { mutableStateOf(current?.spicy_level ?: "sedang") }; var note by remember(current) { mutableStateOf(current?.note.orEmpty()) }
    AlertDialog(onDismissRequest = close, title = { Text("Hari $day · Makan $mealNumber") }, text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Pilih lauk", fontWeight = FontWeight.Bold); menus.forEach { candidate -> val selected = candidate.id == menu?.id; Surface(Modifier.fillMaxWidth().clickable { menu = candidate; variant = candidate.variants.firstOrNull() }, color = if (selected) Terracotta else Beige, shape = RoundedCornerShape(10.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { FoodPhoto(candidate, Modifier.size(58.dp).clip(RoundedCornerShape(8.dp))); Spacer(Modifier.width(10.dp)); Column { Text(candidate.name, color = if (selected) WarmWhite else Brown, fontWeight = FontWeight.Bold); Text("±${candidate.calories_per_portion} kkal", color = if (selected) Beige else Olive, fontSize = 12.sp) } } } }; Text("Pilih bentuk", fontWeight = FontWeight.Bold); FlowChoices(menu?.variants.orEmpty(), variant, { "${label(it.kind)} · ${rupiah(it.price)}" }) { variant = it }; EditableCounter("Porsi", portions, 1, 10) { portions = it }; Text("Add-on", fontWeight = FontWeight.Bold); EditableCounter("Nasi · ${rupiah(5000)}", rice, 0, 10) { rice = it }; EditableCounter("Sambal · ${rupiah(3000)}", sambal, 0, 10) { sambal = it }; EditableCounter("Kerupuk · ${rupiah(4000)}", crackers, 0, 10) { crackers = it }; FlowChoices(listOf("tidak_pedas", "sedang", "pedas"), spicy, { label(it) }) { spicy = it }; OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Catatan (opsional)") }) } }, confirmButton = { Button(onClick = { val selectedVariant = variant ?: return@Button; save(CheckoutItem(selectedVariant.id, 1, portions, spicy, rice, note.ifBlank { null }, current?.slot_id, day, mealNumber, sambal, crackers)) }, enabled = variant != null) { Text("Simpan pilihan") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } })
}

@Composable private fun ChoiceSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = WarmWhite, border = BorderStroke(1.dp, Beige)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp); content() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun <T> FlowChoices(values: List<T>, selected: T?, text: (T) -> String, choose: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { values.forEach { value -> FilterChip(selected = value == selected, onClick = { choose(value) }, label = { Text(text(value)) }, colors = FilterChipDefaults.filterChipColors(containerColor = WarmWhite, labelColor = Brown, selectedContainerColor = Terracotta, selectedLabelColor = WarmWhite)) } }
}

@Composable private fun Counter(title: String, value: Int, min: Int, max: Int, set: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold); OutlinedButton(onClick = { set((value - 1).coerceAtLeast(min)) }, enabled = value > min, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(38.dp)) { Text("−") }; Text("$value", Modifier.width(42.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold); OutlinedButton(onClick = { set((value + 1).coerceAtMost(max)) }, enabled = value < max, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(38.dp)) { Text("+") } }
}

@Composable private fun EditableCounter(title: String, value: Int, min: Int, max: Int, set: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { set((value - 1).coerceAtLeast(min)) }, enabled = value > min, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(38.dp)) { Text("−") }
        OutlinedTextField(value.toString(), { raw -> raw.filter(Char::isDigit).toIntOrNull()?.let { set(it.coerceIn(min, max)) } }, Modifier.width(76.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        OutlinedButton(onClick = { set((value + 1).coerceAtMost(max)) }, enabled = value < max, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(38.dp)) { Text("+") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PaymentScreen(state: AppUiState, nav: NavController, vm: AppViewModel) {
    val result = state.checkout
    val context = LocalContext.current
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Pembayaran", fontWeight = FontWeight.Black) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("✓", fontSize = 52.sp, color = Leaf); Spacer(Modifier.height(12.dp)); Text("Pesanan dibuat", fontSize = 28.sp, fontWeight = FontWeight.Black); Text(result?.order_id.orEmpty(), color = Muted)
            Spacer(Modifier.height(24.dp)); Surface(shape = RoundedCornerShape(18.dp), color = Beige) { Column(Modifier.padding(18.dp).fillMaxWidth()) { PriceRow("Subtotal", result?.breakdown?.subtotal ?: 0); PriceRow("Add-on", result?.breakdown?.addon_total ?: 0); PriceRow("Diskon", -(result?.breakdown?.discount_total ?: 0)); PriceRow("Ongkir", result?.breakdown?.shipping_total ?: 0); HorizontalDivider(Modifier.padding(vertical = 10.dp)); PriceRow("Total", result?.total ?: 0, true) } }
            Spacer(Modifier.height(14.dp)); Text(result?.label ?: "Transaksi simulasi — Midtrans Sandbox", color = Orange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (result?.demo_mode == true) Text("URL pembayaran adalah placeholder karena Server Key belum dikonfigurasi. Status hanya dapat disimulasikan admin melalui backend.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp)); Button(onClick = { result?.snap_redirect_url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = result != null && !result.demo_mode, shape = RoundedCornerShape(14.dp)) { Text("Buka Midtrans Sandbox") }
            OutlinedButton(onClick = { vm.loadOrders(); nav.navigate("orders") { popUpTo("home") } }, Modifier.fillMaxWidth()) { Text("Lihat status pesanan") }
        }
    }
}

@Composable private fun PriceRow(title: String, value: Int, strong: Boolean = false) { Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(title, Modifier.weight(1f), fontWeight = if (strong) FontWeight.Black else FontWeight.Normal); Text(rupiah(value), fontWeight = if (strong) FontWeight.Black else FontWeight.Medium) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun OrdersScreen(state: AppUiState, nav: NavController, vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadOrders() }
    var orderDate by remember { mutableStateOf<String?>(null) }
    val visibleOrders = state.orders.filter { orderDate == null || it.created_at.take(10) == orderDate }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Pesanan Saya", fontWeight = FontWeight.Black) }, navigationIcon = { BackButton(nav::popBackStack) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = { CustomerNav("orders", nav) }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DateFilter("Tanggal pemesanan", orderDate, allowAll = true) { orderDate = it } }
            if (visibleOrders.isEmpty() && !state.loading) item { EmptyState(if (state.orders.isEmpty()) "Belum ada pesanan" else "Tidak ada pesanan di tanggal ini", if (state.orders.isEmpty()) "Cari menu dulu, lalu susun paket sesuai kebutuhanmu." else "Pilih tanggal lain atau tampilkan semua tanggal.", if (state.orders.isEmpty()) "Cari menu" else "Semua tanggal") { if (state.orders.isEmpty()) nav.navigate("home") { launchSingleTop = true } else orderDate = null } }
            items(visibleOrders) { order -> Surface(Modifier.fillMaxWidth().clickable { nav.navigate("order/${order.id}") }, shape = RoundedCornerShape(18.dp), color = Beige) { Column(Modifier.padding(16.dp)) { Row { Column(Modifier.weight(1f)) { Text(order.id, fontWeight = FontWeight.Black); Text(customerOrderStatus(order.status), color = Leaf, fontWeight = FontWeight.Bold, fontSize = 13.sp) }; Text(rupiah(order.total), fontWeight = FontWeight.Black) }; Spacer(Modifier.height(10.dp)); order.items.forEach { Text("Hari ${it.meal_day} · Makan ${it.meal_sequence}: ${it.name} · ${label(it.variant)}", fontSize = 14.sp) }; Text("Pembayaran: ${label(order.payment_status ?: "belum_tersedia")}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)); order.deliveries.forEachIndexed { index, delivery -> Text("Pengiriman ${index + 1}: ${delivery.date ?: "-"} · ${customerDeliveryStatus(delivery.status)}", color = Muted, fontSize = 13.sp) }; Text("Buka detail →", color = Terracotta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) } } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun OrderDetailScreen(state: AppUiState, orderId: String, nav: NavController, vm: AppViewModel) {
    val order = state.orders.firstOrNull { it.id == orderId }
    val isCustomer = state.user?.role == "pelanggan"
    val context = LocalContext.current
    var cancelConfirm by remember { mutableStateOf(false) }; var addressPicker by remember { mutableStateOf(false) }; var scheduleFor by remember { mutableStateOf<DeliveryDto?>(null) }; var reportFor by remember { mutableStateOf<DeliveryDto?>(null) }; var review by remember { mutableStateOf(false) }
    LaunchedEffect(orderId) { vm.refreshOrder(orderId) }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Detail pesanan", fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(nav::popBackStack) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (order == null) item { EmptyState("Pesanan tidak ditemukan", "Muat ulang daftar pesanan.") { vm.loadOrders() } }
            order?.let { current ->
                item { Text(current.id, color = Muted); Text(if (isCustomer) customerOrderStatus(current.status) else adminOrderStatus(current.status), fontSize = 28.sp, fontWeight = FontWeight.Black, color = LeafDark); Text(paymentMessage(current.payment_status), color = if (current.payment_status in listOf("gagal", "kedaluwarsa")) Terracotta else Olive, fontWeight = FontWeight.Bold) }
                if (isCustomer && (current.status == "menunggu_pembayaran" || current.payment_status in listOf("gagal", "kedaluwarsa"))) item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { if (current.payment_status in listOf("gagal", "kedaluwarsa")) vm.retryPayment(current.id) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } else current.payment_url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }, enabled = current.payment_status in listOf("gagal", "kedaluwarsa") || !current.payment_url.isNullOrBlank(), modifier = Modifier.weight(1f)) { Text(if (current.payment_status in listOf("gagal", "kedaluwarsa")) "Bayar ulang" else "Lanjut bayar") }; if (current.status == "menunggu_pembayaran") OutlinedButton(onClick = { cancelConfirm = true }, Modifier.weight(1f)) { Text("Batalkan") } } }
                item { ChoiceSection("Isi pesanan") { current.items.forEach { item -> Text("Hari ${item.meal_day} · Makan ${item.meal_sequence}: ${item.name} · ${label(item.variant)} · ${item.portions} porsi"); val addons = listOfNotNull(item.rice_quantity.takeIf { it > 0 }?.let { "$it nasi" }, item.sambal_quantity.takeIf { it > 0 }?.let { "$it sambal" }, item.cracker_quantity.takeIf { it > 0 }?.let { "$it kerupuk" }); if (addons.isNotEmpty()) Text("Add-on: ${addons.joinToString()}", color = Muted, fontSize = 12.sp) }; HorizontalDivider(); PriceRow("Total", current.total, true) } }
                item { ChoiceSection(if (current.delivery_method == "ambil_sendiri") "Lokasi pengambilan" else "Alamat pengiriman") { Text(current.address?.label ?: "Ambil sendiri", fontWeight = FontWeight.Bold); current.address?.let { Text("${it.recipient} · ${it.phone}\n${it.line}, ${it.zone}", color = Muted) } ?: Text(state.publicSettings.kitchen_address, color = Muted); if (isCustomer && current.delivery_method == "diantar" && current.status in listOf("menunggu_pembayaran", "dibayar", "dikonfirmasi")) OutlinedButton(onClick = { addressPicker = true }) { Text("Ganti alamat") } } }
                items(current.deliveries) { delivery -> ChoiceSection("${delivery.date ?: "Jadwal"} · ${delivery.label ?: ""}") { Text(if (isCustomer) customerDeliveryStatus(delivery.status) else adminDeliveryStatus(delivery.status), color = Olive, fontWeight = FontWeight.Bold); if (isCustomer) DeliveryTimeline(delivery.status) else AdminDeliveryTimeline(delivery.status); delivery.items.forEach { Text("${it.quantity}× ${it.name}") }; delivery.address?.let { Text("Tujuan: ${it.label} · ${it.line}", color = Muted) }; delivery.courier_name?.let { courier -> Text("Pengantar: $courier · ${delivery.courier_phone.orEmpty()}", color = Muted); if (!delivery.courier_phone.isNullOrBlank()) OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${delivery.courier_phone}"))) }, Modifier.fillMaxWidth()) { Text("Hubungi pengantar") } } ?: run { Text(if (isCustomer) "Nama pengantar akan muncul saat pesanan siap berangkat." else "Pengantar belum ditetapkan untuk jadwal ini.", color = Muted, fontSize = 12.sp) }; if (isCustomer) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (current.status in listOf("menunggu_pembayaran", "dibayar", "dikonfirmasi")) OutlinedButton(onClick = { scheduleFor = delivery }) { Text("Ubah jadwal") }; if (delivery.status in listOf("siap_dikirim", "dalam_pengiriman")) Button(onClick = { vm.receiveDelivery(delivery.id) }) { Text("Sudah diterima") } }; TextButton(onClick = { reportFor = delivery }) { Text("Laporkan masalah", color = Terracotta) } } } }
                item { ChoiceSection(if (isCustomer) "Perjalanan pesanan" else "Timeline operasional") { if (isCustomer) OrderTimeline(current.status) else AdminOrderTimeline(current.status) } }
                if (current.status == "selesai") item { Button(onClick = { review = true }, Modifier.fillMaxWidth()) { Text("Beri ulasan dan saran") } }
            }
        }
    }
    if (cancelConfirm) AlertDialog(onDismissRequest = { cancelConfirm = false }, title = { Text("Batalkan pesanan?") }, text = { Text("Slot yang telah dipesan akan dilepas. Tindakan ini tidak dapat dibatalkan.") }, confirmButton = { Button(onClick = { vm.cancelOrder(orderId); cancelConfirm = false }) { Text("Ya, batalkan") } }, dismissButton = { TextButton(onClick = { cancelConfirm = false }) { Text("Kembali") } })
    if (addressPicker) AlertDialog(onDismissRequest = { addressPicker = false }, title = { Text("Pilih alamat") }, text = { Column { state.addresses.forEach { address -> Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.changeOrderAddress(orderId, address.id); addressPicker = false }, color = Beige, shape = RoundedCornerShape(10.dp)) { Column(Modifier.padding(12.dp)) { Text(address.label, fontWeight = FontWeight.Bold); Text(address.line, color = Muted) } } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = { addressPicker = false }) { Text("Tutup") } })
    scheduleFor?.let { delivery -> AlertDialog(onDismissRequest = { scheduleFor = null }, title = { Text("Pindah jadwal") }, text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) { state.slots.filter { it.available && it.id != delivery.slot_id && it.date > LocalDate.now().toString() }.forEach { slot -> Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.rescheduleDelivery(delivery.id, slot.id); scheduleFor = null }, color = Beige, shape = RoundedCornerShape(10.dp)) { Text("${slot.date} · ${slot.label} · sisa ${slot.remaining}", Modifier.padding(12.dp)) } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = { scheduleFor = null }) { Text("Tutup") } }) }
    reportFor?.let { delivery -> ComplaintDialog(delivery, { reportFor = null }) { note, photo -> vm.reportDelivery(delivery.id, note, photo); reportFor = null } }
    if (review) ReviewDialog({ review = false }) { rating, note -> vm.reviewOrder(orderId, rating, note); review = false }
}

@Composable private fun OrderTimeline(status: String) {
    val steps = listOf("menunggu_pembayaran" to "Pesananmu sudah kami terima", "menunggu_produksi" to "Pembayaran sudah dikonfirmasi", "diproduksi" to "Laukmu sedang disiapkan", "lolos_qc" to "Pesanan sudah diperiksa dan dikemas", "siap_dikirim" to "Pesanan siap berangkat", "dalam_pengiriman" to "Pengantar sedang menuju lokasimu", "selesai" to "Pesanan sudah sampai")
    val normalized = if (status in listOf("dibayar", "dikonfirmasi")) "menunggu_produksi" else status
    val current = steps.indexOfFirst { it.first == normalized }
    steps.forEachIndexed { index, (_, text) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(if (index <= current) "●" else "○", color = if (index <= current) Terracotta else Muted); Spacer(Modifier.width(10.dp)); Text(text, color = if (index <= current) Brown else Muted) } }
}

@Composable private fun ProductionTimeline(status: String) {
    val steps = listOf("menunggu_pembayaran" to "Pesanan masuk", "menunggu_produksi" to "Pembayaran masuk", "diproduksi" to "Produksi", "lolos_qc" to "QC selesai", "siap_dikirim" to "Siap diambil")
    val current = steps.indexOfFirst { it.first == status }
    steps.forEachIndexed { index, (_, text) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(if (current >= 0 && index <= current) "●" else "○", color = if (current >= 0 && index <= current) Terracotta else Muted, fontSize = 12.sp); Spacer(Modifier.width(8.dp)); Text(text, color = if (current >= 0 && index <= current) Brown else Muted, fontSize = 12.sp) } }
}

@Composable private fun DeliveryTimeline(status: String) {
    val steps = listOf("terjadwal" to "Jadwal sudah tersimpan", "siap_dikirim" to "Pesanan siap berangkat", "dalam_pengiriman" to "Sedang menuju lokasimu", "diterima" to "Pesanan sudah diterima")
    val current = steps.indexOfFirst { it.first == status }
    steps.forEachIndexed { index, (_, text) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(if (current >= 0 && index <= current) "●" else "○", color = if (current >= 0 && index <= current) Terracotta else Muted, fontSize = 12.sp); Spacer(Modifier.width(8.dp)); Text(text, color = if (current >= 0 && index <= current) Brown else Muted, fontSize = 12.sp) } }
}

private fun customerOrderStatus(status: String) = when (status) { "menunggu_pembayaran" -> "Menunggu pembayaran"; "dibayar", "dikonfirmasi", "menunggu_produksi" -> "Pesanan sedang diproses"; "diproduksi" -> "Lauk sedang disiapkan"; "lolos_qc" -> "Pesanan sudah dikemas"; "siap_dikirim" -> "Siap dikirim"; "dalam_pengiriman" -> "Sedang menuju lokasimu"; "selesai" -> "Pesanan selesai"; "dibatalkan" -> "Pesanan dibatalkan"; "bermasalah" -> "Pesanan sedang kami bantu"; else -> label(status) }

private fun customerDeliveryStatus(status: String) = when (status) { "terjadwal" -> "Jadwal pengiriman sudah tersimpan"; "siap_dikirim" -> "Pesanan siap dikirim"; "dalam_pengiriman" -> "Pengantar sedang menuju lokasimu"; "diterima" -> "Pesanan sudah diterima"; else -> label(status) }

private fun adminOrderStatus(status: String) = when (status) { "menunggu_pembayaran" -> "Menunggu settlement pembayaran"; "dibayar", "dikonfirmasi" -> "Pembayaran terverifikasi"; "menunggu_produksi" -> "Antrean produksi aktif"; "diproduksi" -> "Sedang diproduksi"; "lolos_qc" -> "Lolos QC dapur"; "siap_dikirim" -> "Siap serah terima pengantar"; "dalam_pengiriman" -> "Sedang diantar"; "selesai" -> "Selesai"; "dibatalkan" -> "Dibatalkan"; "bermasalah" -> "Perlu penanganan"; else -> label(status) }

private fun adminDeliveryStatus(status: String) = when (status) { "terjadwal" -> "Terjadwal · menunggu produksi/QC"; "siap_dikirim" -> "Dapur melepas ke pengantar"; "dalam_pengiriman" -> "Bukti pickup tersimpan · perjalanan aktif"; "diterima" -> "Penerimaan dikonfirmasi"; else -> label(status) }

@Composable private fun AdminOrderTimeline(status: String) {
    val steps = listOf("menunggu_pembayaran" to "Menunggu settlement", "menunggu_produksi" to "Antrean produksi aktif", "diproduksi" to "Produksi berjalan", "lolos_qc" to "QC dapur selesai", "siap_dikirim" to "Siap serah terima", "dalam_pengiriman" to "Pengantaran aktif", "selesai" to "Order selesai")
    val normalized = if (status in listOf("dibayar", "dikonfirmasi")) "menunggu_produksi" else status
    val current = steps.indexOfFirst { it.first == normalized }
    steps.forEachIndexed { index, (_, text) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(if (index <= current) "●" else "○", color = if (index <= current) Terracotta else Muted); Spacer(Modifier.width(10.dp)); Text(text, color = if (index <= current) Brown else Muted) } }
}

@Composable private fun AdminDeliveryTimeline(status: String) {
    val steps = listOf("terjadwal" to "Slot terjadwal", "siap_dikirim" to "QC selesai / siap pickup", "dalam_pengiriman" to "Bukti pickup / perjalanan", "diterima" to "Penerimaan selesai")
    val current = steps.indexOfFirst { it.first == status }
    steps.forEachIndexed { index, (_, text) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(if (current >= 0 && index <= current) "●" else "○", color = if (current >= 0 && index <= current) Terracotta else Muted, fontSize = 12.sp); Spacer(Modifier.width(8.dp)); Text(text, color = if (current >= 0 && index <= current) Brown else Muted, fontSize = 12.sp) } }
}

private fun paymentMessage(status: String?) = when (status) { "settlement" -> "Pembayaran berhasil"; "pending" -> "Pembayaran masih diproses"; "gagal" -> "Pembayaran gagal"; "kedaluwarsa" -> "Pembayaran kedaluwarsa"; else -> "Menunggu status pembayaran" }

@Composable private fun ComplaintDialog(delivery: DeliveryDto, close: () -> Unit, send: (String, String?) -> Unit) {
    val context = LocalContext.current; var note by remember { mutableStateOf("") }; var photo by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { runCatching { context.contentResolver.openInputStream(it)?.use { stream -> Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP) } }.getOrNull()?.let { encoded -> photo = encoded } } }
    AlertDialog(onDismissRequest = close, title = { Text("Laporkan masalah") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Pengiriman ${delivery.date ?: delivery.id}", color = Muted); OutlinedTextField(note, { note = it }, label = { Text("Jelaskan masalah") }, minLines = 3); OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (photo == null) "Tambahkan foto" else "Foto terlampir · ganti") } } }, confirmButton = { Button(onClick = { send(note, photo) }, enabled = note.length >= 5) { Text("Kirim laporan") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } })
}

@Composable private fun ReviewDialog(close: () -> Unit, send: (Int, String) -> Unit) { var rating by remember { mutableIntStateOf(5) }; var note by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, title = { Text("Ulasan pesanan") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Nilai pengalamanmu"); FlowChoices((1..5).toList(), rating, { "$it ★" }) { rating = it }; OutlinedTextField(note, { note = it }, label = { Text("Saran atau komentar") }, minLines = 3) } }, confirmButton = { Button(onClick = { send(rating, note) }) { Text("Kirim") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } }) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CookScreen(state: AppUiState, menuId: Int?, nav: NavController, vm: AppViewModel, fromPantry: Boolean = false) {
    val menu = state.catalog?.menus?.firstOrNull { it.id == menuId }
    var variant by remember(menu) { mutableStateOf(menu?.variants?.firstOrNull()) }
    var activeStep by remember(variant) { mutableIntStateOf(0) }
    val stepMinutes = cookingStepMinutes(variant)
    var seconds by remember(variant, activeStep) { mutableIntStateOf((stepMinutes.getOrNull(activeStep) ?: variant?.cook_minutes ?: 0) * 60) }
    val displayedSeconds by animateIntAsState(seconds, label = "timer")
    var running by remember { mutableStateOf(false) }
    var showPhoto by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { (variant?.instructions?.size ?: 1).coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { activeStep = pagerState.currentPage; running = false }
    LaunchedEffect(running, seconds) { if (running && seconds > 0) { delay(1000); seconds-- } else if (seconds == 0 && running) { running = false; if (activeStep < (variant?.instructions?.lastIndex ?: 0)) pagerState.animateScrollToPage(activeStep + 1) } }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Detail lauk", fontWeight = FontWeight.SemiBold) }, navigationIcon = { BackButton(nav::popBackStack) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = {
        Surface(color = WarmWhite, shadowElevation = 4.dp) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Harga per pack", color = Muted, fontSize = 12.sp); Text(rupiah(variant?.price ?: 0), fontSize = 21.sp, fontWeight = FontWeight.Bold) }
            if (fromPantry) Button(onClick = nav::popBackStack, shape = RoundedCornerShape(8.dp)) { Text("Kembali ke stok") } else Column(horizontalAlignment = Alignment.End) { Button(onClick = { val foundMenu = menu; val foundVariant = variant; if (foundMenu != null && foundVariant != null) { vm.buyMenuNow(foundMenu, foundVariant); nav.navigate("builder") } }, enabled = menu != null && variant != null, shape = RoundedCornerShape(8.dp)) { Text("Tambah ke paket") }; TextButton(onClick = { val foundMenu = menu; val foundVariant = variant; if (foundMenu != null && foundVariant != null) { vm.buyMenuNow(foundMenu, foundVariant); nav.navigate("builder") } }, enabled = menu != null && variant != null) { Text("Beli sekarang") } }
        } }
    }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            menu?.let { item { FoodPhoto(it, Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(10.dp)).clickable { showPhoto = true }) } }
            item { Text(menu?.name ?: "Menu", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text(menu?.description.orEmpty(), color = Muted); Spacer(Modifier.height(10.dp)); Text("${menu?.portion_label.orEmpty()} · ±${menu?.calories_per_portion ?: 0} kkal/porsi", color = Olive, fontWeight = FontWeight.Medium); FlowChoices(menu?.variants.orEmpty(), variant, { label(it.kind) }) { running = false; variant = it; activeStep = 0; seconds = (cookingStepMinutes(it).firstOrNull() ?: 0) * 60 } }
            menu?.let { item { ChoiceSection("Bahan & alergen") { Text(it.ingredients.joinToString(", "), color = Muted); Text(if (it.allergens.isEmpty()) "Tidak ada alergen yang tercatat" else "Alergen: ${it.allergens.joinToString(", ")}", fontSize = 13.sp, color = Brown) } } }
            item { Surface(shape = RoundedCornerShape(22.dp), color = LeafDark) { HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth(), pageSpacing = 12.dp, beyondViewportPageCount = 1) { page -> val pageMinutes = stepMinutes.getOrNull(page) ?: 0; Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text("Geser langkah · ${page + 1}/${variant?.instructions?.size ?: 0}", color = Beige, fontSize = 12.sp); Text(variant?.instructions?.getOrNull(page).orEmpty(), color = WarmWhite, fontWeight = FontWeight.Bold); if (pageMinutes > 0) Text("%02d:%02d".format(displayedSeconds / 60, displayedSeconds % 60), color = WarmWhite, fontSize = 42.sp, fontWeight = FontWeight.Black) else Text("Persiapan · tanpa timer", color = Beige, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { scope.launch { pagerState.animateScrollToPage((page - 1).coerceAtLeast(0)) } }, enabled = page > 0) { Text("‹") }; if (pageMinutes > 0) Button(onClick = { running = !running }, colors = ButtonDefaults.buttonColors(containerColor = if (running) Terracotta else Olive, contentColor = WarmWhite)) { Text(if (running) "Jeda" else "Mulai") }; OutlinedButton(onClick = { scope.launch { pagerState.animateScrollToPage((page + 1).coerceAtMost((variant?.instructions?.lastIndex ?: 0))) } }, enabled = page < (variant?.instructions?.lastIndex ?: 0)) { Text("›") } } } } } }
            item { ChoiceSection("Langkah memasak") { variant?.instructions?.forEachIndexed { index, instruction -> val minutes = stepMinutes.getOrNull(index) ?: 0; Surface(Modifier.fillMaxWidth().clickable { scope.launch { pagerState.animateScrollToPage(index) } }, color = if (index == activeStep) Beige else WarmWhite, shape = RoundedCornerShape(8.dp)) { Row(Modifier.padding(10.dp)) { Text("${index + 1}", color = Orange, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp)); Column { Text(instruction); Text(if (minutes > 0) "Timer $minutes menit" else "Langkah persiapan · tanpa timer", color = Muted, fontSize = 12.sp) } } } }; if (variant?.equipment?.isNotEmpty() == true) { Text("Peralatan", fontWeight = FontWeight.Black); Text(variant?.equipment?.joinToString().orEmpty(), color = Muted) }; Text("Petunjuk kematangan", fontWeight = FontWeight.Black); Text(variant?.doneness_guide.orEmpty(), color = Muted); Text("Cara penyimpanan", fontWeight = FontWeight.Black); Text(variant?.storage_guide.orEmpty(), color = Muted) } }
            item { Text("Petunjuk didasarkan pada data yang disetujui admin. Tanggal anjuran penggunaan bukan jaminan keamanan pangan.", color = Muted, fontSize = 12.sp) }
        }
    }
    if (showPhoto && menu != null) Dialog(onDismissRequest = { showPhoto = false }) { Surface(shape = RoundedCornerShape(16.dp), color = WarmWhite) { Column(Modifier.padding(12.dp)) { FoodPhoto(menu, Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(12.dp))); TextButton(onClick = { showPhoto = false }, Modifier.align(Alignment.End)) { Text("Tutup") } } } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun AdminScreen(state: AppUiState, nav: NavController, vm: AppViewModel, logout: () -> Unit) {
    val summary = state.adminSummary
    var tab by remember { mutableStateOf("Ringkasan") }
    var manageSection by remember { mutableStateOf("Produk") }
    var passwordDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var reportCsv by remember { mutableStateOf("") }
    var reportRequest by remember { mutableStateOf<Pair<String, String>?>(null) }
    val reportSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(reportCsv) } } }
    var orderDate by remember { mutableStateOf<String?>(null) }; var slotDate by remember { mutableStateOf<String?>(LocalDate.now().toString()) }; var courierHistoryDate by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<MenuDto?>(null) }; var deleting by remember { mutableStateOf<MenuDto?>(null) }
    var addMenu by remember { mutableStateOf(false) }; var addSlot by remember { mutableStateOf(false) }; var deletingSlot by remember { mutableStateOf<SlotDto?>(null) }; var editingSlot by remember { mutableStateOf<SlotDto?>(null) }
    var packageEditor by remember { mutableStateOf<PackageDto?>(null) }; var addPackage by remember { mutableStateOf(false) }; var deletingPackage by remember { mutableStateOf<PackageDto?>(null) }
    var messagePhoto by remember { mutableStateOf<String?>(null) }
    var showAllProducts by remember { mutableStateOf(false) }
    var chartPeriod by remember { mutableStateOf("Mingguan") }
    var paymentDecision by remember { mutableStateOf<Pair<OrderDto, String>?>(null) }
    val notifiedReady = remember { mutableStateListOf<Int>() }
    val orders = state.orders.filter { orderDate == null || it.created_at.take(10) == orderDate }; val slots = state.slots.filter { slotDate == null || it.date == slotDate }
    fun periodKey(raw: String) = when (chartPeriod) { "Bulanan" -> raw.take(7); "Tahunan" -> raw.take(4); else -> raw.take(10) }
    val seriesLimit = when (chartPeriod) { "Bulanan" -> 6; "Tahunan" -> 5; else -> 7 }
    val salesSeries = state.orders.groupingBy { periodKey(it.created_at) }.eachCount().toList().sortedBy { it.first }.takeLast(seriesLimit).let { actual -> if (actual.size >= 3 || summary?.demo_mode != true) actual else when (chartPeriod) { "Bulanan" -> listOf("Mei" to 18, "Jun" to 24, "Jul" to 21, "Agu" to 31, "Sep" to 29, "Okt" to 36); "Tahunan" -> listOf("2022" to 90, "2023" to 138, "2024" to 176, "2025" to 228, "2026" to 274); else -> listOf(2, 4, 3, 6, 5, 8, 7).mapIndexed { index, value -> LocalDate.now().minusDays((6 - index).toLong()).toString() to value } } }
    val incomeSeries = state.orders.filter { it.payment_status == "settlement" }.groupBy { periodKey(it.created_at) }.mapValues { entry -> entry.value.sumOf { it.total } }.toList().sortedBy { it.first }.takeLast(seriesLimit).let { actual -> if (actual.size >= 3 || summary?.demo_mode != true) actual else when (chartPeriod) { "Bulanan" -> listOf("Mei" to 2100000, "Jun" to 2850000, "Jul" to 2500000, "Agu" to 3600000, "Sep" to 3350000, "Okt" to 4100000); "Tahunan" -> listOf("2022" to 11200000, "2023" to 18400000, "2024" to 25700000, "2025" to 32600000, "2026" to 39800000); else -> listOf(180000, 340000, 265000, 510000, 430000, 720000, 610000).mapIndexed { index, value -> LocalDate.now().minusDays((6 - index).toLong()).toString() to value } } }
    val readyDeliveries = state.orders.flatMap { it.deliveries }.filter { it.status == "siap_dikirim" }
    LaunchedEffect(Unit) { while (true) { vm.loadAdmin(); delay(30_000) } }
    LaunchedEffect(state.orders) { state.orders.flatMap { it.deliveries }.filter { it.status == "siap_dikirim" && it.id !in notifiedReady }.forEach { delivery -> notifiedReady += delivery.id; notifyAdminReady(context, "Dapur menandai pengiriman #${delivery.id} siap diambil · ${delivery.date.orEmpty()} ${delivery.label.orEmpty()}") } }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { BrandMark() }, actions = { IconButton(onClick = { tab = "Profil" }) { Icon(painterResource(R.drawable.ic_profile), "Profil admin", Modifier.size(24.dp)) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = { NavigationBar(containerColor = WarmWhite) { listOf("Ringkasan", "Pesanan", "Kelola", "Profil").forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(painterResource(when(item) { "Ringkasan" -> R.drawable.ic_home; "Pesanan" -> R.drawable.ic_orders; "Kelola" -> R.drawable.ic_stock; else -> R.drawable.ic_profile }), contentDescription = item, Modifier.size(22.dp)) }, label = { Text(item, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Terracotta, selectedIconColor = WarmWhite, selectedTextColor = Terracotta)) } } }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(tab, fontSize = 28.sp, fontWeight = FontWeight.Black, color = LeafDark) }
            if (tab == "Kelola") item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Produk", "Pengiriman", "Masukan", "Pengguna").forEach { section -> Surface(Modifier.weight(1f).clickable { manageSection = section }, color = if (manageSection == section) Terracotta else Beige, shape = RoundedCornerShape(12.dp)) { Text(section, Modifier.padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = if (manageSection == section) WarmWhite else Brown, fontSize = 12.sp, fontWeight = FontWeight.Bold) } } } }
            if (tab == "Ringkasan") {
                if (readyDeliveries.isNotEmpty()) item { ChoiceSection("Siap diambil pengantar") { Text("${readyDeliveries.size} pengiriman sudah dilepas langsung oleh dapur setelah QC. Admin dapat memantau tanpa persetujuan tambahan.", color = Muted); readyDeliveries.forEach { delivery -> Text("#${delivery.id} · ${delivery.date ?: "-"} ${delivery.label.orEmpty()} · ${delivery.courier_name ?: "pengantar belum ditetapkan"}", fontSize = 13.sp, color = Brown) } } }
                item { ChoiceSection("Unduh laporan") { Text("Pilih laporan. Aplikasi akan meminta konfirmasi sebelum menyimpan berkas.", color = Muted); val today = LocalDate.now(); listOf("Harian" to ("pesanan-harian-$today.csv" to ordersCsv(state.orders.filter { it.created_at.take(10) == today.toString() })), "Mingguan" to ("pesanan-mingguan-$today.csv" to ordersCsv(state.orders.filter { runCatching { LocalDate.parse(it.created_at.take(10)) >= today.minusDays(6) }.getOrDefault(false) })), "Bulanan" to ("keuangan-bulanan-${today.toString().take(7)}.csv" to ordersCsv(state.orders.filter { it.created_at.take(7) == today.toString().take(7) })), "Pengantar" to ("laporan-pengantar-$today.csv" to couriersCsv(state.couriers, courierHistoryDate))).chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { (label, file) -> OutlinedButton(onClick = { reportRequest = file }, Modifier.weight(1f)) { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label) } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } } } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Pesanan", summary?.pesanan_masuk ?: 0, Modifier.weight(1f)); Metric("Verifikasi", summary?.perlu_verifikasi ?: 0, Modifier.weight(1f)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Produksi", summary?.perlu_diproduksi ?: 0, Modifier.weight(1f)); Metric("Siap kirim", summary?.siap_kirim ?: 0, Modifier.weight(1f)) } }
                item { Surface(shape = RoundedCornerShape(14.dp), color = LeafDark) { Column(Modifier.padding(20.dp).fillMaxWidth()) { Text("Omzet terkonfirmasi", color = Beige); Text(rupiah(summary?.pendapatan_terkonfirmasi ?: 0), color = WarmWhite, fontSize = 30.sp, fontWeight = FontWeight.Black); Text("${summary?.stok_menipis ?: 0} bahan mencapai stok minimum", color = Beige) } } }
                item { ChoiceSection("Catatan perhitungan") { Text(summary?.catatan ?: "Memuat data biaya…", color = Muted) } }
                item { ChoiceSection("Periode analisis") { FlowChoices(listOf("Mingguan", "Bulanan", "Tahunan"), chartPeriod, { it }) { chartPeriod = it } } }
                item { ChoiceSection("Grafik penjualan · $chartPeriod") { MiniColumnChart(salesSeries, Terracotta) { it.toString() } } }
                item { ChoiceSection("Grafik pemasukan · $chartPeriod") { MiniColumnChart(incomeSeries, Olive) { rupiah(it) } } }
                item { ChoiceSection("Cara menerima pesanan") { val deliveryCount = state.orders.count { it.delivery_method == "diantar" }; val pickupCount = state.orders.count { it.delivery_method == "ambil_sendiri" }; val maxValue = maxOf(deliveryCount, pickupCount, 1); listOf("Diantar" to deliveryCount, "Ambil sendiri" to pickupCount).forEach { (name, value) -> Text("$name · $value pesanan", fontWeight = FontWeight.Bold); Box(Modifier.fillMaxWidth(value.toFloat() / maxValue).height(22.dp).background(if (name == "Diantar") Terracotta else Olive, RoundedCornerShape(6.dp))) } } }
            }
            if (tab == "Pesanan") {
                item { Text("Pesanan", fontSize = 22.sp, fontWeight = FontWeight.Bold); DateFilter("Tanggal pemesanan", orderDate, allowAll = true) { orderDate = it } }
                if (orders.isEmpty()) item { Text("Tidak ada pesanan pada tanggal ini.", color = Muted) }
                items(orders) { order -> ChoiceSection(order.id) { Row { Text("${adminOrderStatus(order.status)} · pembayaran ${label(order.payment_status ?: "-")}", Modifier.weight(1f), color = Muted); Text(rupiah(order.total), fontWeight = FontWeight.Bold) }; Text("Status operasional mengikuti pembayaran, produksi, QC, dan serah terima pengantar.", color = Muted, fontSize = 12.sp); OutlinedButton(onClick = { nav.navigate("order/${order.id}") }, Modifier.fillMaxWidth()) { Text("Lihat detail operasional") }; if (order.status == "menunggu_pembayaran" && summary?.demo_mode == true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { paymentDecision = order to "sukses" }) { Text("Sukses") }; OutlinedButton(onClick = { paymentDecision = order to "gagal" }) { Text("Gagal") } } } }
            }
            if (tab == "Kelola" && manageSection == "Produk") {
                item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Produk", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Tambah, edit, atau arsipkan menu.", color = Muted) }; Button(onClick = { nav.navigate("admin-product/0") }) { Text("Tambah") } } }
                items(state.catalog?.menus.orEmpty().let { if (showAllProducts) it else it.take(6) }.chunked(2)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { menu -> Surface(Modifier.weight(1f), color = WarmWhite, border = BorderStroke(1.dp, Beige), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp).heightIn(min = 190.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(menu.name, fontWeight = FontWeight.Bold); Text(menu.category, color = Olive, fontSize = 12.sp); Text(menu.variants.joinToString { rupiah(it.price) }, fontSize = 12.sp); Spacer(Modifier.weight(1f)); OutlinedButton(onClick = { nav.navigate("admin-product/${menu.id}") }, Modifier.fillMaxWidth()) { Text("Edit") }; TextButton(onClick = { deleting = menu }, Modifier.fillMaxWidth()) { Text("Arsipkan", color = Terracotta) } } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } } }
                if ((state.catalog?.menus?.size ?: 0) > 6) item { OutlinedButton(onClick = { showAllProducts = !showAllProducts }, Modifier.fillMaxWidth()) { Text(if (showAllProducts) "Tampilkan lebih sedikit" else "Lihat semua produk (${state.catalog?.menus?.size})") } }
                if (state.archivedMenus.isNotEmpty()) item { Text("Arsip produk", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                items(state.archivedMenus) { menu -> ChoiceSection(menu.name) { Text(menu.category, color = Muted); Button(onClick = { vm.toggleMenu(menu, true) }) { Text("Aktifkan kembali") } } }
                item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Atur paket", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Tentukan jumlah pack dan diskon.", color = Muted) }; Button(onClick = { addPackage = true }) { Text("Tambah") } } }
                items(state.catalog?.packages.orEmpty().chunked(2)) { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { pack -> ChoiceSection(pack.name, Modifier.weight(1f)) { Text("${pack.duration_days} hari · ${pack.base_packs} lauk", fontWeight = FontWeight.Bold); Text("Diskon ${pack.discount_percent.toInt()}%", color = Olive); Row { OutlinedButton(onClick = { packageEditor = pack }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Edit") }; TextButton(onClick = { deletingPackage = pack }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Arsip", color = Terracotta) } } } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
            }
            if (tab == "Kelola" && manageSection == "Pengiriman") {
                item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Pengiriman", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Jumlah pesanan terlihat per tanggal dan jam.", color = Muted) }; Button(onClick = { addSlot = true }) { Text("Tambah") } } }
                item { AdminSlotCalendar(state.slots, slotDate) { slotDate = it } }
                items(slots) { slot -> val deliveries = state.orders.flatMap { it.deliveries }.filter { it.slot_id == slot.id }; ChoiceSection("${slot.date} · ${slot.label}") { Text("${deliveries.size} pengiriman · ${slot.reserved} pack", fontWeight = FontWeight.Bold); Text("Kapasitas ${slot.capacity} · ongkir ${rupiah(slot.shipping_fee)} · sisa ${slot.remaining}", color = Muted); Row { OutlinedButton(onClick = { editingSlot = slot }) { Text("Edit slot") }; if (slot.reserved == 0) TextButton(onClick = { deletingSlot = slot }) { Text("Hapus", color = Terracotta) } }; deliveries.forEach { delivery -> Text("Pengiriman #${delivery.id} · ${label(delivery.status)} · ${delivery.courier_name ?: "belum ada pengantar"}", fontSize = 13.sp); if (delivery.courier_name == null) FlowChoices(state.couriers, null, { it.name }) { vm.assignCourier(delivery.id, it.id) } } } }
                item { Text("5 riwayat pengantar terakhir", fontSize = 20.sp, fontWeight = FontWeight.Bold); DateFilter("Filter tanggal riwayat", courierHistoryDate, allowAll = true) { courierHistoryDate = it }; OutlinedButton(onClick = { val today = LocalDate.now(); reportRequest = "riwayat-pengantar-$today.csv" to couriersCsv(state.couriers, courierHistoryDate) }, Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Unduh riwayat lebih lengkap") } }
                items(state.couriers) { courier -> ChoiceSection(courier.name) { Text(courier.phone, color = Muted); Text("${courier.active_count} aktif · ${courier.completed_count} selesai", color = Olive, fontWeight = FontWeight.Bold); val historyRows = courier.deliveries.filter { courierHistoryDate == null || it.date == courierHistoryDate }.take(5); if (historyRows.isEmpty()) Text("Tidak ada riwayat pada tanggal ini.", color = Muted); historyRows.forEach { history -> Text("${history.date ?: "-"} · ${history.order_id} · ${label(history.status)} · foto ${if (history.has_before_photo) "ambil ✓" else "ambil –"}/${if (history.has_arrival_photo) "tiba ✓" else "tiba –"}", fontSize = 12.sp, color = Muted) } } }
            }
            if (tab == "Kelola" && manageSection == "Masukan") { item { Text("Masukan customer", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Komplain pengiriman, masalah stok, dan saran umum.", color = Muted) }; if (state.adminMessages.isEmpty()) item { Text("Belum ada pesan.", color = Muted) }; items(state.adminMessages) { message -> ChoiceSection("${label(message.kind)} · ${message.customer}") { Text(message.message); Text("${message.created_at.take(16).replace('T', ' ')} · ${message.order_id ?: "tanpa pesanan"}", color = Muted, fontSize = 12.sp); message.photo_data?.let { OutlinedButton(onClick = { messagePhoto = it }) { Text("Lihat foto") } } } } }
            if (tab == "Kelola" && manageSection == "Pengguna") { item { Text("Data pelanggan & pengantar", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Data dapat dilihat langsung tanpa mengunduh laporan.", color = Muted) }; items(state.adminUsers) { account -> ChoiceSection(account.name) { Text(label(account.role), color = Terracotta, fontWeight = FontWeight.Bold); Text(account.email); Text(account.phone.ifBlank { "Nomor belum diisi" }, color = Muted); Text(if (account.role == "pelanggan") "${account.order_count} pesanan" else "${account.delivery_count} tugas pengantaran", color = Olive, fontWeight = FontWeight.Bold) } } }
            if (tab == "Profil") item { ChoiceSection("Profil admin") { Text(state.user?.name.orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Kelola keamanan akun admin.", color = Muted); Button(onClick = { passwordDialog = true }, Modifier.fillMaxWidth()) { Text("Ubah kata sandi") }; OutlinedButton(onClick = logout, Modifier.fillMaxWidth()) { Text("Keluar") } } }
        }
    }
    editing?.let { menu -> MenuEditDialog(menu, { editing = null }) { request -> vm.editMenu(menu, request); editing = null } }
    deleting?.let { menu -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Arsipkan ${menu.name}?") }, text = { Text("Menu tidak akan muncul lagi untuk customer. Pesanan lama tetap tersimpan.") }, confirmButton = { Button(onClick = { vm.deleteMenu(menu); deleting = null }) { Text("Arsipkan") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } }) }
    if (addMenu) MenuAddDialog({ addMenu = false }) { request -> vm.addMenu(request); addMenu = false }
    if (addSlot) SlotAddDialog(slotDate, { addSlot = false }) { request -> vm.addSlot(request); addSlot = false }
    deletingSlot?.let { slot -> AlertDialog(onDismissRequest = { deletingSlot = null }, title = { Text("Hapus jadwal?") }, text = { Text("${slot.date} · ${slot.label}") }, confirmButton = { Button(onClick = { vm.deleteSlot(slot); deletingSlot = null }) { Text("Hapus") } }, dismissButton = { TextButton(onClick = { deletingSlot = null }) { Text("Batal") } }) }
    editingSlot?.let { slot -> SlotEditDialog(slot, { editingSlot = null }) { capacity, fee -> vm.editSlot(slot, capacity, fee); editingSlot = null } }
    if (addPackage) PackageDialog(null, { addPackage = false }) { vm.savePackage(null, it); addPackage = false }
    packageEditor?.let { pack -> PackageDialog(pack, { packageEditor = null }) { vm.savePackage(pack, it); packageEditor = null } }
    deletingPackage?.let { pack -> AlertDialog(onDismissRequest = { deletingPackage = null }, title = { Text("Arsipkan ${pack.name}?") }, text = { Text("Paket tidak dapat dipilih untuk pesanan baru.") }, confirmButton = { Button(onClick = { vm.deletePackage(pack); deletingPackage = null }) { Text("Arsipkan") } }, dismissButton = { TextButton(onClick = { deletingPackage = null }) { Text("Batal") } }) }
    if (passwordDialog) PasswordDialog({ passwordDialog = false }) { old, fresh -> vm.changePassword(old, fresh) { passwordDialog = false } }
    paymentDecision?.let { (order, decision) -> AlertDialog(onDismissRequest = { paymentDecision = null }, title = { Text(if (decision == "sukses") "Konfirmasi pembayaran sukses?" else "Konfirmasi pembayaran gagal?") }, text = { Text("Status pembayaran ${order.id} akan diubah menjadi ${if (decision == "sukses") "berhasil dan pesanan masuk ke produksi" else "gagal"}. Periksa kembali sebelum melanjutkan.") }, confirmButton = { Button(onClick = { vm.simulatePayment(order.id, decision); paymentDecision = null }) { Text("Ya, ubah status") } }, dismissButton = { TextButton(onClick = { paymentDecision = null }) { Text("Batal") } }) }
    reportRequest?.let { (filename, csv) -> AlertDialog(onDismissRequest = { reportRequest = null }, title = { Text("Unduh laporan?") }, text = { Text("Berkas $filename akan disimpan ke lokasi yang kamu pilih.") }, confirmButton = { Button(onClick = { reportCsv = csv; reportSaver.launch(filename); reportRequest = null }) { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Pilih lokasi") } }, dismissButton = { TextButton(onClick = { reportRequest = null }) { Text("Batal") } }) }
    messagePhoto?.let { encoded -> val bitmap = remember(encoded) { runCatching { val bytes = Base64.decode(encoded.substringAfter("base64,"), Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }; Dialog(onDismissRequest = { messagePhoto = null }) { Surface(shape = RoundedCornerShape(16.dp), color = WarmWhite) { Column(Modifier.padding(12.dp)) { bitmap?.let { Image(it, null, Modifier.fillMaxWidth().heightIn(max = 500.dp), contentScale = ContentScale.Fit) } ?: Text("Foto tidak dapat dibuka"); TextButton(onClick = { messagePhoto = null }, Modifier.align(Alignment.End)) { Text("Tutup") } } } } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun AdminProductEditorScreen(state: AppUiState, menuId: Int, nav: NavController, vm: AppViewModel) {
    val existing = state.catalog?.menus?.firstOrNull { it.id == menuId }
    val cook = existing?.variants?.firstOrNull { it.kind == "siap_masak" }
    val ready = existing?.variants?.firstOrNull { it.kind == "siap_makan" }
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }; var description by remember(existing) { mutableStateOf(existing?.description.orEmpty()) }; var ingredients by remember(existing) { mutableStateOf(existing?.ingredients?.joinToString(", ").orEmpty()) }; var allergens by remember(existing) { mutableStateOf(existing?.allergens?.joinToString(", ").orEmpty()) }; var portion by remember(existing) { mutableStateOf(existing?.portion_label ?: "1–2 porsi") }; var calories by remember(existing) { mutableStateOf((existing?.calories_per_portion ?: 350).toString()) }; var category by remember(existing) { mutableStateOf(existing?.category ?: "Lauk utama") }; var cookPrice by remember(existing) { mutableStateOf(cook?.price?.toString().orEmpty()) }; var readyPrice by remember(existing) { mutableStateOf(ready?.price?.toString().orEmpty()) }; var imageData by remember(existing) { mutableStateOf(existing?.image_url) }; var storage by remember(existing) { mutableStateOf(cook?.storage_guide ?: "Simpan sesuai petunjuk pada label.") }; var doneness by remember(existing) { mutableStateOf(cook?.doneness_guide ?: "Pastikan bagian tengah lauk panas dan matang merata.") }; var confirm by remember { mutableStateOf(false) }
    val defaultSteps = listOf("Siapkan dan potong bahan tambahan yang diperlukan." to "2", "Panaskan alat masak dan masukkan lauk beserta bumbu." to "3", "Masak sambil diaduk hingga matang merata." to "10")
    val cookSteps = remember(existing) { mutableStateListOf<Pair<String, String>>().apply { val source = cook?.instructions?.mapIndexed { index, text -> text to (cook.step_minutes.getOrNull(index)?.takeIf { it > 0 }?.toString().orEmpty()) } ?: defaultSteps; addAll(source) } }
    val readySteps = remember(existing) { mutableStateListOf<Pair<String, String>>().apply { val source = ready?.instructions?.mapIndexed { index, text -> text to (ready.step_minutes.getOrNull(index)?.takeIf { it > 0 }?.toString().orEmpty()) } ?: listOf("Buka kemasan sesuai petunjuk." to "", "Panaskan dan sajikan." to "5"); addAll(source) } }
    val equipment = remember(existing) { mutableStateListOf<String>().apply { addAll((cook?.equipment ?: listOf("wajan", "spatula")).ifEmpty { listOf("") }) } }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { runCatching { context.contentResolver.openInputStream(it)?.use { stream -> Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP) } }.getOrNull()?.let { encoded -> imageData = encoded } } }
    val errors = buildList { if (imageData.isNullOrBlank()) add("Foto produk wajib dipilih"); if (name.length < 2) add("Nama menu minimal 2 karakter"); if (description.length < 3) add("Deskripsi belum lengkap"); if (ingredients.isBlank()) add("Bahan wajib diisi"); if (portion.isBlank()) add("Label porsi wajib diisi"); if ((calories.toIntOrNull() ?: 0) !in 50..3000) add("Kalori harus 50–3000"); if ((cookPrice.toIntOrNull() ?: 0) < 1000) add("Harga siap masak belum benar"); if ((readyPrice.toIntOrNull() ?: 0) < 1000) add("Harga siap makan belum benar"); if (cookSteps.any { it.first.isBlank() }) add("Semua penjelasan langkah siap masak wajib diisi"); if (readySteps.any { it.first.isBlank() }) add("Semua penjelasan langkah siap makan wajib diisi"); if (equipment.any { it.isBlank() }) add("Nama peralatan tidak boleh kosong") }
    fun stepMinutes(rows: List<Pair<String, String>>) = rows.map { it.second.toIntOrNull() ?: 0 }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text(if (existing == null) "Tambah produk" else "Edit produk", fontWeight = FontWeight.Black) }, navigationIcon = { BackButton(nav::popBackStack) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = { Surface(color = WarmWhite, shadowElevation = 6.dp) { Button(onClick = { confirm = true }, Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(52.dp), enabled = errors.isEmpty()) { Text("Tinjau & simpan") } } }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { imageData?.let { encoded -> val bitmap = remember(encoded) { runCatching { val bytes = Base64.decode(encoded.substringAfter("base64,"), Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }; bitmap?.let { Image(it, "Foto produk", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) } }; OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Text(if (imageData == null) "Pilih foto produk" else "Ganti foto produk") } }
            item { ChoiceSection("Informasi produk") { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nama menu") }); OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Deskripsi") }, minLines = 3); FlowChoices(listOf("Lauk utama", "Sayur", "Pendamping", "Protein"), category, { it }) { category = it }; OutlinedTextField(ingredients, { ingredients = it }, Modifier.fillMaxWidth(), label = { Text("Bahan, pisahkan koma") }); OutlinedTextField(allergens, { allergens = it }, Modifier.fillMaxWidth(), label = { Text("Alergen, pisahkan koma") }); OutlinedTextField(portion, { portion = it }, Modifier.fillMaxWidth(), label = { Text("Label porsi") }); OutlinedTextField(calories, { calories = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Kalori per porsi") }) } }
            item { ChoiceSection("Harga varian") { Text("Harga tetap terlihat sebelum mengatur langkah memasak.", color = Muted, fontSize = 12.sp); OutlinedTextField(cookPrice, { cookPrice = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Harga siap masak") }, prefix = { Text("Rp") }); OutlinedTextField(readyPrice, { readyPrice = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Harga siap makan") }, prefix = { Text("Rp") }) } }
            item { DynamicStepsEditor("Langkah siap masak", cookSteps) }
            item { DynamicStepsEditor("Langkah siap makan", readySteps) }
            item { ChoiceSection("Peralatan") { equipment.forEachIndexed { index, value -> Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value, { equipment[index] = it }, Modifier.weight(1f), label = { Text("Peralatan ${index + 1}") }); IconButton(onClick = { if (equipment.size > 1) equipment.removeAt(index) }) { Text("×", color = Terracotta, fontSize = 24.sp) } } }; OutlinedButton(onClick = { equipment.add("") }, Modifier.fillMaxWidth()) { Text("+ Tambah peralatan") } } }
            item { ChoiceSection("Penyimpanan & kematangan") { OutlinedTextField(storage, { storage = it }, Modifier.fillMaxWidth(), label = { Text("Cara penyimpanan") }, minLines = 2); OutlinedTextField(doneness, { doneness = it }, Modifier.fillMaxWidth(), label = { Text("Tanda matang/siap") }, minLines = 2) } }
            if (errors.isNotEmpty()) item { Surface(color = Beige, shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(14.dp)) { Text("Lengkapi bagian berikut:", color = Terracotta, fontWeight = FontWeight.Bold); errors.forEach { Text("• $it", color = Brown, fontSize = 13.sp) } } } }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Simpan produk?") }, text = { Text("$name · ${cookSteps.size} langkah siap masak · ${readySteps.size} langkah siap makan. Durasi kosong disimpan tanpa timer.") }, confirmButton = { Button(onClick = { val ingredientList = ingredients.split(',').map { it.trim() }.filter { it.isNotBlank() }; val allergenList = allergens.split(',').map { it.trim() }.filter { it.isNotBlank() }; val tools = equipment.filter { it.isNotBlank() }; if (existing == null) vm.addMenu(MenuCreateRequest(name, description, ingredientList, allergenList, portion, cookPrice.toInt(), readyPrice.toInt(), calories.toInt(), category, imageData, cookSteps.map { it.first }, stepMinutes(cookSteps), readySteps.map { it.first }, stepMinutes(readySteps), tools, storage, doneness)) else vm.editMenu(existing, MenuFullUpdateRequest(name, description, ingredientList, allergenList, portion, calories.toInt(), category, imageData, listOf(VariantAdminRequest(cook!!.id, cookPrice.toInt(), cookSteps.map { it.first }, stepMinutes(cookSteps), tools, storage, doneness), VariantAdminRequest(ready!!.id, readyPrice.toInt(), readySteps.map { it.first }, stepMinutes(readySteps), tools, storage, doneness)))); confirm = false; nav.popBackStack() }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Periksa lagi") } })
}

@Composable private fun DynamicStepsEditor(title: String, rows: SnapshotStateList<Pair<String, String>>) {
    ChoiceSection(title) { Text("Penjelasan wajib diisi. Durasi per langkah boleh dikosongkan.", color = Muted, fontSize = 12.sp); rows.forEachIndexed { index, row -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(row.first, { rows[index] = it to row.second }, Modifier.weight(1f), label = { Text("Langkah ${index + 1}") }, minLines = 2); OutlinedTextField(row.second, { rows[index] = row.first to it.filter(Char::isDigit) }, Modifier.width(82.dp), label = { Text("Menit") }); IconButton(onClick = { if (rows.size > 1) rows.removeAt(index) }) { Text("×", color = Terracotta, fontSize = 24.sp) } }; }; OutlinedButton(onClick = { rows.add("" to "") }, Modifier.fillMaxWidth()) { Text("+ Tambah langkah") } }
}

private fun ordersCsv(orders: List<OrderDto>): String = buildString { appendLine("id,tanggal,status,pembayaran,total,pengiriman,pengantar"); orders.forEach { order -> appendLine(listOf(order.id, order.created_at.take(10), order.status, order.payment_status.orEmpty(), order.total, order.deliveries.joinToString("|") { it.date.orEmpty() }, order.deliveries.joinToString("|") { it.courier_name.orEmpty() }).joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" }) } }
private fun couriersCsv(couriers: List<CourierDto>, date: String? = null): String = buildString { appendLine("nama,telepon,aktif,selesai,riwayat"); couriers.forEach { courier -> appendLine(listOf(courier.name, courier.phone, courier.active_count, courier.completed_count, courier.deliveries.filter { date == null || it.date == date }.joinToString("|") { "${it.date}:${it.order_id}:${it.status}" }).joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" }) } }

@Composable private fun AdminSlotCalendar(slots: List<SlotDto>, selectedDate: String?, choose: (String) -> Unit) {
    var month by remember(selectedDate) { mutableStateOf(runCatching { YearMonth.from(LocalDate.parse(selectedDate)) }.getOrDefault(YearMonth.now())) }
    val slotCounts = slots.groupingBy { it.date }.eachCount()
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val cells = List(firstOffset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    ChoiceSection("Kalender slot") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("id-ID"))), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
        }
        Row(Modifier.fillMaxWidth()) { listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp, color = Muted) } }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (week + List(7 - week.size) { null }).forEach { day ->
                    if (day == null) Spacer(Modifier.weight(1f).height(48.dp)) else {
                        val key = day.toString(); val selected = key == selectedDate; val count = slotCounts[key] ?: 0
                        Surface(Modifier.weight(1f).height(48.dp).clickable { choose(key) }, color = if (selected) Terracotta else if (count > 0) Beige else WarmWhite, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (selected) Terracotta else Beige)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(day.dayOfMonth.toString(), color = if (selected) WarmWhite else Brown, fontWeight = FontWeight.Bold); if (count > 0) Text("$count slot", color = if (selected) WarmWhite else Olive, fontSize = 9.sp) }
                        }
                    }
                }
            }
        }
        Text(if (selectedDate == null) "Pilih tanggal untuk melihat atau menambah slot." else "Tanggal dipilih: ${runCatching { LocalDate.parse(selectedDate).format(dateFormatter) }.getOrDefault(selectedDate)}", color = Muted, fontSize = 12.sp)
    }
}

@Composable private fun SlotEditDialog(slot: SlotDto, close: () -> Unit, save: (Int, Int) -> Unit) { var capacity by remember { mutableStateOf(slot.capacity.toString()) }; var fee by remember { mutableStateOf(slot.shipping_fee.toString()) }; AlertDialog(onDismissRequest = close, title = { Text("Edit slot pengiriman") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${slot.date} · ${slot.label}"); OutlinedTextField(capacity, { capacity = it.filter(Char::isDigit) }, label = { Text("Kapasitas") }); OutlinedTextField(fee, { fee = it.filter(Char::isDigit) }, label = { Text("Ongkir") }, prefix = { Text("Rp") }); Text("Sudah terisi ${slot.reserved} pack", color = Muted) } }, confirmButton = { Button(onClick = { save(capacity.toIntOrNull() ?: 0, fee.toIntOrNull() ?: 0) }, enabled = (capacity.toIntOrNull() ?: 0) >= slot.reserved) { Text("Simpan") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } }) }

@Composable private fun PasswordDialog(close: () -> Unit, save: (String, String) -> Unit) { var old by remember { mutableStateOf("") }; var fresh by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, title = { Text("Ubah kata sandi") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(old, { old = it }, label = { Text("Kata sandi lama") }, visualTransformation = PasswordVisualTransformation()); OutlinedTextField(fresh, { fresh = it }, label = { Text("Kata sandi baru") }, visualTransformation = PasswordVisualTransformation()); Text("Minimal 6 karakter.", color = Muted, fontSize = 12.sp) } }, confirmButton = { Button(onClick = { save(old, fresh) }, enabled = old.isNotBlank() && fresh.length >= 6) { Text("Simpan") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } }) }

@Composable private fun MenuEditDialog(menu: MenuDto, close: () -> Unit, save: (MenuFullUpdateRequest) -> Unit) {
    val context = LocalContext.current; val cook = menu.variants.firstOrNull { it.kind == "siap_masak" } ?: menu.variants.first(); val eat = menu.variants.firstOrNull { it.kind == "siap_makan" } ?: menu.variants.last()
    var name by remember { mutableStateOf(menu.name) }; var desc by remember { mutableStateOf(menu.description) }; var ingredients by remember { mutableStateOf(menu.ingredients.joinToString(", ")) }; var allergens by remember { mutableStateOf(menu.allergens.joinToString(", ")) }; var portion by remember { mutableStateOf(menu.portion_label) }; var calories by remember { mutableStateOf(menu.calories_per_portion.toString()) }; var category by remember { mutableStateOf(menu.category) }; var image by remember { mutableStateOf<String?>(menu.image_url) }
    var cookPrice by remember { mutableStateOf(cook.price.toString()) }; var cookSteps by remember { mutableStateOf(cook.instructions.joinToString("\n")) }; var cookMinutes by remember { mutableStateOf(cook.step_minutes.joinToString(",")) }; var cookEquipment by remember { mutableStateOf(cook.equipment.joinToString(", ")) }; var cookStorage by remember { mutableStateOf(cook.storage_guide) }; var cookDone by remember { mutableStateOf(cook.doneness_guide) }
    var eatPrice by remember { mutableStateOf(eat.price.toString()) }; var eatSteps by remember { mutableStateOf(eat.instructions.joinToString("\n")) }; var eatMinutes by remember { mutableStateOf(eat.step_minutes.joinToString(",")) }; var eatEquipment by remember { mutableStateOf(eat.equipment.joinToString(", ")) }; var eatStorage by remember { mutableStateOf(eat.storage_guide) }; var eatDone by remember { mutableStateOf(eat.doneness_guide) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { runCatching { context.contentResolver.openInputStream(it)?.use { stream -> Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP) } }.getOrNull()?.let { encoded -> image = encoded } } }
    val cookStepList = cookSteps.lines().filter { it.isNotBlank() }; val cookMinuteList = cookMinutes.split(',').mapNotNull { it.trim().toIntOrNull() }; val eatStepList = eatSteps.lines().filter { it.isNotBlank() }; val eatMinuteList = eatMinutes.split(',').mapNotNull { it.trim().toIntOrNull() }
    AlertDialog(onDismissRequest = close, title = { Text("Edit seluruh produk") }, text = { Column(Modifier.heightIn(max = 570.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Nama") }); OutlinedTextField(desc, { desc = it }, label = { Text("Deskripsi") }); FlowChoices(listOf("Lauk utama", "Sayur", "Pendamping", "Protein"), category, { it }) { category = it }; OutlinedTextField(ingredients, { ingredients = it }, label = { Text("Bahan, pisahkan koma") }); OutlinedTextField(allergens, { allergens = it }, label = { Text("Alergen, pisahkan koma") }); OutlinedTextField(portion, { portion = it }, label = { Text("Label porsi") }); OutlinedTextField(calories, { calories = it.filter(Char::isDigit) }, label = { Text("Kalori per porsi") }); Text("Varian siap masak", fontWeight = FontWeight.Black, color = Terracotta); OutlinedTextField(cookPrice, { cookPrice = it.filter(Char::isDigit) }, label = { Text("Harga") }); OutlinedTextField(cookSteps, { cookSteps = it }, label = { Text("Langkah, satu per baris") }, minLines = 3); OutlinedTextField(cookMinutes, { cookMinutes = it }, label = { Text("Menit tiap langkah") }); OutlinedTextField(cookEquipment, { cookEquipment = it }, label = { Text("Peralatan") }); OutlinedTextField(cookStorage, { cookStorage = it }, label = { Text("Cara simpan") }); OutlinedTextField(cookDone, { cookDone = it }, label = { Text("Tanda matang") }); Text("Varian siap makan", fontWeight = FontWeight.Black, color = Terracotta); OutlinedTextField(eatPrice, { eatPrice = it.filter(Char::isDigit) }, label = { Text("Harga") }); OutlinedTextField(eatSteps, { eatSteps = it }, label = { Text("Langkah penyajian") }, minLines = 2); OutlinedTextField(eatMinutes, { eatMinutes = it }, label = { Text("Menit tiap langkah") }); OutlinedTextField(eatEquipment, { eatEquipment = it }, label = { Text("Peralatan") }); OutlinedTextField(eatStorage, { eatStorage = it }, label = { Text("Cara simpan") }); OutlinedTextField(eatDone, { eatDone = it }, label = { Text("Tanda siap") }); OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Ganti foto") } } }, confirmButton = { Button(onClick = { save(MenuFullUpdateRequest(name, desc, ingredients.split(',').map { it.trim() }.filter { it.isNotBlank() }, allergens.split(',').map { it.trim() }.filter { it.isNotBlank() }, portion, calories.toIntOrNull() ?: 0, category, image, listOf(VariantAdminRequest(cook.id, cookPrice.toIntOrNull() ?: 0, cookStepList, cookMinuteList, cookEquipment.split(',').map { it.trim() }.filter { it.isNotBlank() }, cookStorage, cookDone), VariantAdminRequest(eat.id, eatPrice.toIntOrNull() ?: 0, eatStepList, eatMinuteList, eatEquipment.split(',').map { it.trim() }.filter { it.isNotBlank() }, eatStorage, eatDone)))) }, enabled = name.length >= 2 && desc.length >= 3 && portion.isNotBlank() && (calories.toIntOrNull() ?: 0) > 0 && (cookPrice.toIntOrNull() ?: 0) >= 1000 && (eatPrice.toIntOrNull() ?: 0) >= 1000 && cookStepList.isNotEmpty() && cookStepList.size == cookMinuteList.size && eatStepList.isNotEmpty() && eatStepList.size == eatMinuteList.size) { Text("Simpan semua") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } })
}

@Composable private fun MenuAddDialog(close: () -> Unit, save: (MenuCreateRequest) -> Unit) {
    val context = LocalContext.current; var name by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; var ingredients by remember { mutableStateOf("") }; var allergens by remember { mutableStateOf("") }; var portion by remember { mutableStateOf("1–2 porsi") }; var cookPrice by remember { mutableStateOf("") }; var eatPrice by remember { mutableStateOf("") }; var calories by remember { mutableStateOf("350") }; var category by remember { mutableStateOf("Lauk utama") }; var steps by remember { mutableStateOf("Panaskan lauk\nMasak hingga matang\nSajikan") }; var minutes by remember { mutableStateOf("3,10,2") }; var eatSteps by remember { mutableStateOf("Panaskan sesuai petunjuk\nSajikan") }; var eatMinutes by remember { mutableStateOf("4,1") }; var equipment by remember { mutableStateOf("wajan, spatula") }; var storage by remember { mutableStateOf("Simpan di chiller atau freezer sesuai label.") }; var doneness by remember { mutableStateOf("Pastikan panas merata hingga bagian tengah.") }; var image by remember { mutableStateOf<String?>(null) }; var confirm by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { runCatching { context.contentResolver.openInputStream(it)?.use { stream -> Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP) } }.getOrNull()?.let { encoded -> image = encoded } } }
    val request = MenuCreateRequest(name, desc, ingredients.split(',').map { it.trim() }.filter { it.isNotEmpty() }, allergens.split(',').map { it.trim() }.filter { it.isNotEmpty() }, portion, cookPrice.toIntOrNull() ?: 0, eatPrice.toIntOrNull() ?: 0, calories.toIntOrNull() ?: 350, category, image, steps.lines().filter { it.isNotBlank() }, minutes.split(',').mapNotNull { it.trim().toIntOrNull() }, eatSteps.lines().filter { it.isNotBlank() }, eatMinutes.split(',').mapNotNull { it.trim().toIntOrNull() }, equipment.split(',').map { it.trim() }.filter { it.isNotBlank() }, storage, doneness)
    AlertDialog(onDismissRequest = close, title = { Text("Tambah produk") }, text = { Column(Modifier.heightIn(max = 560.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Nama menu") }); OutlinedTextField(desc, { desc = it }, label = { Text("Deskripsi") }); FlowChoices(listOf("Lauk utama", "Sayur", "Pendamping", "Protein"), category, { it }) { category = it }; OutlinedTextField(ingredients, { ingredients = it }, label = { Text("Bahan, pisahkan koma") }); OutlinedTextField(allergens, { allergens = it }, label = { Text("Alergen, pisahkan koma") }); OutlinedTextField(portion, { portion = it }, label = { Text("Label porsi") }); OutlinedTextField(calories, { calories = it.filter(Char::isDigit) }, label = { Text("Kalori per porsi") }); OutlinedTextField(cookPrice, { cookPrice = it.filter(Char::isDigit) }, label = { Text("Harga siap masak") }); OutlinedTextField(steps, { steps = it }, label = { Text("Langkah siap masak") }, minLines = 3); OutlinedTextField(minutes, { minutes = it }, label = { Text("Timer siap masak") }); OutlinedTextField(eatPrice, { eatPrice = it.filter(Char::isDigit) }, label = { Text("Harga siap makan") }); OutlinedTextField(eatSteps, { eatSteps = it }, label = { Text("Langkah siap makan") }, minLines = 2); OutlinedTextField(eatMinutes, { eatMinutes = it }, label = { Text("Timer siap makan") }); OutlinedTextField(equipment, { equipment = it }, label = { Text("Peralatan, pisahkan koma") }); OutlinedTextField(storage, { storage = it }, label = { Text("Cara penyimpanan") }); OutlinedTextField(doneness, { doneness = it }, label = { Text("Tanda kematangan") }); OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (image == null) "Pilih foto produk" else "Foto terpilih · ganti") } } }, confirmButton = { Button(onClick = { confirm = true }, enabled = name.length >= 2 && desc.length >= 3 && portion.isNotBlank() && (cookPrice.toIntOrNull() ?: 0) >= 1000 && (eatPrice.toIntOrNull() ?: 0) >= 1000 && request.instructions.size == request.step_minutes.size && request.ready_to_eat_instructions.size == request.ready_to_eat_step_minutes.size) { Text("Tinjau") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Tambahkan $name?") }, text = { Text("Kategori $category · ${request.instructions.size} langkah · siap masak ${rupiah(request.ready_to_cook_price)} · siap makan ${rupiah(request.ready_to_eat_price)}") }, confirmButton = { Button(onClick = { save(request); confirm = false }) { Text("Ya, tambahkan") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Periksa lagi") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SlotAddDialog(initialDate: String?, close: () -> Unit, save: (SlotCreateRequest) -> Unit) { var date by remember { mutableStateOf(initialDate?.takeIf { runCatching { LocalDate.parse(it) >= LocalDate.now() }.getOrDefault(false) } ?: LocalDate.now().plusDays(1).toString()) }; var time by remember { mutableStateOf("09.00–12.00") }; var capacity by remember { mutableStateOf("30") }; var fee by remember { mutableStateOf("12000") }; AlertDialog(onDismissRequest = close, title = { Text("Tambah jadwal") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { DateFilter("Tanggal pengiriman", date, futureOnly = true) { if (it != null) date = it }; OutlinedTextField(time, { time = it }, label = { Text("Jam") }); OutlinedTextField(capacity, { capacity = it.filter(Char::isDigit) }, label = { Text("Kapasitas") }); OutlinedTextField(fee, { fee = it.filter(Char::isDigit) }, label = { Text("Ongkir") }) } }, confirmButton = { Button(onClick = { save(SlotCreateRequest(date, time, capacity.toIntOrNull() ?: 0, fee.toIntOrNull() ?: 0)) }, enabled = capacity.toIntOrNull()?.let { it > 0 } == true) { Text("Tambah") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } }) }

@Composable private fun PackageDialog(existing: PackageDto?, close: () -> Unit, save: (PackageManageRequest) -> Unit) { var name by remember { mutableStateOf(existing?.name.orEmpty()) }; var days by remember { mutableStateOf(existing?.duration_days?.toString() ?: "3") }; var discount by remember { mutableStateOf(existing?.discount_percent?.toInt()?.toString() ?: "0") }; AlertDialog(onDismissRequest = close, title = { Text(if (existing == null) "Tambah paket" else "Edit paket") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Nama paket") }); OutlinedTextField(days, { days = it.filter(Char::isDigit) }, label = { Text("Durasi hari") }); Text("Customer dapat memilih 1–3 kali makan per hari. Jumlah lauk dihitung otomatis.", color = Muted, fontSize = 13.sp); OutlinedTextField(discount, { discount = it.filter(Char::isDigit) }, label = { Text("Diskon (%)") }, supportingText = { Text("0–90%. Diskon 100% tidak diizinkan.") }) } }, confirmButton = { Button(onClick = { val duration = days.toIntOrNull() ?: 1; save(PackageManageRequest(name, duration, (duration * 3).coerceAtMost(100), (duration * 2).coerceAtMost(100), discount.toDoubleOrNull() ?: 0.0, duration)) }, enabled = name.length >= 2 && (days.toIntOrNull() ?: 0) in 1..31 && (discount.toIntOrNull() ?: -1) in 0..90) { Text("Simpan") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } }) }

@Composable private fun Metric(title: String, value: Int, modifier: Modifier) { Surface(modifier, shape = RoundedCornerShape(18.dp), color = Beige) { Column(Modifier.padding(16.dp)) { Text(value.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Leaf); Text(title, color = Muted, fontSize = 13.sp) } } }

@Composable private fun MiniColumnChart(series: List<Pair<String, Int>>, color: Color, valueLabel: (Int) -> String) {
    val max = series.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(142.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        series.forEach { (day, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(valueLabel(value), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(.62f).height((24 + 76 * value / max).dp).background(color, RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)))
                Spacer(Modifier.height(4.dp)); Text(day.takeLast(5), fontSize = 9.sp, color = Muted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun KitchenScreen(state: AppUiState, nav: NavController, vm: AppViewModel) {
    var productionDate by remember(state.production) { mutableStateOf(state.production.firstOrNull()?.production_date) }
    val visibleBatches = state.production.filter { productionDate == null || it.production_date == productionDate }.sortedWith(compareBy({ it.deliver_at ?: "9999-12-31T23:59" }, { it.menu_name ?: "" }))
    val totalPacks = visibleBatches.sumOf { it.quantity }
    val tomorrow = LocalDate.now().plusDays(1)
    val tomorrowBatches = state.production.filter { batch -> runCatching { LocalDateTime.parse(batch.deliver_at).toLocalDate() }.getOrDefault(LocalDate.parse(batch.production_date).plusDays(1)) == tomorrow }
    val context = LocalContext.current
    var confirmBatch by remember { mutableStateOf<Pair<ProductionDto, String>?>(null) }
    LaunchedEffect(state.production) { scheduleKitchenReminders(context, state.production) }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Dapur LaukSatSet", fontWeight = FontWeight.Black) }, actions = { IconButton(onClick = { nav.navigate("kitchen-profile") }) { Icon(painterResource(R.drawable.ic_profile), "Profil dapur", Modifier.size(24.dp)) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = { NavigationBar(containerColor = WarmWhite) { NavigationBarItem(true, {}, { Icon(painterResource(R.drawable.ic_orders), "Produksi", Modifier.size(22.dp)) }, label = { Text("Produksi") }, colors = NavigationBarItemDefaults.colors(indicatorColor = Terracotta, selectedIconColor = WarmWhite, selectedTextColor = Terracotta)); NavigationBarItem(false, { nav.navigate("kitchen-profile") }, { Icon(painterResource(R.drawable.ic_profile), "Profil", Modifier.size(22.dp)) }, label = { Text("Profil") }, colors = NavigationBarItemDefaults.colors(indicatorColor = Terracotta, selectedIconColor = WarmWhite, selectedTextColor = Terracotta)) } }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Rekap produksi", fontSize = 28.sp, fontWeight = FontWeight.Black, color = LeafDark); Text("Lihat jumlah per hari, target selesai, dan urutan pekerjaan dapur.", color = Muted) }
            if (tomorrowBatches.isNotEmpty()) item { Surface(color = Terracotta, shape = RoundedCornerShape(12.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("Persiapan kiriman besok", color = WarmWhite, fontWeight = FontWeight.Bold); Text("${tomorrowBatches.sumOf { it.quantity }} pack · ${tomorrowBatches.joinToString { it.menu_name ?: "Menu produksi" }}", color = WarmWhite, fontSize = 13.sp) } } }
            item { DateFilter("Tanggal produksi", productionDate, allowAll = true) { productionDate = it } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Total pack", totalPacks, Modifier.weight(1f)); Metric("Jenis batch", visibleBatches.size, Modifier.weight(1f)) } }
            if (visibleBatches.isEmpty() && !state.loading) item { EmptyState(if (state.production.isEmpty()) "Belum ada batch produksi" else "Tidak ada produksi pada tanggal ini", if (state.production.isEmpty()) "Batch muncul segera setelah customer membuat pesanan dan aktif otomatis setelah pembayaran berhasil." else "Pilih tanggal lain atau tampilkan semua tanggal.") { if (state.production.isEmpty()) vm.loadKitchen() else productionDate = null } }
            items(visibleBatches) { batch ->
                val menuName = batch.menu_name ?: state.catalog?.menus?.firstOrNull { it.id == batch.menu_id }?.name ?: "Menu produksi"
                val finish = batch.finish_by?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDate.parse(batch.production_date).plusDays(1).atTime(8, 30)
                val minutesLeft = Duration.between(LocalDateTime.now(), finish).toMinutes()
                Surface(shape = RoundedCornerShape(14.dp), color = if (minutesLeft in 0..30) Terracotta.copy(alpha = .16f) else Beige) { Column(Modifier.padding(16.dp)) {
                    Row { Column(Modifier.weight(1f)) { Text(menuName, fontWeight = FontWeight.Black, fontSize = 18.sp); Text("${batch.quantity} pack · ${label(batch.status)}", color = Terracotta, fontWeight = FontWeight.Bold) }; Text("Selesai ${finish.format(DateTimeFormatter.ofPattern("HH.mm"))}", fontWeight = FontWeight.Bold, color = if (minutesLeft in 0..30) Terracotta else Brown) }
                    Text("Pedas: ${label(batch.spicy_level)} · Nasi: ${batch.rice_quantity} · Sambal: ${batch.sambal_quantity} · Kerupuk: ${batch.cracker_quantity}", color = Muted)
                    batch.delivery_id?.let { Text("Pengiriman #$it", color = Muted, fontSize = 12.sp) }
                    if (minutesLeft in 0..30) Text(if (minutesLeft <= 10) "Mendesak · kurang dari 10 menit" else "Pengingat · kurang dari 30 menit", color = Terracotta, fontWeight = FontWeight.Bold)
                    if (batch.notes.isNotEmpty()) Text("Catatan: ${batch.notes.joinToString()}", color = Brown, fontSize = 13.sp)
                    if (batch.allergens.isNotEmpty()) Text("Alergen: ${batch.allergens.joinToString()}", color = Olive, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp)); ProductionTimeline(batch.status)
                    if (batch.status == "menunggu_pembayaran") Text("Menunggu pembayaran customer. Batch sudah tercatat, tetapi produksi belum dapat dimulai.", color = Olive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    nextBatchStatus(batch.status)?.let { next -> Spacer(Modifier.height(10.dp)); Button(onClick = { confirmBatch = batch to next }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = WarmWhite)) { Text(if (next == "siap_dikirim") "Tandai siap dikirim" else "Tandai ${label(next)}") } }
                } }
            }
        }
    }
    confirmBatch?.let { (batch, next) -> AlertDialog(onDismissRequest = { confirmBatch = null }, title = { Text(if (next == "siap_dikirim") "Siap diambil pengantar?" else "Ubah status batch?") }, text = { Text(if (next == "siap_dikirim") "Pastikan proses, QC, jumlah pack, add-on, kemasan, dan label ${batch.menu_name ?: "menu"} sudah selesai. Setelah dikonfirmasi, tugas langsung aktif di aplikasi pengantar." else "${batch.menu_name ?: "Menu"} akan ditandai ${label(next)}. Pastikan checklist proses dan QC sudah selesai.") }, confirmButton = { Button(onClick = { vm.updateBatch(batch.id, next); confirmBatch = null }) { Text("Ya, lanjutkan") } }, dismissButton = { TextButton(onClick = { confirmBatch = null }) { Text("Batal") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun KitchenProfileScreen(state: AppUiState, nav: NavController, vm: AppViewModel, logout: () -> Unit) {
    var passwordDialog by remember { mutableStateOf(false) }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Profil dapur", fontWeight = FontWeight.Black) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = { NavigationBar(containerColor = WarmWhite) { NavigationBarItem(false, { nav.navigate("kitchen") { popUpTo("kitchen") { inclusive = true } } }, { Icon(painterResource(R.drawable.ic_orders), "Produksi", Modifier.size(22.dp)) }, label = { Text("Produksi") }, colors = NavigationBarItemDefaults.colors(indicatorColor = Terracotta, selectedIconColor = WarmWhite, selectedTextColor = Terracotta)); NavigationBarItem(true, {}, { Icon(painterResource(R.drawable.ic_profile), "Profil", Modifier.size(22.dp)) }, label = { Text("Profil") }, colors = NavigationBarItemDefaults.colors(indicatorColor = Terracotta, selectedIconColor = WarmWhite, selectedTextColor = Terracotta)) } }) { inset -> Column(Modifier.fillMaxSize().padding(inset).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { BrandMark(); ChoiceSection("Akun dapur") { Text(state.user?.name.orEmpty(), fontSize = 24.sp, fontWeight = FontWeight.Black); Text("Batch diurutkan dari jadwal terdekat. Setelah QC, dapur melepas pesanan langsung ke pengantar; admin memantau statusnya.", color = Muted); Button(onClick = { passwordDialog = true }, Modifier.fillMaxWidth()) { Text("Ubah kata sandi") }; OutlinedButton(onClick = logout, Modifier.fillMaxWidth()) { Text("Keluar") } } } }
    if (passwordDialog) PasswordDialog({ passwordDialog = false }) { old, fresh -> vm.changePassword(old, fresh) { passwordDialog = false } }
}

private fun scheduleKitchenReminders(context: Context, batches: List<ProductionDto>) {
    val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    batches.forEach { batch ->
        val delivery = batch.deliver_at?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return@forEach
        val menu = batch.menu_name ?: "Menu produksi"
        listOf(
            Triple(delivery.minusDays(1).withHour(8).withMinute(0), "Persiapan untuk besok", "$menu · ${batch.quantity} pack perlu disiapkan"),
            Triple(delivery.minusMinutes(30), "30 menit menuju pengiriman", "$menu · ${batch.quantity} pack harus segera siap"),
            Triple(delivery.minusMinutes(10), "10 menit menuju pengiriman", "Periksa akhir $menu dan serahkan ke kurir")
        ).forEachIndexed { index, (time, title, body) ->
            val millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (millis > System.currentTimeMillis()) {
                val requestCode = batch.id * 10 + index
                val intent = Intent(context, KitchenReminderReceiver::class.java).putExtra("id", 2000 + requestCode).putExtra("title", title).putExtra("body", body)
                val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CourierScreen(state: AppUiState, nav: NavController, vm: AppViewModel) {
    val context = LocalContext.current
    val notifiedReady = remember { mutableStateListOf<Int>() }
    var target by remember { mutableStateOf<Pair<CourierDeliveryDto, String>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val selected = target
        if (uri != null && selected != null) runCatching { context.contentResolver.openInputStream(uri)?.use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) } }.getOrNull()?.let { vm.uploadCourierProof(selected.first.id, selected.second, it) }
        target = null
    }
    LaunchedEffect(Unit) { while (true) { vm.loadCourier(); delay(20_000) } }
    LaunchedEffect(state.courierDeliveries) { state.courierDeliveries.filter { it.status == "siap_dikirim" && it.id !in notifiedReady }.forEach { delivery -> notifiedReady += delivery.id; notifyCourierReady(context, "${delivery.order_id} · ${delivery.date.orEmpty()} ${delivery.label.orEmpty()}") } }
    val activeCount = state.courierDeliveries.count { it.status in listOf("siap_dikirim", "dalam_pengiriman") }
    val scheduledCount = state.courierDeliveries.count { it.status == "terjadwal" }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Pengantaran", fontWeight = FontWeight.Black) }, actions = { IconButton(onClick = { nav.navigate("courier-profile") }) { Icon(painterResource(R.drawable.ic_profile), "Profil pengantar", Modifier.size(24.dp)) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = { NavigationBar(containerColor = WarmWhite) { NavigationBarItem(true, {}, { Icon(painterResource(R.drawable.ic_orders), "Tugas", Modifier.size(22.dp)) }, label = { Text("Tugas") }, colors = NavigationBarItemDefaults.colors(indicatorColor = Terracotta, selectedIconColor = WarmWhite, selectedTextColor = Terracotta)); NavigationBarItem(false, { nav.navigate("courier-profile") }, { Icon(painterResource(R.drawable.ic_profile), "Profil", Modifier.size(22.dp)) }, label = { Text("Profil") }, colors = NavigationBarItemDefaults.colors(indicatorColor = Terracotta, selectedIconColor = WarmWhite, selectedTextColor = Terracotta)) } }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Tugas pengantaran", fontSize = 28.sp, fontWeight = FontWeight.Black, color = LeafDark); Text("Tugas diurutkan berdasarkan tanggal dan jam terdekat.", color = Muted) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Siap/berjalan", activeCount, Modifier.weight(1f)); Metric("Terjadwal", scheduledCount, Modifier.weight(1f)) } }
            if (state.courierDeliveries.isEmpty() && !state.loading) item { EmptyState("Belum ada tugas", "Tugas muncul setelah pengantar ditetapkan. Tombol mulai aktif otomatis saat dapur selesai QC dan menandai siap dikirim.") { vm.loadCourier() } }
            items(state.courierDeliveries.sortedWith(compareBy({ it.date ?: "9999-12-31" }, { it.label ?: "" }))) { delivery -> Surface(shape = RoundedCornerShape(16.dp), color = if (delivery.status == "siap_dikirim") Beige else WarmWhite, border = BorderStroke(1.dp, if (delivery.status == "siap_dikirim") Terracotta else Beige)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Column(Modifier.weight(1f)) { Text("${delivery.date ?: "-"} · ${delivery.label ?: ""}", color = Terracotta, fontWeight = FontWeight.Bold); Text(delivery.order_id, fontWeight = FontWeight.Black) }; Surface(color = if (delivery.status == "siap_dikirim") Terracotta else Olive.copy(alpha = .18f), shape = RoundedCornerShape(20.dp)) { Text(label(delivery.status), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = if (delivery.status == "siap_dikirim") WarmWhite else LeafDark, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }; Text("${delivery.recipient} · ${delivery.phone}", fontWeight = FontWeight.Bold); Text(delivery.address, color = Muted, fontSize = 13.sp); AdminDeliveryTimeline(delivery.status); Button(onClick = { target = delivery to "before"; picker.launch("image/*") }, enabled = delivery.status == "siap_dikirim" && !delivery.has_before_photo, modifier = Modifier.fillMaxWidth()) { Text(if (delivery.has_before_photo) "Foto barang tersimpan" else if (delivery.status == "terjadwal") "Menunggu siap dari dapur" else "Foto barang & mulai antar") }; OutlinedButton(onClick = { target = delivery to "arrival"; picker.launch("image/*") }, enabled = delivery.has_before_photo && !delivery.has_arrival_photo, modifier = Modifier.fillMaxWidth()) { Text(if (delivery.has_arrival_photo) "Foto sampai tersimpan" else "Foto saat sampai") }; OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${delivery.phone}"))) }, enabled = delivery.phone.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Hubungi pelanggan") } } } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CourierProfileScreen(state: AppUiState, nav: NavController, vm: AppViewModel, logout: () -> Unit) {
    var passwordDialog by remember { mutableStateOf(false) }
    val completed = state.courierDeliveries.count { it.status == "diterima" }
    LaunchedEffect(Unit) { vm.loadProfile() }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Profil pengantar", fontWeight = FontWeight.Black) }, actions = { Icon(painterResource(R.drawable.ic_profile), null, Modifier.padding(end = 16.dp).size(24.dp)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = { NavigationBar(containerColor = WarmWhite) { NavigationBarItem(false, { nav.navigate("courier") { popUpTo("courier") { inclusive = true } } }, { Icon(painterResource(R.drawable.ic_orders), "Tugas", Modifier.size(22.dp)) }, label = { Text("Tugas") }); NavigationBarItem(true, {}, { Icon(painterResource(R.drawable.ic_profile), "Profil", Modifier.size(22.dp)) }, label = { Text("Profil") }, colors = NavigationBarItemDefaults.colors(indicatorColor = Terracotta, selectedIconColor = WarmWhite, selectedTextColor = Terracotta)) } }) { inset -> LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Surface(Modifier.fillMaxWidth(), color = Beige, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Surface(Modifier.size(72.dp), color = Terracotta, shape = RoundedCornerShape(36.dp)) { Icon(painterResource(R.drawable.ic_profile), null, Modifier.padding(18.dp), tint = WarmWhite) }; Spacer(Modifier.height(10.dp)); Text(state.user?.name.orEmpty(), fontSize = 23.sp, fontWeight = FontWeight.Black); Text("Pengantar LaukSatSet", color = Muted) } } }; item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Tugas", state.courierDeliveries.size, Modifier.weight(1f)); Metric("Selesai", completed, Modifier.weight(1f)) } }; item { ChoiceSection("Akun & keamanan") { state.profile?.email?.let { Text(it) }; state.profile?.phone?.let { Text(it, color = Muted) }; Button(onClick = { passwordDialog = true }, Modifier.fillMaxWidth()) { Text("Ubah kata sandi") }; OutlinedButton(onClick = logout, Modifier.fillMaxWidth()) { Text("Keluar") } } } } }
    if (passwordDialog) PasswordDialog({ passwordDialog = false }) { old, fresh -> vm.changePassword(old, fresh) { passwordDialog = false } }
}

@Composable private fun EmptyState(title: String, body: String, actionLabel: String = "Coba lagi", action: () -> Unit) { Surface(shape = RoundedCornerShape(16.dp), color = Beige) { Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(body, color = Muted, fontSize = 14.sp); Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = action) { Text(actionLabel) } } } }
@Composable private fun DisabledFeature(title: String, body: String) { Surface(shape = RoundedCornerShape(18.dp), color = Beige) { Column(Modifier.padding(16.dp)) { Text(title, color = Muted, fontWeight = FontWeight.Bold); Text(body, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(8.dp)); Button(onClick = {}, enabled = false) { Text("Belum tersedia") } } } }

private fun nextBatchStatus(status: String): String? = when (status) { "menunggu_produksi" -> "diproduksi"; "diproduksi" -> "lolos_qc"; "lolos_qc" -> "siap_dikirim"; "bermasalah" -> "diproduksi"; else -> null }
