package uz.teamwork.mehrgodriver.presentation.main.ui.maktabgo_map

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaCompleteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaPickupResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOnlineResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRouteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRoutesResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaSimpleResponse
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaCompleteUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaPickupUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaRouteViewUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaRoutesUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaSetOnlineUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaStartUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaTrackUC
import javax.inject.Inject

/**
 * VM экрана рейса MaktabGo на карте. Тонкий делегат к Birga use-case'ам
 * (тот же паттерн, что MaktabGoRoutesVM). Никакой таксишной логики.
 */
@HiltViewModel
class MaktabGoMapVM @Inject constructor(
    private val routeViewUC: BirgaRouteViewUC,
    private val routesUC: BirgaRoutesUC,
    private val setOnlineUC: BirgaSetOnlineUC,
    private val startUC: BirgaStartUC,
    private val pickupUC: BirgaPickupUC,
    private val completeUC: BirgaCompleteUC,
    private val trackUC: BirgaTrackUC
) : ViewModel() {

    fun route(id: Int): Flow<Resource<BirgaRouteResponse>> = routeViewUC(id)

    fun routes(): Flow<Resource<BirgaRoutesResponse>> = routesUC()

    fun setOnline(online: Boolean): Flow<Resource<BirgaOnlineResponse>> = setOnlineUC(online)

    fun start(id: Int): Flow<Resource<BirgaRouteResponse>> = startUC(id)

    fun pickup(id: Int, seq: Int): Flow<Resource<BirgaPickupResponse>> = pickupUC(id, seq = seq)

    fun complete(id: Int): Flow<Resource<BirgaCompleteResponse>> = completeUC(id)

    fun track(id: Int, lat: Double, lon: Double): Flow<Resource<BirgaSimpleResponse>> =
        trackUC(id, lat, lon)
}
