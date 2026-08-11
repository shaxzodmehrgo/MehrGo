package uz.teamwork.mehrgodriver.domain.repository

import uz.teamwork.mehrgodriver.domain.model.birga.BirgaCompleteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaLoginResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOnlineResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOtpResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaPickupResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRouteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRoutesResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaSignupResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaSimpleResponse

/** Репозиторий водителя Birga (тонкая обёртка над BirgaApiService, как RouteRepository). */
interface BirgaRepository {
    suspend fun requestOtp(phone: String): BirgaOtpResponse
    suspend fun login(
        phone: String, code: String,
        name: String?, vehicleClass: String?, capacity: Int?,
        plate: String?, carModel: String?, carColor: String?
    ): BirgaLoginResponse
    suspend fun setOnline(online: Boolean, lat: Double?, lon: Double?): BirgaOnlineResponse
    suspend fun getRoutes(): BirgaRoutesResponse
    suspend fun getRoute(id: Int): BirgaRouteResponse
    suspend fun signup(id: Int): BirgaSignupResponse
    suspend fun accept(id: Int): BirgaRouteResponse
    suspend fun start(id: Int): BirgaRouteResponse
    suspend fun pickup(id: Int, childId: Int?, seq: Int?): BirgaPickupResponse
    suspend fun complete(id: Int): BirgaCompleteResponse
    suspend fun track(id: Int, lat: Double, lon: Double): BirgaSimpleResponse
}
