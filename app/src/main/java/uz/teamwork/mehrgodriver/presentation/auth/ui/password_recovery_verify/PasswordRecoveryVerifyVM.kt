package uz.teamwork.mehrgodriver.presentation.auth.ui.password_recovery_verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.FcmTokenSync
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.PasswordRecoveryResendCode
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.auth.PasswordRecoveryChangePasswordUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.PasswordRecoveryResendCodeUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.RegisterDeviceTokenUC
import javax.inject.Inject

@HiltViewModel
class PasswordRecoveryVerifyVM @Inject constructor(
    private val passwordRecoveryChangePasswordUC: PasswordRecoveryChangePasswordUC,
    private val passwordRecoveryResendCodeUC: PasswordRecoveryResendCodeUC,
    private val registerDeviceTokenUC: RegisterDeviceTokenUC
) : ViewModel() {

    fun passwordRecoveryResendCode(
        authKey: String,
        channel: String = "sms"
    ): Flow<Resource<BaseResponse<PasswordRecoveryResendCode>>> {
        return passwordRecoveryResendCodeUC.invoke(authKey, channel)
    }

    fun passwordRecoveryChangePassword(
        password: String,
        code: Int,
        authKeyVerify: String
    ): Flow<Resource<BaseResponse<User>>> {
        return passwordRecoveryChangePasswordUC.invoke(password, code, authKeyVerify)
    }

    fun syncFcmTokenIfNeeded(serverDeviceToken: String?) {
        FcmTokenSync.syncIfNeeded(registerDeviceTokenUC, viewModelScope, serverDeviceToken)
    }
}
