package uz.teamwork.mehrgodriver.presentation.auth.ui.password_recovery

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.PasswordRecovery
import uz.teamwork.mehrgodriver.domain.model.TelegramAuthStatus
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.auth.PasswordRecoveryUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.TelegramAuthStatusUC
import javax.inject.Inject

@HiltViewModel
class PasswordRecoveryVM @Inject constructor(
    private val passwordRecoveryUC: PasswordRecoveryUC,
    private val telegramAuthStatusUC: TelegramAuthStatusUC
) : ViewModel() {
    fun passwordRecovery(
        phoneNumber: String,
        channel: String = Constants.CHANNEL_SMS
    ): Flow<Resource<BaseResponse<PasswordRecovery>>> {
        return passwordRecoveryUC.invoke(phoneNumber, channel)
    }

    fun telegramAuthStatus(phone: String): Flow<Resource<BaseResponse<TelegramAuthStatus>>> {
        return telegramAuthStatusUC.invoke(phone)
    }
}