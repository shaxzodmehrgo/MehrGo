package uz.teamwork.mehrgodriver.presentation.auth.ui.sign_up

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.SignUp
import uz.teamwork.mehrgodriver.domain.model.TelegramAuthStatus
import uz.teamwork.mehrgodriver.domain.model.TermsOfUse
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.auth.SignUpUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.TelegramAuthStatusUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.TermsOfUseUC
import javax.inject.Inject

@HiltViewModel
class SignUpVM @Inject constructor(
    private val signUpUC: SignUpUC,
    private val termsOfUseUC: TermsOfUseUC,
    private val telegramAuthStatusUC: TelegramAuthStatusUC
) : ViewModel() {
    fun signUp(
        phoneNumber: String,
        firstName: String,
        fatherName: String,
        lastName: String,
        password: String,
        confirmPassword: String,
        channel: String = Constants.CHANNEL_SMS
    ): Flow<Resource<BaseResponse<SignUp>>> {
        return signUpUC.invoke(
            phoneNumber,
            firstName,
            fatherName,
            lastName,
            password,
            confirmPassword,
            channel
        )
    }

    fun telegramAuthStatus(phone: String): Flow<Resource<BaseResponse<TelegramAuthStatus>>> {
        return telegramAuthStatusUC.invoke(phone)
    }

    fun termsOfUse(): Flow<Resource<BaseResponse<TermsOfUse>>> {
        return termsOfUseUC.invoke()
    }
}
