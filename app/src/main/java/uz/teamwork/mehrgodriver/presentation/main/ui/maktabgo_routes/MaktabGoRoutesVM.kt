package uz.teamwork.mehrgodriver.presentation.main.ui.maktabgo_routes

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOnlineResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRouteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRoutesResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaSignupResponse
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaAcceptUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaRouteViewUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaRoutesUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaSetOnlineUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaSignupUC
import javax.inject.Inject

/**
 * VM экрана рейсов MaktabGo. Тонкий делегат к Birga use-case'ам (тот же паттерн, что MaktabGoLoginVM).
 */
@HiltViewModel
class MaktabGoRoutesVM @Inject constructor(
    private val routesUC: BirgaRoutesUC,
    private val routeViewUC: BirgaRouteViewUC,
    private val setOnlineUC: BirgaSetOnlineUC,
    private val acceptUC: BirgaAcceptUC,
    private val signupUC: BirgaSignupUC
) : ViewModel() {

    fun routes(): Flow<Resource<BirgaRoutesResponse>> = routesUC()

    fun route(id: Int): Flow<Resource<BirgaRouteResponse>> = routeViewUC(id)

    fun setOnline(online: Boolean): Flow<Resource<BirgaOnlineResponse>> = setOnlineUC(online)

    fun accept(id: Int): Flow<Resource<BirgaRouteResponse>> = acceptUC(id)

    fun signup(id: Int): Flow<Resource<BirgaSignupResponse>> = signupUC(id)
}
