package com.lauksatset.app.data

import com.lauksatset.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface LaukApi {
    @GET("settings/public") suspend fun publicSettings(): PublicSettingsDto
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): LoginResponse
    @POST("auth/register") suspend fun register(@Body request: RegisterRequest): LoginResponse
    @POST("auth/forgot-password") suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Map<String, String>
    @POST("auth/reset-password") suspend fun resetPassword(@Body request: ResetPasswordRequest): Map<String, String>
    @POST("me/password") suspend fun changePassword(@Body request: ChangePasswordRequest): Map<String, String>
    @GET("me") suspend fun profile(): ProfileDto
    @GET("me/preferences") suspend fun preferences(): PreferenceDto
    @PUT("me/preferences") suspend fun savePreferences(@Body request: PreferenceDto): PreferenceDto
    @GET("catalog") suspend fun catalog(): CatalogResponse
    @GET("delivery-slots") suspend fun slots(): List<SlotDto>
    @GET("me/addresses") suspend fun addresses(): List<AddressDto>
    @POST("me/addresses") suspend fun createAddress(@Body request: AddressRequest): AddressDto
    @PATCH("me/addresses/{id}") suspend fun updateAddress(@Path("id") id: Int, @Body request: AddressRequest): AddressDto
    @DELETE("me/addresses/{id}") suspend fun deleteAddress(@Path("id") id: Int): Map<String, Any>
    @POST("checkout") suspend fun checkout(@Body request: CheckoutRequest): CheckoutResponse
    @GET("orders") suspend fun orders(): List<OrderDto>
    @GET("orders/{id}") suspend fun order(@Path("id") id: String): OrderDto
    @POST("orders/{id}/cancel") suspend fun cancelOrder(@Path("id") id: String): OrderDto
    @POST("orders/{id}/retry-payment") suspend fun retryPayment(@Path("id") id: String): Map<String, String>
    @PATCH("orders/{id}/address") suspend fun changeOrderAddress(@Path("id") id: String, @Body request: IdRequest): OrderDto
    @PATCH("deliveries/{id}/reschedule") suspend fun rescheduleDelivery(@Path("id") id: Int, @Body request: IdRequest): OrderDto
    @POST("me/deliveries/{id}/receive") suspend fun receiveDelivery(@Path("id") id: Int): OrderDto
    @POST("deliveries/{id}/problem") suspend fun reportDelivery(@Path("id") id: Int, @Body request: ComplaintRequest): Map<String, Any>
    @POST("orders/{id}/review") suspend fun reviewOrder(@Path("id") id: String, @Body request: ReviewRequest): Map<String, Any>
    @POST("me/feedback") suspend fun feedback(@Body request: FeedbackRequest): Map<String, Any>
    @GET("admin/summary") suspend fun adminSummary(): AdminSummary
    @GET("kitchen/production") suspend fun production(): List<ProductionDto>
    @POST("demo/payments/{id}/{result}") suspend fun demoPayment(@Path("id") id: String, @Path("result") result: String): Map<String, Any>
    @POST("kitchen/production/{id}/status") suspend fun updateBatch(@Path("id") id: Int, @Body request: StatusRequest): Map<String, Any>
    @PATCH("admin/menus/{id}") suspend fun updateMenu(@Path("id") id: Int, @Body request: MenuUpdateRequest): Map<String, Any>
    @PUT("admin/menus/{id}") suspend fun replaceMenu(@Path("id") id: Int, @Body request: MenuFullUpdateRequest): Map<String, Any>
    @POST("admin/menus") suspend fun createMenu(@Body request: MenuCreateRequest): Map<String, Any>
    @DELETE("admin/menus/{id}") suspend fun deleteMenu(@Path("id") id: Int): Map<String, Any>
    @GET("admin/menus/archived") suspend fun archivedMenus(): List<MenuDto>
    @PATCH("admin/slots/{id}") suspend fun updateSlot(@Path("id") id: Int, @Body request: SlotUpdateRequest): Map<String, Any>
    @POST("admin/slots") suspend fun createSlot(@Body request: SlotCreateRequest): Map<String, Any>
    @DELETE("admin/slots/{id}") suspend fun deleteSlot(@Path("id") id: Int): Map<String, Any>
    @POST("admin/packages") suspend fun createPackage(@Body request: PackageManageRequest): Map<String, Any>
    @PATCH("admin/packages/{id}") suspend fun updatePackage(@Path("id") id: Int, @Body request: PackageManageRequest): Map<String, Any>
    @DELETE("admin/packages/{id}") suspend fun deletePackage(@Path("id") id: Int): Map<String, Any>
    @POST("recommendations") suspend fun recommendation(@Body request: RecommendationRequest): RecommendationDto
    @POST("recommendations/options") suspend fun recommendationOptions(@Body request: RecommendationRequest): RecommendationOptionsDto
    @GET("me/pantry") suspend fun pantry(): List<PantryItemDto>
    @POST("me/pantry/{id}/status") suspend fun updatePantry(@Path("id") id: Int, @Body request: StatusRequest): Map<String, Any>
    @GET("admin/couriers") suspend fun couriers(): List<CourierDto>
    @GET("admin/messages") suspend fun adminMessages(): List<AdminMessageDto>
    @GET("admin/users") suspend fun adminUsers(): List<AdminUserDto>
    @POST("admin/deliveries/{id}/assign") suspend fun assignCourier(@Path("id") id: Int, @Body request: CourierAssignRequest): Map<String, Any>
    @GET("courier/deliveries") suspend fun courierDeliveries(): List<CourierDeliveryDto>
    @POST("courier/deliveries/{id}/{stage}") suspend fun courierProof(@Path("id") id: Int, @Path("stage") stage: String, @Body request: CourierProofRequest): Map<String, Any>
}

class ApiProvider {
    @Volatile var token: String? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().apply { token?.let { header("Authorization", "Bearer $it") } }.build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()
    val api: LaukApi = Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(LaukApi::class.java)
}
