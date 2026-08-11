package uz.teamwork.mehrgodriver.presentation.auth.ui.maktabgo_login

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaLoginResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOtpResponse
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaLoginUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaRequestOtpUC
import javax.inject.Inject

@HiltViewModel
class MaktabGoLoginVM @Inject constructor(
    private val requestOtpUC: BirgaRequestOtpUC,
    private val loginUC: BirgaLoginUC
) : ViewModel() {

    fun requestOtp(phone: String): Flow<Resource<BirgaOtpResponse>> = requestOtpUC(phone)

    fun login(
        phone: String,
        code: String,
        name: String?,
        vehicleClass: String?,
        capacity: Int?,
        plate: String?,
        carModel: String?,
        carColor: String?
    ): Flow<Resource<BirgaLoginResponse>> =
        loginUC(phone, code, name, vehicleClass, capacity, plate, carModel, carColor)
}
