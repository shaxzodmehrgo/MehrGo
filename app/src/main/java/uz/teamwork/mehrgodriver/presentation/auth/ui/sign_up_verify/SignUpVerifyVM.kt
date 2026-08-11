package uz.teamwork.mehrgodriver.presentation.auth.ui.sign_up_verify

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.SignUpResendCode
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.auth.SignUpResendCodeUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.SignUpVerifyCodeUC
import javax.inject.Inject

@HiltViewModel
class SignUpVerifyVM @Inject constructor(
    private val signUpVerifyCodeUC: SignUpVerifyCodeUC,
    private val signUpResendCodeUC: SignUpResendCodeUC
) : ViewModel() {

    fun signUpVerifyCode(
        authKey: String,
        verifyCode: Int,
        deviceToken: String? = null
    ): Flow<Resource<BaseResponse<User>>> {
        return signUpVerifyCodeUC.invoke(authKey, verifyCode, deviceToken)
    }

    fun signUpResendCode(
        authKey: String,
        channel: String = "sms"
    ): Flow<Resource<BaseResponse<SignUpResendCode>>> {
        return signUpResendCodeUC.invoke(authKey, channel)
    }
}