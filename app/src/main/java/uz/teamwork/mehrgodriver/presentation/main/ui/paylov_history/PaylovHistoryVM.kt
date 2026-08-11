package uz.teamwork.mehrgodriver.presentation.main.ui.paylov_history

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.paylov.BalanceHistory
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalList
import uz.teamwork.mehrgodriver.domain.use_case.paylov.BalanceHistoryUC
import uz.teamwork.mehrgodriver.domain.use_case.paylov.WithdrawalHistoryUC
import javax.inject.Inject

@HiltViewModel
class PaylovHistoryVM @Inject constructor(
    private val withdrawalHistoryUC: WithdrawalHistoryUC,
    private val balanceHistoryUC: BalanceHistoryUC
) : ViewModel() {
    fun withdrawalHistory(page: Int): Flow<Resource<BaseResponse<WithdrawalList>>> {
        return withdrawalHistoryUC.invoke(page, PER_PAGE)
    }

    /** [type]: null = all, 1 = income only, 2 = expense only (server-side filter). */
    fun balanceHistory(page: Int, type: Int?): Flow<Resource<BaseResponse<BalanceHistory>>> {
        return balanceHistoryUC.invoke(page, PER_PAGE, type)
    }

    companion object {
        const val PER_PAGE = 20
    }
}
