package uz.teamwork.mehrgodriver.presentation.main.ui.my_payment

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.paylov.CardCreateResult
import uz.teamwork.mehrgodriver.domain.model.paylov.CardsResponse
import uz.teamwork.mehrgodriver.domain.model.paylov.PaylovCard
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalInfo
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalRequest
import uz.teamwork.mehrgodriver.domain.use_case.main.UserUC
import uz.teamwork.mehrgodriver.domain.use_case.paylov.CancelWithdrawalUC
import uz.teamwork.mehrgodriver.domain.use_case.paylov.ConfirmCardUC
import uz.teamwork.mehrgodriver.domain.use_case.paylov.CreateCardUC
import uz.teamwork.mehrgodriver.domain.use_case.paylov.CreateWithdrawalUC
import uz.teamwork.mehrgodriver.domain.use_case.paylov.DeleteCardUC
import uz.teamwork.mehrgodriver.domain.use_case.paylov.GetCardsUC
import uz.teamwork.mehrgodriver.domain.use_case.paylov.WithdrawalInfoUC
import javax.inject.Inject

@HiltViewModel
class MyPaymentViewModel @Inject constructor(
    private val userUC: UserUC,
    private val getCardsUC: GetCardsUC,
    private val withdrawalInfoUC: WithdrawalInfoUC,
    private val createCardUC: CreateCardUC,
    private val confirmCardUC: ConfirmCardUC,
    private val deleteCardUC: DeleteCardUC,
    private val createWithdrawalUC: CreateWithdrawalUC,
    private val cancelWithdrawalUC: CancelWithdrawalUC
) : ViewModel() {
    fun getUser(): Flow<Resource<BaseResponse<User>>> {
        return userUC.invoke()
    }

    fun getCards(): Flow<Resource<BaseResponse<CardsResponse>>> {
        return getCardsUC.invoke()
    }

    fun withdrawalInfo(): Flow<Resource<BaseResponse<WithdrawalInfo>>> {
        return withdrawalInfoUC.invoke()
    }

    fun createCard(
        cardNumber: String,
        expireDate: String
    ): Flow<Resource<BaseResponse<CardCreateResult>>> {
        return createCardUC.invoke(cardNumber, expireDate)
    }

    fun confirmCard(cardId: String, otp: String): Flow<Resource<BaseResponse<PaylovCard>>> {
        return confirmCardUC.invoke(cardId, otp)
    }

    fun deleteCard(cardId: String): Flow<Resource<BaseResponse<Any>>> {
        return deleteCardUC.invoke(cardId)
    }

    fun createWithdrawal(
        cardId: String,
        amount: Long
    ): Flow<Resource<BaseResponse<WithdrawalRequest>>> {
        return createWithdrawalUC.invoke(cardId, amount)
    }

    fun cancelWithdrawal(id: Int): Flow<Resource<BaseResponse<Any>>> {
        return cancelWithdrawalUC.invoke(id)
    }
}
