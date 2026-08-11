package uz.teamwork.mehrgodriver.presentation.main.ui.add_card_confirm

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.paylov.CardCreateResult
import uz.teamwork.mehrgodriver.domain.model.paylov.PaylovCard
import uz.teamwork.mehrgodriver.domain.use_case.paylov.ConfirmCardUC
import uz.teamwork.mehrgodriver.domain.use_case.paylov.CreateCardUC
import javax.inject.Inject

@HiltViewModel
class AddCardConfirmVM @Inject constructor(
    private val confirmCardUC: ConfirmCardUC,
    private val createCardUC: CreateCardUC
) : ViewModel() {
    fun confirmCard(cardId: String, otp: String): Flow<Resource<BaseResponse<PaylovCard>>> {
        return confirmCardUC.invoke(cardId, otp)
    }

    /** Resend = re-registering the same card; Paylov issues a fresh cardId + OTP. */
    fun resend(
        cardNumber: String,
        expireDate: String
    ): Flow<Resource<BaseResponse<CardCreateResult>>> {
        return createCardUC.invoke(cardNumber, expireDate)
    }
}
