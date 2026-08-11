package uz.teamwork.mehrgodriver.data.repository

import uz.teamwork.mehrgodriver.data.remote.BirgaApiService
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaCompleteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaLoginResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOnlineResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOtpResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaPickupResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRouteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRoutesResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaSignupResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaSimpleResponse
import uz.teamwork.mehrgodriver.domain.repository.BirgaRepository
import javax.inject.Inject

class BirgaRepositoryImpl @Inject constructor(
    private val api: BirgaApiService
) : BirgaRepository {
    override suspend fun requestOtp(phone: String): BirgaOtpResponse = api.requestOtp(phone)
    override suspend fun login(
        phone: String, code: String,
        name: String?, vehicleClass: String?, capacity: Int?,
        plate: String?, carModel: String?, carColor: String?
    ): BirgaLoginResponse = api.login(phone, code, name, vehicleClass, capacity, plate, carModel, carColor)
    override suspend fun setOnline(online: Boolean, lat: Double?, lon: Double?): BirgaOnlineResponse =
        api.setOnline(if (online) 1 else 0, lat, lon)
    override suspend fun getRoutes(): BirgaRoutesResponse = api.getRoutes()
    override suspend fun getRoute(id: Int): BirgaRouteResponse = api.getRoute(id)
    override suspend fun signup(id: Int): BirgaSignupResponse = api.signup(id)
    override suspend fun accept(id: Int): BirgaRouteResponse = api.accept(id)
    override suspend fun start(id: Int): BirgaRouteResponse = api.start(id)
    override suspend fun pickup(id: Int, childId: Int?, seq: Int?): BirgaPickupResponse =
        api.pickup(id, childId, seq)
    override suspend fun complete(id: Int): BirgaCompleteResponse = api.complete(id)
    override suspend fun track(id: Int, lat: Double, lon: Double): BirgaSimpleResponse =
        api.track(id, lat, lon)
}
