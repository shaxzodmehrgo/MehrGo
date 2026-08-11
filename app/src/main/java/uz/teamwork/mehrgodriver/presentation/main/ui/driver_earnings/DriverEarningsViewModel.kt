package uz.teamwork.mehrgodriver.presentation.main.ui.driver_earnings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.DriverEarningsSummary
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.main.DriverEarningsSummaryUC
import javax.inject.Inject

@HiltViewModel
class DriverEarningsViewModel @Inject constructor(
    private val driverEarningsSummaryUC: DriverEarningsSummaryUC
) : ViewModel() {

    fun getSummary(
        period: String,
        from: String? = null,
        to: String? = null
    ): Flow<Resource<BaseResponse<DriverEarningsSummary>>> {
        return driverEarningsSummaryUC.invoke(period, from, to)
    }
}
