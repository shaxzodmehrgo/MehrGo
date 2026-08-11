package uz.teamwork.mehrgodriver.data.remote

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaCompleteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaLoginResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOnlineResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOtpResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaPickupResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRouteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRoutesResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaSignupResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaSimpleResponse

/**
 * API водителя Birga. База — Constants.BASE_URL (https://birga.mehrgo.uz/api/), тот же
 * default-Retrofit и OkHttp, что у ApiService: HeaderInterceptor уже добавляет
 * Authorization: Bearer <token> из UserManager. Ответы плоские ({"ok":true,...}); на ошибке
 * бэкенд отдаёт HTTP 4xx + {"ok":false,"error":"..."} → HttpException, разбирается в UC.
 */
interface BirgaApiService {

    @FormUrlEncoded
    @POST("driver/otp")
    suspend fun requestOtp(@Field("phone") phone: String): BirgaOtpResponse

    @FormUrlEncoded
    @POST("driver/login")
    suspend fun login(
        @Field("phone") phone: String,
        @Field("code") code: String,
        @Field("name") name: String? = null,
        @Field("vehicle_class") vehicleClass: String? = null,
        @Field("capacity") capacity: Int? = null,
        @Field("plate") plate: String? = null,
        @Field("car_model") carModel: String? = null,
        @Field("car_color") carColor: String? = null
    ): BirgaLoginResponse

    @FormUrlEncoded
    @POST("driver/online")
    suspend fun setOnline(
        @Field("online") online: Int,   // 1/0, НЕ boolean: PHP (bool)"false" == true
        @Field("lat") lat: Double? = null,
        @Field("lon") lon: Double? = null
    ): BirgaOnlineResponse

    @GET("driver/routes")
    suspend fun getRoutes(): BirgaRoutesResponse

    @GET("driver/routes/{id}")
    suspend fun getRoute(@Path("id") id: Int): BirgaRouteResponse

    @POST("driver/routes/{id}/signup")
    suspend fun signup(@Path("id") id: Int): BirgaSignupResponse

    @POST("driver/routes/{id}/accept")
    suspend fun accept(@Path("id") id: Int): BirgaRouteResponse

    @POST("driver/routes/{id}/start")
    suspend fun start(@Path("id") id: Int): BirgaRouteResponse

    @FormUrlEncoded
    @POST("driver/routes/{id}/pickup")
    suspend fun pickup(
        @Path("id") id: Int,
        @Field("child_id") childId: Int? = null,
        @Field("seq") seq: Int? = null
    ): BirgaPickupResponse

    @POST("driver/routes/{id}/complete")
    suspend fun complete(@Path("id") id: Int): BirgaCompleteResponse

    @FormUrlEncoded
    @POST("driver/routes/{id}/track")
    suspend fun track(
        @Path("id") id: Int,
        @Field("lat") lat: Double,
        @Field("lon") lon: Double
    ): BirgaSimpleResponse
}
