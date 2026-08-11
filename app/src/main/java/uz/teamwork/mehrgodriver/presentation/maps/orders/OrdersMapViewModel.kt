package uz.teamwork.mehrgodriver.presentation.maps.orders

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.model.Tariff
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.requests.OrderCreateRequest
import uz.teamwork.mehrgodriver.domain.use_case.main.AllOrdersUC
import uz.teamwork.mehrgodriver.domain.use_case.main.GetTariffsUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderAcceptUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderCreateUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderSkipUC
import javax.inject.Inject

@HiltViewModel
class OrdersMapViewModel @Inject constructor(
    private val allOrdersUC: AllOrdersUC,
    private val orderAcceptUC: OrderAcceptUC,
    private val orderSkipUC: OrderSkipUC,
    private val getTariffsUC: GetTariffsUC,
    private val orderCreateUC: OrderCreateUC
) : ViewModel() {

    fun getAllOrders(): Flow<Resource<BaseResponse<List<Order>>>> {
        return allOrdersUC.invoke()
    }

    fun orderAccept(orderId: Int): Flow<Resource<BaseResponse<Order>>> {
        return orderAcceptUC.invoke(orderId)
    }

    fun orderSkip(orderId: Int): Flow<Resource<BaseResponse<Any>>> {
        return orderSkipUC.invoke(orderId)
    }

    fun getTariffs(branchId: Int): Flow<Resource<BaseResponse<List<Tariff>>>> {
        return getTariffsUC.invoke(branchId)
    }

    fun orderCreate(request: OrderCreateRequest): Flow<Resource<BaseResponse<Any>>> {
        return orderCreateUC.invoke(request)
    }
}