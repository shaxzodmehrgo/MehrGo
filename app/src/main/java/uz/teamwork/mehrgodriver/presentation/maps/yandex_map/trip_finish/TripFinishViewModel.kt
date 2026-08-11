package uz.teamwork.mehrgodriver.presentation.maps.yandex_map.trip_finish

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderSettlementUC
import javax.inject.Inject

@HiltViewModel
class TripFinishViewModel @Inject constructor(
    private val orderSettlementUC: OrderSettlementUC
) : ViewModel() {

    fun settlement(orderId: Int) = orderSettlementUC(orderId)
}
