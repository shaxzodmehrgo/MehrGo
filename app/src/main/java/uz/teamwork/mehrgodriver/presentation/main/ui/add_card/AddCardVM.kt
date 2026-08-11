package uz.teamwork.mehrgodriver.presentation.main.ui.add_card

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.paylov.CardCreateResult
import uz.teamwork.mehrgodriver.domain.use_case.paylov.CreateCardUC
import javax.inject.Inject

@HiltViewModel
class AddCardVM @Inject constructor(
    private val createCardUC: CreateCardUC
) : ViewModel() {
    fun createCard(
        cardNumber: String,
        expireDate: String
    ): Flow<Resource<BaseResponse<CardCreateResult>>> {
        return createCardUC.invoke(cardNumber, expireDate)
    }
}
