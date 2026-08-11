package uz.teamwork.mehrgodriver.presentation.auth.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.FcmTokenSync
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.auth.LoginUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.RegisterDeviceTokenUC
import javax.inject.Inject

@HiltViewModel
class LoginVM @Inject constructor(
    private val loginUC: LoginUC,
    private val registerDeviceTokenUC: RegisterDeviceTokenUC
) : ViewModel() {
    fun login(phoneNumber: String, password: String): Flow<Resource<BaseResponse<User>>> {
        return loginUC.invoke(phoneNumber, password)
    }

    fun syncFcmTokenIfNeeded(serverDeviceToken: String?) {
        FcmTokenSync.syncIfNeeded(registerDeviceTokenUC, viewModelScope, serverDeviceToken)
    }
}
