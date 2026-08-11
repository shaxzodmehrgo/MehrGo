package uz.teamwork.mehrgodriver.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import uz.teamwork.mehrgodriver.domain.model.AddressInBranch
import uz.teamwork.mehrgodriver.domain.model.CarBrand
import uz.teamwork.mehrgodriver.domain.model.CarColor
import uz.teamwork.mehrgodriver.domain.model.CarModel
import uz.teamwork.mehrgodriver.domain.model.DeviceTokenResult
import uz.teamwork.mehrgodriver.domain.model.DriverEarningsSummary
import uz.teamwork.mehrgodriver.domain.model.Instruction
import uz.teamwork.mehrgodriver.domain.model.Introduce
import uz.teamwork.mehrgodriver.domain.model.LicenseUploadResult
import uz.teamwork.mehrgodriver.domain.model.Notification
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.model.OrderAddress
import uz.teamwork.mehrgodriver.domain.model.OrderCancelReason
import uz.teamwork.mehrgodriver.domain.model.OrderHistory
import uz.teamwork.mehrgodriver.domain.model.OrderService
import uz.teamwork.mehrgodriver.domain.model.PasswordRecovery
import uz.teamwork.mehrgodriver.domain.model.PasswordRecoveryResendCode
import uz.teamwork.mehrgodriver.domain.model.Region
import uz.teamwork.mehrgodriver.domain.model.SignUp
import uz.teamwork.mehrgodriver.domain.model.SignUpResendCode
import uz.teamwork.mehrgodriver.domain.model.SubscriptionData
import uz.teamwork.mehrgodriver.domain.model.Tariff
import uz.teamwork.mehrgodriver.domain.model.TelegramAuthStatus
import uz.teamwork.mehrgodriver.domain.model.TermsOfUse
import uz.teamwork.mehrgodriver.domain.model.UpdateApp
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.VerificationStatus
import uz.teamwork.mehrgodriver.domain.model.Video
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.fare.FareGpsBatchRequest
import uz.teamwork.mehrgodriver.domain.model.fare.FareResponse
import uz.teamwork.mehrgodriver.domain.model.paylov.BalanceHistory
import uz.teamwork.mehrgodriver.domain.model.paylov.CardCreateResult
import uz.teamwork.mehrgodriver.domain.model.paylov.CardsResponse
import uz.teamwork.mehrgodriver.domain.model.paylov.PaylovCard
import uz.teamwork.mehrgodriver.domain.model.paylov.PaymentCreateResult
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalInfo
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalList
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalRequest
import uz.teamwork.mehrgodriver.domain.model.requests.DeviceTokenRequest
import uz.teamwork.mehrgodriver.domain.model.requests.ErrorRequest
import uz.teamwork.mehrgodriver.domain.model.requests.OrderCreateRequest
import uz.teamwork.mehrgodriver.domain.model.requests.RequestOrderFinish

interface ApiService {
    // Auth...
    @FormUrlEncoded
    @POST("user/login")
    suspend fun login(
        @Field("phone") phoneNumber: String,
        @Field("password") password: String
    ): BaseResponse<User>

    @FormUrlEncoded
    @POST("user/register")
    suspend fun signUp(
        @Field("phone") phoneNumber: String,
        @Field("first_name") firstName: String,
        @Field("father_name") fatherName: String,
        @Field("last_name") lastName: String,
        @Field("password") password: String,
        @Field("password_repeat") confirmPassword: String,
        // Code delivery channel: "sms" (default) or "telegram" (backend deliverCode).
        @Field("channel") channel: String = "sms"
    ): BaseResponse<SignUp>

    @FormUrlEncoded
    @POST("user/refresh")
    suspend fun signUpResendCode(
        @Field("auth_key") authKey: String,
        @Field("channel") channel: String = "sms"
    ): BaseResponse<SignUpResendCode>

    // Telegram OTP availability + link status for a phone. Not on every backend yet —
    // callers must tolerate a 404/failure and fall back to SMS.
    @GET("telegram-auth/status")
    suspend fun telegramAuthStatus(
        @Query("phone") phone: String
    ): BaseResponse<TelegramAuthStatus>

    @FormUrlEncoded
    @POST("user/confirm")
    suspend fun signUpVerifyCode(
        @Field("auth_key") authKey: String,
        @Field("code") verifyCode: Int,
        @Field("device_token") deviceToken: String? = null
    ): BaseResponse<User>

    @FormUrlEncoded
    @POST("user/recover")
    suspend fun passwordRecovery(
        @Field("phone") phoneNumber: String,
        @Field("channel") channel: String = "sms"
    ): BaseResponse<PasswordRecovery>

    @FormUrlEncoded
    @POST("user/refresh?recover=1")
    suspend fun passwordRecoveryResendCode(
        @Field("auth_key") authKey: String,
        @Field("channel") channel: String = "sms"
    ): BaseResponse<PasswordRecoveryResendCode>

    @FormUrlEncoded
    @POST("user/change-password")
    suspend fun passwordRecoveryChangePassword(
        @Field("password") password: String,
        @Field("password_repeat") passwordRepeat: String,
        @Field("code") verifyCode: Int,
        @Field("auth_key") authKeyVerify: String
    ): BaseResponse<User>

    // Last or next process from Auth...
    @GET("slider/index")
    suspend fun getIntroduceData(
    ): BaseResponse<List<Introduce>>

    @GET("branch/index")
    suspend fun getRegions(): BaseResponse<List<Region>>

    @GET("cars/index")
    suspend fun getCarBrands(): BaseResponse<List<CarBrand>>

    @GET("cars/index")
    suspend fun getCarModels(): BaseResponse<List<CarModel>>

    @GET("colors/index")
    suspend fun getCarColors(): BaseResponse<List<CarColor>>

    @Multipart
    @POST("user/fill-data")
    suspend fun uploadDriverInfo(
        @Part("auth_key") authKey: RequestBody,
        @Part("speciality_id") carBrandId: RequestBody,

        @Part("branch_id") regionId: RequestBody,
        @Part("gender") genderTypeId: RequestBody,
        @Part("born") birthday: RequestBody,
        @Part("address") address: RequestBody,

        @Part("passport_sn") passportNumber: RequestBody,
        @Part("given_by") passportGiveBy: RequestBody,
        @Part("given_date") passportGiveDate: RequestBody,
        @Part("drivers_license") licenseNumber: RequestBody,
        @Part("license_category") licenseTypeId: RequestBody,

        @Part("model_id") carModelId: RequestBody,
        @Part("color_id") carColorId: RequestBody,
        @Part("gov_number") carNumber: RequestBody,
        @Part("made_date") carMade: RequestBody,
        @Part photo: MultipartBody.Part,
        @Part photos: List<MultipartBody.Part>
    ): BaseResponse<User>

    @GET("license/index")
    suspend fun getTermOfUse(
    ): BaseResponse<TermsOfUse>

    @GET("user/me")
    suspend fun getUser(): BaseResponse<User>

    // Main
    @GET("order/new-address")
    suspend fun getOrderAddress(): BaseResponse<List<OrderAddress>>

    @GET("order/index/{category_id}")
    suspend fun getOrders(
        @Path("category_id") orderAddressId: String
    ): BaseResponse<List<Order>>

    @GET("order/list")
    suspend fun getAllOrders(): BaseResponse<List<Order>>

    // Location goes in the form body (backend reads it from the POST body); id stays in the query.
    @FormUrlEncoded
    @POST("order/accept")
    suspend fun orderAccept(
        @Query("id") orderId: Int,
        @Field("lat") lat: Double?,
        @Field("long") long: Double?,
        @Field("accuracy") accuracy: Float?
    ): BaseResponse<Order>

    // Driver declines an offer — carry the driver's GPS fix like the other order actions
    // (GET, so location rides in the query). Backend stores it on the order-history row.
    @GET("order/order-skip")
    suspend fun orderSkip(
        @Query("order_id") orderId: Int,
        @Query("lat") lat: Double?,
        @Query("long") long: Double?,
        @Query("accuracy") accuracy: Float?
    ): BaseResponse<Any>

    @GET("order-cancel-issue?type=1")
    suspend fun getOrderCancelReasons(): BaseResponse<List<OrderCancelReason>>

    @FormUrlEncoded
    @POST("order/cancel")
    suspend fun orderCancel(
        @Field("order_id") orderId: Int,
        @Field("issue_id") issueId: Int,
        @Field("lat") lat: Double?,
        @Field("long") long: Double?,
        @Field("accuracy") accuracy: Float?
    ): BaseResponse<Any>

    // Socket-side cancel: terminate the driver's order outright (no re-offer).
    // Carries the driver's GPS fix at cancel time like the other state-change calls.
    @FormUrlEncoded
    @POST("socket/order-cancel")
    suspend fun socketOrderCancel(
        @Field("order_id") orderId: Int,
        @Field("issue_id") issueId: Int,
        @Field("lat") lat: Double?,
        @Field("long") long: Double?,
        @Field("accuracy") accuracy: Float?
    ): BaseResponse<Any>

    // Socket-side cancel that re-offers (renews) the order to other drivers.
    // Plumbing only for now — not wired into a UI trigger.
    @FormUrlEncoded
    @POST("socket/order-cancel-and-renew")
    suspend fun socketOrderCancelAndRenew(
        @Field("order_id") orderId: Int,
        @Field("issue_id") issueId: Int,
        @Field("lat") lat: Double?,
        @Field("long") long: Double?,
        @Field("accuracy") accuracy: Float?
    ): BaseResponse<Any>

    // POST per backend contract; order_id in query, location in the form body.
    @FormUrlEncoded
    @POST("order/start")
    suspend fun orderStart(
        @Query("order_id") orderId: Int,
        @Field("lat") lat: Double?,
        @Field("long") long: Double?,
        @Field("accuracy") accuracy: Float?
    ): BaseResponse<Order>

    @FormUrlEncoded
    @POST("order-change/state?state=8")
    suspend fun orderArrive(
        @Query("id") orderId: Int,
        @Field("lat") lat: Double?,
        @Field("long") long: Double?,
        @Field("accuracy") accuracy: Float?
    ): BaseResponse<Order>

    @FormUrlEncoded
    @POST("order-change/state?state=9")
    suspend fun orderGo(
        @Query("id") orderId: Int,
        @Field("lat") lat: Double?,
        @Field("long") long: Double?,
        @Field("accuracy") accuracy: Float?
    ): BaseResponse<Order>

    @GET("service/order")
    suspend fun getOrderServices(@Query("id") orderId: Int): BaseResponse<List<OrderService>>

    // Adding/removing a paid service mutates the order (and its price) — carry the
    // driver's GPS in the form body like the other order-mutating POSTs.
    @FormUrlEncoded
    @POST("order/item")
    suspend fun addRemoveService(
        @Field("order_id") orderId: Int,
        @Field("service_id") serviceId: Int,
        @Field("action") action: Int,
        @Field("lat") lat: Double?,
        @Field("long") long: Double?,
        @Field("accuracy") accuracy: Float?
    ): BaseResponse<Any>

    @GET("address")
    suspend fun getAddressInBranch(
        @Query("branch_id") branchId: String,
    ): BaseResponse<List<AddressInBranch>>

    @POST("order/complete")
    suspend fun orderFinish(
        @Query("order_id") orderId: String,
        @Body requestOrderFinish: RequestOrderFinish
    ): BaseResponse<User>

    @GET("order/my-orders")
    suspend fun getActiveMyOrders(): BaseResponse<List<Order>>

    @GET("order/my-private-orders")
    suspend fun getRecommendOrder(): BaseResponse<List<Order>>

    @FormUrlEncoded
    @POST("location/send")
    suspend fun uploadLocation(
        @Field("lat") latitude: Double,
        @Field("lon") longitude: Double,
        @Field("bearing") bearing: Float
    ): BaseResponse<Any>

    @GET("order/history?expand=myOrder")
    suspend fun getOrderHistory(
        @Query("page") page: Int
    ): BaseResponse<OrderHistory>

    @GET("instruction/index")
    suspend fun getInstruction(): BaseResponse<List<Instruction>>

    @GET("notification/index")
    suspend fun getNotifications(): BaseResponse<List<Notification>>

    @GET("video/index")
    suspend fun getVideos(): BaseResponse<List<Video>>

    @GET("mobile/version-driver")
    suspend fun updateApp(
        @Query("token") token: String?,
        @Query("device_token") deviceToken: String? = null
    ): UpdateApp

    @POST("user/report")
    suspend fun sendErrors(
        @Body errors: List<ErrorRequest>
    ): BaseResponse<Any>

    @GET("subscription/list")
    suspend fun getSubscriptions(
        @Query("page") page: Int
    ): BaseResponse<SubscriptionData>

    @GET("subscription/purchase")
    suspend fun purchaseSubscription(
        @Query("id") id: Int
    ): BaseResponse<Any>

    @FormUrlEncoded
    @POST("driver/start")
    suspend fun startWork(
        @Field("device_token") deviceToken: String? = null
    ): BaseResponse<Any>

    @POST("device-token/register")
    suspend fun registerDeviceToken(
        @Body request: DeviceTokenRequest
    ): BaseResponse<DeviceTokenResult>

    @GET("user/change-language")
    suspend fun changeLanguage(
        @Query("language") language: String
    ): BaseResponse<String>

    @POST("driver/end")
    suspend fun finishWork(): BaseResponse<Any>

    // Pre-shift verification (driver document checks). See driver-verification-api.md.
    @GET("driver/verification-status")
    suspend fun getVerificationStatus(): BaseResponse<VerificationStatus>

    @Multipart
    @POST("driver/upload-license")
    suspend fun uploadLicense(
        @Part license: MultipartBody.Part
    ): BaseResponse<LicenseUploadResult>

    @GET("tarif?expand=name_with_group&is_taximeter=1")
    suspend fun getTariffs(
        @Query("branch_id") branchId: Int
    ): BaseResponse<List<Tariff>>

    @POST("order-new/create")
    suspend fun orderCreate(@Body request: OrderCreateRequest): BaseResponse<Any>

    // Driver earnings analytics. `period` is one of day|week|month|custom.
    // `from`/`to` (YYYY-MM-DD) are required only when period=custom.
    @GET("driver-earnings/summary")
    suspend fun getDriverEarningsSummary(
        @Query("period") period: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("tz") tz: String? = null
    ): BaseResponse<DriverEarningsSummary>

    // Server-side fare. Mobile uploads a batch of GPS points; server replies with
    // current distance / waiting / fare. `cpid` per point is the idempotency key.
    @POST("order-gps/batch")
    suspend fun sendGpsBatch(
        @Query("order_id") orderId: Int,
        @Body request: FareGpsBatchRequest
    ): BaseResponse<FareResponse>

    // Joint snapshot for reconnect / screen-reopen. Either driver or passenger token works.
    // The driver's current fix rides along (GET, so it goes in the query): a stationary car
    // produces no batch points — passesClientFilter drops near-duplicates — so this poll is
    // the only thing telling the server where the driver is while they wait. `lon`, not
    // `long`: the order-gps family spells it that way (see the batch body), unlike the
    // order state-change endpoints.
    @GET("order-gps/fare")
    suspend fun getFare(
        @Query("order_id") orderId: Int,
        @Query("lat") lat: Double?,
        @Query("lon") lon: Double?,
        @Query("accuracy") accuracy: Float?
    ): BaseResponse<FareResponse>

    // ---- Paylov cards + balance withdrawal ----------------------------------------
    // The Retrofit base URL is https://<host>/api/v1/ , but the Paylov routes live under
    // /api/paylov/... (NOT under v1). OkHttp resolves relative paths per RFC 3986, so a
    // path of "../paylov/x" from base ".../api/v1/" yields ".../api/paylov/x".
    // e.g. ../paylov/user-card/create -> https://<host>/api/paylov/user-card/create
    // The balance-history endpoint is the exception — it stays under v1 ("balance/index").

    @FormUrlEncoded
    @POST("../paylov/user-card/create")
    suspend fun paylovCardCreate(
        @Field("cardNumber") cardNumber: String,
        @Field("expireDate") expireDate: String,
        @Field("phoneNumber") phoneNumber: String? = null
    ): BaseResponse<CardCreateResult>

    // Confirmed card is assumed to come back as data. If the server returns a different
    // shape here, switch this to BaseResponse<Any> — the caller only needs success/failure.
    @FormUrlEncoded
    @POST("../paylov/user-card/confirm")
    suspend fun paylovCardConfirm(
        @Field("cardId") cardId: String,
        @Field("otp") otp: String,
        @Field("cardName") cardName: String? = null,
        @Field("pinfl") pinfl: String? = null
    ): BaseResponse<PaylovCard>

    @GET("../paylov/user-card/cards")
    suspend fun paylovCards(): BaseResponse<CardsResponse>

    @DELETE("../paylov/user-card/delete")
    suspend fun paylovCardDelete(
        @Query("cardId") cardId: String
    ): BaseResponse<Any>

    @GET("../paylov/withdrawal/info")
    suspend fun paylovWithdrawalInfo(): BaseResponse<WithdrawalInfo>

    @FormUrlEncoded
    @POST("../paylov/withdrawal/create")
    suspend fun paylovWithdrawalCreate(
        @Field("card_id") cardId: String,
        @Field("amount") amount: Long,
        @Field("note") note: String? = null
    ): BaseResponse<WithdrawalRequest>

    @GET("../paylov/withdrawal/index")
    suspend fun paylovWithdrawalIndex(
        @Query("page") page: Int,
        @Query("per-page") perPage: Int
    ): BaseResponse<WithdrawalList>

    @FormUrlEncoded
    @POST("../paylov/withdrawal/cancel")
    suspend fun paylovWithdrawalCancel(
        @Field("id") id: Int
    ): BaseResponse<Any>

    @FormUrlEncoded
    @POST("../paylov/payment/fill-balance")
    suspend fun paylovFillBalance(
        @Field("cardId") cardId: String,
        @Field("amount") amount: Long
    ): BaseResponse<Any>

    @FormUrlEncoded
    @POST("../paylov/payment/create")
    suspend fun paylovPaymentCreate(
        @Field("cardId") cardId: String,
        @Field("amount") amount: Long
    ): BaseResponse<PaymentCreateResult>

    @FormUrlEncoded
    @POST("../paylov/payment/confirm")
    suspend fun paylovPaymentConfirm(
        @Field("transactionId") transactionId: String,
        @Field("otp") otp: String
    ): BaseResponse<Any>

    // Balance history is under v1 (relative path stays under .../api/v1/).
    @GET("balance/index")
    suspend fun getBalanceHistory(
        @Query("page") page: Int,
        @Query("per-page") perPage: Int,
        @Query("type") type: Int? = null,
        @Query("reason") reason: Int? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): BaseResponse<BalanceHistory>
}