package uz.teamwork.mehrgodriver.presentation.activity.main

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.model.OrderCancelReason
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.main.ActiveMyOrdersUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderAcceptUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderArriveUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderCancelReasonsUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderSkipUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderStartUC
import uz.teamwork.mehrgodriver.domain.use_case.main.SocketOrderCancelUC
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val orderAcceptUC: OrderAcceptUC,
    private val orderSkipUC: OrderSkipUC,
    private val activeMyOrders: ActiveMyOrdersUC,
    private val orderCancelReason: OrderCancelReasonsUC,
    private val socketOrderCancelUC: SocketOrderCancelUC,
    private val orderStartUC: OrderStartUC,
    private val orderArriveUC: OrderArriveUC
) : ViewModel() {

    fun orderAccept(orderId: Int): Flow<Resource<BaseResponse<Order>>> {
        return orderAcceptUC.invoke(orderId)
    }

    fun orderSkip(orderId: Int): Flow<Resource<Any>> {
        return orderSkipUC.invoke(orderId)
    }

    fun getActiveMyOrders(): Flow<Resource<BaseResponse<List<Order>>>> {
        return activeMyOrders.invoke()
    }

    fun getOrderCancelReasons(): Flow<Resource<BaseResponse<List<OrderCancelReason>>>> {
        return orderCancelReason.invoke()
    }

    fun orderCancel(orderId: Int, issueId: Int): Flow<Resource<BaseResponse<Any>>> {
        return socketOrderCancelUC.invoke(orderId, issueId)
    }

    fun orderStart(orderId: Int): Flow<Resource<BaseResponse<Order>>> {
        return orderStartUC.invoke(orderId)
    }

    fun orderArrive(orderId: Int): Flow<Resource<BaseResponse<Order>>> {
        return orderArriveUC.invoke(orderId)
    }
}